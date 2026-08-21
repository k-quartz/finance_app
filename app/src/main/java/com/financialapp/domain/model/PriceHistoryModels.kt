package com.financialapp.domain.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate

data class CommodityPricePoint(
    val id: Long = 0L,
    val productId: Long,
    val asOfDate: LocalDate,
    val price: Money,
    val source: String? = null,
    val notes: String? = null,
    val createdAt: Instant,
)

enum class CommodityPriceDirection { UP, DOWN, FLAT }

data class CommodityPriceTrend(
    val productId: Long,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val openingPoint: CommodityPricePoint,
    val closingPoint: CommodityPricePoint,
    val change: Money,
    val changePercent: BigDecimal?,
    val direction: CommodityPriceDirection,
    val observations: List<CommodityPricePoint>,
)

data class CommodityPriceComparison(
    val productId: Long,
    val productName: String,
    val asOfDate: LocalDate,
    val latestPoint: CommodityPricePoint,
    val previousPoint: CommodityPricePoint?,
    val change: Money?,
    val changePercent: BigDecimal?,
    val direction: CommodityPriceDirection,
)

fun calculatePriceChangePercent(change: Money, baseline: Money): BigDecimal? {
    require(change.currencyCode == baseline.currencyCode) { "Price values must use the same currency" }
    if (baseline.minorUnits == 0L) return null
    return change.minorUnits.toBigDecimal()
        .multiply(BigDecimal.valueOf(100L))
        .divide(baseline.minorUnits.toBigDecimal(), 4, RoundingMode.HALF_UP)
}