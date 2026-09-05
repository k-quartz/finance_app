package com.financialapp.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financialapp.data.repository.AccountRepository
import com.financialapp.data.repository.FinancialRepository
import com.financialapp.domain.model.Account
import com.financialapp.domain.model.AccountBalance
import com.financialapp.domain.model.AccountType
import com.financialapp.domain.model.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountsUiState(
    val isLoading: Boolean = true,
    val accounts: List<Account> = emptyList(),
    val balances: List<AccountBalance> = emptyList(),
    val namesById: Map<Long, String> = emptyMap(),
    val errorMessage: String? = null,
    val isSaving: Boolean = false,
)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val financialRepository: FinancialRepository,
    private val clock: Clock,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AccountsUiState())
    val uiState: StateFlow<AccountsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                val activeAccounts = accountRepository.getActiveAccounts().map { entity ->
                    Account(
                        id = entity.accountId,
                        name = entity.name,
                        type = AccountType.valueOf(entity.accountType.name),
                        openingBalance = Money(entity.openingBalanceMinor, entity.currencyCode),
                        isActive = entity.isActive,
                        isDeleted = entity.isDeleted,
                        createdAt = Instant.ofEpochMilli(entity.createdAtEpochMs),
                        updatedAt = Instant.ofEpochMilli(entity.updatedAtEpochMs),
                    )
                }
                val balances = financialRepository.getActiveAccountBalances()
                Pair(activeAccounts, balances)
            }.onSuccess { (accounts, balances) ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        accounts = accounts,
                        balances = balances,
                        namesById = accounts.associate { account -> account.id to account.name },
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(isLoading = false, errorMessage = error.message ?: "Unable to load accounts") }
            }
        }
    }

    fun createAccount(name: String, type: AccountType, openingBalanceMinorUnits: Long, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val now = Instant.now(clock)
            val result = runCatching {
                financialRepository.createAccount(
                    Account(
                        name = name.trim(),
                        type = type,
                        openingBalance = Money(openingBalanceMinorUnits, "INR"),
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
            finishMutation(result, onDone, "Unable to create account")
        }
    }

    fun updateAccount(accountId: Long, name: String, type: AccountType, openingBalanceMinorUnits: Long, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val result = runCatching {
                val existing = _uiState.value.accounts.firstOrNull { it.id == accountId }
                    ?: throw IllegalArgumentException("Account does not exist")
                financialRepository.updateAccount(
                    existing.copy(
                        name = name.trim(),
                        type = type,
                        openingBalance = Money(openingBalanceMinorUnits, "INR"),
                        updatedAt = Instant.now(clock),
                    ),
                )
            }
            finishMutation(result, onDone, "Unable to update account")
        }
    }

    fun deleteAccount(accountId: Long, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val result = runCatching { financialRepository.deleteAccount(accountId, Instant.now(clock)) }
            finishMutation(result, onDone, "Unable to delete account")
        }
    }

    private fun finishMutation(result: Result<*>, onDone: (Boolean, String?) -> Unit, fallback: String) {
        _uiState.update { it.copy(isSaving = false) }
        result.onSuccess {
            refresh()
            onDone(true, null)
        }.onFailure { error ->
            onDone(false, error.message ?: fallback)
        }
    }
}
