package com.financialapp.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financialapp.data.repository.DashboardRepository
import com.financialapp.domain.model.DashboardPeriod
import com.financialapp.domain.model.DashboardSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

data class DashboardUiState(
    val period: DashboardPeriod = DashboardPeriod.MONTH,
    val isLoading: Boolean = true,
    val summary: DashboardSummary? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val dashboardRepository: DashboardRepository,
    private val clock: Clock,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init {
        refresh()
    }

    fun selectPeriod(period: DashboardPeriod) {
        if (period == _uiState.value.period) return
        _uiState.update { it.copy(period = period, errorMessage = null) }
        refresh()
    }

    fun refresh() {
        val period = _uiState.value.period
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                dashboardRepository.loadDashboard(period, LocalDate.now(clock))
            }.onSuccess { summary ->
                _uiState.update { it.copy(isLoading = false, summary = summary, errorMessage = null) }
            }.onFailure { error ->
                _uiState.update { it.copy(isLoading = false, errorMessage = error.message ?: "Unable to load dashboard") }
            }
        }
    }
}