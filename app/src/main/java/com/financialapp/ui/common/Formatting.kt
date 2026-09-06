package com.financialapp.ui.common

import com.financialapp.domain.model.Money
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Currency
import java.util.Locale

/** Formats an exact [Money] value as an INR currency string for display only. */
fun formatMoney(amount: Money): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    formatter.currency = Currency.getInstance(amount.currencyCode)
    formatter.minimumFractionDigits = 2
    formatter.maximumFractionDigits = 2
    return formatter.format(BigDecimal.valueOf(amount.minorUnits, 2))
}

/**
 * Parses a user-entered rupee amount (e.g. "1234.56") into exact INR minor units.
 * Returns null for blank, non-numeric, non-positive, or over-precise input so callers can
 * surface a validation error instead of silently rounding user-entered money.
 */
fun parseRupeesToMinorUnits(text: String, allowZero: Boolean = false): Long? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    val decimal = trimmed.toBigDecimalOrNull() ?: return null
    if (decimal.scale() > 2) return null
    if (decimal < BigDecimal.ZERO) return null
    if (decimal == BigDecimal.ZERO && !allowZero) return null
    return decimal.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()
}

/** Formats exact INR minor units as a rupee decimal string for edit fields. */
fun formatRupeesFromMinorUnits(minorUnits: Long): String =
    BigDecimal.valueOf(minorUnits, 2).setScale(2, RoundingMode.UNNECESSARY).toPlainString()

private fun String.toBigDecimalOrNull(): BigDecimal? = try {
    BigDecimal(this)
} catch (e: NumberFormatException) {
    null
}

private val isoDate: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

/** Parses a yyyy-MM-dd date field into a [LocalDate], or null if invalid. */
fun parseIsoDateOrNull(text: String): LocalDate? = try {
    LocalDate.parse(text.trim(), isoDate)
} catch (e: DateTimeParseException) {
    null
}

fun LocalDate.toIsoString(): String = format(isoDate)

fun fieldErrorMessage(message: String?, field: String): String? = message?.takeIf {
    when (field) {
        "name" -> it.contains("name", ignoreCase = true)
        "unit" -> it.contains("unit", ignoreCase = true)
        "account" -> it.contains("account", ignoreCase = true)
        "category" -> it.contains("category", ignoreCase = true)
        "product" -> it.contains("product", ignoreCase = true)
        "quantity" -> it.contains("quantity", ignoreCase = true)
        "amount" -> it.contains("amount", ignoreCase = true)
        "price" -> it.contains("price", ignoreCase = true)
        "date" -> it.contains("date", ignoreCase = true)
        "fund" -> it.contains("fund", ignoreCase = true)
        "code" -> it.contains("code", ignoreCase = true)
        "units" -> it.contains("units", ignoreCase = true)
        else -> false
    }
}
