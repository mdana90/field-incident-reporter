package com.company.fieldincidentreporter.ui.form

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.company.fieldincidentreporter.data.IncidentRepository
import com.company.fieldincidentreporter.data.CameraPhoto
import com.company.fieldincidentreporter.data.DraftPhotoStore
import com.company.fieldincidentreporter.data.ReportInput
import com.company.fieldincidentreporter.data.offline.PendingReports
import com.company.fieldincidentreporter.model.Severity
import com.company.fieldincidentreporter.ui.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import retrofit2.HttpException

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
    val saving: Boolean = false,
    val dirty: Boolean = false,
    val error: String? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val savedId: String? = null,
    val queued: Boolean = false,
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
    private val pendingStore: PendingReports,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val logger = Logger.getLogger(IncidentFormViewModel::class.java.name)
    val editId = savedStateHandle.get<String>("id")?.takeIf(String::isNotBlank)
    private val requestId: String = savedStateHandle["clientRequestId"]
        ?: UUID.randomUUID().toString().also { savedStateHandle["clientRequestId"] = it }
    private val restoredDraft = restoreDraft()
    private val _state = MutableStateFlow(restoredDraft ?: FormUiState(loading = editId != null))
    val state = _state.asStateFlow()

    fun newCameraPhoto(): CameraPhoto = photoStorage.newCameraPhoto(requestId)

    fun cameraResult(path: String, success: Boolean) {
        val file = File(path)
        if (!success || _state.value.saving || _state.value.photos.size >= 3) {
            photoStorage.deletePhoto(file)
            return
        }
        viewModelScope.launch {
            try {
                photoStorage.acceptCameraPhoto(requestId, file)
                addLocalPhoto(file)
            } catch (failure: Exception) {
                photoStorage.deletePhoto(file)
                logger.log(Level.WARNING, "Failed to add camera photo", failure)
                _state.update { it.copy(error = failure.userMessage()) }
            }
        }
    }

    fun discardDraft() {
        photoStorage.deleteDraft(requestId)
        savedStateHandle["draftExists"] = false
    }

    fun showError(message: String) = _state.update { it.copy(error = message) }

    init {
        if (editId != null && restoredDraft == null) loadIncident()
    }

    private fun loadIncident() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val incident = repository.get(requireNotNull(editId))
                _state.update {
                    it.copy(
                        title = incident.title,
                        description = incident.description,
                        severity = incident.severity,
                        location = incident.location,
                        photos = incident.photos.map { photo -> FormPhoto(id = photo.id, url = photo.url) },
                        loading = false,
                    )
                }
                persistDraft()
            } catch (failure: Exception) {
                logger.log(Level.WARNING, "Failed to load incident", failure)
                _state.update { it.copy(loading = false, error = failure.userMessage()) }
            }
        }
    }

    fun retryLoad() {
        if (editId != null) loadIncident()
    }

    fun title(value: String) = changeDraft { it.copy(title = value, dirty = true, fieldErrors = it.fieldErrors - "title") }
    fun description(value: String) = changeDraft { it.copy(description = value, dirty = true, fieldErrors = it.fieldErrors - "description") }
    fun location(value: String) = changeDraft { it.copy(location = value, dirty = true, fieldErrors = it.fieldErrors - "location") }
    fun severity(value: Severity) = changeDraft { it.copy(severity = value, dirty = true, fieldErrors = it.fieldErrors - "severity") }

    fun addPhoto(uri: Uri) {
        if (_state.value.saving || _state.value.photos.size >= 3) return
        viewModelScope.launch {
            try {
                addLocalPhoto(photoStorage.copyPickedPhoto(requestId, uri))
            } catch (failure: Exception) {
                logger.log(Level.WARNING, "Failed to add photo", failure)
                _state.update { it.copy(error = failure.userMessage()) }
            }
        }
    }

    private fun addLocalPhoto(file: File) {
        if (_state.value.saving || _state.value.photos.size >= 3) {
            photoStorage.deletePhoto(file)
            return
        }
        changeDraft {
            it.copy(photos = it.photos + FormPhoto(localFile = file), dirty = true, fieldErrors = it.fieldErrors - "photos")
        }
    }

    fun removePhoto(key: String) {
        if (_state.value.saving) return
        val file = _state.value.photos.firstOrNull { it.key == key }?.localFile
        changeDraft { it.copy(photos = it.photos.filterNot { photo -> photo.key == key }, dirty = true) }
        file?.let(photoStorage::deletePhoto)
    }

    fun save() {
        val current = _state.value
        if (current.saving || current.loading || current.savedId != null || current.queued) return
        val errors = validateReport(current.title, current.description, current.severity, current.location, current.photos.size)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(fieldErrors = errors) }
            return
        }
        val input = ReportInput(
            title = current.title.trim(),
            description = current.description.trim(),
            severity = requireNotNull(current.severity),
            location = current.location.trim(),
            newPhotos = current.photos.mapNotNull(FormPhoto::localFile),
            keepPhotoIds = current.photos.mapNotNull(FormPhoto::id),
            clientRequestId = requestId,
        )
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val saved = if (editId == null) repository.create(input) else repository.update(editId, input)
                clearDraftFiles()
                _state.update { it.copy(saving = false, savedId = saved.id, dirty = false) }
                persistDraft()
            } catch (failure: Exception) {
                logger.log(Level.WARNING, "Failed to save incident", failure)
                if (editId == null && (failure is IOException || failure is HttpException && failure.code() >= 500)) {
                    try {
                        pendingStore.queue(input)
                        clearDraftFiles()
                        _state.update { it.copy(saving = false, queued = true, dirty = false) }
                        persistDraft()
                    } catch (queueFailure: Exception) {
                        logger.log(Level.WARNING, "Failed to queue incident", queueFailure)
                        _state.update { it.copy(saving = false, error = "Could not save the report for later. Try again.") }
                    }
                } else {
                    _state.update { it.copy(saving = false, error = failure.userMessage()) }
                }
            }
        }
    }

    private fun clearDraftFiles() {
        runCatching { photoStorage.deleteDraft(requestId) }
            .onFailure { logger.log(Level.WARNING, "Failed to delete draft photos", it) }
    }

    private fun changeDraft(change: (FormUiState) -> FormUiState) {
        _state.update(change)
        persistDraft()
    }

    private fun persistDraft() {
        val draft = _state.value
        savedStateHandle["draftTitle"] = draft.title
        savedStateHandle["draftDescription"] = draft.description
        savedStateHandle["draftSeverity"] = draft.severity?.name
        savedStateHandle["draftLocation"] = draft.location
        savedStateHandle["draftPhotos"] = Json.encodeToString(draft.photos.map {
            SavedFormPhoto(id = it.id, url = it.url, path = it.localFile?.absolutePath)
        })
        savedStateHandle["draftDirty"] = draft.dirty
        savedStateHandle["draftSavedId"] = draft.savedId
        savedStateHandle["draftQueued"] = draft.queued
        savedStateHandle["draftExists"] = true
    }

    private fun restoreDraft(): FormUiState? {
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
            severity = severity,
            location = savedStateHandle["draftLocation"] ?: "",
            photos = photos,
            dirty = savedStateHandle["draftDirty"] ?: false,
            savedId = savedStateHandle["draftSavedId"],
            queued = savedStateHandle["draftQueued"] ?: false,
        )
    }
}
