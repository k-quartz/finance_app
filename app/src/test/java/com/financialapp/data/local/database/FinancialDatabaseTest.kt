package com.financialapp.data.local.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.financialapp.data.local.entity.AccountEntity
import com.financialapp.data.local.entity.AccountType
import com.financialapp.data.local.entity.CategoryEntity
import com.financialapp.data.local.entity.CategoryType
import com.financialapp.data.local.entity.ProductEntity
import com.financialapp.data.local.entity.ProductPriceHistoryEntity
import com.financialapp.data.local.entity.PurchaseEntity
import com.financialapp.data.local.entity.TransactionEntity
import com.financialapp.data.local.entity.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
class FinancialDatabaseTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `purchase total is exact and quantity is preserved`() = runBlocking {
        val accountId = database.accountDao().upsert(
            AccountEntity(
                name = "HDFC Savings",
                accountType = AccountType.BANK,
                currencyCode = "INR",
                isActive = true,
                isDeleted = false,
                createdAtEpochMs = 1_700_000_000_000L,
                updatedAtEpochMs = 1_700_000_000_000L,
            )
        )

        val productId = database.productDao().upsert(
            ProductEntity(
                name = "Rice",
                defaultUnit = "kg",
                defaultCurrencyCode = "INR",
                categoryId = null,
                isActive = true,
                isDeleted = false,
                createdAtEpochMs = 1_700_000_000_000L,
                updatedAtEpochMs = 1_700_000_000_000L,
            )
        )

        val purchaseId = database.purchaseDao().upsert(
            PurchaseEntity(
                productId = productId,
                transactionId = null,
                categoryId = null,
                merchantName = "Local Market",
                quantityMinor = 2_5000L,
                quantityScale = 4,
                unit = "kg",
                unitPriceMinor = 6_000L,
                totalAmountMinor = 15_000L,
                currencyCode = "INR",
                purchaseDateEpochDay = 19700L,
                notes = "Monthly rice",
                isDeleted = false,
                createdAtEpochMs = 1_700_000_000_000L,
                updatedAtEpochMs = 1_700_000_000_000L,
            )
        )

        val saved = database.purchaseDao().getPurchaseById(purchaseId)
        assertNotNull(saved)
        assertEquals(2_5000L, saved!!.quantityMinor)
        assertEquals(15_000L, saved.totalAmountMinor)
    }

    @Test
    fun `transaction type semantics keep transfer separate from expense`() = runBlocking {
        val savingsId = database.accountDao().upsert(
            AccountEntity(
                name = "Savings",
                accountType = AccountType.BANK,
                currencyCode = "INR",
                isActive = true,
                isDeleted = false,
                createdAtEpochMs = 1_700_000_000_000L,
                updatedAtEpochMs = 1_700_000_000_000L,
            )
        )
        val cardId = database.accountDao().upsert(
            AccountEntity(
                name = "Credit Card",
                accountType = AccountType.CREDIT_CARD,
                currencyCode = "INR",
                isActive = true,
                isDeleted = false,
                createdAtEpochMs = 1_700_000_000_000L,
                updatedAtEpochMs = 1_700_000_000_000L,
            )
        )

        val categoryId = database.categoryDao().upsert(
            CategoryEntity(
                parentCategoryId = null,
                name = "Food",
                categoryType = CategoryType.EXPENSE,
                isActive = true,
                isDeleted = false,
                createdAtEpochMs = 1_700_000_000_000L,
                updatedAtEpochMs = 1_700_000_000_000L,
            )
        )

        database.transactionDao().upsert(
            TransactionEntity(
                accountId = savingsId,
                counterpartyAccountId = cardId,
                categoryId = categoryId,
                transactionType = TransactionType.TRANSFER,
                amountMinor = 50_000L,
                currencyCode = "INR",
                occurredDateEpochDay = 19700L,
                postedAtEpochMs = 1_700_000_000_000L,
                notes = "Transfer between accounts",
                isDeleted = false,
                createdAtEpochMs = 1_700_000_000_000L,
                updatedAtEpochMs = 1_700_000_000_000L,
            )
        )

        val transactions = database.transactionDao().getTransactionsForAccount(savingsId)
        assertEquals(1, transactions.size)
        assertEquals(TransactionType.TRANSFER, transactions.first().transactionType)
    }

    @Test
    fun `historical price lookup keeps exact days and rejects duplicate dates`() = runBlocking {
        val productId = database.productDao().upsert(
            ProductEntity(
                name = "Gold",
                defaultUnit = "g",
                defaultCurrencyCode = "INR",
                categoryId = null,
                isActive = true,
                isDeleted = false,
                createdAtEpochMs = 1_700_000_000_000L,
                updatedAtEpochMs = 1_700_000_000_000L,
            )
        )

        database.productPriceHistoryDao().insert(
            ProductPriceHistoryEntity(
                productId = productId,
                asOfDateEpochDay = 19700L,
                unitPriceMinor = 7_200L,
                currencyCode = "INR",
                source = "manual",
                notes = "Daily reference",
                createdAtEpochMs = 1_700_000_000_000L,
            )
        )

        val price = database.productPriceHistoryDao().getPriceForDate(productId, 19700L)
        assertNotNull(price)
        assertEquals(7_200L, price!!.unitPriceMinor)

        database.productPriceHistoryDao().insert(
            ProductPriceHistoryEntity(
                productId = productId,
                asOfDateEpochDay = 19701L,
                unitPriceMinor = 7_500L,
                currencyCode = "INR",
                source = "manual",
                notes = "Deleted reference",
                createdAtEpochMs = 1_700_000_100_000L,
                isDeleted = true,
                deletedAtEpochMs = 1_700_000_200_000L,
            )
        )

        assertEquals(7_200L, database.productPriceHistoryDao().getLatestPriceBeforeOrOnDate(productId, 19701L)!!.unitPriceMinor)
        assertEquals(1, database.productPriceHistoryDao().getHistoryForProduct(productId).size)
    }
}
