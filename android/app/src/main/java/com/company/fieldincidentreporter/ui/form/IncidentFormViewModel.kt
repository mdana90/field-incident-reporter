package com.company.fieldincidentreporter.ui.form

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.company.fieldincidentreporter.data.IncidentRepository
import com.company.fieldincidentreporter.data.PhotoStorage
import com.company.fieldincidentreporter.data.ReportInput
import com.company.fieldincidentreporter.data.offline.PendingReportStore
import com.company.fieldincidentreporter.model.Severity
import com.company.fieldincidentreporter.ui.FormRoute
import com.company.fieldincidentreporter.ui.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import retrofit2.HttpException

data class FormPhoto(val id: String? = null, val url: String? = null, val localFile: File? = null) {
    val key: String get() = id ?: localFile?.path.orEmpty()
}

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
    private val photoStorage: PhotoStorage,
    private val pendingStore: PendingReportStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val editId = savedStateHandle.toRoute<FormRoute>().id.takeIf(String::isNotBlank)
    private val requestId: String = savedStateHandle["clientRequestId"]
        ?: UUID.randomUUID().toString().also { savedStateHandle["clientRequestId"] = it }
    private val _state = MutableStateFlow(FormUiState(loading = editId != null))
    val state = _state.asStateFlow()

    fun newCameraUri(): Uri = photoStorage.newCameraUri()

    fun showError(message: String) = _state.update { it.copy(error = message) }

    init {
        if (editId != null) loadIncident()
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
            } catch (failure: Exception) {
                _state.update { it.copy(loading = false, error = failure.userMessage()) }
            }
        }
    }

    fun retryLoad() {
        if (editId != null) loadIncident()
    }

    fun title(value: String) = _state.update { it.copy(title = value, dirty = true, fieldErrors = it.fieldErrors - "title") }
    fun description(value: String) = _state.update { it.copy(description = value, dirty = true, fieldErrors = it.fieldErrors - "description") }
    fun location(value: String) = _state.update { it.copy(location = value, dirty = true, fieldErrors = it.fieldErrors - "location") }
    fun severity(value: Severity) = _state.update { it.copy(severity = value, dirty = true, fieldErrors = it.fieldErrors - "severity") }

    fun addPhoto(uri: Uri) {
        if (_state.value.saving || _state.value.photos.size >= 3) return
        viewModelScope.launch {
            try {
                val file = photoStorage.copyToTemporaryFile(uri)
                _state.update {
                    if (it.photos.size >= 3) {
                        file.delete()
                        it
                    } else it.copy(photos = it.photos + FormPhoto(localFile = file), dirty = true, fieldErrors = it.fieldErrors - "photos")
                }
            } catch (failure: Exception) {
                _state.update { it.copy(error = failure.userMessage()) }
            }
        }
    }

    fun removePhoto(key: String) = _state.update {
        if (it.saving) it else it.copy(photos = it.photos.filterNot { photo -> photo.key == key }, dirty = true)
    }

    fun save() {
        val current = _state.value
        if (current.saving || current.loading || current.savedId != null) return
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
                _state.update { it.copy(saving = false, savedId = saved.id, dirty = false) }
            } catch (failure: Exception) {
                if (editId == null && (failure is IOException || failure is HttpException && failure.code() >= 500)) {
                    try {
                        pendingStore.queue(input)
                        _state.update { it.copy(saving = false, queued = true, dirty = false) }
                    } catch (queueFailure: Exception) {
                        _state.update { it.copy(saving = false, error = "Could not save the report for later: ${queueFailure.message}") }
                    }
                } else {
                    _state.update { it.copy(saving = false, error = failure.userMessage()) }
                }
            }
        }
    }
}
