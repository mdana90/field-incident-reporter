package com.company.fieldincidentreporter.ui.form

import android.net.Uri
import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.company.fieldincidentreporter.data.DraftPhotoStore
import com.company.fieldincidentreporter.data.IncidentRepository
import com.company.fieldincidentreporter.data.isRejection
import com.company.fieldincidentreporter.data.offline.DraftReports
import com.company.fieldincidentreporter.di.ApplicationScope
import com.company.fieldincidentreporter.di.IoDispatcher
import com.company.fieldincidentreporter.model.DraftState
import com.company.fieldincidentreporter.model.ReportInput
import com.company.fieldincidentreporter.model.Result
import com.company.fieldincidentreporter.model.Severity
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import timber.log.Timber

data class FormPhoto(val id: String? = null, val url: String? = null, val localFile: File? = null) {
    val key: String get() = id ?: localFile?.path.orEmpty()
}

@Parcelize
internal data class SavedFormPhoto(val id: String? = null, val url: String? = null, val path: String? = null) : Parcelable

/** Snapshot of form fields kept in [SavedStateHandle] so edits survive process death. */
@Parcelize
internal data class SavedForm(
    val title: String,
    val description: String,
    val severity: Severity?,
    val location: String,
    val photos: List<SavedFormPhoto>,
    val dirty: Boolean,
    val stored: Boolean,
) : Parcelable

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
    val importingPhotos: Int = 0,
) {
    /** Fields and photos accept changes only once loaded, while nothing is saving and the draft is not locked by an unconfirmed send. */
    val editable: Boolean get() = !saving && !loading && !loadError && !unconfirmed
    val hasPhotoSlots: Boolean get() = photos.size < MAX_PHOTOS
    /** Waits for photo imports so a quick tap cannot submit without the photo just picked. */
    val canSubmit: Boolean get() = !saving && !loading && !loadError && importingPhotos == 0
}

const val MAX_PHOTOS = 3

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
    if (photoCount !in 1..MAX_PHOTOS) put("photos", "Choose 1 to $MAX_PHOTOS photos")
}

@HiltViewModel
class IncidentFormViewModel @Inject constructor(
    private val repository: IncidentRepository,
    private val photoStorage: DraftPhotoStore,
    private val drafts: DraftReports,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
    @ApplicationScope private val appScope: CoroutineScope,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    // FormRoute args are read by key rather than toRoute(), which needs Robolectric in JVM tests.
    val editId = savedStateHandle.get<String>(KEY_EDIT_ID)?.takeIf(String::isNotBlank)
    private val draftId = savedStateHandle.get<String>(KEY_DRAFT_ID)?.takeIf(String::isNotBlank)
    private val hadRequestId = !savedStateHandle.get<String>(KEY_REQUEST_ID).isNullOrBlank()
    private val requestId: String = draftId ?: savedStateHandle[KEY_REQUEST_ID]
        ?: UUID.randomUUID().toString().also { savedStateHandle[KEY_REQUEST_ID] = it }
    private val savedForm: SavedForm? = savedStateHandle.get<SavedForm>(KEY_FORM)
    private val persistedDraftId = draftId ?: if (savedForm?.stored == true) requestId else null
    private val shouldCheckDraft = editId == null && (draftId != null || hadRequestId)
    private val restored = savedForm?.toUiState()
    private var savedInput: ReportInput? = null
    private val _state = MutableStateFlow((restored ?: FormUiState()).copy(
        loading = if (editId != null) restored == null else shouldCheckDraft,
        hasSavedDraft = restored?.hasSavedDraft == true || draftId != null,
    ))
    val state = _state.asStateFlow()

    init {
        when {
            shouldCheckDraft -> loadDraft()
            editId != null && restored == null -> loadIncident()
        }
    }

    /** Creates the file the camera app writes into and returns its URI, or null after showing an error. */
    fun prepareCameraPhoto(): Uri? {
        val photo = runCatching { photoStorage.newCameraPhoto(requestId) }
            .onFailure(::cameraLaunchFailed)
            .getOrNull() ?: return null
        savedStateHandle[KEY_CAMERA_PATH] = photo.file.absolutePath
        return photo.uri
    }

    fun cameraLaunchFailed(failure: Throwable) {
        Timber.w(failure, "Failed to open camera")
        cameraResult(success = false)
        showError("Could not open the camera. Try again.")
    }

    fun cameraResult(success: Boolean) {
        val file = savedStateHandle.remove<String>(KEY_CAMERA_PATH)?.let(::File) ?: return
        if (!success || !canAddPhoto()) {
            deleteInBackground { photoStorage.deletePhoto(file) }
            return
        }
        launchImport {
            when (val result = withContext(dispatcher) { photoStorage.acceptCameraPhoto(requestId, file) }) {
                is Result.Success -> addLocalPhoto(result.data)
                is Result.Error -> {
                    deleteInBackground { photoStorage.deletePhoto(file) }
                    _state.update { it.copy(error = result.message) }
                }
            }
        }
    }

    fun discardChanges() {
        val retained = savedInput?.newPhotos.orEmpty().toSet()
        if (savedInput == null) {
            if (persistedDraftId == null && !_state.value.loading && !_state.value.loadError) deleteInBackground { photoStorage.deleteDraft(requestId) }
        } else {
            val discarded = _state.value.photos.mapNotNull(FormPhoto::localFile).filterNot { it in retained }
            deleteInBackground { discarded.forEach(photoStorage::deletePhoto) }
        }
        savedStateHandle.remove<SavedForm>(KEY_FORM)
    }

    /** Runs on [appScope] so the cleanup still finishes after the screen closes and [viewModelScope] is cancelled. */
    private fun deleteInBackground(delete: () -> Unit) {
        appScope.launch(dispatcher) {
            runCatching(delete).onFailure { Timber.w(it, "Failed to delete draft photos") }
        }
    }

    fun showError(message: String) = _state.update { it.copy(error = message) }

    private fun loadDraft() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, loadError = false, error = null) }
            val draft = when (val result = withContext(dispatcher) { drafts.get(requestId) }) {
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
            _state.update { it.copy(loading = true, loadError = false, error = null) }
            when (val result = withContext(dispatcher) { repository.get(requireNotNull(editId)) }) {
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
                is Result.Error -> _state.update { it.copy(loading = false, loadError = true, error = result.message) }
            }
        }
    }

    fun retryLoad() {
        if (editId != null) loadIncident() else loadDraft()
    }

    private fun canAddPhoto() = _state.value.editable && _state.value.hasPhotoSlots
    private fun changeForm(change: (FormUiState) -> FormUiState) {
        if (!_state.value.editable) return
        _state.update(change)
        persistForm()
    }

    fun title(value: String) = changeForm { it.copy(title = value, dirty = true, fieldErrors = it.fieldErrors - "title") }
    fun description(value: String) = changeForm { it.copy(description = value, dirty = true, fieldErrors = it.fieldErrors - "description") }
    fun location(value: String) = changeForm { it.copy(location = value, dirty = true, fieldErrors = it.fieldErrors - "location") }
    fun severity(value: Severity) = changeForm { it.copy(severity = value, dirty = true, fieldErrors = it.fieldErrors - "severity") }

    /** Copies the picked photos that still fit, ignoring any beyond [MAX_PHOTOS]. */
    fun addPhotos(uris: List<Uri>) {
        if (!canAddPhoto()) return
        uris.take(MAX_PHOTOS - _state.value.photos.size).forEach(::addPhoto)
    }

    private fun addPhoto(uri: Uri) {
        launchImport {
            when (val result = withContext(dispatcher) { photoStorage.copyPickedPhoto(requestId, uri) }) {
                is Result.Success -> addLocalPhoto(result.data)
                is Result.Error -> _state.update { it.copy(error = result.message) }
            }
        }
    }

    private fun launchImport(import: suspend () -> Unit) {
        _state.update { it.copy(importingPhotos = it.importingPhotos + 1) }
        viewModelScope.launch {
            try {
                import()
            } finally {
                _state.update { it.copy(importingPhotos = it.importingPhotos - 1) }
            }
        }
    }

    private fun addLocalPhoto(file: File) {
        if (!canAddPhoto()) {
            deleteInBackground { photoStorage.deletePhoto(file) }
            return
        }
        changeForm { it.copy(photos = it.photos + FormPhoto(localFile = file), dirty = true, fieldErrors = it.fieldErrors - "photos") }
    }

    fun removePhoto(key: String) {
        if (!_state.value.editable) return
        val file = _state.value.photos.firstOrNull { it.key == key }?.localFile
        changeForm { it.copy(photos = it.photos.filterNot { photo -> photo.key == key }, dirty = true) }
        if (file != null && file !in savedInput?.newPhotos.orEmpty()) deleteInBackground { photoStorage.deletePhoto(file) }
    }

    fun saveDraft() = submit(send = false)
    fun sendReport() = submit(send = true)

    private fun submit(send: Boolean) {
        val current = _state.value
        if (!current.canSubmit || current.savedId != null || current.draftSaved || current.sendUnconfirmed) return
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
            if (withContext(dispatcher) { photoStorage.validatePhotos(input.newPhotos) } is Result.Error) {
                val message = if (current.unconfirmed) "A photo in this draft is missing or invalid. Delete the draft and report again."
                else "A selected photo is missing or invalid. Choose it again before sending."
                _state.update { it.copy(saving = false, error = message) }
                return@launch
            }
            if (send) sendToServer(payload, alreadyUnconfirmed = current.unconfirmed) else storeDraft(input)
        }
    }

    private suspend fun updateIncident(id: String, input: ReportInput) {
        when (val result = withContext(dispatcher) { repository.update(id, input) }) {
            is Result.Success -> {
                clearDraftFiles()
                _state.update { it.copy(saving = false, savedId = result.data.id, dirty = false) }
                persistForm()
            }
            is Result.Error -> _state.update { it.copy(saving = false, error = result.message) }
        }
    }

    private suspend fun storeDraft(input: ReportInput) {
        if (withContext(dispatcher) { drafts.save(input, DraftState.DRAFT) } is Result.Error) {
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
            if (withContext(dispatcher) { drafts.save(payload, DraftState.UNCONFIRMED) } is Result.Error) {
                _state.update { it.copy(saving = false, error = DEVICE_SAVE_ERROR) }
                return
            }
            removeOldPhotos(payload)
            savedInput = payload
            persistForm()
        }
        when (val result = withContext(dispatcher) { repository.create(payload) }) {
            is Result.Success -> {
                if (withContext(dispatcher) { drafts.delete(requestId) } is Result.Error) {
                    _state.update { it.copy(saving = false, unconfirmed = true, error = DEVICE_SAVE_ERROR) }
                    return
                }
                savedInput = null
                _state.update { it.copy(saving = false, savedId = result.data.id, dirty = false) }
            }
            is Result.Error -> if (result.exception.isRejection()) {
                if (withContext(dispatcher) { drafts.save(payload, DraftState.DRAFT) } is Result.Error) {
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

    private suspend fun removeOldPhotos(input: ReportInput) {
        val oldPhotos = savedInput?.newPhotos.orEmpty().filterNot { it in input.newPhotos }
        withContext(dispatcher) { oldPhotos.forEach(photoStorage::deletePhoto) }
    }

    private suspend fun clearDraftFiles() = withContext(dispatcher) {
        runCatching { photoStorage.deleteDraft(requestId) }
            .onFailure { Timber.w(it, "Failed to delete draft photos") }
    }

    private fun persistForm() {
        val form = _state.value
        if (form.savedId != null) {
            savedStateHandle.remove<SavedForm>(KEY_FORM)
            return
        }
        savedStateHandle[KEY_FORM] = SavedForm(
            title = form.title,
            description = form.description,
            severity = form.severity,
            location = form.location,
            photos = form.photos.map { SavedFormPhoto(id = it.id, url = it.url, path = it.localFile?.absolutePath) },
            dirty = form.dirty,
            stored = savedInput != null,
        )
    }

    companion object {
        internal const val KEY_EDIT_ID = "id"
        internal const val KEY_DRAFT_ID = "draftId"
        internal const val KEY_REQUEST_ID = "clientRequestId"
        internal const val KEY_FORM = "form"
        internal const val KEY_CAMERA_PATH = "cameraPath"
        private const val DEVICE_SAVE_ERROR = "Could not save the report on this device. Try again."
    }
}

private fun SavedForm.toUiState() = FormUiState(
    title = title,
    description = description,
    severity = severity,
    location = location,
    photos = photos.map { FormPhoto(it.id, it.url, it.path?.let(::File)) },
    dirty = dirty,
    hasSavedDraft = stored,
)
