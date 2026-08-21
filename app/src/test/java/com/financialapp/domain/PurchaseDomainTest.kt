package com.financialapp.domain

import com.financialapp.domain.model.Money
import com.financialapp.domain.model.Quantity
import com.financialapp.domain.model.calculatePurchaseTotal
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.test.assertFailsWith

class PurchaseDomainTest {
    @Test
    fun `decimal quantity preserves exact total`() {
        val total = calculatePurchaseTotal(Money(600, "INR"), Quantity(2500, 3, "kg"))

        assertEquals(1_500L, total.minorUnits)
    }

    @Test
    fun `fractional minor-unit total rounds half up`() {
        val total = calculatePurchaseTotal(Money(1, "INR"), Quantity(5, 1, "each"))

        assertEquals(1L, total.minorUnits)
    }

    @Test
    fun `quantity rejects zero and negative values`() {
        assertFailsWith<IllegalArgumentException> { Quantity(0, 0, "kg") }
        assertFailsWith<IllegalArgumentException> { Quantity(-1, 0, "kg") }
    }

    @Test
    fun `total overflow is rejected`() {
        assertFailsWith<ArithmeticException> {
            calculatePurchaseTotal(Money(Long.MAX_VALUE, "INR"), Quantity(2, 0, "each"))
        }
    }
}