package com.financialapp.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.financialapp.data.local.database.AppDatabase
import com.financialapp.data.local.entity.AccountEntity
import com.financialapp.data.local.entity.AccountType
import com.financialapp.domain.model.FundUnits
import com.financialapp.domain.model.InvestmentTransactionType
import com.financialapp.domain.model.Money
import com.financialapp.domain.model.MutualFund
import com.financialapp.domain.model.MutualFundTransaction
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MutualFundRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: MutualFundRepository
    private val date = LocalDate.of(2026, 1, 1)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = LocalMutualFundRepository(database, database.mutualFundDao(), database.accountDao(), database.transactionDao())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `purchase redemption and valuation preserve exact holding`() = runBlocking {
        val accountId = insertAccount("Investment", AccountType.INVESTMENT)
        val fundId = repository.createFund(MutualFund(name = "Index Fund", code = "INDEX-1"))
        repository.recordPurchase(transaction(fundId, accountId, InvestmentTransactionType.PURCHASE, 2_000L, 1_000L, 2_000L))
        repository.recordRedemption(transaction(fundId, accountId, InvestmentTransactionType.REDEMPTION, 500L, 1_200L, 600L))
        repository.recordNav(fundId, Money(1_300L, "INR"), date, Instant.ofEpochMilli(2_000L))

        val holding = repository.getHolding(fundId, accountId)!!
        assertEquals(FundUnits(1_500L, 3), holding.units)
        val valuation = repository.valueAt(fundId, accountId, date)
        assertEquals(Money(1_950L, "INR"), valuation.value)
        assertEquals("27.5000", repository.calculateReturn(fundId, accountId, date).returnPercent!!.toPlainString())
    }

    @Test
    fun `redemption cannot exceed persisted holding`(): Unit = runBlocking {
        val accountId = insertAccount("Investment", AccountType.INVESTMENT)
        val fundId = repository.createFund(MutualFund(name = "Index Fund", code = "INDEX-2"))
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.recordRedemption(transaction(fundId, accountId, InvestmentTransactionType.REDEMPTION, 1_000L, 1_000L, 1_000L)) }
        }
    }

    @Test
    fun `linked cash transaction must have the purchase direction`(): Unit = runBlocking {
        val investmentAccountId = insertAccount("Investment", AccountType.INVESTMENT)
        val cashAccountId = insertAccount("Cash", AccountType.BANK)
        val fundId = repository.createFund(MutualFund(name = "Index Fund", code = "INDEX-3"))
        val categoryId = database.categoryDao().upsert(com.financialapp.data.local.entity.CategoryEntity(name = "Income", categoryType = com.financialapp.data.local.entity.CategoryType.INCOME, createdAtEpochMs = 1L, updatedAtEpochMs = 1L))
        val transactionId = database.transactionDao().insert(com.financialapp.data.local.entity.TransactionEntity(accountId = cashAccountId, categoryId = categoryId, transactionType = com.financialapp.data.local.entity.TransactionType.INCOME, amountMinor = 1_000L, currencyCode = "INR", occurredDateEpochDay = date.toEpochDay(), postedAtEpochMs = 1L, createdAtEpochMs = 1L, updatedAtEpochMs = 1L))
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.recordPurchase(transaction(fundId, investmentAccountId, InvestmentTransactionType.PURCHASE, 1_000L, 1_000L, 1_000L, cashAccountId, transactionId)) }
        }
    }

    private suspend fun insertAccount(name: String, type: AccountType): Long = database.accountDao().upsert(AccountEntity(accountType = type, name = name, currencyCode = "INR", createdAtEpochMs = 1L, updatedAtEpochMs = 1L))

    private fun transaction(fundId: Long, accountId: Long, type: InvestmentTransactionType, unitsMinor: Long, navMinor: Long, amountMinor: Long, cashAccountId: Long? = null, linkedCashId: Long? = null) = MutualFundTransaction(
        fundId = fundId, investmentAccountId = accountId, cashAccountId = cashAccountId, type = type,
        units = FundUnits(unitsMinor, 3), nav = Money(navMinor, "INR"), totalAmount = Money(amountMinor, "INR"),
        transactionDate = date, linkedCashTransactionId = linkedCashId, createdAt = Instant.ofEpochMilli(1_000L),
    )
}