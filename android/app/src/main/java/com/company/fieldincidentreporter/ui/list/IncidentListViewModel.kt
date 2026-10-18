package com.company.fieldincidentreporter.ui.list

import androidx.lifecycle.ViewModel
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.company.fieldincidentreporter.data.IncidentRepository
import com.company.fieldincidentreporter.data.offline.PendingReport
import com.company.fieldincidentreporter.data.offline.PendingReports
import com.company.fieldincidentreporter.model.Incident
import com.company.fieldincidentreporter.ui.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

data class ListUiState(
    val incidents: List<Incident> = emptyList(),
    val pending: List<PendingReport> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class IncidentListViewModel @Inject constructor(
    private val repository: IncidentRepository,
    private val pendingStore: PendingReports,
) : ViewModel() {
    private val _state = MutableStateFlow(ListUiState())
    val state = _state.asStateFlow()
    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            var previousIds: Set<String>? = null
            pendingStore.observe().collect { reports ->
                _state.update { it.copy(pending = reports) }
                pendingStore.ensureScheduled(reports)
                val currentIds = reports.map(PendingReport::id).toSet()
                if (previousIds?.any { it !in currentIds } == true) refresh()
                previousIds = currentIds
            }
        }
    }

    fun retry(id: String) = viewModelScope.launch { pendingStore.retry(id) }
    fun discard(id: String) = viewModelScope.launch { pendingStore.discard(id) }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val incidents = repository.list()
                _state.update { it.copy(incidents = incidents, loading = false) }
            } catch (failure: Exception) {
                Log.e("IncidentList", "Failed to load incidents", failure)
                _state.update { it.copy(loading = false, error = failure.userMessage()) }
            }
        }
    }
}
