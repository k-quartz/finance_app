package com.financialapp.performance

import com.financialapp.domain.calculation.TransactionAnalytics
import com.financialapp.domain.model.FinancialTransaction
import com.financialapp.domain.model.Money
import com.financialapp.domain.model.ReportingPeriod
import com.financialapp.domain.model.TransactionType
import java.time.Instant
import java.time.LocalDate
import kotlin.system.measureTimeMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceBaselineTest {
    @Test
    fun `large transaction analytics stays within baseline`() {
        val transactions = (0 until 50_000).map { index ->
            FinancialTransaction(
                accountId = (index % 8) + 1L,
                type = if (index % 5 == 0) TransactionType.INCOME else TransactionType.EXPENSE,
                amount = Money((index % 500) + 1L, "INR"),
                categoryId = if (index % 5 == 0) null else (index % 20) + 1L,
                transactionDate = LocalDate.of(2022 + (index % 4), (index % 12) + 1, (index % 28) + 1),
                createdAt = Instant.ofEpochMilli(index.toLong()),
            )
        }

        var reportExpense = 0L
        val elapsedMs = measureTimeMillis {
            val report = TransactionAnalytics.analyze(transactions, ReportingPeriod.year(2024))
            reportExpense = report.buckets.single().expense.minorUnits
        }

        assertEquals(transactions.filter { it.transactionDate.year == 2024 && it.type == TransactionType.EXPENSE }.sumOf { it.amount.minorUnits }, reportExpense)
        assertTrue("50,000 transaction analytics exceeded 5,000 ms: ${elapsedMs} ms", elapsedMs < 5_000L)
        println("PERFORMANCE_BASELINE transaction_analytics_50k_ms=$elapsedMs")
    }
}
