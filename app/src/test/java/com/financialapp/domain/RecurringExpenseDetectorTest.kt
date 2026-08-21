package com.financialapp.domain

import com.financialapp.domain.calculation.RecurringExpenseDetector
import com.financialapp.domain.model.FinancialTransaction
import com.financialapp.domain.model.Money
import com.financialapp.domain.model.RecurringExpenseCadence
import com.financialapp.domain.model.TransactionType
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringExpenseDetectorTest {
    private val createdAt = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun `detects normalized weekly expenses with small date and amount variation`() {
        val transactions = listOf(
            transaction(LocalDate.of(2026, 1, 2), 1_000, "  Gym   Club "),
            transaction(LocalDate.of(2026, 1, 9), 1_020, "gym club"),
            transaction(LocalDate.of(2026, 1, 17), 980, "GYM CLUB"),
        )

        val patterns = RecurringExpenseDetector.detect(transactions)

        assertEquals(1, patterns.size)
        assertEquals(RecurringExpenseCadence.WEEKLY, patterns.single().cadence)
        assertEquals("gym club", patterns.single().merchantOrPayee)
        assertTrue(patterns.single().confidencePercent >= 80)
        assertEquals(1_000L, patterns.single().averageAmount.minorUnits)
    }

    @Test
    fun `rejects one off expenses income transfers and irregular groups`() {
        val transactions = listOf(
            transaction(LocalDate.of(2026, 1, 1), 100, "One Off"),
            transaction(LocalDate.of(2026, 1, 8), 100, "One Off"),
            transaction(LocalDate.of(2026, 1, 20), 100, "One Off"),
            transaction(LocalDate.of(2026, 1, 1), 100, "Irregular", TransactionType.INCOME),
            transaction(LocalDate.of(2026, 1, 8), 100, "Irregular", TransactionType.TRANSFER),
        )

        assertTrue(RecurringExpenseDetector.detect(transactions).isEmpty())
    }

    @Test
    fun `detects month end billing across short months`() {
        val transactions = listOf(
            transaction(LocalDate.of(2026, 1, 31), 2_000, "Rent"),
            transaction(LocalDate.of(2026, 2, 28), 2_000, "rent"),
            transaction(LocalDate.of(2026, 3, 31), 2_000, " RENT "),
        )

        val pattern = RecurringExpenseDetector.detect(transactions).single()

        assertEquals(RecurringExpenseCadence.MONTHLY, pattern.cadence)
    }

    @Test
    fun `rejects amount outliers and invalid date ranges`() {
        val transactions = listOf(
            transaction(LocalDate.of(2026, 1, 2), 1_000, "Variable Service"),
            transaction(LocalDate.of(2026, 1, 9), 1_000, "Variable Service"),
            transaction(LocalDate.of(2026, 1, 16), 1_500, "Variable Service"),
        )

        assertTrue(RecurringExpenseDetector.detect(transactions).isEmpty())
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            RecurringExpenseDetector.detect(transactions, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1))
        }
    }

    private fun transaction(date: LocalDate, amount: Long, merchant: String, type: TransactionType = TransactionType.EXPENSE) = FinancialTransaction(
        accountId = 1L,
        type = type,
        amount = Money(amount, "INR"),
        transactionDate = date,
        createdAt = createdAt,
        merchantOrPayee = merchant,
    )
}