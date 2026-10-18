package com.company.fieldincidentreporter.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.company.fieldincidentreporter.data.IncidentRepository
import com.company.fieldincidentreporter.data.offline.DraftReport
import com.company.fieldincidentreporter.data.offline.DraftReports
import com.company.fieldincidentreporter.model.Incident
import com.company.fieldincidentreporter.ui.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.util.logging.Logger
import java.util.logging.Level
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ListUiState(
    val incidents: List<Incident> = emptyList(),
    val drafts: List<DraftReport> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val draftError: String? = null,
)

@HiltViewModel
class IncidentListViewModel @Inject constructor(
    private val repository: IncidentRepository,
    private val drafts: DraftReports,
) : ViewModel() {
    private val logger = Logger.getLogger(IncidentListViewModel::class.java.name)
    private val _state = MutableStateFlow(ListUiState())
    val state = _state.asStateFlow()
    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                drafts.observe().collect { reports -> _state.update { it.copy(drafts = reports, draftError = null) } }
            } catch (failure: Exception) {
                logger.log(Level.WARNING, "Failed to load drafts", failure)
                _state.update { it.copy(draftError = "Could not load saved drafts on this device.") }
            }
        }
    }

    fun deleteDraft(id: String) = viewModelScope.launch {
        try {
            drafts.delete(id)
        } catch (failure: Exception) {
            logger.log(Level.WARNING, "Failed to delete draft", failure)
            _state.update { it.copy(draftError = "Could not delete this draft. Try again.") }
        }
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val incidents = repository.list()
                _state.update { it.copy(incidents = incidents, loading = false) }
            } catch (failure: Exception) {
                logger.log(Level.WARNING, "Failed to load incidents", failure)
                _state.update { it.copy(loading = false, error = failure.userMessage()) }
            }
        }
    }
}
