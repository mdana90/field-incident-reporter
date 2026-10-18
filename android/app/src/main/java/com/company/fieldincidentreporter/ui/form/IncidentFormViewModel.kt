package com.company.fieldincidentreporter.ui.form

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.company.fieldincidentreporter.data.CameraPhoto
import com.company.fieldincidentreporter.data.DraftPhotoStore
import com.company.fieldincidentreporter.data.IncidentRepository
import com.company.fieldincidentreporter.data.ReportInput
import com.company.fieldincidentreporter.data.offline.DraftReports
import com.company.fieldincidentreporter.data.offline.DraftState
import com.company.fieldincidentreporter.model.Result
import com.company.fieldincidentreporter.model.Severity
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import timber.log.Timber

data class FormPhoto(val id: String? = null, val url: String? = null, val localFile: File? = null) {
    val key: String get() = id ?: localFile?.path.orEmpty()
}

@Serializable
private data class SavedFormPhoto(val id: String? = null, val url: String? = null, val path: String? = null)

data class FormUiState(
    val title: String = "",
    val description: String = "",
    val severity: Severity? = null,
    val location: String = "",
    val photos: List<FormPhoto> = emptyList(),
    val loading: Boolean = false,
    val loadError: Boolean = false,
    val saving: Boolean = false,
    val dirty: Boolean = false,
    val error: String? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val savedId: String? = null,
    val draftSaved: Boolean = false,
    val sendUnconfirmed: Boolean = false,
    val unconfirmed: Boolean = false,
    val hasSavedDraft: Boolean = false,
)

fun validateReport(
    title: String,
    description: String,
    severity: Severity?,
    location: String,
    photoCount: Int,
): Map<String, String> = buildMap {
    if (title.isBlank()) put("title", "Required")
    if (description.isBlank()) put("description", "Required")
    if (severity == null) put("severity", "Choose a severity")
    if (location.isBlank()) put("location", "Required")
    if (photoCount !in 1..3) put("photos", "Choose 1 to 3 photos")
}

@HiltViewModel
class IncidentFormViewModel @Inject constructor(
    private val repository: IncidentRepository,
    private val photoStorage: DraftPhotoStore,
    private val drafts: DraftReports,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val editId = savedStateHandle.get<String>("id")?.takeIf(String::isNotBlank)
    val draftId = savedStateHandle.get<String>("draftId")?.takeIf(String::isNotBlank)
    private val hadRequestId = !savedStateHandle.get<String>("clientRequestId").isNullOrBlank()
    private val requestId: String = draftId ?: savedStateHandle["clientRequestId"]
        ?: UUID.randomUUID().toString().also { savedStateHandle["clientRequestId"] = it }
    private val persistedDraftId = draftId ?: if (savedStateHandle.get<Boolean>("draftStored") == true) requestId else null
    private val shouldCheckDraft = editId == null && (draftId != null || hadRequestId)
    private val restored = restoreForm()
    private var savedInput: ReportInput? = null
    private val _state = MutableStateFlow((restored ?: FormUiState()).copy(
        loading = if (editId != null) restored == null else shouldCheckDraft,
    ))
    val state = _state.asStateFlow()

    init {
        when {
            shouldCheckDraft -> loadDraft()
            editId != null && restored == null -> loadIncident()
        }
    }

    fun newCameraPhoto(): CameraPhoto = photoStorage.newCameraPhoto(requestId)

    fun cameraResult(path: String, success: Boolean) {
        val file = File(path)
        if (!success || !canChangePhotos() || _state.value.photos.size >= 3) {
            photoStorage.deletePhoto(file)
            return
        }
        viewModelScope.launch {
            when (val result = photoStorage.acceptCameraPhoto(requestId, file)) {
                is Result.Success -> addLocalPhoto(result.data)
                is Result.Error -> {
                    photoStorage.deletePhoto(file)
                    _state.update { it.copy(error = result.message) }
                }
            }
        }
    }

    fun discardChanges() {
        val retained = savedInput?.newPhotos.orEmpty().toSet()
        if (savedInput == null) {
            if (persistedDraftId == null && !_state.value.loading && !_state.value.loadError) photoStorage.deleteDraft(requestId)
        } else {
            _state.value.photos.mapNotNull(FormPhoto::localFile).filterNot { it in retained }.forEach(photoStorage::deletePhoto)
        }
        savedStateHandle["draftExists"] = false
    }

    fun showError(message: String) = _state.update { it.copy(error = message) }

    private fun loadDraft() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, loadError = false, error = null) }
            val draft = when (val result = drafts.get(requestId)) {
                is Result.Success -> result.data
                is Result.Error -> {
                    _state.update { it.copy(loading = false, loadError = true, error = "Could not check saved drafts on this device. Try again.") }
                    return@launch
                }
            }
            if (draft == null) {
                _state.update {
                    if (persistedDraftId == null) it.copy(loading = false)
                    else it.copy(loading = false, loadError = true, error = "Saved draft could not be found.")
                }
                return@launch
            }
            savedInput = draft.input
            val savedForm = FormUiState(
                title = draft.input.title,
                description = draft.input.description,
                severity = draft.input.severity,
                location = draft.input.location,
                photos = draft.input.newPhotos.map { FormPhoto(localFile = it) },
            )
            val inProgress = restored?.takeIf { it.dirty && draft.state == DraftState.DRAFT }
            _state.value = (inProgress ?: savedForm).copy(
                loading = false,
                loadError = false,
                unconfirmed = draft.state == DraftState.UNCONFIRMED,
                hasSavedDraft = true,
                dirty = inProgress != null,
            )
            persistForm()
        }
    }

    private fun loadIncident() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val result = repository.get(requireNotNull(editId))) {
                is Result.Success -> {
                    val incident = result.data
                    _state.update {
                        it.copy(
                            title = incident.title, description = incident.description,
                            severity = incident.severity, location = incident.location,
                            photos = incident.photos.map { photo -> FormPhoto(id = photo.id, url = photo.url) },
                            loading = false,
                        )
                    }
                    persistForm()
                }
                is Result.Error -> _state.update { it.copy(loading = false, error = result.message) }
            }
        }
    }

    fun retryLoad() {
        if (editId != null) loadIncident() else loadDraft()
    }

    private fun canChangePhotos() = !_state.value.saving && !_state.value.loading && !_state.value.loadError && !_state.value.unconfirmed
    private fun changeForm(change: (FormUiState) -> FormUiState) {
        if (_state.value.saving || _state.value.loading || _state.value.loadError || _state.value.unconfirmed) return
        _state.update(change)
        persistForm()
    }

    fun title(value: String) = changeForm { it.copy(title = value, dirty = true, fieldErrors = it.fieldErrors - "title") }
    fun description(value: String) = changeForm { it.copy(description = value, dirty = true, fieldErrors = it.fieldErrors - "description") }
    fun location(value: String) = changeForm { it.copy(location = value, dirty = true, fieldErrors = it.fieldErrors - "location") }
    fun severity(value: Severity) = changeForm { it.copy(severity = value, dirty = true, fieldErrors = it.fieldErrors - "severity") }

    fun addPhoto(uri: Uri) {
        if (!canChangePhotos() || _state.value.photos.size >= 3) return
        viewModelScope.launch {
            when (val result = photoStorage.copyPickedPhoto(requestId, uri)) {
                is Result.Success -> addLocalPhoto(result.data)
                is Result.Error -> _state.update { it.copy(error = result.message) }
            }
        }
    }

    private fun addLocalPhoto(file: File) {
        if (!canChangePhotos() || _state.value.photos.size >= 3) {
            photoStorage.deletePhoto(file)
            return
        }
        changeForm { it.copy(photos = it.photos + FormPhoto(localFile = file), dirty = true, fieldErrors = it.fieldErrors - "photos") }
    }

    fun removePhoto(key: String) {
        if (!canChangePhotos()) return
        val file = _state.value.photos.firstOrNull { it.key == key }?.localFile
        changeForm { it.copy(photos = it.photos.filterNot { photo -> photo.key == key }, dirty = true) }
        if (file != null && file !in savedInput?.newPhotos.orEmpty()) photoStorage.deletePhoto(file)
    }

    fun saveDraft() = submit(send = false)
    fun sendReport() = submit(send = true)

    private fun submit(send: Boolean) {
        val current = _state.value
        if (current.saving || current.loading || current.loadError || current.savedId != null || current.draftSaved || current.sendUnconfirmed) return
        if (current.unconfirmed && !send) return
        val errors = validateReport(current.title, current.description, current.severity, current.location, current.photos.size)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(fieldErrors = errors) }
            return
        }
        val input = ReportInput(
            title = current.title.trim(), description = current.description.trim(),
            severity = requireNotNull(current.severity), location = current.location.trim(),
            newPhotos = current.photos.mapNotNull(FormPhoto::localFile),
            keepPhotoIds = current.photos.mapNotNull(FormPhoto::id), clientRequestId = requestId,
        )
        val payload = if (current.unconfirmed) savedInput ?: return else input
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            if (editId != null) {
                updateIncident(editId, input)
                return@launch
            }
            if (photoStorage.validatePhotos(input.newPhotos) is Result.Error) {
                val message = if (current.unconfirmed) "A photo in this draft is missing or invalid. Delete the draft and report again."
                else "A selected photo is missing or invalid. Choose it again before sending."
                _state.update { it.copy(saving = false, error = message) }
                return@launch
            }
            if (send) sendToServer(payload, alreadyUnconfirmed = current.unconfirmed) else storeDraft(input)
        }
    }

    private suspend fun updateIncident(id: String, input: ReportInput) {
        when (val result = repository.update(id, input)) {
            is Result.Success -> {
                clearDraftFiles()
                _state.update { it.copy(saving = false, savedId = result.data.id, dirty = false) }
                persistForm()
            }
            is Result.Error -> _state.update { it.copy(saving = false, error = result.message) }
        }
    }

    private suspend fun storeDraft(input: ReportInput) {
        if (drafts.save(input, DraftState.DRAFT) is Result.Error) {
            _state.update { it.copy(saving = false, error = DEVICE_SAVE_ERROR) }
            return
        }
        removeOldPhotos(input)
        savedInput = input
        _state.update { it.copy(saving = false, dirty = false, draftSaved = true, hasSavedDraft = true) }
        persistForm()
    }

    private suspend fun sendToServer(payload: ReportInput, alreadyUnconfirmed: Boolean) {
        if (!alreadyUnconfirmed) {
            if (drafts.save(payload, DraftState.UNCONFIRMED) is Result.Error) {
                _state.update { it.copy(saving = false, error = DEVICE_SAVE_ERROR) }
                return
            }
            removeOldPhotos(payload)
            savedInput = payload
            persistForm()
        }
        when (val result = repository.create(payload)) {
            is Result.Success -> {
                if (drafts.delete(requestId) is Result.Error) {
                    _state.update { it.copy(saving = false, unconfirmed = true, error = DEVICE_SAVE_ERROR) }
                    return
                }
                savedInput = null
                savedStateHandle["draftExists"] = false
                _state.update { it.copy(saving = false, savedId = result.data.id, dirty = false) }
            }
            is Result.Error -> if ((result.exception as? HttpException)?.code() in listOf(400, 413, 422)) {
                if (drafts.save(payload, DraftState.DRAFT) is Result.Error) {
                    _state.update { it.copy(saving = false, unconfirmed = true, error = DEVICE_SAVE_ERROR) }
                    return
                }
                _state.update { it.copy(saving = false, unconfirmed = false, hasSavedDraft = true, error = result.message) }
            } else {
                _state.update { it.copy(saving = false, unconfirmed = true, hasSavedDraft = true, sendUnconfirmed = true, dirty = false) }
            }
        }
        persistForm()
    }

    private fun removeOldPhotos(input: ReportInput) {
        savedInput?.newPhotos.orEmpty().filterNot { it in input.newPhotos }.forEach(photoStorage::deletePhoto)
    }

    private fun clearDraftFiles() {
        runCatching { photoStorage.deleteDraft(requestId) }
            .onFailure { Timber.w(it, "Failed to delete draft photos") }
    }

    private fun persistForm() {
        val form = _state.value
        if (form.savedId != null) {
            savedStateHandle["draftExists"] = false
            savedStateHandle["draftStored"] = false
            return
        }
        savedStateHandle["draftTitle"] = form.title
        savedStateHandle["draftDescription"] = form.description
        savedStateHandle["draftSeverity"] = form.severity?.name
        savedStateHandle["draftLocation"] = form.location
        savedStateHandle["draftPhotos"] = Json.encodeToString(form.photos.map {
            SavedFormPhoto(id = it.id, url = it.url, path = it.localFile?.absolutePath)
        })
        savedStateHandle["draftDirty"] = form.dirty
        savedStateHandle["draftSavedId"] = form.savedId
        savedStateHandle["draftStored"] = savedInput != null
        savedStateHandle["draftExists"] = true
    }

    private fun restoreForm(): FormUiState? {
        if (savedStateHandle.get<Boolean>("draftExists") != true) return null
        val photos = runCatching {
            Json.decodeFromString<List<SavedFormPhoto>>(savedStateHandle.get<String>("draftPhotos") ?: "[]")
        }.getOrDefault(emptyList()).map { FormPhoto(it.id, it.url, it.path?.let(::File)) }
        val severity = savedStateHandle.get<String>("draftSeverity")?.let { name ->
            Severity.entries.firstOrNull { it.name == name }
        }
        return FormUiState(
            title = savedStateHandle["draftTitle"] ?: "",
            description = savedStateHandle["draftDescription"] ?: "",
            severity = severity, location = savedStateHandle["draftLocation"] ?: "",
            photos = photos, dirty = savedStateHandle["draftDirty"] ?: false,
            savedId = savedStateHandle["draftSavedId"],
            hasSavedDraft = savedStateHandle["draftStored"] ?: false,
        )
    }

    companion object {
        private const val DEVICE_SAVE_ERROR = "Could not save the report on this device. Try again."
    }
}
