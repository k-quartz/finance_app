package com.financialapp.ui.dashboard

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.financialapp.domain.model.AccountBalance
import com.financialapp.domain.model.AccountType
import com.financialapp.domain.model.CommodityPriceComparison
import com.financialapp.domain.model.CommodityPriceDirection
import com.financialapp.domain.model.CommodityPricePoint
import com.financialapp.domain.model.DashboardCategorySummary
import com.financialapp.domain.model.DashboardPeriod
import com.financialapp.domain.model.DashboardSummary
import com.financialapp.domain.model.FundUnits
import com.financialapp.domain.model.Money
import com.financialapp.domain.model.MutualFundHoldingValue
import com.financialapp.domain.model.MutualFundPortfolioSummary
import com.financialapp.domain.model.ReportingGranularity
import com.financialapp.domain.model.ReportingPeriod
import com.financialapp.domain.model.TransactionAnalyticsReport
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test

class DashboardScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun periodSwitchUpdatesDashboardContent() {
        val monthSummary = summary("Monthly view", 12_000L, 3_500L, 29_000L)
        val yearSummary = summary("Yearly view", 120_000L, 44_000L, 310_000L)
        val state = mutableStateOf(DashboardUiState(period = DashboardPeriod.MONTH, isLoading = false, summary = monthSummary))

        composeRule.setContent {
            DashboardScreen(
                state = state.value,
                onPeriodSelected = { period -> state.value = state.value.copy(period = period, summary = if (period == DashboardPeriod.MONTH) monthSummary else yearSummary) },
                onRefresh = {},
            )
        }

        composeRule.onNodeWithText("Net worth").assertIsDisplayed()
        composeRule.onNodeWithText("Monthly view").assertIsDisplayed()
        composeRule.onNodeWithText("Yearly").performClick()
        composeRule.onNodeWithText("Yearly view").assertIsDisplayed()
    }

    private fun summary(label: String, income: Long, expense: Long, netWorth: Long): DashboardSummary {
        val asOfDate = LocalDate.of(2026, 2, 15)
        return DashboardSummary(
            asOfDate = asOfDate,
            period = DashboardPeriod.MONTH,
            currentIncome = Money(income, "INR"),
            currentExpense = Money(expense, "INR"),
            previousIncome = Money(10_000L, "INR"),
            previousExpense = Money(2_000L, "INR"),
            incomeChange = Money(income - 10_000L, "INR"),
            expenseChange = Money(expense - 2_000L, "INR"),
            savingsRatePercent = BigDecimal("65.00"),
            accountBalances = listOf(AccountBalance(1L, Money(10_000L, "INR"), AccountType.BANK)),
            assetBalance = Money(10_000L, "INR"),
            liabilityBalance = Money(0L, "INR"),
            portfolio = MutualFundPortfolioSummary(
                asOfDate = asOfDate,
                totalValue = Money(1_000L, "INR"),
                holdings = listOf(
                    MutualFundHoldingValue(1L, label, 2L, FundUnits(1_000L, 3), Money(1_000L, "INR"), Money(1_000L, "INR")),
                ),
            ),
            netWorth = Money(netWorth, "INR"),
            topExpenseCategories = listOf(DashboardCategorySummary(1L, "Food", Money(expense, "INR"))),
            priceMovers = listOf(
                CommodityPriceComparison(
                    productId = 1L,
                    productName = "Rice",
                    asOfDate = asOfDate,
                    latestPoint = CommodityPricePoint(1L, 1L, asOfDate, Money(1_200L, "INR"), createdAt = Instant.EPOCH),
                    previousPoint = CommodityPricePoint(2L, 1L, asOfDate.minusDays(1), Money(1_000L, "INR"), createdAt = Instant.EPOCH),
                    change = Money(200L, "INR"),
                    changePercent = BigDecimal("20.0000"),
                    direction = CommodityPriceDirection.UP,
                ),
            ),
        )
    }
}