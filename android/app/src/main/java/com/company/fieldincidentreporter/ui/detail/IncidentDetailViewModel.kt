package com.company.fieldincidentreporter.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.company.fieldincidentreporter.data.IncidentRepository
import com.company.fieldincidentreporter.model.Incident
import com.company.fieldincidentreporter.ui.DetailRoute
import com.company.fieldincidentreporter.ui.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailUiState(
    val incident: Incident? = null,
    val loading: Boolean = true,
    val deleting: Boolean = false,
    val deleted: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class IncidentDetailViewModel @Inject constructor(
    private val repository: IncidentRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val id = savedStateHandle.toRoute<DetailRoute>().id
    private val _state = MutableStateFlow(DetailUiState())
    val state = _state.asStateFlow()
    private var refreshJob: Job? = null

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val incident = repository.get(id)
                _state.update { it.copy(incident = incident, loading = false) }
            } catch (failure: Exception) {
                _state.update { it.copy(loading = false, error = failure.userMessage()) }
            }
        }
    }

    fun delete() {
        if (_state.value.deleting || _state.value.deleted) return
        viewModelScope.launch {
            _state.update { it.copy(deleting = true, error = null) }
            try {
                repository.delete(id)
                _state.update { it.copy(deleting = false, deleted = true) }
            } catch (failure: Exception) {
                _state.update { it.copy(deleting = false, error = failure.userMessage()) }
            }
        }
    }
}
