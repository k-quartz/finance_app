package com.financialapp.ui.accounts

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import com.financialapp.domain.model.Account
import com.financialapp.domain.model.AccountType
import com.financialapp.ui.common.formatMoney
import com.financialapp.ui.common.formatRupeesFromMinorUnits
import com.financialapp.ui.common.fieldErrorMessage
import com.financialapp.ui.common.parseRupeesToMinorUnits

@Composable
fun AccountsRoute(
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Re-query on resume so balances reflect transactions entered elsewhere.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    AccountsScreen(
        state = state,
        onCreateAccount = viewModel::createAccount,
        onUpdateAccount = viewModel::updateAccount,
        onDeleteAccount = viewModel::deleteAccount,
    )
}

@Composable
fun AccountsScreen(
    state: AccountsUiState,
    onCreateAccount: (String, AccountType, Long, (Boolean, String?) -> Unit) -> Unit,
    onUpdateAccount: (Long, String, AccountType, Long, (Boolean, String?) -> Unit) -> Unit,
    onDeleteAccount: (Long, (Boolean, String?) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Account?>(null) }
    var deleting by remember { mutableStateOf<Account?>(null) }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add account")
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
            Text("Accounts", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Bank, cash, credit card, and investment accounts with derived balances.",
                style = MaterialTheme.typography.bodyMedium,
            )
            state.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp))
            } else if (state.balances.isEmpty()) {
                Text(
                    "No accounts yet. Tap + to create your first account.",
                    modifier = Modifier.padding(top = 24.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.balances, key = { it.accountId }) { balance ->
                        val account = state.accounts.firstOrNull { it.id == balance.accountId }
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f).clickable(enabled = account != null) { editing = account },
                                ) {
                                    Text(
                                        state.namesById[balance.accountId] ?: "Account #${balance.accountId}",
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(balance.accountType.name, style = MaterialTheme.typography.bodySmall)
                                    Text("Tap to edit", style = MaterialTheme.typography.labelSmall)
                                }
                                Text(formatMoney(balance.amount), style = MaterialTheme.typography.titleMedium)
                                IconButton(onClick = { if (account != null) deleting = account }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete account")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AccountFormDialog(
            title = "New account",
            confirmLabel = "Create",
            initial = null,
            isSaving = state.isSaving,
            onDismiss = { showAddDialog = false },
            onSave = { name, type, minorUnits, done ->
                onCreateAccount(name, type, minorUnits) { success, message ->
                    if (success) showAddDialog = false
                    done(success, message)
                }
            },
        )
    }
    editing?.let { account ->
        AccountFormDialog(
            title = "Edit account",
            confirmLabel = "Save",
            initial = account,
            isSaving = state.isSaving,
            onDismiss = { editing = null },
            onSave = { name, type, minorUnits, done ->
                onUpdateAccount(account.id, name, type, minorUnits) { success, message ->
                    if (success) editing = null
                    done(success, message)
                }
            },
        )
    }
    deleting?.let { account ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete account") },
            text = { Text("Delete ${account.name}? Historical transactions remain, but the account will no longer appear.") },
            confirmButton = {
                TextButton(
                    enabled = !state.isSaving,
                    onClick = {
                        onDeleteAccount(account.id) { success, _ -> if (success) deleting = null }
                    },
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AccountFormDialog(
    title: String,
    confirmLabel: String,
    initial: Account?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, AccountType, Long, (Boolean, String?) -> Unit) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var openingBalance by remember { mutableStateOf(initial?.let { formatRupeesFromMinorUnits(it.openingBalance.minorUnits) } ?: "0") }
    var typeExpanded by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf(initial?.type ?: AccountType.BANK) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Account name") },
                    singleLine = true,
                    isError = fieldErrorMessage(errorMessage, "name") != null,
                    supportingText = { fieldErrorMessage(errorMessage, "name")?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
                    OutlinedTextField(
                        value = selectedType.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Account type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    DropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        AccountType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name) },
                                onClick = {
                                    selectedType = type
                                    typeExpanded = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = openingBalance,
                    onValueChange = { openingBalance = it },
                    label = { Text("Opening balance (INR)") },
                    singleLine = true,
                    isError = fieldErrorMessage(errorMessage, "amount") != null,
                    supportingText = { fieldErrorMessage(errorMessage, "amount")?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSaving,
                onClick = {
                    val minorUnits = parseRupeesToMinorUnits(openingBalance, allowZero = true)
                    if (name.isBlank()) {
                        errorMessage = "Account name is required"
                    } else if (minorUnits == null) {
                        errorMessage = "Enter a valid non-negative amount"
                    } else {
                        onSave(name, selectedType, minorUnits) { success, message ->
                            if (!success) errorMessage = message
                        }
                    }
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
