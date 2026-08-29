package com.financialapp.domain.calculation

import com.financialapp.domain.model.CategoryConsumptionComparison
import com.financialapp.domain.model.FinancialTransaction
import com.financialapp.domain.model.Money
import com.financialapp.domain.model.PeriodBucket
import com.financialapp.domain.model.ReportingGranularity
import com.financialapp.domain.model.ReportingPeriod
import com.financialapp.domain.model.TransactionAnalyticsReport
import com.financialapp.domain.model.TransactionType
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

object TransactionAnalytics {
    fun analyze(transactions: List<FinancialTransaction>, period: ReportingPeriod): TransactionAnalyticsReport {
        val ranges = bucketRanges(period)
        val totals = ranges.map { BucketTotals() }
        transactions.asSequence()
            .filter { it.transactionDate in period.startDate..period.endDate }
            .forEach { transaction ->
                val bucketIndex = bucketIndex(transaction.transactionDate, period)
                if (bucketIndex in totals.indices) totals[bucketIndex].add(transaction)
            }
        val buckets = ranges.mapIndexed { index, (start, end) ->
            totals[index].toBucket(start, end)
        }
        val comparisons = if (buckets.size < 2) emptyList() else {
            val previous = buckets[buckets.lastIndex - 1]
            val current = buckets.last()
            (previous.expenseByCategory.keys + current.expenseByCategory.keys).sorted().map { categoryId ->
                val previousExpense = previous.expenseByCategory[categoryId] ?: Money(0L, "INR")
                val currentExpense = current.expenseByCategory[categoryId] ?: Money(0L, "INR")
                CategoryConsumptionComparison(categoryId, previousExpense, currentExpense, currentExpense - previousExpense)
            }
        }
        return TransactionAnalyticsReport(period, buckets, comparisons)
    }

    private fun bucketRanges(period: ReportingPeriod): List<Pair<LocalDate, LocalDate>> {
        val ranges = mutableListOf<Pair<LocalDate, LocalDate>>()
        var current = when (period.granularity) {
            ReportingGranularity.MONTH -> period.startDate.withDayOfMonth(1)
            ReportingGranularity.QUARTER -> period.startDate.withDayOfMonth(1).minusMonths((period.startDate.monthValue - 1) % 3L)
            ReportingGranularity.YEAR -> period.startDate.withDayOfYear(1)
        }
        while (!current.isAfter(period.endDate)) {
            val end = when (period.granularity) {
                ReportingGranularity.MONTH -> current.withDayOfMonth(current.lengthOfMonth())
                ReportingGranularity.QUARTER -> current.plusMonths(2).withDayOfMonth(current.plusMonths(2).lengthOfMonth())
                ReportingGranularity.YEAR -> current.withDayOfYear(current.lengthOfYear())
            }
            ranges += maxOf(current, period.startDate) to minOf(end, period.endDate)
            current = when (period.granularity) {
                ReportingGranularity.MONTH -> current.plusMonths(1)
                ReportingGranularity.QUARTER -> current.plusMonths(3)
                ReportingGranularity.YEAR -> current.plusYears(1)
            }
        }
        return ranges
    }

    private fun bucketIndex(date: LocalDate, period: ReportingPeriod): Int = when (period.granularity) {
        ReportingGranularity.MONTH -> ChronoUnit.MONTHS.between(
            period.startDate.withDayOfMonth(1),
            YearMonth.from(date).atDay(1),
        ).toInt()
        ReportingGranularity.QUARTER -> ChronoUnit.MONTHS.between(
            period.startDate.withDayOfMonth(1).minusMonths((period.startDate.monthValue - 1) % 3L),
            YearMonth.from(date).atDay(1),
        ).toInt() / 3
        ReportingGranularity.YEAR -> date.year - period.startDate.year
    }

    private class BucketTotals {
        var income = 0L
        var expense = 0L
        val categoryTotals = linkedMapOf<Long, Long>()

        fun add(transaction: FinancialTransaction) {
            when (transaction.type) {
                TransactionType.INCOME -> income = Math.addExact(income, transaction.amount.minorUnits)
                TransactionType.EXPENSE -> {
                    expense = Math.addExact(expense, transaction.amount.minorUnits)
                    transaction.categoryId?.let { categoryId ->
                        categoryTotals[categoryId] = Math.addExact(categoryTotals[categoryId] ?: 0L, transaction.amount.minorUnits)
                    }
                }
                TransactionType.TRANSFER -> Unit
            }
        }

        fun toBucket(start: LocalDate, end: LocalDate) = PeriodBucket(
            start,
            end,
            Money(income, "INR"),
            Money(expense, "INR"),
            categoryTotals.mapValues { Money(it.value, "INR") },
        )
    }
}