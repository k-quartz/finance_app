package com.financialapp.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.financialapp.data.local.database.AppDatabase
import com.financialapp.domain.model.Account
import com.financialapp.domain.model.AccountType
import com.financialapp.domain.model.Category
import com.financialapp.domain.model.CategoryType
import com.financialapp.domain.model.DashboardPeriod
import com.financialapp.domain.model.FundUnits
import com.financialapp.domain.model.FinancialTransaction
import com.financialapp.domain.model.InvestmentTransactionType
import com.financialapp.domain.model.Money
import com.financialapp.domain.model.MutualFund
import com.financialapp.domain.model.MutualFundTransaction
import com.financialapp.domain.model.Product
import com.financialapp.domain.model.Purchase
import com.financialapp.domain.model.Quantity
import com.financialapp.domain.model.Transfer
import com.financialapp.domain.model.TransactionType
import com.financialapp.domain.model.calculatePurchaseTotal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Phase18EndToEndTest {
    private lateinit var database: AppDatabase
    private lateinit var targetDatabase: AppDatabase
    private lateinit var financial: LocalFinancialRepository
    private lateinit var categories: LocalCategoryRepository
    private lateinit var products: LocalProductRepository
    private lateinit var purchases: LocalPurchaseRepository
    private lateinit var prices: LocalProductPriceHistoryRepository
    private lateinit var funds: LocalMutualFundRepository
    private lateinit var dashboard: LocalDashboardRepository
    private val now = Instant.parse("2026-02-15T12:00:00Z")
    private val january = LocalDate.of(2026, 1, 15)
    private val february = LocalDate.of(2026, 2, 15)

    @Before
    fun setUp() {
        database = buildDatabase()
        targetDatabase = buildDatabase()
        financial = LocalFinancialRepository(database.accountDao(), database.categoryDao(), database.transactionDao())
        categories = LocalCategoryRepository(database)
        products = LocalProductRepository(database.productDao(), database.categoryDao())
        purchases = LocalPurchaseRepository(database, financial)
        prices = LocalProductPriceHistoryRepository(database.productDao(), database.productPriceHistoryDao())
        funds = LocalMutualFundRepository(database, database.mutualFundDao(), database.accountDao(), database.transactionDao())
        dashboard = LocalDashboardRepository(financial, categories, funds, products, prices)
    }

    @After
    fun tearDown() {
        database.close()
        targetDatabase.close()
    }

    @Test
    fun `realistic V1 workflow reconciles history analytics portfolio dashboard and backup`() = runBlocking {
        val bankId = financial.createAccount(account("Savings", AccountType.BANK, 100_000L))
        val cardId = financial.createAccount(account("Card", AccountType.CREDIT_CARD))
        val investmentId = financial.createAccount(account("Investments", AccountType.INVESTMENT))
        val incomeCategoryId = financial.createCategory(Category(type = CategoryType.INCOME, name = "Salary"))
        val expenseCategoryId = financial.createCategory(Category(type = CategoryType.EXPENSE, name = "Groceries"))

        financial.createTransaction(transaction(bankId, TransactionType.INCOME, 200_000L, incomeCategoryId, january, "Employer"))
        val originalExpenseId = financial.createTransaction(transaction(bankId, TransactionType.EXPENSE, 30_000L, expenseCategoryId, february, "Market"))
        financial.createTransfer(Transfer("bank-card-payment", bankId, cardId, Money(50_000L, "INR"), february, now))
        financial.correctTransaction(originalExpenseId, transaction(bankId, TransactionType.EXPENSE, 25_000L, expenseCategoryId, february, "Market"))
        financial.createTransaction(transaction(bankId, TransactionType.INCOME, 5_000L, incomeCategoryId, february, "Refund"))

        assertEquals(230_000L, financial.calculateAccountBalance(bankId).amount.minorUnits)
        assertEquals(5_000L, financial.summarize(february, february).income.minorUnits)
        assertEquals(25_000L, financial.summarize(february, february).expense.minorUnits)
        assertEquals(4, financial.getTransactions(bankId).size)
        assertTrue(database.transactionDao().getById(originalExpenseId)!!.isDeleted)

        val productId = products.create(Product(name = "Rice", defaultUnit = "kg", categoryId = expenseCategoryId))
        val purchase = Purchase(
            productId = productId,
            categoryId = expenseCategoryId,
            merchantName = "Market",
            quantity = Quantity(2_500L, 3, "kg"),
            unitPrice = Money(600L, "INR"),
            totalAmount = calculatePurchaseTotal(Money(600L, "INR"), Quantity(2_500L, 3, "kg")),
            purchaseDate = february,
            createdAt = now,
        )
        purchases.create(purchase)
        prices.recordPrice(price(productId, january, 1_000L))
        prices.recordPrice(price(productId, february, 1_200L))
        assertEquals(1_500L, purchases.summarize(february, february).totalSpend.minorUnits)
        assertEquals(1_200L, prices.getLatestPriceBeforeOrOnDate(productId, february.toEpochDay())!!.price.minorUnits)
        assertEquals(200L, prices.analyzeTrend(productId, january, february).change.minorUnits)

        val fundId = funds.createFund(MutualFund(name = "Index Fund", code = "INDEX"))
        funds.recordPurchase(investment(fundId, investmentId, InvestmentTransactionType.PURCHASE, 2_000L, 1_000L, 2_000L, january))
        funds.recordRedemption(investment(fundId, investmentId, InvestmentTransactionType.REDEMPTION, 500L, 1_200L, 600L, february))
        funds.recordNav(fundId, Money(1_300L, "INR"), february, now)
        assertEquals(FundUnits(1_500L, 3), funds.getHolding(fundId, investmentId)!!.units)
        assertEquals(1_950L, funds.valueAt(fundId, investmentId, february).value.minorUnits)
        assertEquals("27.5000", funds.calculateReturn(fundId, investmentId, february).returnPercent!!.toPlainString())

        val report = financial.analyze(com.financialapp.domain.model.ReportingPeriod(january, february, com.financialapp.domain.model.ReportingGranularity.MONTH))
        assertEquals(listOf(200_000L, 5_000L), report.buckets.map { it.income.minorUnits })
        val firstQuarterMonths = financial.analyze(
            com.financialapp.domain.model.ReportingPeriod(
                january,
                LocalDate.of(2026, 3, 31),
                com.financialapp.domain.model.ReportingGranularity.MONTH,
            ),
        )
        assertEquals(205_000L, firstQuarterMonths.buckets.sumOf { it.income.minorUnits })
        assertTrue(financial.detectRecurringExpenses(january, february).isEmpty())

        val summary = dashboard.loadDashboard(DashboardPeriod.MONTH, february)
        assertEquals(5_000L, summary.currentIncome.minorUnits)
        assertEquals(25_000L, summary.currentExpense.minorUnits)
        assertEquals(1_950L, summary.portfolio.totalValue.minorUnits)

        categories.delete(expenseCategoryId)
        assertEquals(25_000L, financial.summarize(february, february).expense.minorUnits)

        val backup = buildBackupRepository(database).exportBackup()
        val targetBackup = buildBackupRepository(targetDatabase)
        targetBackup.restoreBackup(backup)
        assertEquals(database.transactionDao().getAll(), targetDatabase.transactionDao().getAll())
        assertEquals(database.purchaseDao().getAll(), targetDatabase.purchaseDao().getAll())
        assertEquals(database.mutualFundDao().getAllTransactions(), targetDatabase.mutualFundDao().getAllTransactions())
    }

    private fun buildDatabase() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .allowMainThreadQueries().build()

    private fun buildBackupRepository(database: AppDatabase) = LocalBackupRepository(
        database, database.accountDao(), database.categoryDao(), database.transactionDao(), database.productDao(),
        database.productPriceHistoryDao(), database.purchaseDao(), database.mutualFundDao(),
        Clock.fixed(now, ZoneOffset.UTC),
    )

    private fun account(name: String, type: AccountType, opening: Long = 0L) = Account(
        name = name, type = type, openingBalance = Money(opening, "INR"), createdAt = now, updatedAt = now,
    )

    private fun transaction(accountId: Long, type: TransactionType, amount: Long, categoryId: Long, date: LocalDate, merchant: String) = FinancialTransaction(
        accountId = accountId, type = type, amount = Money(amount, "INR"), categoryId = categoryId,
        transactionDate = date, merchantOrPayee = merchant, createdAt = now,
    )

    private fun investment(fundId: Long, accountId: Long, type: InvestmentTransactionType, units: Long, nav: Long, total: Long, date: LocalDate) = MutualFundTransaction(
        fundId = fundId, investmentAccountId = accountId, type = type, units = FundUnits(units, 3),
        nav = Money(nav, "INR"), totalAmount = Money(total, "INR"), transactionDate = date, createdAt = now,
    )

    private fun price(productId: Long, date: LocalDate, amount: Long) = com.financialapp.domain.model.CommodityPricePoint(
        productId = productId, asOfDate = date, price = Money(amount, "INR"), createdAt = now,
    )

}
