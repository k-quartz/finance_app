package com.financialapp.domain.calculation

import com.financialapp.domain.model.CommodityPriceComparison
import com.financialapp.domain.model.CommodityPriceDirection
import com.financialapp.domain.model.CommodityPricePoint
import com.financialapp.domain.model.CommodityPriceTrend
import com.financialapp.domain.model.Money
import com.financialapp.domain.model.calculatePriceChangePercent
import java.time.LocalDate

object CommodityPriceAnalytics {
    fun analyzeTrend(
        productId: Long,
        points: List<CommodityPricePoint>,
        startDate: LocalDate,
        endDate: LocalDate,
    ): CommodityPriceTrend {
        require(!endDate.isBefore(startDate)) { "End date cannot precede start date" }
        val ordered = points.filter { !it.asOfDate.isAfter(endDate) }.sortedBy { it.asOfDate }
        val openingPoint = ordered.lastOrNull { !it.asOfDate.isAfter(startDate) }
            ?: throw IllegalArgumentException("Price history is not available for the requested start date")
        val closingPoint = ordered.lastOrNull()
            ?: throw IllegalArgumentException("Price history is not available for the requested end date")
        val change = closingPoint.price - openingPoint.price
        return CommodityPriceTrend(
            productId = productId,
            startDate = startDate,
            endDate = endDate,
            openingPoint = openingPoint,
            closingPoint = closingPoint,
            change = change,
            changePercent = calculatePriceChangePercent(change, openingPoint.price),
            direction = changeDirection(change),
            observations = ordered.filter { it.asOfDate in startDate..endDate },
        )
    }

    fun compareLatestPrices(
        productNames: Map<Long, String>,
        pointsByProduct: Map<Long, List<CommodityPricePoint>>,
        asOfDate: LocalDate,
    ): List<CommodityPriceComparison> {
        return pointsByProduct.mapNotNull { (productId, points) ->
            val ordered = points.filter { !it.asOfDate.isAfter(asOfDate) }.sortedBy { it.asOfDate }
            val latestPoint = ordered.lastOrNull() ?: return@mapNotNull null
            val previousPoint = ordered.dropLast(1).lastOrNull()
            val change = previousPoint?.let { latestPoint.price - it.price }
            CommodityPriceComparison(
                productId = productId,
                productName = productNames[productId] ?: throw IllegalArgumentException("Product does not exist"),
                asOfDate = asOfDate,
                latestPoint = latestPoint,
                previousPoint = previousPoint,
                change = change,
                changePercent = change?.let { previousPoint?.let { baseline -> calculatePriceChangePercent(it, baseline.price) } },
                direction = changeDirection(change),
            )
        }.sortedWith(
            compareByDescending<CommodityPriceComparison> { it.latestPoint.price.minorUnits }
                .thenBy { it.productName }
                .thenBy { it.productId },
        )
    }

    private fun changeDirection(change: Money?): CommodityPriceDirection = when {
        change == null -> CommodityPriceDirection.FLAT
        change.minorUnits > 0L -> CommodityPriceDirection.UP
        change.minorUnits < 0L -> CommodityPriceDirection.DOWN
        else -> CommodityPriceDirection.FLAT
    }
}