package com.financialapp.ui.workflows

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financialapp.data.repository.AccountRepository
import com.financialapp.data.repository.CategoryRepository
import com.financialapp.data.repository.FinancialRepository
import com.financialapp.data.repository.MutualFundRepository
import com.financialapp.data.repository.ProductPriceHistoryRepository
import com.financialapp.data.repository.ProductRepository
import com.financialapp.data.repository.PurchaseRepository
import com.financialapp.domain.model.Account
import com.financialapp.domain.model.AccountType
import com.financialapp.domain.model.Category
import com.financialapp.domain.model.CategoryType
import com.financialapp.domain.model.FinancialTransaction
import com.financialapp.domain.model.FundUnits
import com.financialapp.domain.model.InvestmentTransactionType
import com.financialapp.domain.model.Money
import com.financialapp.domain.model.MutualFund
import com.financialapp.domain.model.MutualFundNavPoint
import com.financialapp.domain.model.MutualFundTransaction
import com.financialapp.domain.model.Product
import com.financialapp.domain.model.Quantity
import com.financialapp.domain.model.ReportingGranularity
import com.financialapp.domain.model.TransactionType
import com.financialapp.domain.model.calculatePurchaseTotal
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorkflowUiState(
    val isLoading: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    val analysis: String? = null,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val products: List<Product> = emptyList(),
    val purchases: List<com.financialapp.domain.model.Purchase> = emptyList(),
    val transactions: List<FinancialTransaction> = emptyList(),
    val funds: List<MutualFund> = emptyList(),
    val investmentTransactions: List<MutualFundTransaction> = emptyList(),
    val navHistory: List<MutualFundNavPoint> = emptyList(),
    val priceHistory: List<com.financialapp.domain.model.CommodityPricePoint> = emptyList(),
)

@HiltViewModel
class WorkflowsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val financialRepository: FinancialRepository,
    private val productRepository: ProductRepository,
    private val purchaseRepository: PurchaseRepository,
    private val priceRepository: ProductPriceHistoryRepository,
    private val mutualFundRepository: MutualFundRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WorkflowUiState())
    val uiState: StateFlow<WorkflowUiState> = _uiState.asStateFlow()
    private var refreshJob: Job? = null

    init { refresh() }

    fun refresh(successMessage: String? = null) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = successMessage, isError = false) }
            runCatching {
                val accounts = accountRepository.observeAll().first().map { entity ->
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
                val categories = categoryRepository.getTree(includeInactive = true).flatMap { flatten(it) }
                val products = productRepository.observeAll().first()
                val transactions = financialRepository.getTransactions(LocalDate.MIN, LocalDate.MAX)
                val purchases = products.flatMap { purchaseRepository.getPurchasesForProduct(it.id) }
                val funds = mutualFundRepository.getActiveFunds()
                val investments = mutualFundRepository.getAllInvestmentTransactions()
                val navs = mutualFundRepository.getAllNavHistory()
                val prices = products.flatMap { priceRepository.getHistoryForProduct(it.id) }
                WorkflowUiState(accounts = accounts, categories = categories, products = products, purchases = purchases, transactions = transactions, funds = funds, investmentTransactions = investments, navHistory = navs, priceHistory = prices)
            }.onSuccess { loaded -> _uiState.update { loaded.copy(message = successMessage ?: "Data loaded") } }
                .onFailure { error -> _uiState.update { it.copy(isLoading = false, message = error.message ?: "Unable to load data", isError = true) } }
        }
    }

    private fun flatten(node: com.financialapp.domain.model.CategoryTreeNode): List<Category> = listOf(node.category) + node.children.flatMap(::flatten)

    fun submit(action: suspend () -> Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = null, isError = false) }
            runCatching { action() }
                .onSuccess { id -> refresh("Saved #$id") }
                .onFailure { error -> _uiState.update { it.copy(isLoading = false, message = error.message ?: "Unable to save", isError = true) } }
        }
    }

    fun createAccount(name: String, type: String, opening: String) = submit {
        val now = Instant.now()
        financialRepository.createAccount(Account(name = name, type = AccountType.valueOf(type.uppercase()), openingBalance = Money(opening.toMinorUnits(), "INR"), createdAt = now, updatedAt = now))
    }

    fun setAccountActive(id: Long, active: Boolean) = submit {
        val entity = accountRepository.getById(id) ?: throw IllegalArgumentException("Account does not exist")
        val now = System.currentTimeMillis()
        accountRepository.upsert(entity.copy(isActive = active, updatedAtEpochMs = now))
        id
    }
    fun deleteAccount(id: Long) = submit {
        val now = System.currentTimeMillis()
        accountRepository.softDelete(id, now, now)
        id
    }
    fun setCategoryActive(id: Long, active: Boolean) = submit { if (active) categoryRepository.activate(id) else categoryRepository.deactivate(id); id }
    fun deleteCategory(id: Long) = submit { categoryRepository.delete(id); id }
    fun setProductActive(id: Long, active: Boolean) = submit { productRepository.setActive(id, active); id }
    fun deleteProduct(id: Long) = submit { productRepository.softDelete(id, System.currentTimeMillis(), System.currentTimeMillis()).toLong() }
    fun deletePurchase(id: Long) = submit { purchaseRepository.softDelete(id, System.currentTimeMillis(), System.currentTimeMillis()).toLong() }

    fun correctTransaction(originalId: String, account: String, type: String, amount: String, category: String, date: String, merchant: String) = submit {
        financialRepository.correctTransaction(originalId.toLong(), FinancialTransaction(accountId = account.toLong(), type = TransactionType.valueOf(type.uppercase()), amount = Money(amount.toMinorUnits(), "INR"), categoryId = category.toNullableId(), transactionDate = LocalDate.parse(date), createdAt = Instant.now(), merchantOrPayee = merchant.ifBlank { null }))
    }

    fun createCategory(name: String, type: String, parentId: String) = submit {
        categoryRepository.create(Category(name = name, type = CategoryType.valueOf(type.uppercase()), parentId = parentId.toNullableId()))
    }

    fun createProduct(name: String, unit: String) = submit { productRepository.create(Product(name = name, defaultUnit = unit)) }

    fun createTransfer(source: String, destination: String, amount: String, date: String) = submit {
        financialRepository.createTransfer(com.financialapp.domain.model.Transfer("transfer-${System.nanoTime()}", source.toLong(), destination.toLong(), Money(amount.toMinorUnits(), "INR"), LocalDate.parse(date), Instant.now()))
    }

    fun createTransaction(account: String, type: String, amount: String, category: String, date: String, merchant: String) = submit {
        financialRepository.createTransaction(FinancialTransaction(accountId = account.toLong(), type = TransactionType.valueOf(type.uppercase()), amount = Money(amount.toMinorUnits(), "INR"), categoryId = category.toNullableId(), transactionDate = LocalDate.parse(date), createdAt = Instant.now(), merchantOrPayee = merchant.ifBlank { null }))
    }

    fun createProductAndFund(name: String, code: String) = submit { mutualFundRepository.createFund(MutualFund(name = name, code = code)) }

    fun createPurchase(product: String, quantity: String, unit: String, unitPrice: String, date: String, category: String, merchant: String) = submit {
        val price = Money(unitPrice.toMinorUnits(), "INR")
        val parsedQuantity = quantity.toScaledQuantity(unit)
        purchaseRepository.create(com.financialapp.domain.model.Purchase(productId = product.toLong(), categoryId = category.toNullableId(), merchantName = merchant.ifBlank { null }, quantity = parsedQuantity, unitPrice = price, totalAmount = calculatePurchaseTotal(price, parsedQuantity), purchaseDate = LocalDate.parse(date), createdAt = Instant.now()))
    }

    fun createFund(name: String, code: String) = submit { mutualFundRepository.createFund(MutualFund(name = name, code = code)) }

    fun recordNav(fund: String, nav: String, date: String) = submit { mutualFundRepository.recordNav(fund.toLong(), Money(nav.toMinorUnits(), "INR"), LocalDate.parse(date), Instant.now()) }

    fun recordPrice(product: String, price: String, date: String) = submit { priceRepository.recordPrice(com.financialapp.domain.model.CommodityPricePoint(productId = product.toLong(), asOfDate = LocalDate.parse(date), price = Money(price.toMinorUnits(), "INR"), createdAt = Instant.now())) }

    fun recordInvestment(fund: String, account: String, type: String, units: String, nav: String, total: String, date: String) = submit {
        val transaction = MutualFundTransaction(fundId = fund.toLong(), investmentAccountId = account.toLong(), type = InvestmentTransactionType.valueOf(type.uppercase()), units = units.toScaledUnits(), nav = Money(nav.toMinorUnits(), "INR"), totalAmount = Money(total.toMinorUnits(), "INR"), transactionDate = LocalDate.parse(date), createdAt = Instant.now())
        if (transaction.type == InvestmentTransactionType.PURCHASE) mutualFundRepository.recordPurchase(transaction) else mutualFundRepository.recordRedemption(transaction)
    }

    fun loadAnalysis(start: String, end: String, account: String, granularity: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = null, isError = false) }
            runCatching {
                val from = LocalDate.parse(start)
                val to = LocalDate.parse(end)
                val accountId = account.toNullableId()
                val summary = financialRepository.summarize(from, to, accountId)
                val recurring = financialRepository.detectRecurringExpenses(from, to, accountId)
                val report = financialRepository.analyze(
                    com.financialapp.domain.model.ReportingPeriod(from, to, ReportingGranularity.valueOf(granularity)),
                    accountId,
                )
                "${summary.income.format()} income, ${summary.expense.format()} expense; ${report.buckets.size} ${granularity.lowercase()} bucket(s); ${recurring.size} recurring pattern(s)"
            }.onSuccess { result -> _uiState.update { it.copy(isLoading = false, message = "Analysis loaded", isError = false, analysis = result) } }
                .onFailure { error -> _uiState.update { it.copy(isLoading = false, message = error.message ?: "Unable to load analysis", isError = true) } }
        }
    }
}

private fun String.toMinorUnits(): Long = (toBigDecimal() * java.math.BigDecimal("100")).longValueExact()
private fun String.toNullableId(): Long? = trim().takeIf { it.isNotEmpty() }?.toLong()
private fun String.toScaledQuantity(unit: String): Quantity = toScaledNumber().let { Quantity(it.first, it.second, unit) }
private fun String.toScaledUnits(): FundUnits = toScaledNumber().let { FundUnits(it.first, it.second) }
private fun String.toScaledNumber(): Pair<Long, Int> {
    val value = toBigDecimal().stripTrailingZeros()
    val scale = value.scale().coerceIn(0, 9)
    return value.movePointRight(scale).longValueExact() to scale
}

private fun Money.format(): String = "${currencyCode} ${java.math.BigDecimal.valueOf(minorUnits, 2)}"