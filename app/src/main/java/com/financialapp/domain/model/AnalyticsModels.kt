package com.financialapp.domain.model

import java.time.LocalDate
import java.time.YearMonth

enum class ReportingGranularity { MONTH, YEAR }

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