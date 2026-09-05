package com.financialapp.ui.mutualfunds

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.financialapp.domain.model.InvestmentTransactionType
import com.financialapp.domain.model.MutualFund
import com.financialapp.domain.model.MutualFundHoldingValue
import com.financialapp.ui.common.formatMoney
import com.financialapp.ui.common.fieldErrorMessage
import com.financialapp.ui.common.parseIsoDateOrNull
import com.financialapp.ui.common.parseRupeesToMinorUnits
import com.financialapp.ui.common.toIsoString
import com.financialapp.ui.common.TypeaheadField
import java.time.LocalDate

@Composable
fun MutualFundsRoute(
    viewModel: MutualFundsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Re-query on resume so holdings/accounts created elsewhere are reflected.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    MutualFundsScreen(
        state = state,
        onCreateFund = viewModel::createFund,
        onUpdateFund = viewModel::updateFund,
        onDeleteFund = viewModel::deleteFund,
        onRecordNav = viewModel::recordNav,
        onRecordInvestment = viewModel::recordInvestment,
    )
}

@Composable
fun MutualFundsScreen(
    state: MutualFundsUiState,
    onCreateFund: (String, String, (Boolean, String?) -> Unit) -> Unit,
    onUpdateFund: (Long, String, String, (Boolean, String?) -> Unit) -> Unit,
    onDeleteFund: (Long, (Boolean, String?) -> Unit) -> Unit,
    onRecordNav: (Long, Long, LocalDate, (Boolean, String?) -> Unit) -> Unit,
    onRecordInvestment: (Long, Long, Long?, InvestmentTransactionType, String, Long, LocalDate, (Boolean, String?) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showFundDialog by remember { mutableStateOf(false) }
    var showTransactionDialog by remember { mutableStateOf(false) }
    var lastCreatedFundId by remember { mutableStateOf<Long?>(null) }
    var editingFund by remember { mutableStateOf<MutualFund?>(null) }
    var deletingFund by remember { mutableStateOf<MutualFund?>(null) }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFloatingActionButton(onClick = { showFundDialog = true }) {
                    Icon(Icons.Filled.ShowChart, contentDescription = "Add fund")
                }
                ExtendedFloatingActionButton(onClick = { showTransactionDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("Investment", modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
            Text("Mutual funds", style = MaterialTheme.typography.headlineMedium)
            Text("Holdings, NAV history, purchases, and redemptions.", style = MaterialTheme.typography.bodyMedium)
            state.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp))
            } else {
                val portfolio = state.portfolio
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { Text("Funds", style = MaterialTheme.typography.titleMedium) }
                    if (state.funds.isEmpty()) {
                        item {
                            Text("No mutual funds yet. Use the chart button to add a fund.")
                        }
                    } else {
                        items(state.funds, key = { "fund-${it.id}" }) { fund ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f).clickable { editingFund = fund }) {
                                        Text(fund.name, style = MaterialTheme.typography.titleSmall)
                                        Text("${fund.code} · tap to edit", style = MaterialTheme.typography.bodySmall)
                                    }
                                    IconButton(onClick = { deletingFund = fund }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete fund")
                                    }
                                }
                            }
                        }
                    }
                    item { Text("Holdings", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                    if (portfolio == null || portfolio.holdings.isEmpty()) {
                        item {
                            Text("No holdings yet. Record a purchase with units and NAV.")
                        }
                    } else {
                        item {
                            Text(
                                "Portfolio value: ${formatMoney(portfolio.totalValue)}",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        items(portfolio.holdings, key = { "${it.fundId}-${it.investmentAccountId}" }) { holding ->
                            HoldingRow(holding)
                        }
                    }
                }
            }
        }
    }

    if (showFundDialog) {
        AddFundDialog(
            title = "New mutual fund",
            confirmLabel = "Create",
            initial = null,
            isSaving = state.isSaving,
            onDismiss = { showFundDialog = false },
            onSave = { name, code, done ->
                onCreateFund(name, code) { success, fundId ->
                    if (success) {
                        lastCreatedFundId = fundId?.toLongOrNull()
                        showFundDialog = false
                    }
                    done(success, if (success) null else fundId)
                }
            },
        )
    }
    editingFund?.let { fund ->
        AddFundDialog(
            title = "Edit mutual fund",
            confirmLabel = "Save",
            initial = fund,
            isSaving = state.isSaving,
            onDismiss = { editingFund = null },
            onSave = { name, code, done ->
                onUpdateFund(fund.id, name, code) { success, message ->
                    if (success) editingFund = null
                    done(success, message)
                }
            },
        )
    }
    deletingFund?.let { fund ->
        AlertDialog(
            onDismissRequest = { deletingFund = null },
            title = { Text("Delete mutual fund") },
            text = { Text("Delete ${fund.name}? Historical holdings stay in the database but the fund is hidden.") },
            confirmButton = {
                TextButton(
                    enabled = !state.isSaving,
                    onClick = { onDeleteFund(fund.id) { success, _ -> if (success) deletingFund = null } },
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deletingFund = null }) { Text("Cancel") } },
        )
    }

    if (showTransactionDialog) {
        RecordInvestmentDialog(
            state = state,
            defaultFundId = lastCreatedFundId,
            onCreateFund = { showFundDialog = true },
            isSaving = state.isSaving,
            onDismiss = { showTransactionDialog = false },
            onRecordNav = onRecordNav,
            onRecordInvestment = { fundId, accountId, cashAccountId, type, units, nav, date ->
                onRecordInvestment(fundId, accountId, cashAccountId, type, units, nav, date) { success, _ ->
                    if (success) showTransactionDialog = false
                }
            },
        )
    }
}

@Composable
private fun HoldingRow(holding: MutualFundHoldingValue) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(holding.fundName, style = MaterialTheme.typography.titleSmall)
                Text("${holding.units.asDecimal()} units @ ${formatMoney(holding.nav)}", style = MaterialTheme.typography.bodySmall)
            }
            Text(formatMoney(holding.value), style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun AddFundDialog(
    title: String,
    confirmLabel: String,
    initial: MutualFund?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, (Boolean, String?) -> Unit) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var code by remember { mutableStateOf(initial?.code.orEmpty()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Fund name") },
                    singleLine = true,
                    isError = fieldErrorMessage(errorMessage, "name") != null,
                    supportingText = { fieldErrorMessage(errorMessage, "name")?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Fund code (unique)") },
                    singleLine = true,
                    isError = fieldErrorMessage(errorMessage, "code") != null,
                    supportingText = { fieldErrorMessage(errorMessage, "code")?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSaving,
                onClick = {
                    if (name.isBlank() || code.isBlank()) {
                        errorMessage = "Fund name and code are required"
                    } else {
                        onSave(name, code) { success, message -> if (!success) errorMessage = message }
                    }
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * Records a NAV entry and/or a purchase/redemption for a selected fund.
 */
@Composable
private fun RecordInvestmentDialog(
    state: MutualFundsUiState,
    defaultFundId: Long?,
    onCreateFund: () -> Unit,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onRecordNav: (Long, Long, LocalDate, (Boolean, String?) -> Unit) -> Unit,
    onRecordInvestment: (Long, Long, Long?, InvestmentTransactionType, String, Long, LocalDate) -> Unit,
) {
    var selectedFund by remember {
        mutableStateOf(state.funds.firstOrNull { it.id == defaultFundId } ?: state.funds.firstOrNull())
    }
    var fundText by remember { mutableStateOf(selectedFund?.name.orEmpty()) }
    var navAmount by remember { mutableStateOf("") }
    var navDate by remember { mutableStateOf(LocalDate.now().toIsoString()) }
    var typeExpanded by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf(InvestmentTransactionType.PURCHASE) }
    var accountExpanded by remember { mutableStateOf(false) }
    var selectedAccount by remember { mutableStateOf(state.investmentAccounts.firstOrNull()) }
    var cashAccountExpanded by remember { mutableStateOf(false) }
    var selectedCashAccount by remember { mutableStateOf<InvestmentAccountOption?>(null) }
    var units by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    val fundResolved = selectedFund?.name == fundText && fundText.isNotBlank()
    androidx.compose.runtime.LaunchedEffect(defaultFundId, state.funds) {
        defaultFundId?.let { id -> state.funds.firstOrNull { it.id == id }?.let { selectedFund = it; fundText = it.name } }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record NAV & investment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.funds.isEmpty()) {
                    Text("Create a mutual fund first.", color = MaterialTheme.colorScheme.error)
                }
                TypeaheadField(
                    value = fundText,
                    onValueChange = { fundText = it; selectedFund = null },
                    label = "Fund",
                    suggestions = state.funds.filter { it.name.contains(fundText, true) }.map { it.name },
                    onSuggestionSelected = { name -> selectedFund = state.funds.first { it.name == name }; fundText = name },
                    createLabel = "Create fund \"$fundText\"",
                    onCreate = onCreateFund,
                    errorText = fieldErrorMessage(errorMessage, "fund"),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = navAmount,
                        onValueChange = { navAmount = it },
                        label = { Text("NAV (INR)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = navDate,
                        onValueChange = { navDate = it },
                        label = { Text("NAV date") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                TextButton(onClick = {
                    val fund = selectedFund
                    val nav = parseRupeesToMinorUnits(navAmount)
                    val date = parseIsoDateOrNull(navDate)
                    when {
                        !fundResolved || fund == null -> errorMessage = "Select a fund from the list"
                        nav == null -> errorMessage = "Enter a valid positive NAV"
                        date == null -> errorMessage = "Enter a valid NAV date"
                        else -> {
                            errorMessage = null
                            onRecordNav(fund.id, nav, date) { success, message ->
                                infoMessage = if (success) "NAV recorded" else message
                            }
                        }
                    }
                }) { Text("Record NAV only") }

                ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
                    OutlinedTextField(
                        value = selectedType.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Transaction type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    DropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        InvestmentTransactionType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name) },
                                onClick = { selectedType = type; typeExpanded = false },
                            )
                        }
                    }
                }
                ExposedDropdownMenuBox(expanded = accountExpanded, onExpandedChange = { accountExpanded = it }) {
                    OutlinedTextField(
                        value = selectedAccount?.name ?: "Select investment account",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Investment account") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    DropdownMenu(expanded = accountExpanded, onDismissRequest = { accountExpanded = false }) {
                        state.investmentAccounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text(account.name) },
                                onClick = { selectedAccount = account; accountExpanded = false },
                            )
                        }
                    }
                }
                ExposedDropdownMenuBox(expanded = cashAccountExpanded, onExpandedChange = { cashAccountExpanded = it }) {
                    OutlinedTextField(
                        value = selectedCashAccount?.name ?: "No linked cash account",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Linked cash account (optional)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cashAccountExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    DropdownMenu(expanded = cashAccountExpanded, onDismissRequest = { cashAccountExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("No linked cash account") },
                            onClick = { selectedCashAccount = null; cashAccountExpanded = false },
                        )
                        state.cashAccounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text(account.name) },
                                onClick = { selectedCashAccount = account; cashAccountExpanded = false },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = units,
                    onValueChange = { units = it },
                    label = { Text("Units") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                infoMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSaving && fundResolved,
                onClick = {
                    val fund = selectedFund
                    val account = selectedAccount
                    val nav = parseRupeesToMinorUnits(navAmount)
                    val date = parseIsoDateOrNull(navDate)
                    when {
                        !fundResolved || fund == null -> errorMessage = "Select a fund from the list"
                        account == null -> errorMessage = "Select an investment account"
                        nav == null -> errorMessage = "Enter a valid positive NAV"
                        date == null -> errorMessage = "Enter a valid date"
                        units.isBlank() -> errorMessage = "Enter units"
                        else -> {
                            errorMessage = null
                            onRecordInvestment(fund.id, account.id, selectedCashAccount?.id, selectedType, units, nav, date)
                        }
                    }
                },
            ) { Text("Record") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
