package com.financialapp.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.financialapp.data.local.database.AppDatabase
import com.financialapp.data.local.entity.CategoryEntity
import com.financialapp.data.local.entity.CategoryType as PersistenceCategoryType
import com.financialapp.domain.model.Account
import com.financialapp.domain.model.AccountType
import com.financialapp.domain.model.Category
import com.financialapp.domain.model.CategoryType
import com.financialapp.domain.model.FinancialTransaction
import com.financialapp.domain.model.Money
import com.financialapp.domain.model.ReportingGranularity
import com.financialapp.domain.model.ReportingPeriod
import com.financialapp.domain.model.TransactionType
import com.financialapp.domain.model.Transfer
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FinancialRepositoryHighSeverityTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: LocalFinancialRepository
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val transactionDate = LocalDate.of(2026, 1, 1)
    private val inr = "INR"

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = LocalFinancialRepository(database.accountDao(), database.categoryDao(), database.transactionDao())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `duplicate transfer creation returns the original event and counts once`() = runBlocking {
        repository.createAccount(account(1L, "Bank"))
        repository.createAccount(account(2L, "Card", AccountType.CREDIT_CARD))
        val transfer = Transfer("payment-1", 1L, 2L, Money(1_000, inr), transactionDate, now)
        val firstId = repository.createTransfer(transfer)
        val duplicateId = repository.createTransfer(transfer)
        assertEquals(firstId, duplicateId)
        assertEquals(1, database.transactionDao().getTransactionsForAccount(1L).size)
        assertEquals(-1_000L, repository.calculateAccountBalance(1L).amount.minorUnits)
        assertEquals(-1_000L, repository.calculateAccountBalance(2L).amount.minorUnits)
    }

    @Test
    fun `transfers reject missing inactive deleted and self-referenced accounts`() = runBlocking {
        repository.createAccount(account(1L, "Active"))
        repository.createAccount(account(2L, "Inactive", isActive = false))
        repository.createAccount(account(3L, "Deleted", isDeleted = true))
        assertTransferFails { repository.createTransfer(transfer(1L, 99L, "missing-destination")) }
        assertTransferFails { repository.createTransfer(transfer(2L, 1L, "inactive-source")) }
        assertTransferFails { repository.createTransfer(transfer(1L, 2L, "inactive-destination")) }
        assertTransferFails { repository.createTransfer(transfer(3L, 1L, "deleted-source")) }
        assertTransferFails { repository.createTransfer(transfer(1L, 3L, "deleted-destination")) }
        assertTransferFails { repository.createTransfer(transfer(1L, 1L, "self")) }
    }

    @Test
    fun `transactions reject inactive deleted and invalid parent categories`() = runBlocking {
        repository.createAccount(account(1L, "Bank"))
        repository.createCategory(Category(type = CategoryType.EXPENSE, name = "Inactive", isActive = false))
        database.categoryDao().insert(CategoryEntity(categoryType = PersistenceCategoryType.EXPENSE, name = "Deleted", isActive = false, isDeleted = true, deletedAtEpochMs = 1L, createdAtEpochMs = 1L, updatedAtEpochMs = 1L))
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.createCategory(Category(parentId = 1L, type = CategoryType.EXPENSE, name = "Child")) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.createCategory(Category(parentId = 2L, type = CategoryType.EXPENSE, name = "Child")) } }
        listOf(1L, 2L).forEach { categoryId ->
            assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.createTransaction(expense(categoryId)) } }
        }
    }

    @Test
    fun `create category is insert only and rejects existing ids or deleted state`() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.createCategory(Category(99L, type = CategoryType.EXPENSE, name = "Existing")) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.createCategory(Category(type = CategoryType.EXPENSE, name = "Deleted", isDeleted = true)) } }
        val createdId = repository.createCategory(Category(type = CategoryType.EXPENSE, name = "Created"))
        assertEquals(1L, createdId)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.createCategory(Category(createdId, type = CategoryType.EXPENSE, name = "Replacement")) } }
        assertEquals("Created", database.categoryDao().getById(createdId)!!.name)
    }

    @Test
    fun `explicit transaction dates survive month year and midnight boundaries`() = runBlocking {
        repository.createAccount(account(1L, "Bank"))
        val foodCategoryId = repository.createCategory(Category(type = CategoryType.EXPENSE, name = "Food"))
        val dates = listOf(
            LocalDate.of(2025, 12, 31) to Instant.parse("2026-01-01T00:30:00Z"),
            LocalDate.of(2026, 1, 1) to Instant.parse("2025-12-31T23:30:00Z"),
            LocalDate.of(2026, 12, 31) to Instant.parse("2026-12-31T23:59:59Z"),
        )
        dates.forEachIndexed { index, (date, createdAt) ->
            repository.createTransaction(FinancialTransaction(accountId = 1L, type = TransactionType.EXPENSE, amount = Money(index + 1L, inr), categoryId = foodCategoryId, transactionDate = date, createdAt = createdAt))
        }
        val actualDates = database.transactionDao().getTransactionsForAccount(1L).map { entity -> LocalDate.ofEpochDay(entity.occurredDateEpochDay) }
        assertEquals(dates.map { it.first }.sortedDescending(), actualDates)
    }

    @Test
    fun `opening balance overflow fails instead of wrapping`(): Unit = runBlocking {
        repository.createAccount(account(1L, "Near Limit", openingBalance = Long.MAX_VALUE))
        val incomeCategoryId = repository.createCategory(Category(type = CategoryType.INCOME, name = "Income"))
        repository.createTransaction(FinancialTransaction(accountId = 1L, type = TransactionType.INCOME, amount = Money(1L, inr), categoryId = incomeCategoryId, transactionDate = transactionDate, createdAt = now))
        assertThrows(ArithmeticException::class.java) { runBlocking { repository.calculateAccountBalance(1L) } }
    }

    @Test
    fun `SQLite aggregate overflow fails instead of wrapping`(): Unit = runBlocking {
        repository.createAccount(account(1L, "Aggregate Limit"))
        val largeIncomeCategoryId = repository.createCategory(Category(type = CategoryType.INCOME, name = "Large Income"))
        repeat(2) {
            repository.createTransaction(FinancialTransaction(accountId = 1L, type = TransactionType.INCOME, amount = Money(Long.MAX_VALUE, inr), categoryId = largeIncomeCategoryId, transactionDate = transactionDate, createdAt = now))
        }
        assertThrows(RuntimeException::class.java) { runBlocking { repository.calculateAccountBalance(1L) } }
    }

    @Test
    fun `transaction creation is insert only and retrieval is date ordered`(): Unit = runBlocking {
        repository.createAccount(account(1L, "Bank"))
        val categoryId = repository.createCategory(Category(type = CategoryType.EXPENSE, name = "Food"))
        val first = expense(categoryId).copy(transactionDate = LocalDate.of(2026, 1, 1), createdAt = now.plusSeconds(2), merchantOrPayee = "Cafe")
        val second = expense(categoryId).copy(transactionDate = LocalDate.of(2026, 1, 2), createdAt = now.plusSeconds(1), merchantOrPayee = "Market")
        repository.createTransaction(first)
        repository.createTransaction(second)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.createTransaction(first.copy(id = 99L)) } }
        assertEquals(listOf(second.transactionDate, first.transactionDate), repository.getTransactions(1L).map { it.transactionDate })
        assertEquals(listOf(first.transactionDate), repository.getTransactions(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1)).map { it.transactionDate })
    }

    @Test
    fun `summary excludes transfers and groups exact income and expense totals`(): Unit = runBlocking {
        repository.createAccount(account(1L, "Bank"))
        repository.createAccount(account(2L, "Wallet"))
        val incomeCategoryId = repository.createCategory(Category(type = CategoryType.INCOME, name = "Salary"))
        val expenseCategoryId = repository.createCategory(Category(type = CategoryType.EXPENSE, name = "Food"))
        repository.createTransaction(FinancialTransaction(accountId = 1L, type = TransactionType.INCOME, amount = Money(10_000, inr), categoryId = incomeCategoryId, transactionDate = transactionDate, createdAt = now))
        repository.createTransaction(FinancialTransaction(accountId = 1L, type = TransactionType.EXPENSE, amount = Money(2_500, inr), categoryId = expenseCategoryId, transactionDate = transactionDate, createdAt = now.plusSeconds(1)))
        repository.createTransfer(Transfer("summary-transfer", 1L, 2L, Money(4_000, inr), transactionDate, now.plusSeconds(2)))

        val summary = repository.summarize(transactionDate, transactionDate)
        assertEquals(10_000L, summary.income.minorUnits)
        assertEquals(2_500L, summary.expense.minorUnits)
        assertEquals(2_500L, summary.expenseByCategory[expenseCategoryId]!!.minorUnits)
    }

    @Test
    fun `analytics repository report preserves category comparison and account filtering`(): Unit = runBlocking {
        repository.createAccount(account(1L, "Bank"))
        repository.createAccount(account(2L, "Wallet"))
        val foodCategoryId = repository.createCategory(Category(type = CategoryType.EXPENSE, name = "Food"))
        repository.createTransaction(expense(foodCategoryId).copy(transactionDate = LocalDate.of(2026, 1, 31), amount = Money(300, inr)))
        repository.createTransaction(expense(foodCategoryId).copy(transactionDate = LocalDate.of(2026, 2, 1), amount = Money(500, inr)))
        repository.createTransaction(expense(foodCategoryId).copy(accountId = 2L, transactionDate = LocalDate.of(2026, 2, 1), amount = Money(9_000, inr)))

        val report = repository.analyze(
            ReportingPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 28), ReportingGranularity.MONTH),
            accountId = 1L,
        )

        assertEquals(listOf(300L, 500L), report.buckets.map { it.expense.minorUnits })
        assertEquals(200L, report.categoryComparisons.single { it.categoryId == foodCategoryId }.change.minorUnits)
    }

    @Test
    fun `recurring expense detection filters account date range and transfers`(): Unit = runBlocking {
        repository.createAccount(account(1L, "Bank"))
        repository.createAccount(account(2L, "Wallet"))
        val categoryId = repository.createCategory(Category(type = CategoryType.EXPENSE, name = "Subscriptions"))
        listOf(
            LocalDate.of(2026, 1, 2),
            LocalDate.of(2026, 1, 9),
            LocalDate.of(2026, 1, 16),
        ).forEach { date ->
            repository.createTransaction(expense(categoryId).copy(transactionDate = date, merchantOrPayee = " Stream   Service "))
        }
        repository.createTransaction(expense(categoryId).copy(accountId = 2L, transactionDate = LocalDate.of(2026, 1, 23), merchantOrPayee = "Stream Service"))
        repository.createTransfer(transfer(1L, 2L, "recurring-transfer"))

        val patterns = repository.detectRecurringExpenses(
            LocalDate.of(2026, 1, 2),
            LocalDate.of(2026, 1, 16),
            accountId = 1L,
        )

        assertEquals(1, patterns.size)
        assertEquals(3, patterns.single().transactions.size)
        assertEquals("stream service", patterns.single().merchantOrPayee)
    }

    @Test
    fun `duplicate detection normalizes merchant and correction preserves history atomically`(): Unit = runBlocking {
        repository.createAccount(account(1L, "Bank"))
        val categoryId = repository.createCategory(Category(type = CategoryType.EXPENSE, name = "Food"))
        val original = expense(categoryId).copy(merchantOrPayee = "  Fresh   Mart ")
        val originalId = repository.createTransaction(original)
        val candidate = expense(categoryId).copy(merchantOrPayee = "fresh mart")
        assertEquals(listOf(originalId), repository.findPotentialDuplicates(candidate).map { it.transaction.id })

        val replacementId = repository.correctTransaction(originalId, candidate.copy(amount = Money(125L, inr)))
        assertEquals(1, database.transactionDao().getTransactionsForAccount(1L).size)
        assertTrue(database.transactionDao().getById(originalId)!!.isDeleted)
        assertEquals(125L, repository.getTransactions(1L).single { it.id == replacementId }.amount.minorUnits)
    }

    @Test
    fun `duplicate detection rejects inactive category and corrections reject transfers`(): Unit = runBlocking {
        repository.createAccount(account(1L, "Bank"))
        repository.createAccount(account(2L, "Wallet"))
        val categoryId = repository.createCategory(Category(type = CategoryType.EXPENSE, name = "Food"))
        repository.deactivateCategoryForTest(categoryId)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.findPotentialDuplicates(expense(categoryId)) } }
        val transferId = repository.createTransfer(Transfer("cannot-correct", 1L, 2L, Money(100, inr), transactionDate, now))
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.correctTransaction(transferId, expense(0L).copy(categoryId = null)) } }
    }

    private fun account(id: Long, name: String, type: AccountType = AccountType.BANK, isActive: Boolean = true, isDeleted: Boolean = false, openingBalance: Long = 0L) = Account(id = id, name = name, type = type, openingBalance = Money(openingBalance, inr), isActive = isActive, isDeleted = isDeleted, createdAt = now, updatedAt = now)
    private fun transfer(sourceId: Long, destinationId: Long, id: String) = Transfer(id, sourceId, destinationId, Money(100, inr), transactionDate, now)
    private fun expense(categoryId: Long) = FinancialTransaction(accountId = 1L, type = TransactionType.EXPENSE, amount = Money(100, inr), categoryId = categoryId, transactionDate = transactionDate, createdAt = now)

    private suspend fun LocalFinancialRepository.deactivateCategoryForTest(categoryId: Long) {
        database.categoryDao().setActive(categoryId, false, now.toEpochMilli())
    }

    private suspend fun assertTransferFails(block: suspend () -> Unit) {
        try { block() } catch (_: IllegalArgumentException) { return }
        throw AssertionError("Expected transfer creation to fail")
    }
}
