package com.financialapp.ui.common

import com.financialapp.domain.model.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FormattingTest {

    @Test
    fun formatRupeesFromMinorUnits_alwaysUsesTwoDecimals() {
        assertEquals("42.00", formatRupeesFromMinorUnits(4200))
        assertEquals("100.00", formatRupeesFromMinorUnits(10000))
        assertEquals("99.50", formatRupeesFromMinorUnits(9950))
        assertEquals("1000.79", formatRupeesFromMinorUnits(100079))
    }

    @Test
    fun formatMoney_alwaysUsesTwoFractionDigits() {
        val formatted = formatMoney(Money(minorUnits = 4200, currencyCode = "INR"))
        assertTrue(formatted.endsWith("42.00"), "Expected two decimals in formatted money, got: $formatted")
    }
}
