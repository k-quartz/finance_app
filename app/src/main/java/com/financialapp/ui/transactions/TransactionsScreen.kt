package com.financialapp.ui.transactions

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
import androidx.compose.material.icons.filled.SwapHoriz
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
import com.financialapp.domain.model.Category
import com.financialapp.domain.model.CategoryType
import com.financialapp.domain.model.FinancialTransaction
import com.financialapp.domain.model.TransactionType
import com.financialapp.ui.common.formatMoney
import com.financialapp.ui.common.formatRupeesFromMinorUnits
import com.financialapp.ui.common.fieldErrorMessage
import com.financialapp.ui.common.parseIsoDateOrNull
import com.financialapp.ui.common.parseRupeesToMinorUnits
import com.financialapp.ui.common.toIsoString
import com.financialapp.ui.common.TypeaheadField
import java.time.LocalDate

@Composable
fun TransactionsRoute(
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Re-query on resume so data entered elsewhere (e.g. purchases) is reflected.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    TransactionsScreen(
        state = state,
        onCreateTransaction = viewModel::createTransaction,
        onCreateTransactionWithCategory = viewModel::createTransactionWithCategory,
        onUpdateTransaction = viewModel::updateTransaction,
        onDeleteTransaction = viewModel::deleteTransaction,
        onCreateTransfer = viewModel::createTransfer,
    )
}

@Composable
fun TransactionsScreen(
    state: TransactionsUiState,
    onCreateTransaction: (Long, TransactionType, Long, Long, LocalDate, String?, String?, (Boolean, String?) -> Unit) -> Unit,
    onCreateTransactionWithCategory: (Long, TransactionType, Long, String, LocalDate, String?, String?, (Boolean, String?) -> Unit) -> Unit,
    onUpdateTransaction: (Long, Long, TransactionType, Long, Long, LocalDate, String?, String?, (Boolean, String?) -> Unit) -> Unit,
    onDeleteTransaction: (Long, (Boolean, String?) -> Unit) -> Unit,
    onCreateTransfer: (Long, Long, Long, LocalDate, (Boolean, String?) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showTransactionDialog by remember { mutableStateOf(false) }
    var showTransferDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<FinancialTransaction?>(null) }
    var deleting by remember { mutableStateOf<FinancialTransaction?>(null) }
    val accountNames = state.accounts.associate { it.id to it.name }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFloatingActionButton(onClick = { showTransferDialog = true }) {
                    Icon(Icons.Filled.SwapHoriz, contentDescription = "Add transfer")
                }
                ExtendedFloatingActionButton(onClick = { showTransactionDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("Transaction", modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
            Text("Transactions", style = MaterialTheme.typography.headlineMedium)
            Text("Income, expense, and transfer activity for the last 90 days.", style = MaterialTheme.typography.bodyMedium)
            state.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp))
            } else if (state.transactions.isEmpty()) {
                Text("No transactions in the last 90 days.", modifier = Modifier.padding(top = 24.dp))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.transactions, key = { it.id }) { transaction ->
                        TransactionRow(
                            transaction = transaction,
                            accountName = accountNames[transaction.accountId] ?: "Account #${transaction.accountId}",
                            onEdit = { if (transaction.type != TransactionType.TRANSFER) editing = transaction },
                            onDelete = { deleting = transaction },
                        )
                    }
                }
            }
        }
    }

    if (showTransactionDialog) {
        AddTransactionDialog(
            title = "New transaction",
            confirmLabel = "Save",
            accounts = state.accounts,
            categories = state.categories,
            initial = null,
            isSaving = state.isSaving,
            onDismiss = { showTransactionDialog = false },
            onSave = { accountId, type, amount, categoryId, date, merchant, notes, done ->
                onCreateTransaction(accountId, type, amount, categoryId, date, merchant, notes) { success, message ->
                    if (success) showTransactionDialog = false
                    done(success, message)
                }
            },
            onCreateWithCategory = onCreateTransactionWithCategory,
        )
    }

    editing?.let { transaction ->
        AddTransactionDialog(
            title = "Edit transaction",
            confirmLabel = "Save",
            accounts = state.accounts,
            categories = state.categories,
            initial = transaction,
            isSaving = state.isSaving,
            onDismiss = { editing = null },
            onSave = { accountId, type, amount, categoryId, date, merchant, notes, done ->
                onUpdateTransaction(transaction.id, accountId, type, amount, categoryId, date, merchant, notes) { success, message ->
                    if (success) editing = null
                    done(success, message)
                }
            },
            onCreateWithCategory = null,
        )
    }

    deleting?.let { transaction ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete transaction") },
            text = { Text("Delete this ${transaction.type.name.lowercase()} entry? The original row is kept for history and hidden from totals.") },
            confirmButton = {
                TextButton(
                    enabled = !state.isSaving,
                    onClick = { onDeleteTransaction(transaction.id) { success, _ -> if (success) deleting = null } },
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }

    if (showTransferDialog) {
        AddTransferDialog(
            accounts = state.accounts,
            isSaving = state.isSaving,
            onDismiss = { showTransferDialog = false },
            onSave = { sourceId, destinationId, amount, date ->
                onCreateTransfer(sourceId, destinationId, amount, date) { success, _ ->
                    if (success) showTransferDialog = false
                }
            },
        )
    }
}

@Composable
private fun TransactionRow(
    transaction: FinancialTransaction,
    accountName: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).clickable(onClick = onEdit)) {
                Text(transaction.merchantOrPayee ?: transaction.type.name, style = MaterialTheme.typography.titleSmall)
                Text("$accountName • ${transaction.transactionDate}", style = MaterialTheme.typography.bodySmall)
                if (transaction.type != TransactionType.TRANSFER) {
                    Text("Tap to edit", style = MaterialTheme.typography.labelSmall)
                }
            }
            Text(formatMoney(transaction.amount), style = MaterialTheme.typography.titleSmall)
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete transaction")
            }
        }
    }
}

@Composable
private fun AddTransactionDialog(
    title: String,
    confirmLabel: String,
    accounts: List<AccountOption>,
    categories: List<Category>,
    initial: FinancialTransaction?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (Long, TransactionType, Long, Long, LocalDate, String?, String?, (Boolean, String?) -> Unit) -> Unit,
    onCreateWithCategory: ((Long, TransactionType, Long, String, LocalDate, String?, String?, (Boolean, String?) -> Unit) -> Unit)? = null,
) {
    var typeExpanded by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf(initial?.type ?: TransactionType.EXPENSE) }
    var accountExpanded by remember { mutableStateOf(false) }
    var selectedAccount by remember { mutableStateOf(accounts.firstOrNull { it.id == initial?.accountId }) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf(categories.firstOrNull { it.id == initial?.categoryId }) }
    var categoryText by remember { mutableStateOf(selectedCategory?.name.orEmpty()) }
    var showCreateCategory by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf(initial?.let { formatRupeesFromMinorUnits(it.amount.minorUnits) }.orEmpty()) }
    var date by remember { mutableStateOf((initial?.transactionDate ?: LocalDate.now()).toIsoString()) }
    var merchant by remember { mutableStateOf(initial?.merchantOrPayee.orEmpty()) }
    var notes by remember { mutableStateOf(initial?.notes.orEmpty()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val eligibleCategoryType = if (selectedType == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
    val eligibleCategories = categoriesForType(categories, eligibleCategoryType)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
                    OutlinedTextField(
                        value = selectedType.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    DropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        listOf(TransactionType.EXPENSE, TransactionType.INCOME).forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name) },
                                onClick = { selectedType = type; selectedCategory = null; typeExpanded = false },
                            )
                        }
                    }
                }
                ExposedDropdownMenuBox(expanded = accountExpanded, onExpandedChange = { accountExpanded = it }) {
                    OutlinedTextField(
                        value = selectedAccount?.name ?: "Select account",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Account") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountExpanded) },
                        isError = fieldErrorMessage(errorMessage, "account") != null,
                        supportingText = { fieldErrorMessage(errorMessage, "account")?.let { Text(it) } },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    DropdownMenu(expanded = accountExpanded, onDismissRequest = { accountExpanded = false }) {
                        accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text(account.name) },
                                onClick = { selectedAccount = account; accountExpanded = false },
                            )
                        }
                    }
                }
                TypeaheadField(
                    value = categoryText,
                    onValueChange = { categoryText = it; selectedCategory = null },
                    label = "Category",
                    suggestions = eligibleCategories.filter { it.name.contains(categoryText, true) }.map { it.name },
                    onSuggestionSelected = { name -> selectedCategory = eligibleCategories.first { it.name == name }; categoryText = name },
                    errorText = fieldErrorMessage(errorMessage, "category"),
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount (INR)") },
                    singleLine = true,
                    isError = fieldErrorMessage(errorMessage, "amount") != null,
                    supportingText = { fieldErrorMessage(errorMessage, "amount")?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date (yyyy-MM-dd)") },
                    singleLine = true,
                    isError = fieldErrorMessage(errorMessage, "date") != null,
                    supportingText = { fieldErrorMessage(errorMessage, "date")?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("Merchant / payee (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSaving,
                onClick = {
                    val minorUnits = parseRupeesToMinorUnits(amount)
                    val parsedDate = parseIsoDateOrNull(date)
                    val account = selectedAccount
                    val category = selectedCategory
                    when {
                        account == null -> errorMessage = "Select an account"
                        category == null && categoryText.isBlank() -> errorMessage = "Select a category"
                        category == null -> if (onCreateWithCategory != null) showCreateCategory = true else errorMessage = "Select a category"
                        minorUnits == null -> errorMessage = "Enter a valid positive amount"
                        parsedDate == null -> errorMessage = "Enter a valid date (yyyy-MM-dd)"
                        else -> {
                            errorMessage = null
                            onSave(account.id, selectedType, minorUnits, category!!.id, parsedDate, merchant, notes) { success, message ->
                                if (!success) errorMessage = message
                            }
                        }
                    }
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )

    if (showCreateCategory) {
        AlertDialog(
            onDismissRequest = { showCreateCategory = false },
            title = { Text("Create category?") },
            text = { Text("Create \"$categoryText\" as a ${eligibleCategoryType.name.lowercase()} category?") },
            confirmButton = { TextButton(onClick = {
                showCreateCategory = false
                val account = selectedAccount!!
                val minorUnits = parseRupeesToMinorUnits(amount)!!
                val parsedDate = parseIsoDateOrNull(date)!!
                onCreateWithCategory!!(account.id, selectedType, minorUnits, categoryText, parsedDate, merchant, notes) { success, message -> if (!success) errorMessage = message }
            }) { Text("Create") } },
            dismissButton = { TextButton(onClick = { showCreateCategory = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AddTransferDialog(
    accounts: List<AccountOption>,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (Long, Long, Long, LocalDate) -> Unit,
) {
    var sourceExpanded by remember { mutableStateOf(false) }
    var selectedSource by remember { mutableStateOf<AccountOption?>(null) }
    var destinationExpanded by remember { mutableStateOf(false) }
    var selectedDestination by remember { mutableStateOf<AccountOption?>(null) }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toIsoString()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New transfer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExposedDropdownMenuBox(expanded = sourceExpanded, onExpandedChange = { sourceExpanded = it }) {
                    OutlinedTextField(
                        value = selectedSource?.name ?: "From account",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("From") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sourceExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    DropdownMenu(expanded = sourceExpanded, onDismissRequest = { sourceExpanded = false }) {
                        accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text(account.name) },
                                onClick = { selectedSource = account; sourceExpanded = false },
                            )
                        }
                    }
                }
                ExposedDropdownMenuBox(expanded = destinationExpanded, onExpandedChange = { destinationExpanded = it }) {
                    OutlinedTextField(
                        value = selectedDestination?.name ?: "To account",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("To") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = destinationExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    DropdownMenu(expanded = destinationExpanded, onDismissRequest = { destinationExpanded = false }) {
                        accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text(account.name) },
                                onClick = { selectedDestination = account; destinationExpanded = false },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount (INR)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date (yyyy-MM-dd)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSaving,
                onClick = {
                    val minorUnits = parseRupeesToMinorUnits(amount)
                    val parsedDate = parseIsoDateOrNull(date)
                    val source = selectedSource
                    val destination = selectedDestination
                    when {
                        source == null || destination == null -> errorMessage = "Select both accounts"
                        source.id == destination.id -> errorMessage = "Source and destination must differ"
                        minorUnits == null -> errorMessage = "Enter a valid positive amount"
                        parsedDate == null -> errorMessage = "Enter a valid date (yyyy-MM-dd)"
                        else -> {
                            errorMessage = null
                            onSave(source.id, destination.id, minorUnits, parsedDate)
                        }
                    }
                },
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
