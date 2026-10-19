package com.company.fieldincidentreporter.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.company.fieldincidentreporter.data.IncidentRepository
import com.company.fieldincidentreporter.di.IoDispatcher
import com.company.fieldincidentreporter.model.Incident
import com.company.fieldincidentreporter.model.Result
import com.company.fieldincidentreporter.ui.DetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
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
            when (val result = withContext(dispatcher) { repository.get(id) }) {
                is Result.Success -> _state.update { it.copy(incident = result.data, loading = false) }
                is Result.Error -> _state.update { it.copy(loading = false, error = result.message) }
            }
        }
    }

    fun delete() {
        if (_state.value.deleting || _state.value.deleted) return
        viewModelScope.launch {
            _state.update { it.copy(deleting = true, error = null) }
            when (val result = withContext(dispatcher) { repository.delete(id) }) {
                is Result.Success -> _state.update { it.copy(deleting = false, deleted = true) }
                is Result.Error -> _state.update { it.copy(deleting = false, error = result.message) }
            }
        }
    }
}
