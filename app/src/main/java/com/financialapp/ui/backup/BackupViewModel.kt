package com.financialapp.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financialapp.data.repository.BackupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BackupUiState(
    val isBusy: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupRepository: BackupRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    suspend fun createBackup(): String? {
        _uiState.update { it.copy(isBusy = true, message = null, isError = false) }
        return runCatching {
            withContext(Dispatchers.IO) { backupRepository.exportBackup() }
        }.onFailure { error ->
            showError(error.message ?: "Unable to create backup")
        }.also {
            _uiState.update { state -> state.copy(isBusy = false) }
        }.getOrNull()
    }

    fun restoreBackup(json: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, message = null, isError = false) }
            runCatching {
                withContext(Dispatchers.IO) { backupRepository.restoreBackup(json) }
            }.onSuccess {
                _uiState.update { it.copy(message = "Backup restored", isError = false) }
            }.onFailure { error ->
                showError(error.message ?: "Unable to restore backup")
            }
            _uiState.update { it.copy(isBusy = false) }
        }
    }

    fun reportBackupSaved() {
        _uiState.update { it.copy(message = "Backup saved", isError = false) }
    }

    fun reportBackupSaveFailed() {
        showError("Unable to save backup")
    }

    fun reportBackupReadFailed() {
        showError("Unable to read backup")
    }

    private fun showError(message: String) {
        _uiState.update { it.copy(message = message, isError = true) }
    }
}