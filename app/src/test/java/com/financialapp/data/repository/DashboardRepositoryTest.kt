package com.financialapp.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.financialapp.data.local.database.AppDatabase
import com.financialapp.data.local.entity.AccountEntity
import com.financialapp.data.local.entity.AccountType as PersistenceAccountType
import com.financialapp.data.local.entity.CategoryEntity
import com.financialapp.data.local.entity.CategoryType as PersistenceCategoryType
import com.financialapp.data.local.entity.MutualFundEntity
import com.financialapp.data.local.entity.MutualFundHoldingEntity
import com.financialapp.data.local.entity.MutualFundNavEntity
import com.financialapp.data.local.entity.MutualFundTransactionEntity
import com.financialapp.data.local.entity.InvestmentTransactionType as PersistenceInvestmentTransactionType
import com.financialapp.data.local.entity.ProductEntity
import com.financialapp.data.local.entity.TransactionEntity
import com.financialapp.data.local.entity.TransactionType as PersistenceTransactionType
import com.financialapp.domain.model.CategoryType
import com.financialapp.domain.model.DashboardPeriod
import com.financialapp.domain.model.Money
import com.financialapp.domain.model.TransactionType
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DashboardRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: DashboardRepository
    private val inr = "INR"
    private val january = LocalDate.of(2026, 1, 15)
    private val february = LocalDate.of(2026, 2, 15)
    private val createdAt = Instant.parse("2026-02-15T00:00:00Z")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val financialRepository = LocalFinancialRepository(database.accountDao(), database.categoryDao(), database.transactionDao())
        val categoryRepository = LocalCategoryRepository(database)
        val productRepository = LocalProductRepository(database.productDao(), database.categoryDao())
        val priceRepository = LocalProductPriceHistoryRepository(database.productDao(), database.productPriceHistoryDao())
        val mutualFundRepository = LocalMutualFundRepository(database, database.mutualFundDao(), database.accountDao(), database.transactionDao())
        repository = LocalDashboardRepository(financialRepository, categoryRepository, mutualFundRepository, productRepository, priceRepository)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `dashboard combines period analytics net worth holdings and price movement`() = runBlocking {
        val bankAccountId = database.accountDao().upsert(account("Bank", PersistenceAccountType.BANK, 10_000L))
        val creditCardAccountId = database.accountDao().upsert(account("Card", PersistenceAccountType.CREDIT_CARD))
        val investmentAccountId = database.accountDao().upsert(account("Fund Account", PersistenceAccountType.INVESTMENT))
        val incomeCategoryId = database.categoryDao().upsert(categoryEntity("Salary", CategoryType.INCOME))
        val expenseCategoryId = database.categoryDao().upsert(categoryEntity("Food", CategoryType.EXPENSE))

        database.transactionDao().insert(transaction(bankAccountId, TransactionType.INCOME, 10_000L, incomeCategoryId, january, 1_000L))
        database.transactionDao().insert(transaction(bankAccountId, TransactionType.EXPENSE, 2_000L, expenseCategoryId, january.plusDays(1), 2_000L))
        database.transactionDao().insert(transaction(bankAccountId, TransactionType.INCOME, 12_000L, incomeCategoryId, february, 3_000L))
        database.transactionDao().insert(transaction(bankAccountId, TransactionType.EXPENSE, 3_000L, expenseCategoryId, february.plusDays(1), 4_000L))
        database.transactionDao().insert(transaction(creditCardAccountId, TransactionType.EXPENSE, 500L, expenseCategoryId, february.plusDays(2), 5_000L))

        val productId = database.productDao().upsert(productEntity())
        database.productPriceHistoryDao().insert(price(productId, LocalDate.of(2026, 1, 1), 1_000L))
        database.productPriceHistoryDao().insert(price(productId, february, 1_200L))

        val fundId = database.mutualFundDao().insertFund(com.financialapp.data.local.entity.MutualFundEntity(name = "Index Fund", code = "IDX", currencyCode = inr, isActive = true, isDeleted = false, createdAtEpochMs = 1L, updatedAtEpochMs = 1L))
        database.mutualFundDao().insertNav(com.financialapp.data.local.entity.MutualFundNavEntity(fundId = fundId, asOfDateEpochDay = january.toEpochDay(), navMinor = 1_000L, currencyCode = inr, createdAtEpochMs = 1L))
        database.mutualFundDao().insertNav(com.financialapp.data.local.entity.MutualFundNavEntity(fundId = fundId, asOfDateEpochDay = february.toEpochDay(), navMinor = 1_250L, currencyCode = inr, createdAtEpochMs = 2L))
        database.mutualFundDao().insertTransaction(MutualFundTransactionEntity(
            fundId = fundId,
            investmentAccountId = investmentAccountId,
            cashAccountId = null,
            transactionType = PersistenceInvestmentTransactionType.PURCHASE,
            unitsMinor = 2_000L,
            unitsScale = 3,
            navMinor = 1_000L,
            totalAmountMinor = 2_000L,
            currencyCode = inr,
            transactionDateEpochDay = january.toEpochDay(),
            linkedCashTransactionId = null,
            notes = null,
            createdAtEpochMs = 1L,
        ))
        database.mutualFundDao().insertHolding(com.financialapp.data.local.entity.MutualFundHoldingEntity(fundId = fundId, investmentAccountId = investmentAccountId, unitsMinor = 2_000L, unitsScale = 3, updatedAtEpochMs = 1L))

        val summary = repository.loadDashboard(DashboardPeriod.MONTH, february)

        assertEquals(Money(12_000L, inr), summary.currentIncome)
        assertEquals(Money(3_500L, inr), summary.currentExpense)
        assertEquals(Money(10_000L, inr), summary.previousIncome)
        assertEquals(Money(2_000L, inr), summary.previousExpense)
        assertEquals(Money(29_000L, inr), summary.netWorth)
        assertEquals(Money(2_500L, inr), summary.portfolio.totalValue)
        assertEquals(1, summary.priceMovers.size)
        assertEquals(Money(200L, inr), summary.priceMovers.single().change)
        assertEquals("Food", summary.topExpenseCategories.single().categoryName)
    }

    private fun account(name: String, type: PersistenceAccountType, openingBalance: Long = 0L) = AccountEntity(name = name, accountType = type, currencyCode = inr, openingBalanceMinor = openingBalance, createdAtEpochMs = 1L, updatedAtEpochMs = 1L)
    private fun productEntity() = ProductEntity(name = "Rice", defaultUnit = "kg", defaultCurrencyCode = inr, createdAtEpochMs = 1L, updatedAtEpochMs = 1L)
    private fun price(productId: Long, date: LocalDate, amount: Long) = com.financialapp.data.local.entity.ProductPriceHistoryEntity(productId = productId, asOfDateEpochDay = date.toEpochDay(), unitPriceMinor = amount, currencyCode = inr, createdAtEpochMs = 1L)
    private fun transaction(accountId: Long, type: TransactionType, amount: Long, categoryId: Long, date: LocalDate, createdAtMs: Long) = TransactionEntity(accountId = accountId, categoryId = categoryId, transactionType = PersistenceTransactionType.valueOf(type.name), amountMinor = amount, currencyCode = inr, occurredDateEpochDay = date.toEpochDay(), postedAtEpochMs = createdAtMs, createdAtEpochMs = createdAtMs, updatedAtEpochMs = createdAtMs)
    private fun categoryEntity(name: String, type: CategoryType) = CategoryEntity(name = name, categoryType = PersistenceCategoryType.valueOf(type.name), createdAtEpochMs = 1L, updatedAtEpochMs = 1L)
}
