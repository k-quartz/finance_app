package com.financialapp.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.database.sqlite.SQLiteConstraintException
import com.financialapp.data.local.database.AppDatabase
import com.financialapp.data.local.entity.ProductEntity
import com.financialapp.domain.model.CommodityPricePoint
import com.financialapp.domain.model.Money
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProductPriceHistoryRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: ProductPriceHistoryRepository
    private val createdAt = Instant.parse("2026-01-01T00:00:00Z")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = LocalProductPriceHistoryRepository(database.productDao(), database.productPriceHistoryDao())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `recording price history is insert only and boundary lookups are exact`() {
        runBlocking {
            val productId = database.productDao().upsert(product("Rice"))

            repository.recordPrice(price(productId, LocalDate.of(2026, 1, 1), 1_000L))
            repository.recordPrice(price(productId, LocalDate.of(2026, 1, 5), 1_250L))

            val exact = repository.getPriceForDate(productId, LocalDate.of(2026, 1, 1).toEpochDay())
            assertNotNull(exact)
            assertEquals(1_000L, exact!!.price.minorUnits)
            assertEquals(null, repository.getPriceForDate(productId, LocalDate.of(2026, 1, 2).toEpochDay()))
            assertEquals(1_000L, repository.getLatestPriceBeforeOrOnDate(productId, LocalDate.of(2026, 1, 4).toEpochDay())!!.price.minorUnits)

            assertThrows(SQLiteConstraintException::class.java) {
                runBlocking { repository.recordPrice(price(productId, LocalDate.of(2026, 1, 5), 1_300L)) }
            }
        }
    }

    @Test
    fun `inactive products cannot receive history and deleted rows stay hidden`() {
        runBlocking {
            val productId = database.productDao().upsert(product("Gold", isActive = false))

            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repository.recordPrice(price(productId, LocalDate.of(2026, 1, 1), 7_200L)) }
            }

            val activeProductId = database.productDao().upsert(product("Silver"))
            database.productPriceHistoryDao().insert(
                com.financialapp.data.local.entity.ProductPriceHistoryEntity(
                    productId = activeProductId,
                    asOfDateEpochDay = LocalDate.of(2026, 1, 1).toEpochDay(),
                    unitPriceMinor = 5_000L,
                    currencyCode = "INR",
                    source = "manual",
                    notes = null,
                    createdAtEpochMs = 1_000L,
                    isDeleted = true,
                    deletedAtEpochMs = 2_000L,
                ),
            )

            assertEquals(null, repository.getPriceForDate(activeProductId, LocalDate.of(2026, 1, 1).toEpochDay()))
            assertEquals(null, repository.getLatestPriceBeforeOrOnDate(activeProductId, LocalDate.of(2026, 1, 1).toEpochDay()))
        }
    }

    @Test
    fun `trend and comparison use the stored history`() {
        runBlocking {
            val riceId = database.productDao().upsert(product("Rice"))
            val goldId = database.productDao().upsert(product("Gold"))

            repository.recordPrice(price(riceId, LocalDate.of(2026, 1, 1), 1_000L))
            repository.recordPrice(price(riceId, LocalDate.of(2026, 1, 10), 1_200L))
            repository.recordPrice(price(goldId, LocalDate.of(2026, 1, 1), 7_000L))
            repository.recordPrice(price(goldId, LocalDate.of(2026, 1, 10), 6_500L))

            val trend = repository.analyzeTrend(riceId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 10))
            assertEquals(Money(200L, "INR"), trend.change)
            assertEquals(2, trend.observations.size)

            val comparisons = repository.compareLatestPrices(listOf(riceId, goldId), LocalDate.of(2026, 1, 10))
            assertEquals(listOf(goldId, riceId), comparisons.map { it.productId })
            assertEquals("Rice", comparisons.last().productName)
        }
    }

    private fun product(name: String, isActive: Boolean = true) = ProductEntity(
        name = name,
        defaultUnit = "kg",
        defaultCurrencyCode = "INR",
        categoryId = null,
        isActive = isActive,
        isDeleted = false,
        createdAtEpochMs = 1_000L,
        updatedAtEpochMs = 1_000L,
    )

    private fun price(productId: Long, date: LocalDate, amount: Long) = CommodityPricePoint(
        productId = productId,
        asOfDate = date,
        price = Money(amount, "INR"),
        source = "manual",
        createdAt = createdAt,
    )
}