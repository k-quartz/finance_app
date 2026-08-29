package com.financialapp.domain.model

import java.time.LocalDate
import java.time.YearMonth

enum class ReportingGranularity { MONTH, QUARTER, YEAR }

data class ReportingPeriod(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val granularity: ReportingGranularity,
) {
    init {
        require(!endDate.isBefore(startDate)) { "End date cannot precede start date" }
    }

    companion object {
        fun month(month: YearMonth): ReportingPeriod = ReportingPeriod(
            month.atDay(1),
            month.atEndOfMonth(),
            ReportingGranularity.MONTH,
        )

        fun quarter(year: Int, quarter: Int): ReportingPeriod {
            require(quarter in 1..4) { "Quarter must be between 1 and 4" }
            val startMonth = (quarter - 1) * 3 + 1
            val start = LocalDate.of(year, startMonth, 1)
            return ReportingPeriod(start, start.plusMonths(2).withDayOfMonth(start.plusMonths(2).lengthOfMonth()), ReportingGranularity.QUARTER)
        }

        fun year(year: Int): ReportingPeriod = ReportingPeriod(
            LocalDate.of(year, 1, 1),
            LocalDate.of(year, 12, 31),
            ReportingGranularity.YEAR,
        )
    }
}

data class PeriodBucket(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val income: Money,
    val expense: Money,
    val expenseByCategory: Map<Long, Money>,
)

data class CategoryConsumptionComparison(
    val categoryId: Long,
    val previousExpense: Money,
    val currentExpense: Money,
    val change: Money,
)

data class TransactionAnalyticsReport(
    val period: ReportingPeriod,
    val buckets: List<PeriodBucket>,
    val categoryComparisons: List<CategoryConsumptionComparison>,
)