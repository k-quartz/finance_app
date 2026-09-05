package com.financialapp.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.financialapp.domain.model.DashboardPeriod
import com.financialapp.domain.model.DashboardSummary
import com.financialapp.domain.model.Money
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

@Composable
fun DashboardRoute(
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    // Re-query on resume so data entered elsewhere (e.g. purchases, transactions) is reflected.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    DashboardScreen(
        state = state,
        onPeriodSelected = viewModel::selectPeriod,
        onRefresh = viewModel::refresh,
    )
}

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onPeriodSelected: (DashboardPeriod) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = Brush.verticalGradient(
        listOf(
            Color(0xFF08111F),
            Color(0xFF0E1728),
            Color(0xFF162232),
        ),
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background)
            .padding(16.dp),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                DashboardHeader(
                    period = state.period,
                    isLoading = state.isLoading,
                    errorMessage = state.errorMessage,
                    onPeriodSelected = onPeriodSelected,
                    onRefresh = onRefresh,
                )
            }
            state.summary?.let { summary ->
                item { NetWorthHero(summary = summary) }
                item { SnapshotGrid(summary = summary) }
                item { PeriodPerformanceCard(summary = summary) }
                item { TopExpenseCategoriesCard(summary = summary) }
                item { PortfolioHoldingsCard(summary = summary) }
                item { PriceMoversCard(summary = summary) }
            }
            if (state.summary == null && !state.isLoading && state.errorMessage == null) {
                item {
                    EmptyStateCard()
                }
            }
        }

        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun DashboardHeader(
    period: DashboardPeriod,
    isLoading: Boolean,
    errorMessage: String?,
    onPeriodSelected: (DashboardPeriod) -> Unit,
    onRefresh: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Financial Dashboard",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
        Text(
            text = "Net worth, cash flow, investments, and price movement in one view.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFB7C1D6),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = period == DashboardPeriod.MONTH,
                onClick = { onPeriodSelected(DashboardPeriod.MONTH) },
                label = { Text("Monthly") },
            )
            FilterChip(
                selected = period == DashboardPeriod.YEAR,
                onClick = { onPeriodSelected(DashboardPeriod.YEAR) },
                label = { Text("Yearly") },
            )
            Spacer(modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onRefresh, enabled = !isLoading) {
                Icon(imageVector = Icons.Outlined.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("Refresh")
            }
        }
        errorMessage?.let {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF5C1F22))) {
                Text(
                    text = it,
                    modifier = Modifier.padding(12.dp),
                    color = Color(0xFFFFD9D9),
                )
            }
        }
    }
}

@Composable
private fun NetWorthHero(summary: DashboardSummary) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10263D)),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = "Net worth", color = Color(0xFF9FADC4), style = MaterialTheme.typography.labelLarge)
            Text(text = formatMoney(summary.netWorth), color = Color.White, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text(
                text = "Assets ${formatMoney(summary.assetBalance)}  •  Debt ${formatMoney(summary.liabilityBalance)}  •  Portfolio ${formatMoney(summary.portfolio.totalValue)}",
                color = Color(0xFFC4D1E4),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SnapshotGrid(summary: DashboardSummary) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        MetricCard(
            modifier = Modifier.weight(1f),
            title = "Income",
            value = formatMoney(summary.currentIncome),
            accent = Color(0xFF1C8A5B),
        )
        MetricCard(
            modifier = Modifier.weight(1f),
            title = "Expense",
            value = formatMoney(summary.currentExpense),
            accent = Color(0xFFCA5D57),
        )
    }
}

@Composable
private fun PeriodPerformanceCard(summary: DashboardSummary) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF111A27)), shape = RoundedCornerShape(24.dp)) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = "Period performance", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            MetricLine("Income change", formatMoney(summary.incomeChange))
            MetricLine("Expense change", formatMoney(summary.expenseChange))
            MetricLine("Savings rate", summary.savingsRatePercent?.let { "${it.setScale(2)}%" } ?: "N/A")
        }
    }
}

@Composable
private fun TopExpenseCategoriesCard(summary: DashboardSummary) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF111A27)), shape = RoundedCornerShape(24.dp)) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = "Top spending categories", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (summary.topExpenseCategories.isEmpty()) {
                Text(text = "No expense activity in this period.", color = Color(0xFFB7C1D6))
            } else {
                summary.topExpenseCategories.forEach { category ->
                    MetricLine(category.categoryName, formatMoney(category.amount))
                }
            }
        }
    }
}

@Composable
private fun PortfolioHoldingsCard(summary: DashboardSummary) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF111A27)), shape = RoundedCornerShape(24.dp)) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = "Portfolio holdings", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (summary.portfolio.holdings.isEmpty()) {
                Text(text = "No active holdings yet.", color = Color(0xFFB7C1D6))
            } else {
                summary.portfolio.holdings.take(3).forEach { holding ->
                    MetricLine(holding.fundName, formatMoney(holding.value))
                }
            }
        }
    }
}

@Composable
private fun PriceMoversCard(summary: DashboardSummary) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF111A27)), shape = RoundedCornerShape(24.dp)) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = "Tracked price movement", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (summary.priceMovers.isEmpty()) {
                Text(text = "Record price history to see movement here.", color = Color(0xFFB7C1D6))
            } else {
                summary.priceMovers.forEach { comparison ->
                    val changeText = comparison.change?.let { formatMoney(it) } ?: "No prior price"
                    MetricLine(comparison.productName, changeText)
                }
            }
        }
    }
}

@Composable
private fun EmptyStateCard() {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF111A27)), shape = RoundedCornerShape(24.dp)) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "No data yet", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Create accounts, transactions, purchases, and holdings to populate the dashboard.",
                color = Color(0xFFB7C1D6),
            )
        }
    }
}

@Composable
private fun MetricCard(modifier: Modifier = Modifier, title: String, value: String, accent: Color) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111A27)),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = title, color = Color(0xFF9FADC4), style = MaterialTheme.typography.labelLarge)
            Text(text = value, color = accent, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun MetricLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, color = Color(0xFFB7C1D6), modifier = Modifier.weight(1f))
        Text(text = value, color = Color.White, textAlign = TextAlign.End)
    }
}

private fun formatMoney(amount: Money): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    formatter.currency = Currency.getInstance(amount.currencyCode)
    return formatter.format(BigDecimal.valueOf(amount.minorUnits, 2))
}