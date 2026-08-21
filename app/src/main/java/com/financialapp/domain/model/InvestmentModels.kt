package com.financialapp.domain.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate

data class MutualFund(
    val id: Long = 0L,
    val name: String,
    val code: String,
    val currencyCode: String = "INR",
    val isActive: Boolean = true,
    val isDeleted: Boolean = false,
)

data class FundUnits(
    val minorUnits: Long,
    val scale: Int = 6,
) {
    init {
        require(minorUnits >= 0) { "Fund units cannot be negative" }
        require(scale in 0..9) { "Fund unit scale must be between 0 and 9" }
    }

    fun asDecimal(): BigDecimal = BigDecimal.valueOf(minorUnits, scale)
}

enum class InvestmentTransactionType { PURCHASE, REDEMPTION }

data class MutualFundTransaction(
    val id: Long = 0L,
    val fundId: Long,
    val investmentAccountId: Long,
    val cashAccountId: Long? = null,
    val type: InvestmentTransactionType,
    val units: FundUnits,
    val nav: Money,
    val totalAmount: Money,
    val transactionDate: LocalDate,
    val linkedCashTransactionId: Long? = null,
    val notes: String? = null,
    val createdAt: Instant,
)

data class MutualFundHolding(
    val fundId: Long,
    val investmentAccountId: Long,
    val units: FundUnits,
    val updatedAt: Instant,
)

data class MutualFundValuation(
    val holding: MutualFundHolding,
    val nav: Money,
    val value: Money,
    val asOfDate: LocalDate,
)

data class MutualFundHoldingValue(
    val fundId: Long,
    val fundName: String,
    val investmentAccountId: Long,
    val units: FundUnits,
    val nav: Money,
    val value: Money,
)

data class MutualFundPortfolioSummary(
    val asOfDate: LocalDate,
    val totalValue: Money,
    val holdings: List<MutualFundHoldingValue>,
)

data class MutualFundReturn(
    val invested: Money,
    val redeemed: Money,
    val currentValue: Money,
    val netGain: Money,
    val returnPercent: BigDecimal?,
)

fun calculateInvestmentAmount(nav: Money, units: FundUnits): Money {
    require(nav.minorUnits > 0) { "NAV must be positive" }
    return Money(
        nav.minorUnits.toBigDecimal()
            .multiply(units.asDecimal())
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact(),
        nav.currencyCode,
    )
}

fun calculateMutualFundReturn(
    purchases: List<MutualFundTransaction>,
    redemptions: List<MutualFundTransaction>,
    currentValue: Money,
): MutualFundReturn {
    val investedMinor = purchases.fold(0L) { total, transaction -> Math.addExact(total, transaction.totalAmount.minorUnits) }
    val redeemedMinor = redemptions.fold(0L) { total, transaction -> Math.addExact(total, transaction.totalAmount.minorUnits) }
    val netGain = Math.addExact(Math.addExact(redeemedMinor, currentValue.minorUnits), Math.negateExact(investedMinor))
    val returnPercent = if (investedMinor == 0L) null else
        netGain.toBigDecimal().multiply(BigDecimal(100)).divide(investedMinor.toBigDecimal(), 4, RoundingMode.HALF_UP)
    return MutualFundReturn(
        invested = Money(investedMinor, currentValue.currencyCode),
        redeemed = Money(redeemedMinor, currentValue.currencyCode),
        currentValue = currentValue,
        netGain = Money(netGain, currentValue.currencyCode),
        returnPercent = returnPercent,
    )
}