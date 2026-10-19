package com.company.fieldincidentreporter.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.company.fieldincidentreporter.data.IncidentRepository
import com.company.fieldincidentreporter.data.offline.DraftReports
import com.company.fieldincidentreporter.di.IoDispatcher
import com.company.fieldincidentreporter.model.DraftReport
import com.company.fieldincidentreporter.model.Incident
import com.company.fieldincidentreporter.model.Result
import com.company.fieldincidentreporter.ui.incidentLocalDate
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/** An incident with its local date already formatted, so list rows do not re-parse it on every recomposition. */
data class IncidentListItem(val incident: Incident, val reportedOn: String)

data class ListUiState(
    val incidents: List<IncidentListItem> = emptyList(),
    val drafts: List<DraftReport> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val draftError: String? = null,
) {
    val isEmpty: Boolean get() = incidents.isEmpty() && drafts.isEmpty()
}

@HiltViewModel
class IncidentListViewModel @Inject constructor(
    private val repository: IncidentRepository,
    private val drafts: DraftReports,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val _state = MutableStateFlow(ListUiState())
    val state = _state.asStateFlow()
    private var refreshJob: Job? = null

    init {
        drafts.observe()
            .flowOn(dispatcher)
            .onEach { reports -> _state.update { it.copy(drafts = reports, draftError = null) } }
            .catch { failure ->
                Timber.w(failure, "Failed to load drafts")
                _state.update { it.copy(draftError = "Could not load saved drafts on this device.") }
            }
            .launchIn(viewModelScope)
    }

    fun deleteDraft(id: String) = viewModelScope.launch {
        if (withContext(dispatcher) { drafts.delete(id) } is Result.Error) {
            _state.update { it.copy(draftError = "Could not delete this draft. Try again.") }
        }
    }

    fun refresh(fromPull: Boolean = false) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, refreshing = fromPull, error = null) }
            when (val result = withContext(dispatcher) { repository.list() }) {
                is Result.Success -> {
                    val incidents = result.data.map { incident -> IncidentListItem(incident, incidentLocalDate(incident.createdAt)) }
                    _state.update { it.copy(incidents = incidents, loading = false, refreshing = false) }
                }
                is Result.Error -> _state.update { it.copy(loading = false, refreshing = false, error = result.message) }
            }
        }
    }
}
