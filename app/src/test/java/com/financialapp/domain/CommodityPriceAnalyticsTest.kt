package com.financialapp.domain

import com.financialapp.domain.calculation.CommodityPriceAnalytics
import com.financialapp.domain.model.CommodityPriceDirection
import com.financialapp.domain.model.CommodityPricePoint
import com.financialapp.domain.model.Money
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CommodityPriceAnalyticsTest {
    private val createdAt = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun `trend uses explicit boundary prices and exact percentage change`() {
        val points = listOf(
            price(1L, LocalDate.of(2026, 1, 1), 1_000L),
            price(1L, LocalDate.of(2026, 1, 10), 1_250L),
            price(1L, LocalDate.of(2026, 1, 20), 900L),
        )

        val trend = CommodityPriceAnalytics.analyzeTrend(1L, points, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 20))

        assertEquals(Money(-100L, "INR"), trend.change)
        assertEquals("-10.0000", trend.changePercent!!.toPlainString())
        assertEquals(CommodityPriceDirection.DOWN, trend.direction)
        assertEquals(3, trend.observations.size)
    }

    @Test
    fun `trend rejects missing start boundary`() {
        val points = listOf(price(1L, LocalDate.of(2026, 1, 10), 1_250L))

        assertThrows(IllegalArgumentException::class.java) {
            CommodityPriceAnalytics.analyzeTrend(1L, points, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 20))
        }
    }

    @Test
    fun `comparison sorts by latest price and preserves previous change`() {
        val productNames = mapOf(1L to "Rice", 2L to "Gold")
        val comparisons = CommodityPriceAnalytics.compareLatestPrices(
            productNames,
            mapOf(
                1L to listOf(price(1L, LocalDate.of(2026, 1, 1), 1_000L), price(1L, LocalDate.of(2026, 1, 20), 1_200L)),
                2L to listOf(price(2L, LocalDate.of(2026, 1, 1), 500L), price(2L, LocalDate.of(2026, 1, 20), 750L)),
            ),
            LocalDate.of(2026, 1, 20),
        )

        assertEquals(listOf(1L, 2L), comparisons.map { it.productId })
        assertEquals(Money(200L, "INR"), comparisons.first().change)
        assertEquals("20.0000", comparisons.first().changePercent!!.toPlainString())
        assertEquals(Money(250L, "INR"), comparisons.last().change)
    }

    private fun price(productId: Long, date: LocalDate, amount: Long) = CommodityPricePoint(
        productId = productId,
        asOfDate = date,
        price = Money(amount, "INR"),
        createdAt = createdAt,
    )
}