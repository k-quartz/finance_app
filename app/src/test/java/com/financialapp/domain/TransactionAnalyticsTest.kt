package com.financialapp.domain

import com.financialapp.domain.calculation.TransactionAnalytics
import com.financialapp.domain.model.FinancialTransaction
import com.financialapp.domain.model.Money
import com.financialapp.domain.model.ReportingGranularity
import com.financialapp.domain.model.ReportingPeriod
import com.financialapp.domain.model.TransactionType
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionAnalyticsTest {
    private val createdAt = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun `monthly buckets include boundaries empty periods and exclude transfers`() {
        val transactions = listOf(
            transaction(LocalDate.of(2026, 1, 1), TransactionType.EXPENSE, 100, 1L),
            transaction(LocalDate.of(2026, 1, 31), TransactionType.INCOME, 500, null),
            transaction(LocalDate.of(2026, 2, 1), TransactionType.EXPENSE, 250, 1L),
            transaction(LocalDate.of(2026, 2, 15), TransactionType.TRANSFER, 9_999, null),
        )

        val report = TransactionAnalytics.analyze(
            transactions,
            ReportingPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), ReportingGranularity.MONTH),
        )

        assertEquals(3, report.buckets.size)
        assertEquals(100L, report.buckets[0].expense.minorUnits)
        assertEquals(500L, report.buckets[0].income.minorUnits)
        assertEquals(250L, report.buckets[1].expense.minorUnits)
        assertEquals(0L, report.buckets[2].expense.minorUnits)
        assertEquals(-250L, report.categoryComparisons.single { it.categoryId == 1L }.change.minorUnits)
    }

    @Test
    fun `year buckets preserve exact totals for large deterministic history`() {
        val transactions = (0 until 20_000).map { index ->
            transaction(LocalDate.of(2020, 1 + index % 12, 1 + index % 28), TransactionType.EXPENSE, 3, 7L)
        }
        val report = TransactionAnalytics.analyze(transactions, ReportingPeriod.year(2020))

        assertEquals(1, report.buckets.size)
        assertEquals(60_000L, report.buckets.single().expense.minorUnits)
        assertTrue(report.buckets.single().expenseByCategory.containsKey(7L))
    }

    @Test
    fun `custom ranges clamp first and last bucket boundaries`() {
        val transactions = listOf(
            transaction(LocalDate.of(2026, 1, 1), TransactionType.EXPENSE, 900, 1L),
            transaction(LocalDate.of(2026, 1, 15), TransactionType.EXPENSE, 100, 1L),
            transaction(LocalDate.of(2026, 2, 10), TransactionType.EXPENSE, 200, 1L),
            transaction(LocalDate.of(2026, 2, 28), TransactionType.EXPENSE, 800, 1L),
        )

        val report = TransactionAnalytics.analyze(
            transactions,
            ReportingPeriod(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 2, 20), ReportingGranularity.MONTH),
        )

        assertEquals(LocalDate.of(2026, 1, 10), report.buckets.first().startDate)
        assertEquals(LocalDate.of(2026, 2, 20), report.buckets.last().endDate)
        assertEquals(300L, report.buckets.sumOf { it.expense.minorUnits })
    }

    @Test
    fun `quarter buckets include quarter boundaries and leap day`() {
        val transactions = listOf(
            transaction(LocalDate.of(2026, 1, 1), TransactionType.EXPENSE, 100, 1L),
            transaction(LocalDate.of(2026, 3, 31), TransactionType.INCOME, 500, null),
            transaction(LocalDate.of(2026, 4, 1), TransactionType.EXPENSE, 250, 1L),
            transaction(LocalDate.of(2026, 6, 30), TransactionType.EXPENSE, 750, 1L),
            transaction(LocalDate.of(2026, 7, 1), TransactionType.TRANSFER, 9_999, null),
        )

        val report = TransactionAnalytics.analyze(
            transactions,
            ReportingPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 30), ReportingGranularity.QUARTER),
        )

        assertEquals(3, report.buckets.size)
        assertEquals(LocalDate.of(2026, 1, 1), report.buckets.first().startDate)
        assertEquals(LocalDate.of(2026, 3, 31), report.buckets.first().endDate)
        assertEquals(100L, report.buckets[0].expense.minorUnits)
        assertEquals(1_000L, report.buckets[1].expense.minorUnits)
        assertEquals(0L, report.buckets[2].expense.minorUnits)
    }

    private fun transaction(date: LocalDate, type: TransactionType, amount: Long, categoryId: Long?) = FinancialTransaction(
        accountId = 1L,
        type = type,
        amount = Money(amount, "INR"),
        categoryId = categoryId,
        transactionDate = date,
        createdAt = createdAt,
    )
}