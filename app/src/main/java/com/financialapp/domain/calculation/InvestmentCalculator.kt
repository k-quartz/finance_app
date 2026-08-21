package com.financialapp.domain.calculation

import com.financialapp.domain.model.FundUnits
import com.financialapp.domain.model.InvestmentTransactionType
import com.financialapp.domain.model.MutualFundHolding
import com.financialapp.domain.model.MutualFundTransaction
import java.math.BigDecimal
import java.time.Instant

object InvestmentCalculator {
    fun reconcileHolding(
        current: MutualFundHolding?,
        transaction: MutualFundTransaction,
        updatedAt: Instant,
    ): MutualFundHolding {
        val currentUnits = current?.units ?: FundUnits(0L, transaction.units.scale)
        val scale = maxOf(currentUnits.scale, transaction.units.scale)
        val currentMinor = scaleUnits(currentUnits.minorUnits, currentUnits.scale, scale)
        val transactionMinor = scaleUnits(transaction.units.minorUnits, transaction.units.scale, scale)
        val nextMinor = when (transaction.type) {
            InvestmentTransactionType.PURCHASE -> Math.addExact(currentMinor, transactionMinor)
            InvestmentTransactionType.REDEMPTION -> Math.subtractExact(currentMinor, transactionMinor)
        }
        require(nextMinor >= 0L) { "Redemption exceeds current holding" }
        return MutualFundHolding(transaction.fundId, transaction.investmentAccountId, FundUnits(nextMinor, scale), updatedAt)
    }

    private fun scaleUnits(value: Long, fromScale: Int, toScale: Int): Long {
        if (fromScale == toScale) return value
        return Math.multiplyExact(value, BigDecimal.TEN.pow(toScale - fromScale).longValueExact())
    }
}