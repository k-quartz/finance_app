package com.financialapp.domain

import com.financialapp.domain.calculation.DashboardCalculator
import com.financialapp.domain.model.AccountBalance
import com.financialapp.domain.model.AccountType
import com.financialapp.domain.model.CommodityPriceComparison
import com.financialapp.domain.model.CommodityPriceDirection
import com.financialapp.domain.model.CommodityPricePoint
import com.financialapp.domain.model.DashboardCategorySummary
import com.financialapp.domain.model.DashboardPeriod
import com.financialapp.domain.model.DashboardSummary
import com.financialapp.domain.model.FundUnits
import com.financialapp.domain.model.Money
import com.financialapp.domain.model.MutualFundHoldingValue
import com.financialapp.domain.model.MutualFundPortfolioSummary
import com.financialapp.domain.model.ReportingGranularity
import com.financialapp.domain.model.ReportingPeriod
import com.financialapp.domain.model.TransactionAnalyticsReport
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DashboardCalculatorTest {
    private val asOfDate = LocalDate.of(2026, 2, 15)
    private val accountBalances = listOf(
        AccountBalance(1L, Money(27_000L, "INR"), AccountType.BANK),
        AccountBalance(2L, Money(500L, "INR"), AccountType.CREDIT_CARD),
    )
    private val portfolio = MutualFundPortfolioSummary(
        asOfDate = asOfDate,
        totalValue = Money(2_500L, "INR"),
        holdings = listOf(
            MutualFundHoldingValue(
                fundId = 1L,
                fundName = "Index Fund",
                investmentAccountId = 3L,
                units = FundUnits(2_000L, 3),
                nav = Money(1_250L, "INR"),
                value = Money(2_500L, "INR"),
            ),
        ),
    )

    @Test
    fun `summary rolls up liabilities assets savings rate and categories`() {
        val summary = DashboardCalculator.summarize(
            period = DashboardPeriod.MONTH,
            asOfDate = asOfDate,
            report = report(),
            accountBalances = accountBalances,
            portfolio = portfolio,
            categoryNames = mapOf(10L to "Food"),
            priceMovers = listOf(priceComparison()),
        )

        assertEquals(Money(27_000L, "INR"), summary.assetBalance)
        assertEquals(Money(500L, "INR"), summary.liabilityBalance)
        assertEquals(Money(29_000L, "INR"), summary.netWorth)
        assertEquals(Money(12_000L, "INR"), summary.currentIncome)
        assertEquals(Money(3_500L, "INR"), summary.currentExpense)
        assertEquals("70.83", summary.savingsRatePercent!!.toPlainString())
        assertEquals("Food", summary.topExpenseCategories.single().categoryName)
        assertEquals(Money(3_500L, "INR"), summary.topExpenseCategories.single().amount)
        assertEquals(Money(2_500L, "INR"), summary.portfolio.totalValue)
        assertEquals(1, summary.priceMovers.size)
    }

    @Test
    fun `reporting window spans current and previous period`() {
        val (previous, current) = DashboardCalculator.reportingWindow(DashboardPeriod.YEAR, asOfDate)
        assertEquals(LocalDate.of(2025, 1, 1), previous.startDate)
        assertEquals(LocalDate.of(2025, 12, 31), previous.endDate)
        assertEquals(LocalDate.of(2026, 1, 1), current.startDate)
        assertEquals(LocalDate.of(2026, 12, 31), current.endDate)
        assertEquals(ReportingGranularity.YEAR, current.granularity)
    }

    private fun report(): TransactionAnalyticsReport = TransactionAnalyticsReport(
        period = ReportingPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 28), ReportingGranularity.MONTH),
        buckets = listOf(
            bucket(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), 10_000L, 2_000L),
            bucket(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28), 12_000L, 3_500L),
        ),
        categoryComparisons = emptyList(),
    )

    private fun bucket(start: LocalDate, end: LocalDate, income: Long, expense: Long) = com.financialapp.domain.model.PeriodBucket(
        startDate = start,
        endDate = end,
        income = Money(income, "INR"),
        expense = Money(expense, "INR"),
        expenseByCategory = mapOf(10L to Money(expense, "INR")),
    )

    private fun priceComparison() = CommodityPriceComparison(
        productId = 1L,
        productName = "Rice",
        asOfDate = asOfDate,
        latestPoint = CommodityPricePoint(1L, 1L, asOfDate, Money(1_200L, "INR"), createdAt = Instant.EPOCH),
        previousPoint = CommodityPricePoint(2L, 1L, asOfDate.minusDays(1), Money(1_000L, "INR"), createdAt = Instant.EPOCH),
        change = Money(200L, "INR"),
        changePercent = BigDecimal("20.0000"),
        direction = CommodityPriceDirection.UP,
    )
}