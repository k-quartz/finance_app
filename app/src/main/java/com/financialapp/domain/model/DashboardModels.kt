package com.financialapp.domain.model

import java.math.BigDecimal
import java.time.LocalDate

enum class DashboardPeriod {
    MONTH,
    YEAR,
}

data class DashboardCategorySummary(
    val categoryId: Long,
    val categoryName: String,
    val amount: Money,
)

data class DashboardSummary(
    val asOfDate: LocalDate,
    val period: DashboardPeriod,
    val currentIncome: Money,
    val currentExpense: Money,
    val previousIncome: Money,
    val previousExpense: Money,
    val incomeChange: Money,
    val expenseChange: Money,
    val savingsRatePercent: BigDecimal?,
    val accountBalances: List<AccountBalance>,
    val assetBalance: Money,
    val liabilityBalance: Money,
    val portfolio: MutualFundPortfolioSummary,
    val netWorth: Money,
    val topExpenseCategories: List<DashboardCategorySummary>,
    val priceMovers: List<CommodityPriceComparison>,
)