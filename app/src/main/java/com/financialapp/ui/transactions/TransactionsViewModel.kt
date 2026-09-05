package com.financialapp.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financialapp.data.repository.AccountRepository
import com.financialapp.data.repository.CategoryRepository
import com.financialapp.data.repository.FinancialRepository
import com.financialapp.domain.model.Category
import com.financialapp.domain.model.CategoryType
import com.financialapp.domain.model.FinancialTransaction
import com.financialapp.domain.model.Money
import com.financialapp.domain.model.Transfer
import com.financialapp.domain.model.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountOption(val id: Long, val name: String)

data class TransactionsUiState(
    val isLoading: Boolean = true,
    val transactions: List<FinancialTransaction> = emptyList(),
    val accounts: List<AccountOption> = emptyList(),
    val categories: List<Category> = emptyList(),
    val errorMessage: String? = null,
    val isSaving: Boolean = false,
)

private const val LOOKBACK_DAYS = 90L

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val financialRepository: FinancialRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val clock: Clock,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TransactionsUiState())
    val uiState: StateFlow<TransactionsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val today = LocalDate.now(clock)
            runCatching {
                val transactions = financialRepository.getTransactions(today.minusDays(LOOKBACK_DAYS), today)
                    .sortedByDescending { it.transactionDate }
                val accounts = accountRepository.getActiveAccounts().map { AccountOption(it.accountId, it.name) }
                val categories = categoryRepository.getTree(includeInactive = false)
                    .let(::flattenCategories)
                Triple(transactions, accounts, categories)
            }.onSuccess { (transactions, accounts, categories) ->
                _uiState.update {
                    it.copy(isLoading = false, transactions = transactions, accounts = accounts, categories = categories)
                }
            }.onFailure { error ->
                _uiState.update { it.copy(isLoading = false, errorMessage = error.message ?: "Unable to load transactions") }
            }
        }
    }

    fun createTransaction(
        accountId: Long,
        type: TransactionType,
        amountMinorUnits: Long,
        categoryId: Long,
        date: LocalDate,
        merchant: String?,
        notes: String?,
        onDone: (Boolean, String?) -> Unit,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val result = runCatching {
                financialRepository.createTransaction(
                    FinancialTransaction(
                        accountId = accountId,
                        type = type,
                        amount = Money(amountMinorUnits, "INR"),
                        categoryId = categoryId,
                        transactionDate = date,
                        createdAt = Instant.now(clock),
                        notes = notes?.trim()?.takeIf { it.isNotEmpty() },
                        merchantOrPayee = merchant?.trim()?.takeIf { it.isNotEmpty() },
                    ),
                )
            }
            _uiState.update { it.copy(isSaving = false) }
            result.onSuccess {
                refresh()
                onDone(true, null)
            }.onFailure { error ->
                onDone(false, error.message ?: "Unable to create transaction")
            }
        }
    }

    fun createTransactionWithCategory(
        accountId: Long,
        type: TransactionType,
        amountMinorUnits: Long,
        categoryName: String,
        date: LocalDate,
        merchant: String?,
        notes: String?,
        onDone: (Boolean, String?) -> Unit,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val result = runCatching {
                val categoryId = categoryRepository.create(
                    Category(name = categoryName.trim(), type = if (type == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE),
                )
                financialRepository.createTransaction(
                    FinancialTransaction(
                        accountId = accountId,
                        type = type,
                        amount = Money(amountMinorUnits, "INR"),
                        categoryId = categoryId,
                        transactionDate = date,
                        createdAt = Instant.now(clock),
                        notes = notes?.trim()?.takeIf(String::isNotEmpty),
                        merchantOrPayee = merchant?.trim()?.takeIf(String::isNotEmpty),
                    ),
                )
            }
            _uiState.update { it.copy(isSaving = false) }
            result.onSuccess { refresh(); onDone(true, null) }.onFailure { onDone(false, it.message ?: "Unable to create transaction") }
        }
    }

    fun createTransfer(
        sourceAccountId: Long,
        destinationAccountId: Long,
        amountMinorUnits: Long,
        date: LocalDate,
        onDone: (Boolean, String?) -> Unit,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val result = runCatching {
                financialRepository.createTransfer(
                    Transfer(
                        transferId = UUID.randomUUID().toString(),
                        sourceAccountId = sourceAccountId,
                        destinationAccountId = destinationAccountId,
                        amount = Money(amountMinorUnits, "INR"),
                        transactionDate = date,
                        createdAt = Instant.now(clock),
                    ),
                )
            }
            _uiState.update { it.copy(isSaving = false) }
            result.onSuccess {
                refresh()
                onDone(true, null)
            }.onFailure { error ->
                onDone(false, error.message ?: "Unable to create transfer")
            }
        }
    }

    fun updateTransaction(
        transactionId: Long,
        accountId: Long,
        type: TransactionType,
        amountMinorUnits: Long,
        categoryId: Long,
        date: LocalDate,
        merchant: String?,
        notes: String?,
        onDone: (Boolean, String?) -> Unit,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val result = runCatching {
                financialRepository.correctTransaction(
                    transactionId,
                    FinancialTransaction(
                        accountId = accountId,
                        type = type,
                        amount = Money(amountMinorUnits, "INR"),
                        categoryId = categoryId,
                        transactionDate = date,
                        createdAt = Instant.now(clock),
                        notes = notes?.trim()?.takeIf { it.isNotEmpty() },
                        merchantOrPayee = merchant?.trim()?.takeIf { it.isNotEmpty() },
                    ),
                )
            }
            _uiState.update { it.copy(isSaving = false) }
            result.onSuccess {
                refresh()
                onDone(true, null)
            }.onFailure { error ->
                onDone(false, error.message ?: "Unable to update transaction")
            }
        }
    }

    fun deleteTransaction(transactionId: Long, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val result = runCatching { financialRepository.deleteTransaction(transactionId, Instant.now(clock)) }
            _uiState.update { it.copy(isSaving = false) }
            result.onSuccess {
                refresh()
                onDone(true, null)
            }.onFailure { error ->
                onDone(false, error.message ?: "Unable to delete transaction")
            }
        }
    }

    private fun flattenCategories(tree: List<com.financialapp.domain.model.CategoryTreeNode>): List<Category> {
        val result = mutableListOf<Category>()
        fun visit(node: com.financialapp.domain.model.CategoryTreeNode) {
            result += node.category
            node.children.forEach(::visit)
        }
        tree.forEach(::visit)
        return result
    }
}

fun categoriesForType(categories: List<Category>, type: CategoryType): List<Category> =
    categories.filter { it.type == type }
