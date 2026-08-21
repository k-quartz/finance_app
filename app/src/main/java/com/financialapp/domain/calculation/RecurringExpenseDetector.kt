package com.financialapp.domain.calculation

import com.financialapp.domain.model.FinancialTransaction
import com.financialapp.domain.model.Money
import com.financialapp.domain.model.RecurringExpenseCadence
import com.financialapp.domain.model.RecurringExpensePattern
import com.financialapp.domain.model.TransactionType
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

object RecurringExpenseDetector {
    private const val MINIMUM_OCCURRENCES = 3
    private const val MAX_AMOUNT_VARIANCE_PERCENT = 10
    private const val MAX_WEEKLY_DATE_DRIFT_DAYS = 1
    private const val MAX_MONTHLY_DATE_DRIFT_DAYS = 3
    private const val MINIMUM_CONFIDENCE_PERCENT = 80

    fun detect(
        transactions: List<FinancialTransaction>,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null,
    ): List<RecurringExpensePattern> {
        require(startDate == null || endDate == null || !endDate.isBefore(startDate)) {
            "End date cannot precede start date"
        }
        return transactions.asSequence()
            .filter { it.type == TransactionType.EXPENSE }
            .filter { it.amount.minorUnits > 0L }
            .filter { startDate == null || !it.transactionDate.isBefore(startDate) }
            .filter { endDate == null || !it.transactionDate.isAfter(endDate) }
            .filter { !it.merchantOrPayee.isNullOrBlank() }
            .groupBy { transaction ->
                Triple(transaction.accountId, transaction.categoryId, normalizeMerchant(transaction.merchantOrPayee!!))
            }
            .mapNotNull { (key, grouped) -> detectGroup(key, grouped.sortedBy { it.transactionDate }) }
            .sortedWith(compareByDescending<RecurringExpensePattern> { it.confidencePercent }
                .thenBy { it.accountId }
                .thenBy { it.categoryId ?: Long.MIN_VALUE }
                .thenBy { it.merchantOrPayee })
            .toList()
    }

    private fun detectGroup(
        key: Triple<Long, Long?, String>,
        transactions: List<FinancialTransaction>,
    ): RecurringExpensePattern? {
        if (transactions.size < MINIMUM_OCCURRENCES) return null
        val candidates = RecurringExpenseCadence.entries.mapNotNull { cadence ->
            val dateScore = dateSimilarity(transactions, cadence) ?: return@mapNotNull null
            val amountScore = amountSimilarity(transactions)
            val confidence = (dateScore.multiply(BigDecimal.valueOf(60L))
                .add(amountScore.multiply(BigDecimal.valueOf(40L))))
                .setScale(0, RoundingMode.HALF_UP)
                .toInt()
            if (confidence < MINIMUM_CONFIDENCE_PERCENT) return@mapNotNull null
            RecurringExpensePattern(
                accountId = key.first,
                categoryId = key.second,
                merchantOrPayee = key.third,
                cadence = cadence,
                transactions = transactions,
                averageAmount = Money(averageAmount(transactions), transactions.first().amount.currencyCode),
                confidencePercent = confidence,
            )
        }
        return candidates.maxWithOrNull(compareBy { it.confidencePercent })
    }

    private fun dateSimilarity(transactions: List<FinancialTransaction>, cadence: RecurringExpenseCadence): BigDecimal? {
        val matches = transactions.zipWithNext().count { (previous, current) ->
            when (cadence) {
                RecurringExpenseCadence.WEEKLY ->
                    kotlin.math.abs(ChronoUnit.DAYS.between(previous.transactionDate, current.transactionDate).toInt() - 7) <= MAX_WEEKLY_DATE_DRIFT_DAYS
                RecurringExpenseCadence.MONTHLY ->
                    ChronoUnit.MONTHS.between(previous.transactionDate.withDayOfMonth(1), current.transactionDate.withDayOfMonth(1)) == 1L &&
                        kotlin.math.abs(previous.transactionDate.dayOfMonth - current.transactionDate.dayOfMonth) <= MAX_MONTHLY_DATE_DRIFT_DAYS
            }
        }
        if (matches != transactions.size - 1) return null
        return BigDecimal.ONE
    }

    private fun amountSimilarity(transactions: List<FinancialTransaction>): BigDecimal {
        val amounts = transactions.map { BigDecimal.valueOf(it.amount.minorUnits) }
        val minimum = amounts.minOrNull()!!
        val maximum = amounts.maxOrNull()!!
        val allowedVariance = maximum.multiply(BigDecimal.valueOf(MAX_AMOUNT_VARIANCE_PERCENT.toLong())).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)
        return if (maximum.subtract(minimum) <= allowedVariance) BigDecimal.ONE else BigDecimal.ZERO
    }

    private fun averageAmount(transactions: List<FinancialTransaction>): Long =
        transactions.asSequence()
            .map { BigDecimal.valueOf(it.amount.minorUnits) }
            .reduce(BigDecimal::add)
            .divide(BigDecimal.valueOf(transactions.size.toLong()), 0, RoundingMode.HALF_UP)
            .longValueExact()

    private fun normalizeMerchant(value: String): String = value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
}