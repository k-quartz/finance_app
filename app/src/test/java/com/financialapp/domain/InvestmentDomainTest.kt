package com.financialapp.domain

import com.financialapp.domain.calculation.InvestmentCalculator
import com.financialapp.domain.model.FundUnits
import com.financialapp.domain.model.InvestmentTransactionType
import com.financialapp.domain.model.Money
import com.financialapp.domain.model.MutualFundHolding
import com.financialapp.domain.model.MutualFundTransaction
import com.financialapp.domain.model.calculateInvestmentAmount
import com.financialapp.domain.model.calculateMutualFundReturn
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class InvestmentDomainTest {
    private val purchase = MutualFundTransaction(
        fundId = 1L,
        investmentAccountId = 2L,
        type = InvestmentTransactionType.PURCHASE,
        units = FundUnits(1_500L, 3),
        nav = Money(1_234L, "INR"),
        totalAmount = Money(1_851L, "INR"),
        transactionDate = LocalDate.of(2026, 1, 1),
        createdAt = Instant.ofEpochMilli(1_000L),
    )

    @Test
    fun `investment amount uses exact decimal units and half up rounding`() {
        assertEquals(Money(1_851L, "INR"), calculateInvestmentAmount(Money(1_234L, "INR"), FundUnits(1_500L, 3)))
        assertEquals(Money(1_853L, "INR"), calculateInvestmentAmount(Money(1_235L, "INR"), FundUnits(1_5L, 1)))
    }

    @Test
    fun `holding reconciliation normalizes scales and rejects oversell`() {
        val holding = InvestmentCalculator.reconcileHolding(null, purchase, Instant.ofEpochMilli(1_000L))
        val redemption = purchase.copy(
            type = InvestmentTransactionType.REDEMPTION,
            units = FundUnits(500L, 3),
            nav = Money(1_200L, "INR"),
            totalAmount = Money(600L, "INR"),
        )
        assertEquals(FundUnits(1_000L, 3), InvestmentCalculator.reconcileHolding(holding, redemption, Instant.ofEpochMilli(2_000L)).units)
        assertThrows(IllegalArgumentException::class.java) {
            InvestmentCalculator.reconcileHolding(holding, redemption.copy(units = FundUnits(2_000L, 3), totalAmount = Money(2_400L, "INR")), Instant.EPOCH)
        }
    }

    @Test
    fun `return is proceeds plus current value minus invested amount`() {
        val redemption = purchase.copy(
            type = InvestmentTransactionType.REDEMPTION,
            units = FundUnits(500L, 3),
            nav = Money(1_200L, "INR"),
            totalAmount = Money(600L, "INR"),
        )
        val result = calculateMutualFundReturn(listOf(purchase), listOf(redemption), Money(1_950L, "INR"))
        assertEquals(Money(1_851L, "INR"), result.invested)
        assertEquals(Money(600L, "INR"), result.redeemed)
        assertEquals(Money(699L, "INR"), result.netGain)
        assertEquals("37.7634", result.returnPercent!!.toPlainString())
    }
}