package com.financialapp.ui.workflows

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.financialapp.domain.model.Account
import com.financialapp.domain.model.Category
import com.financialapp.domain.model.CommodityPricePoint
import com.financialapp.domain.model.FinancialTransaction
import com.financialapp.domain.model.MutualFund
import com.financialapp.domain.model.MutualFundTransaction
import com.financialapp.domain.model.Product
import com.financialapp.domain.model.Purchase
import com.financialapp.domain.model.ReportingGranularity
import java.math.BigDecimal

private val sections = listOf("Accounts", "Categories", "Transactions", "Purchases", "Investments", "Prices", "Analytics")

@Composable
fun WorkflowsRoute(viewModel: WorkflowsViewModel = hiltViewModel()) {
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        var selected by rememberSaveable { mutableStateOf(sections.first()) }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Manage finances", style = MaterialTheme.typography.headlineMedium)
                OutlinedButton(onClick = viewModel::refresh, enabled = !state.isLoading) { Text("Refresh") }
            }
            sections.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { section ->
                        FilterChip(selected == section, { selected = section }, label = { Text(section) })
                    }
                }
            }
            when (selected) {
                "Accounts" -> AccountPanel(viewModel, state.accounts)
                "Categories" -> CategoryPanel(viewModel, state.categories)
                "Transactions" -> TransactionPanel(viewModel, state.transactions)
                "Purchases" -> PurchasePanel(viewModel, state.purchases)
                "Investments" -> InvestmentPanel(viewModel, state.funds, state.investmentTransactions, state.navHistory)
                "Prices" -> PricePanel(viewModel, state.products, state.priceHistory)
                "Analytics" -> AnalyticsPanel(viewModel, state.analysis)
            }
            if (state.isLoading) CircularProgressIndicator()
            if (!state.isLoading && state.accounts.isEmpty() && state.categories.isEmpty() && state.products.isEmpty() && state.transactions.isEmpty()) Text("No records yet. Create one above to begin.")
            state.message?.let { Text(it, color = if (state.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
        }
}

@Composable private fun AccountPanel(vm: WorkflowsViewModel, accounts: List<Account>) {
        Form("New account", listOf("Name", "Type: BANK", "Opening balance: 0.00"), listOf("name", "type", "opening")) { v -> vm.createAccount(v[0], v[1], v[2]) }
        ListTitle("Accounts", accounts.isEmpty())
        accounts.forEach { account ->
            Text("${account.id}: ${account.name} (${account.type}) ${if (account.isActive) "Active" else "Inactive"}")
            ActionRow({ vm.setAccountActive(account.id, !account.isActive) }, if (account.isActive) "Deactivate" else "Activate", { vm.deleteAccount(account.id) }, "Delete")
        }
}

@Composable private fun CategoryPanel(vm: WorkflowsViewModel, categories: List<Category>) {
        Form("New category or subcategory", listOf("Name", "Type: EXPENSE", "Parent id (optional)"), listOf("name", "type", "parent")) { v -> vm.createCategory(v[0], v[1], v[2]) }
        ListTitle("Categories", categories.isEmpty())
        categories.forEach { category ->
            Text("${category.id}: ${category.name} (${category.type})${category.parentId?.let { " parent $it" } ?: ""} ${if (category.isActive) "Active" else "Inactive"}")
            ActionRow({ vm.setCategoryActive(category.id, !category.isActive) }, if (category.isActive) "Deactivate" else "Activate", { vm.deleteCategory(category.id) }, "Delete")
        }
}

@Composable private fun TransactionPanel(vm: WorkflowsViewModel, transactions: List<FinancialTransaction>) {
        Form("New income or expense", listOf("Account id", "Type: EXPENSE", "Amount", "Category id (optional)", "Date: 2026-08-22", "Merchant (optional)"), listOf("account", "type", "amount", "category", "date", "merchant")) { v -> vm.createTransaction(v[0], v[1], v[2], v[3], v[4], v[5]) }
        Form("New transfer", listOf("Source account id", "Destination account id", "Amount", "Date: 2026-08-22"), listOf("source", "destination", "amount", "date")) { v -> vm.createTransfer(v[0], v[1], v[2], v[3]) }
        Form("Correct income or expense", listOf("Original transaction id", "Account id", "Type: EXPENSE", "Amount", "Category id (optional)", "Date: 2026-08-22", "Merchant (optional)"), listOf("original", "account", "type", "amount", "category", "date", "merchant")) { v -> vm.correctTransaction(v[0], v[1], v[2], v[3], v[4], v[5], v[6]) }
        ListTitle("Transactions", transactions.isEmpty())
        transactions.forEach { transaction -> Text("${transaction.id}: ${transaction.transactionDate} ${transaction.type} ${formatMinor(transaction.amount.minorUnits)} account ${transaction.accountId}") }
}

@Composable private fun PurchasePanel(vm: WorkflowsViewModel, purchases: List<Purchase>) {
        Form("New purchase", listOf("Product id", "Quantity", "Unit: item", "Unit price", "Date: 2026-08-22", "Category id (optional)", "Merchant (optional)"), listOf("product", "quantity", "unit", "price", "date", "category", "merchant")) { v -> vm.createPurchase(v[0], v[1], v[2], v[3], v[4], v[5], v[6]) }
        ListTitle("Purchase history", purchases.isEmpty())
        purchases.forEach { purchase ->
            Text("${purchase.id}: ${purchase.purchaseDate}: product ${purchase.productId}, ${purchase.quantity.asDecimal()} ${purchase.quantity.unit}, ${formatMinor(purchase.totalAmount.minorUnits)}")
            OutlinedButton(onClick = { vm.deletePurchase(purchase.id) }) { Text("Delete purchase") }
        }
}

@Composable private fun InvestmentPanel(vm: WorkflowsViewModel, funds: List<MutualFund>, transactions: List<MutualFundTransaction>, navs: List<com.financialapp.data.repository.FundNavEntry>) {
        Form("New mutual fund", listOf("Fund name", "Fund code"), listOf("name", "code")) { v -> vm.createFund(v[0], v[1]) }
        Form("Investment purchase or redemption", listOf("Fund id", "Investment account id", "Type: PURCHASE", "Units", "NAV", "Total amount", "Date: 2026-08-22"), listOf("fund", "account", "type", "units", "nav", "total", "date")) { v -> vm.recordInvestment(v[0], v[1], v[2], v[3], v[4], v[5], v[6]) }
        Form("NAV history", listOf("Fund id", "NAV", "Date: 2026-08-22"), listOf("fund", "nav", "date")) { v -> vm.recordNav(v[0], v[1], v[2]) }
        ListTitle("Funds", funds.isEmpty())
        funds.forEach { Text("${it.id}: ${it.name} (${it.code})") }
        ListTitle("Investment history", transactions.isEmpty())
        transactions.forEach { Text("${it.transactionDate}: fund ${it.fundId} ${it.type} ${it.units.asDecimal()} units") }
        ListTitle("NAV history", navs.isEmpty())
        navs.forEach { Text("${it.asOfDate}: fund ${it.fundId} ${formatMinor(it.nav.minorUnits)}") }
}

@Composable private fun PricePanel(vm: WorkflowsViewModel, products: List<Product>, prices: List<CommodityPricePoint>) {
        Form("New product", listOf("Name", "Unit: item"), listOf("name", "unit")) { v -> vm.createProduct(v[0], v[1]) }
        Form("Historical product price", listOf("Product id", "Price", "Date: 2026-08-22"), listOf("product", "price", "date")) { v -> vm.recordPrice(v[0], v[1], v[2]) }
        ListTitle("Products", products.isEmpty())
        products.forEach { product ->
            Text("${product.id}: ${product.name} (${product.defaultUnit}) ${if (product.isActive) "Active" else "Inactive"}")
            ActionRow({ vm.setProductActive(product.id, !product.isActive) }, if (product.isActive) "Deactivate" else "Activate", { vm.deleteProduct(product.id) }, "Delete")
        }
        ListTitle("Price history", prices.isEmpty())
        prices.forEach { Text("${it.asOfDate}: product ${it.productId} ${formatMinor(it.price.minorUnits)}") }
}

@Composable private fun AnalyticsPanel(vm: WorkflowsViewModel, result: String?) {
        var granularity by rememberSaveable { mutableStateOf(ReportingGranularity.MONTH.name) }
        Text("Reporting granularity", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReportingGranularity.entries.forEach { option ->
                FilterChip(
                    selected = granularity == option.name,
                    onClick = { granularity = option.name },
                    label = { Text(option.name.lowercase().replaceFirstChar { it.uppercase() }) },
                )
            }
        }
        Form("Period and recurring analysis", listOf("Start date: 2026-08-01", "End date: 2026-08-22", "Account id (optional)"), listOf("start", "end", "account")) { v -> vm.loadAnalysis(v[0], v[1], v[2], granularity) }
        result?.let { Text(it, style = MaterialTheme.typography.titleMedium) } ?: Text("No analysis loaded for this period.")
}

@Composable private fun ListTitle(title: String, empty: Boolean) { Text(title, style = MaterialTheme.typography.titleLarge); if (empty) Text("No ${title.lowercase()} yet.") }
@Composable private fun ActionRow(first: () -> Unit, firstLabel: String, second: () -> Unit, secondLabel: String) { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = first) { Text(firstLabel) }; OutlinedButton(onClick = second) { Text(secondLabel) } } }
@Composable private fun Form(title: String, hints: List<String>, keys: List<String>, submit: (List<String>) -> Unit) {
    var values by rememberSaveable(keys) { mutableStateOf(List(keys.size) { "" }) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        hints.forEachIndexed { index, hint -> OutlinedTextField(values[index], { next -> values = values.toMutableList().also { it[index] = next } }, label = { Text(hint) }, modifier = Modifier.fillMaxWidth(), singleLine = true, isError = values[index].isBlank() && !hint.contains("optional", true)) }
        Button(onClick = { submit(values) }, modifier = Modifier.fillMaxWidth(), enabled = values.withIndex().all { (index, value) -> value.isNotBlank() || hints[index].contains("optional", true) }) { Text("Save") }
    }
}

private fun formatMinor(value: Long): String = "INR ${BigDecimal.valueOf(value, 2)}"