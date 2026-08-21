package com.financialapp.domain.calculation

import com.financialapp.domain.model.AccountBalance
import com.financialapp.domain.model.AccountType
import com.financialapp.domain.model.DashboardCategorySummary
import com.financialapp.domain.model.DashboardPeriod
import com.financialapp.domain.model.DashboardSummary
import com.financialapp.domain.model.Money
import com.financialapp.domain.model.MutualFundPortfolioSummary
import com.financialapp.domain.model.ReportingGranularity
import com.financialapp.domain.model.ReportingPeriod
import com.financialapp.domain.model.TransactionAnalyticsReport
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

object DashboardCalculator {
    fun summarize(
        period: DashboardPeriod,
        asOfDate: LocalDate,
        report: TransactionAnalyticsReport,
        accountBalances: List<AccountBalance>,
        portfolio: MutualFundPortfolioSummary,
        categoryNames: Map<Long, String>,
        priceMovers: List<com.financialapp.domain.model.CommodityPriceComparison>,
    ): DashboardSummary {
        require(report.buckets.size >= 2) { "Dashboard analytics require a current and previous bucket" }
        val previousBucket = report.buckets[report.buckets.lastIndex - 1]
        val currentBucket = report.buckets.last()
        val incomeChange = currentBucket.income - previousBucket.income
        val expenseChange = currentBucket.expense - previousBucket.expense
        val savingsRatePercent = if (currentBucket.income.minorUnits == 0L) null else {
            currentBucket.income.minorUnits.toBigDecimal()
                .subtract(currentBucket.expense.minorUnits.toBigDecimal())
                .multiply(BigDecimal(100))
                .divide(currentBucket.income.minorUnits.toBigDecimal(), 2, RoundingMode.HALF_UP)
        }
        val assetBalance = sumBalances(accountBalances.filter { it.accountType != AccountType.CREDIT_CARD })
        val liabilityBalance = sumBalances(accountBalances.filter { it.accountType == AccountType.CREDIT_CARD })
        val netWorth = assetBalance + portfolio.totalValue - liabilityBalance
        val topExpenseCategories = currentBucket.expenseByCategory.entries
            .sortedWith(compareByDescending<Map.Entry<Long, Money>> { it.value.minorUnits }.thenBy { it.key })
            .take(3)
            .map { (categoryId, amount) ->
                DashboardCategorySummary(
                    categoryId = categoryId,
                    categoryName = categoryNames[categoryId] ?: "Category #$categoryId",
                    amount = amount,
                )
            }
        return DashboardSummary(
            asOfDate = asOfDate,
            period = period,
            currentIncome = currentBucket.income,
            currentExpense = currentBucket.expense,
            previousIncome = previousBucket.income,
            previousExpense = previousBucket.expense,
            incomeChange = incomeChange,
            expenseChange = expenseChange,
            savingsRatePercent = savingsRatePercent,
            accountBalances = accountBalances.sortedWith(compareByDescending<AccountBalance> { it.amount.minorUnits }.thenBy { it.accountId }),
            assetBalance = assetBalance,
            liabilityBalance = liabilityBalance,
            portfolio = portfolio,
            netWorth = netWorth,
            topExpenseCategories = topExpenseCategories,
            priceMovers = priceMovers,
        )
    }

    fun reportingWindow(period: DashboardPeriod, asOfDate: LocalDate): Pair<ReportingPeriod, ReportingPeriod> {
        val current = when (period) {
            DashboardPeriod.MONTH -> ReportingPeriod.month(YearMonth.from(asOfDate))
            DashboardPeriod.YEAR -> ReportingPeriod.year(asOfDate.year)
        }
        val previous = when (period) {
            DashboardPeriod.MONTH -> ReportingPeriod.month(YearMonth.from(asOfDate).minusMonths(1))
            DashboardPeriod.YEAR -> ReportingPeriod.year(asOfDate.year - 1)
        }
        return previous to current
    }

    fun combinedReportingPeriod(period: DashboardPeriod, asOfDate: LocalDate): ReportingPeriod {
        val (previous, current) = reportingWindow(period, asOfDate)
        return ReportingPeriod(previous.startDate, current.endDate, when (period) {
            DashboardPeriod.MONTH -> ReportingGranularity.MONTH
            DashboardPeriod.YEAR -> ReportingGranularity.YEAR
        })
    }

    private fun sumBalances(balances: List<AccountBalance>): Money {
        if (balances.isEmpty()) return Money(0L, "INR")
        return balances.drop(1).fold(balances.first().amount) { total, balance -> total + balance.amount }
    }
}