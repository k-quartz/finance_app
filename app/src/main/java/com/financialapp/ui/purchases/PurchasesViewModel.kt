package com.financialapp.ui.purchases

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financialapp.data.repository.AccountRepository
import com.financialapp.data.repository.CategoryRepository
import com.financialapp.data.repository.ProductRepository
import com.financialapp.data.repository.PurchaseRepository
import com.financialapp.domain.model.Category
import com.financialapp.domain.model.CategoryType
import com.financialapp.domain.model.Money
import com.financialapp.domain.model.Product
import com.financialapp.domain.model.Purchase
import com.financialapp.domain.model.Quantity
import com.financialapp.domain.model.calculatePurchaseTotal
import com.financialapp.ui.transactions.AccountOption
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val LOOKBACK_DAYS = 90L

data class PurchasesUiState(
    val isLoading: Boolean = true,
    val products: List<Product> = emptyList(),
    val purchases: List<Purchase> = emptyList(),
    val accounts: List<AccountOption> = emptyList(),
    val expenseCategories: List<Category> = emptyList(),
    val errorMessage: String? = null,
    val isSaving: Boolean = false,
)

@HiltViewModel
class PurchasesViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val purchaseRepository: PurchaseRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val clock: Clock,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PurchasesUiState())
    val uiState: StateFlow<PurchasesUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val today = LocalDate.now(clock)
            runCatching {
                val products = productRepository.getActiveProducts()
                val purchases = purchaseRepository.getPurchasesForDateRangeCompat(today.minusDays(LOOKBACK_DAYS), today)
                val accounts = accountRepository.getActiveAccounts().map { AccountOption(it.accountId, it.name) }
                val categories = flattenCategories(categoryRepository.getTree(includeInactive = false))
                    .filter { it.type == CategoryType.EXPENSE }
                Triple(products, purchases, accounts) to categories
            }.onSuccess { (lists, categories) ->
                val (products, purchases, accounts) = lists
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        products = products,
                        purchases = purchases.sortedByDescending { p -> p.purchaseDate },
                        accounts = accounts,
                        expenseCategories = categories,
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(isLoading = false, errorMessage = error.message ?: "Unable to load purchases") }
            }
        }
    }

    fun createProduct(name: String, unit: String, categoryId: Long?, onDone: (Boolean, Long?, String?) -> Unit) {
        mutateWithId(onDone, "Unable to create product") {
            productRepository.create(Product(name = name.trim(), defaultUnit = unit.trim(), categoryId = categoryId))
        }
    }

    fun createCategory(name: String, type: CategoryType, parentId: Long?, onDone: (Boolean, Long?, String?) -> Unit) {
        mutateWithId(onDone, "Unable to create category") {
            categoryRepository.create(Category(name = name.trim(), type = type, parentId = parentId))
        }
    }

    fun updateProduct(productId: Long, name: String, unit: String, onDone: (Boolean, String?) -> Unit) {
        mutate(onDone, "Unable to update product") {
            val existing = _uiState.value.products.firstOrNull { it.id == productId }
                ?: throw IllegalArgumentException("Product does not exist")
            productRepository.update(existing.copy(name = name.trim(), defaultUnit = unit.trim()))
        }
    }

    fun deleteProduct(productId: Long, onDone: (Boolean, String?) -> Unit) {
        mutate(onDone, "Unable to delete product") {
            val now = Instant.now(clock).toEpochMilli()
            productRepository.softDelete(productId, now, now)
        }
    }

    fun createPurchase(
        accountId: Long,
        categoryId: Long,
        productId: Long,
        quantityValue: String,
        unit: String,
        unitPriceMinorUnits: Long,
        date: LocalDate,
        merchant: String?,
        notes: String?,
        onDone: (Boolean, String?) -> Unit,
    ) {
        mutate(onDone, "Unable to create purchase") {
            purchaseRepository.recordWithExpense(accountId, buildPurchase(productId, categoryId, quantityValue, unit, unitPriceMinorUnits, date, merchant, notes))
        }
    }

    fun updatePurchase(
        purchaseId: Long,
        accountId: Long,
        categoryId: Long,
        productId: Long,
        quantityValue: String,
        unit: String,
        unitPriceMinorUnits: Long,
        date: LocalDate,
        merchant: String?,
        notes: String?,
        onDone: (Boolean, String?) -> Unit,
    ) {
        mutate(onDone, "Unable to update purchase") {
            purchaseRepository.correct(
                purchaseId,
                accountId,
                buildPurchase(productId, categoryId, quantityValue, unit, unitPriceMinorUnits, date, merchant, notes),
            )
        }
    }

    fun deletePurchase(purchaseId: Long, onDone: (Boolean, String?) -> Unit) {
        mutate(onDone, "Unable to delete purchase") {
            val now = Instant.now(clock).toEpochMilli()
            purchaseRepository.softDelete(purchaseId, now, now)
        }
    }

    private fun buildPurchase(
        productId: Long,
        categoryId: Long,
        quantityValue: String,
        unit: String,
        unitPriceMinorUnits: Long,
        date: LocalDate,
        merchant: String?,
        notes: String?,
    ): Purchase {
        val quantity = parseQuantity(quantityValue, unit)
            ?: throw IllegalArgumentException("Enter a valid positive quantity")
        val unitPrice = Money(unitPriceMinorUnits, "INR")
        return Purchase(
            productId = productId,
            categoryId = categoryId,
            quantity = quantity,
            unitPrice = unitPrice,
            totalAmount = calculatePurchaseTotal(unitPrice, quantity),
            purchaseDate = date,
            merchantName = merchant?.trim()?.takeIf { it.isNotEmpty() },
            notes = notes?.trim()?.takeIf { it.isNotEmpty() },
            createdAt = Instant.now(clock),
        )
    }

    private fun mutate(onDone: (Boolean, String?) -> Unit, fallback: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val result = runCatching { block() }
            _uiState.update { it.copy(isSaving = false) }
            result.onSuccess {
                refresh()
                onDone(true, null)
            }.onFailure { error ->
                onDone(false, error.message ?: fallback)
            }
        }
    }

    private fun mutateWithId(onDone: (Boolean, Long?, String?) -> Unit, fallback: String, block: suspend () -> Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val result = runCatching { block() }
            _uiState.update { it.copy(isSaving = false) }
            result.onSuccess { id -> refresh(); onDone(true, id, null) }
                .onFailure { error -> onDone(false, null, error.message ?: fallback) }
        }
    }

    private fun parseQuantity(value: String, unit: String): Quantity? {
        val trimmed = value.trim()
        if (trimmed.isEmpty() || unit.isBlank()) return null
        val decimal = try {
            java.math.BigDecimal(trimmed)
        } catch (e: NumberFormatException) {
            return null
        }
        if (decimal <= java.math.BigDecimal.ZERO) return null
        val scale = decimal.scale().coerceIn(0, 9)
        val minorUnits = decimal.movePointRight(scale).longValueExact()
        return Quantity(minorUnits, scale, unit.trim())
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

/** Fetches purchases in a date range by taking the first emission from the repository's Flow. */
private suspend fun PurchaseRepository.getPurchasesForDateRangeCompat(start: LocalDate, end: LocalDate): List<Purchase> =
    observePurchasesForDateRange(start.toEpochDay(), end.toEpochDay()).first()
