package com.financialapp.ui.mutualfunds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financialapp.data.repository.AccountRepository
import com.financialapp.data.repository.MutualFundRepository
import com.financialapp.domain.model.FundUnits
import com.financialapp.domain.model.InvestmentTransactionType
import com.financialapp.domain.model.Money
import com.financialapp.domain.model.MutualFund
import com.financialapp.domain.model.MutualFundPortfolioSummary
import com.financialapp.domain.model.MutualFundTransaction
import com.financialapp.domain.model.calculateInvestmentAmount
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InvestmentAccountOption(val id: Long, val name: String)

data class MutualFundsUiState(
    val isLoading: Boolean = true,
    val funds: List<MutualFund> = emptyList(),
    val portfolio: MutualFundPortfolioSummary? = null,
    val investmentAccounts: List<InvestmentAccountOption> = emptyList(),
    val cashAccounts: List<InvestmentAccountOption> = emptyList(),
    val errorMessage: String? = null,
    val isSaving: Boolean = false,
)

@HiltViewModel
class MutualFundsViewModel @Inject constructor(
    private val mutualFundRepository: MutualFundRepository,
    private val accountRepository: AccountRepository,
    private val clock: Clock,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MutualFundsUiState())
    val uiState: StateFlow<MutualFundsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val today = LocalDate.now(clock)
            runCatching {
                val accounts = accountRepository.getActiveAccounts()
                val investmentAccounts = accounts
                    .filter { it.accountType.name == "INVESTMENT" }
                    .map { InvestmentAccountOption(it.accountId, it.name) }
                val cashAccounts = accounts
                    .filter { it.accountType.name != "INVESTMENT" }
                    .map { InvestmentAccountOption(it.accountId, it.name) }
                val portfolio = mutualFundRepository.getPortfolioSummary(today)
                val funds = mutualFundRepository.getActiveFunds()
                Triple(investmentAccounts, cashAccounts, portfolio) to funds
            }.onSuccess { (accountsAndPortfolio, funds) ->
                val (investmentAccounts, cashAccounts, portfolio) = accountsAndPortfolio
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        portfolio = portfolio,
                        funds = funds,
                        investmentAccounts = investmentAccounts,
                        cashAccounts = cashAccounts,
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(isLoading = false, errorMessage = error.message ?: "Unable to load mutual funds") }
            }
        }
    }

    fun createFund(name: String, code: String, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val result = runCatching {
                mutualFundRepository.createFund(MutualFund(name = name.trim(), code = code.trim()))
            }
            _uiState.update { it.copy(isSaving = false) }
            result.onSuccess { fundId ->
                refresh()
                onDone(true, fundId.toString())
            }.onFailure { error ->
                onDone(false, error.message ?: "Unable to create mutual fund")
            }
        }
    }

    fun updateFund(fundId: Long, name: String, code: String, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val result = runCatching {
                val existing = _uiState.value.funds.firstOrNull { it.id == fundId }
                    ?: throw IllegalArgumentException("Mutual fund does not exist")
                mutualFundRepository.updateFund(existing.copy(name = name.trim(), code = code.trim()))
            }
            _uiState.update { it.copy(isSaving = false) }
            result.onSuccess {
                refresh()
                onDone(true, null)
            }.onFailure { error ->
                onDone(false, error.message ?: "Unable to update mutual fund")
            }
        }
    }

    fun deleteFund(fundId: Long, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val result = runCatching { mutualFundRepository.deleteFund(fundId, Instant.now(clock)) }
            _uiState.update { it.copy(isSaving = false) }
            result.onSuccess {
                refresh()
                onDone(true, null)
            }.onFailure { error ->
                onDone(false, error.message ?: "Unable to delete mutual fund")
            }
        }
    }

    fun recordNav(fundId: Long, navMinorUnits: Long, date: LocalDate, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val result = runCatching {
                mutualFundRepository.recordNav(fundId, Money(navMinorUnits, "INR"), date, Instant.now(clock))
            }
            _uiState.update { it.copy(isSaving = false) }
            result.onSuccess {
                refresh()
                onDone(true, null)
            }.onFailure { error ->
                onDone(false, error.message ?: "Unable to record NAV")
            }
        }
    }

    fun recordInvestment(
        fundId: Long,
        investmentAccountId: Long,
        cashAccountId: Long?,
        type: InvestmentTransactionType,
        unitsValue: String,
        navMinorUnits: Long,
        date: LocalDate,
        onDone: (Boolean, String?) -> Unit,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val result = runCatching {
                val units = parseUnits(unitsValue) ?: throw IllegalArgumentException("Enter a valid positive unit quantity")
                val nav = Money(navMinorUnits, "INR")
                val total = calculateInvestmentAmount(nav, units)
                val transaction = MutualFundTransaction(
                    fundId = fundId,
                    investmentAccountId = investmentAccountId,
                    cashAccountId = cashAccountId,
                    type = type,
                    units = units,
                    nav = nav,
                    totalAmount = total,
                    transactionDate = date,
                    createdAt = Instant.now(clock),
                )
                if (type == InvestmentTransactionType.PURCHASE) {
                    mutualFundRepository.recordPurchase(transaction)
                } else {
                    mutualFundRepository.recordRedemption(transaction)
                }
            }
            _uiState.update { it.copy(isSaving = false) }
            result.onSuccess {
                refresh()
                onDone(true, null)
            }.onFailure { error ->
                onDone(false, error.message ?: "Unable to record investment transaction")
            }
        }
    }

    private fun parseUnits(value: String): FundUnits? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        val decimal = try {
            java.math.BigDecimal(trimmed)
        } catch (e: NumberFormatException) {
            return null
        }
        if (decimal <= java.math.BigDecimal.ZERO) return null
        val scale = decimal.scale().coerceIn(0, 6)
        return FundUnits(decimal.movePointRight(scale).longValueExact(), scale)
    }
}
