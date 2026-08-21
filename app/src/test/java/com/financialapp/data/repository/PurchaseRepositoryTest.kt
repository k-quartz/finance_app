package com.financialapp.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.financialapp.data.local.database.AppDatabase
import com.financialapp.domain.model.Category
import com.financialapp.domain.model.CategoryType
import com.financialapp.domain.model.Money
import com.financialapp.domain.model.Product
import com.financialapp.domain.model.Purchase
import com.financialapp.domain.model.Quantity
import com.financialapp.domain.model.calculatePurchaseTotal
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
class PurchaseRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var productRepository: LocalProductRepository
    private lateinit var purchaseRepository: LocalPurchaseRepository
    private val now = Instant.parse("2026-01-01T00:00:00Z")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        productRepository = LocalProductRepository(database.productDao(), database.categoryDao())
        purchaseRepository = LocalPurchaseRepository(database.purchaseDao(), database.productDao(), database.categoryDao(), database.transactionDao())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `purchase creation validates total and summary aggregates exact values`() = runBlocking {
        val categoryId = database.categoryDao().upsert(
            com.financialapp.data.local.entity.CategoryEntity(
                categoryType = com.financialapp.data.local.entity.CategoryType.EXPENSE,
                name = "Groceries",
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 1L,
            ),
        )
        val productId = productRepository.create(Product(name = "Rice", defaultUnit = "kg", categoryId = categoryId))
        val first = purchase(productId, Quantity(2500, 3, "kg"), Money(600, "INR"), categoryId, LocalDate.of(2026, 1, 1))
        val second = purchase(productId, Quantity(500, 3, "kg"), Money(700, "INR"), categoryId, LocalDate.of(2026, 1, 2))
        purchaseRepository.create(first)
        purchaseRepository.create(second)

        val summary = purchaseRepository.summarize(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2))

        assertEquals(1_850L, summary.totalSpend.minorUnits)
        assertEquals(2, summary.purchaseCount)
        assertEquals(3_000L, summary.quantityByProduct[productId]!!.minorUnits)
        assertEquals(1_850L, summary.spendByProduct[productId]!!.minorUnits)
    }

    @Test
    fun `purchase creation rejects mismatched total and inactive product`(): Unit = runBlocking {
        val productId = productRepository.create(Product(name = "Milk", defaultUnit = "l"))
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { purchaseRepository.create(purchase(productId, Quantity(1, 0, "l"), Money(100, "INR"), null, LocalDate.of(2026, 1, 1), total = Money(99, "INR"))) }
        }
        database.productDao().softDelete(productId, 2L, 2L)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { purchaseRepository.create(purchase(productId, Quantity(1, 0, "l"), Money(100, "INR"), null, LocalDate.of(2026, 1, 1))) }
        }
    }

    @Test
    fun `product creation rejects inactive category`(): Unit = runBlocking {
        val categoryId = database.categoryDao().upsert(
            com.financialapp.data.local.entity.CategoryEntity(
                categoryType = com.financialapp.data.local.entity.CategoryType.EXPENSE,
                name = "Archived",
                isActive = false,
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 1L,
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { productRepository.create(Product(name = "Tea", defaultUnit = "kg", categoryId = categoryId)) }
        }
    }

    private fun purchase(productId: Long, quantity: Quantity, unitPrice: Money, categoryId: Long?, date: LocalDate, total: Money = calculatePurchaseTotal(unitPrice, quantity)) =
        Purchase(productId = productId, categoryId = categoryId, quantity = quantity, unitPrice = unitPrice, totalAmount = total, purchaseDate = date, createdAt = now)
}