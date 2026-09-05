package com.financialapp.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.financialapp.data.local.database.AppDatabase
import com.financialapp.data.local.entity.AccountEntity
import com.financialapp.data.local.entity.AccountType
import com.financialapp.data.local.entity.CategoryEntity
import com.financialapp.data.local.entity.CategoryType
import com.financialapp.data.local.entity.InvestmentTransactionType
import com.financialapp.data.local.entity.MutualFundEntity
import com.financialapp.data.local.entity.MutualFundHoldingEntity
import com.financialapp.data.local.entity.MutualFundNavEntity
import com.financialapp.data.local.entity.MutualFundTransactionEntity
import com.financialapp.data.local.entity.ProductEntity
import com.financialapp.data.local.entity.ProductPriceHistoryEntity
import com.financialapp.data.local.entity.PurchaseEntity
import com.financialapp.data.local.entity.TransactionEntity
import com.financialapp.data.local.entity.TransactionType
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupRepositoryTest {
    private lateinit var sourceDatabase: AppDatabase
    private lateinit var targetDatabase: AppDatabase
    private lateinit var sourceRepository: BackupRepository
    private lateinit var targetRepository: BackupRepository

    @Before
    fun setUp() = runBlocking {
        sourceDatabase = buildDatabase()
        targetDatabase = buildDatabase()
        seedDatabase(sourceDatabase)
        sourceRepository = buildRepository(sourceDatabase)
        targetRepository = buildRepository(targetDatabase)
    }

    @After
    fun tearDown() {
        sourceDatabase.close()
        targetDatabase.close()
    }

    @Test
    fun `export and restore preserve the full local dataset`() {
        runBlocking {
            val backupJson = sourceRepository.exportBackup()

            targetRepository.restoreBackup(backupJson)

            assertEquals(sourceDatabase.accountDao().getAll(), targetDatabase.accountDao().getAll())
            assertEquals(sourceDatabase.categoryDao().getAll(), targetDatabase.categoryDao().getAll())
            assertEquals(sourceDatabase.transactionDao().getAll(), targetDatabase.transactionDao().getAll())
            assertEquals(sourceDatabase.productDao().getAll(), targetDatabase.productDao().getAll())
            assertEquals(sourceDatabase.productPriceHistoryDao().getAll(), targetDatabase.productPriceHistoryDao().getAll())
            assertEquals(sourceDatabase.purchaseDao().getAll(), targetDatabase.purchaseDao().getAll())
            assertEquals(sourceDatabase.mutualFundDao().getAllFunds(), targetDatabase.mutualFundDao().getAllFunds())
            assertEquals(sourceDatabase.mutualFundDao().getAllHoldingsList(), targetDatabase.mutualFundDao().getAllHoldingsList())
            assertEquals(sourceDatabase.mutualFundDao().getAllTransactions(), targetDatabase.mutualFundDao().getAllTransactions())
            assertEquals(sourceDatabase.mutualFundDao().getAllNavHistory(), targetDatabase.mutualFundDao().getAllNavHistory())

            val exported = JSONObject(backupJson)
            assertEquals(1, exported.getInt("formatVersion"))
            assertEquals(8, exported.getInt("databaseVersion"))
            assertTrue(exported.getJSONArray("accounts").length() > 0)
        }
    }

    @Test
    fun `malformed backup json is rejected`() {
        runBlocking {
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    targetRepository.restoreBackup("not-json")
                }
            }
        }
    }

    @Test
    fun `failed restore rolls back partial writes`() {
        runBlocking {
            val beforeAccounts = sourceDatabase.accountDao().getAll()
            val beforeTransactions = sourceDatabase.transactionDao().getAll()
            val backupJson = JSONObject(sourceRepository.exportBackup())
            backupJson.getJSONArray("transactions").getJSONObject(0).put("accountId", 999_999L)

            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    sourceRepository.restoreBackup(backupJson.toString())
                }
            }

            assertEquals(beforeAccounts, sourceDatabase.accountDao().getAll())
            assertEquals(beforeTransactions, sourceDatabase.transactionDao().getAll())
            assertEquals(2, sourceDatabase.accountDao().getAll().size)
            assertEquals(2, sourceDatabase.transactionDao().getAll().size)
        }
    }

    private fun buildDatabase(): AppDatabase = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        AppDatabase::class.java,
    ).allowMainThreadQueries().build()

    private fun buildRepository(database: AppDatabase): BackupRepository = LocalBackupRepository(
        database = database,
        accountDao = database.accountDao(),
        categoryDao = database.categoryDao(),
        transactionDao = database.transactionDao(),
        productDao = database.productDao(),
        productPriceHistoryDao = database.productPriceHistoryDao(),
        purchaseDao = database.purchaseDao(),
        mutualFundDao = database.mutualFundDao(),
        clock = Clock.fixed(Instant.parse("2026-08-20T12:00:00Z"), ZoneOffset.UTC),
    )

    private suspend fun seedDatabase(database: AppDatabase) {
        database.accountDao().upsert(
            AccountEntity(
                accountId = 1L,
                accountType = AccountType.BANK,
                name = "Primary Savings",
                currencyCode = "INR",
                openingBalanceMinor = 125_000L,
                isActive = true,
                isDeleted = false,
                createdAtEpochMs = 1_700_000_000_000L,
                updatedAtEpochMs = 1_700_000_000_000L,
                notes = "Main account",
            ),
        )
        database.accountDao().upsert(
            AccountEntity(
                accountId = 2L,
                accountType = AccountType.CREDIT_CARD,
                name = "Archived Card",
                currencyCode = "INR",
                openingBalanceMinor = 0L,
                isActive = false,
                isDeleted = true,
                deletedAtEpochMs = 1_700_000_100_000L,
                createdAtEpochMs = 1_700_000_000_000L,
                updatedAtEpochMs = 1_700_000_100_000L,
                notes = null,
            ),
        )
        database.categoryDao().upsert(
            CategoryEntity(
                categoryId = 10L,
                parentCategoryId = null,
                categoryType = CategoryType.EXPENSE,
                name = "Household",
                sortOrder = 0,
                isActive = true,
                isDeleted = false,
                createdAtEpochMs = 1_700_000_000_000L,
                updatedAtEpochMs = 1_700_000_000_000L,
            ),
        )
        database.categoryDao().upsert(
            CategoryEntity(
                categoryId = 11L,
                parentCategoryId = 10L,
                categoryType = CategoryType.EXPENSE,
                name = "Groceries",
                sortOrder = 1,
                isActive = false,
                isDeleted = true,
                deletedAtEpochMs = 1_700_000_100_000L,
                createdAtEpochMs = 1_700_000_000_000L,
                updatedAtEpochMs = 1_700_000_100_000L,
            ),
        )
        database.productDao().upsert(
            ProductEntity(
                productId = 20L,
                name = "Rice",
                defaultUnit = "kg",
                defaultCurrencyCode = "INR",
                categoryId = 10L,
                isActive = true,
                isDeleted = false,
                createdAtEpochMs = 1_700_000_000_000L,
                updatedAtEpochMs = 1_700_000_000_000L,
            ),
        )
        database.productDao().upsert(
            ProductEntity(
                productId = 21L,
                name = "Tea",
                defaultUnit = "g",
                defaultCurrencyCode = "INR",
                categoryId = 11L,
                isActive = false,
                isDeleted = true,
                deletedAtEpochMs = 1_700_000_100_000L,
                createdAtEpochMs = 1_700_000_000_000L,
                updatedAtEpochMs = 1_700_000_100_000L,
            ),
        )
        database.transactionDao().upsert(
            TransactionEntity(
                transactionId = 30L,
                accountId = 1L,
                categoryId = 10L,
                transactionType = TransactionType.EXPENSE,
                amountMinor = 4_500L,
                currencyCode = "INR",
                occurredDateEpochDay = 19_000L,
                postedAtEpochMs = 1_700_000_000_000L,
                notes = "Monthly groceries",
                merchantName = "Local Store",
                isDeleted = false,
                createdAtEpochMs = 1_700_000_000_000L,
                updatedAtEpochMs = 1_700_000_000_000L,
            ),
        )
        database.transactionDao().upsert(
            TransactionEntity(
                transactionId = 31L,
                accountId = 1L,
                categoryId = null,
                transactionType = TransactionType.INCOME,
                amountMinor = 25_000L,
                currencyCode = "INR",
                occurredDateEpochDay = 19_001L,
                postedAtEpochMs = 1_700_000_100_000L,
                notes = null,
                merchantName = "Salary",
                isDeleted = true,
                deletedAtEpochMs = 1_700_000_200_000L,
                createdAtEpochMs = 1_700_000_100_000L,
                updatedAtEpochMs = 1_700_000_200_000L,
            ),
        )
        database.purchaseDao().insert(
            PurchaseEntity(
                purchaseId = 40L,
                productId = 20L,
                transactionId = 30L,
                categoryId = 10L,
                merchantName = "Local Store",
                quantityMinor = 2_5000L,
                quantityScale = 4,
                unit = "kg",
                unitPriceMinor = 6_000L,
                totalAmountMinor = 15_000L,
                currencyCode = "INR",
                purchaseDateEpochDay = 19_000L,
                notes = "Bulk rice purchase",
                isDeleted = false,
                createdAtEpochMs = 1_700_000_000_000L,
                updatedAtEpochMs = 1_700_000_000_000L,
            ),
        )
        database.productPriceHistoryDao().insert(
            ProductPriceHistoryEntity(
                priceHistoryId = 50L,
                productId = 20L,
                asOfDateEpochDay = 19_000L,
                unitPriceMinor = 6_000L,
                currencyCode = "INR",
                source = "manual",
                notes = "Shelf price",
                createdAtEpochMs = 1_700_000_000_000L,
                isDeleted = false,
                updatedAtEpochMs = 1_700_000_000_000L,
            ),
        )
        database.mutualFundDao().insertFund(
            MutualFundEntity(
                fundId = 60L,
                name = "Growth Fund",
                code = "GFUND",
                currencyCode = "INR",
                isActive = true,
                isDeleted = false,
                createdAtEpochMs = 1_700_000_000_000L,
                updatedAtEpochMs = 1_700_000_000_000L,
            ),
        )
        database.mutualFundDao().insertFund(
            MutualFundEntity(
                fundId = 61L,
                name = "Archived Fund",
                code = "AFUND",
                currencyCode = "INR",
                isActive = false,
                isDeleted = true,
                createdAtEpochMs = 1_700_000_000_000L,
                updatedAtEpochMs = 1_700_000_100_000L,
            ),
        )
        database.mutualFundDao().insertHolding(
            MutualFundHoldingEntity(
                holdingId = 70L,
                fundId = 60L,
                investmentAccountId = 1L,
                unitsMinor = 1_5000L,
                unitsScale = 4,
                updatedAtEpochMs = 1_700_000_000_000L,
            ),
        )
        database.mutualFundDao().insertTransaction(
            MutualFundTransactionEntity(
                investmentTransactionId = 80L,
                fundId = 60L,
                investmentAccountId = 1L,
                cashAccountId = 1L,
                transactionType = InvestmentTransactionType.PURCHASE,
                unitsMinor = 1_5000L,
                unitsScale = 4,
                navMinor = 100L,
                totalAmountMinor = 150L,
                currencyCode = "INR",
                transactionDateEpochDay = 19_000L,
                linkedCashTransactionId = 30L,
                notes = "Monthly SIP",
                createdAtEpochMs = 1_700_000_000_000L,
            ),
        )
        database.mutualFundDao().insertNav(
            MutualFundNavEntity(
                navId = 90L,
                fundId = 60L,
                asOfDateEpochDay = 19_000L,
                navMinor = 100L,
                currencyCode = "INR",
                source = "manual",
                notes = null,
                createdAtEpochMs = 1_700_000_000_000L,
            ),
        )
    }
}