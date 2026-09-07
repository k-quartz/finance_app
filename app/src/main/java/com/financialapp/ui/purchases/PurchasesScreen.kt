package com.financialapp.ui.purchases

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
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import com.financialapp.domain.model.Product
import com.financialapp.domain.model.CategoryType
import com.financialapp.domain.model.Purchase
import com.financialapp.ui.common.DatePickerField
import com.financialapp.ui.common.SelectionDropdownField
import com.financialapp.ui.common.formatMoney
import com.financialapp.ui.common.formatRupeesFromMinorUnits
import com.financialapp.ui.common.fieldErrorMessage
import com.financialapp.ui.common.parseRupeesToMinorUnits
import com.financialapp.ui.common.TypeaheadField
import java.time.LocalDate

@Composable
fun PurchasesRoute(
    viewModel: PurchasesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Re-query on resume so newly created categories/accounts elsewhere are reflected.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    PurchasesScreen(
        state = state,
        onCreateProduct = viewModel::createProduct,
        onCreateCategory = viewModel::createCategory,
        onUpdateProduct = viewModel::updateProduct,
        onDeleteProduct = viewModel::deleteProduct,
        onCreatePurchase = viewModel::createPurchase,
        onUpdatePurchase = viewModel::updatePurchase,
        onDeletePurchase = viewModel::deletePurchase,
    )
}

@Composable
fun PurchasesScreen(
    state: PurchasesUiState,
    onCreateProduct: (String, String, Long?, (Boolean, Long?, String?) -> Unit) -> Unit,
    onCreateCategory: (String, CategoryType, Long?, (Boolean, Long?, String?) -> Unit) -> Unit,
    onUpdateProduct: (Long, String, String, (Boolean, String?) -> Unit) -> Unit,
    onDeleteProduct: (Long, (Boolean, String?) -> Unit) -> Unit,
    onCreatePurchase: (Long, Long, Long, String, String, Long, LocalDate, String?, String?, (Boolean, String?) -> Unit) -> Unit,
    onUpdatePurchase: (Long, Long, Long, Long, String, String, Long, LocalDate, String?, String?, (Boolean, String?) -> Unit) -> Unit,
    onDeletePurchase: (Long, (Boolean, String?) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showProductDialog by remember { mutableStateOf(false) }
    var showPurchaseDialog by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var deletingProduct by remember { mutableStateOf<Product?>(null) }
    var editingPurchase by remember { mutableStateOf<Purchase?>(null) }
    var deletingPurchase by remember { mutableStateOf<Purchase?>(null) }
    var createProductForPurchase by remember { mutableStateOf(false) }
    var createCategoryForPurchase by remember { mutableStateOf(false) }
    var pendingProductId by remember { mutableStateOf<Long?>(null) }
    var pendingCategoryId by remember { mutableStateOf<Long?>(null) }
    val productNames = state.products.associate { it.id to it.name }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFloatingActionButton(onClick = { showProductDialog = true }) {
                    Icon(Icons.Filled.Inventory2, contentDescription = "Add product")
                }
                ExtendedFloatingActionButton(onClick = { showPurchaseDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("Purchase", modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
            Text("Purchases", style = MaterialTheme.typography.headlineMedium)
            Text("Product purchases with quantity, unit price, and totals.", style = MaterialTheme.typography.bodyMedium)
            state.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.products.isNotEmpty()) {
                        item { Text("Products", style = MaterialTheme.typography.titleMedium) }
                        items(state.products, key = { "product-${it.id}" }) { product ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f).clickable { editingProduct = product }) {
                                        Text(product.name, style = MaterialTheme.typography.titleSmall)
                                        Text("${product.defaultUnit} · tap to edit", style = MaterialTheme.typography.bodySmall)
                                    }
                                    IconButton(onClick = { deletingProduct = product }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete product")
                                    }
                                }
                            }
                        }
                    }
                    item { Text("Recent purchases", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                    if (state.purchases.isEmpty()) {
                        item {
                            Text("No purchases in the last 90 days. Add a product, then record a purchase.")
                        }
                    } else {
                        items(state.purchases, key = { it.id }) { purchase ->
                            PurchaseRow(
                                purchase = purchase,
                                productName = productNames[purchase.productId] ?: "Product #${purchase.productId}",
                                onEdit = { editingPurchase = purchase },
                                onDelete = { deletingPurchase = purchase },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showProductDialog) {
        ProductFormDialog(
            title = "New product",
            confirmLabel = "Create",
            initial = null,
            isSaving = state.isSaving,
            onDismiss = { showProductDialog = false },
            categories = state.expenseCategories,
            onSave = { name, unit, categoryId, done ->
                onCreateProduct(name, unit, categoryId) { success, _, message ->
                    if (success) showProductDialog = false
                    done(success, message)
                }
            },
        )
    }
    editingProduct?.let { product ->
        ProductFormDialog(
            title = "Edit product",
            confirmLabel = "Save",
            initial = product,
            categories = state.expenseCategories,
            isSaving = state.isSaving,
            onDismiss = { editingProduct = null },
            onSave = { name, unit, _, done ->
                onUpdateProduct(product.id, name, unit) { success, message ->
                    if (success) editingProduct = null
                    done(success, message)
                }
            },
        )
    }
    deletingProduct?.let { product ->
        AlertDialog(
            onDismissRequest = { deletingProduct = null },
            title = { Text("Delete product") },
            text = { Text("Delete ${product.name}? Historical purchases remain.") },
            confirmButton = {
                TextButton(
                    enabled = !state.isSaving,
                    onClick = { onDeleteProduct(product.id) { success, _ -> if (success) deletingProduct = null } },
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deletingProduct = null }) { Text("Cancel") } },
        )
    }

    if (showPurchaseDialog) {
        PurchaseFormDialog(
            title = "New purchase",
            confirmLabel = "Save",
            state = state,
            initial = null,
            isSaving = state.isSaving,
            onDismiss = { showPurchaseDialog = false },
            onCreateProduct = { createProductForPurchase = true },
            onCreateCategory = { createCategoryForPurchase = true },
            pendingProductId = pendingProductId,
            pendingCategoryId = pendingCategoryId,
            onSave = { accountId, categoryId, productId, quantity, unit, unitPrice, date, merchant, notes, done ->
                onCreatePurchase(accountId, categoryId, productId, quantity, unit, unitPrice, date, merchant, notes) { success, message ->
                    if (success) showPurchaseDialog = false
                    done(success, message)
                }
            },
        )
    }
    if (createProductForPurchase) {
        ProductFormDialog("New product", "Create", null, state.expenseCategories, state.isSaving, { createProductForPurchase = false }) { name, unit, categoryId, done ->
            onCreateProduct(name, unit, categoryId) { success, id, message ->
                if (success) { pendingProductId = id; createProductForPurchase = false }
                done(success, message)
            }
        }
    }
    if (createCategoryForPurchase) {
        var categoryName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { createCategoryForPurchase = false },
            title = { Text("New expense category") },
            text = { OutlinedTextField(categoryName, { categoryName = it }, label = { Text("Category name") }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = categoryName.isNotBlank() && !state.isSaving, onClick = {
                    onCreateCategory(categoryName, CategoryType.EXPENSE, null) { success, id, _ ->
                        if (success) { pendingCategoryId = id; createCategoryForPurchase = false }
                    }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { createCategoryForPurchase = false }) { Text("Cancel") } },
        )
    }
    editingPurchase?.let { purchase ->
        PurchaseFormDialog(
            title = "Edit purchase",
            confirmLabel = "Save",
            state = state,
            initial = purchase,
            isSaving = state.isSaving,
            onDismiss = { editingPurchase = null },
            onCreateProduct = {},
            onCreateCategory = {},
            pendingProductId = null,
            pendingCategoryId = null,
            onSave = { accountId, categoryId, productId, quantity, unit, unitPrice, date, merchant, notes, done ->
                onUpdatePurchase(purchase.id, accountId, categoryId, productId, quantity, unit, unitPrice, date, merchant, notes) { success, message ->
                    if (success) editingPurchase = null
                    done(success, message)
                }
            },
        )
    }
    deletingPurchase?.let { purchase ->
        AlertDialog(
            onDismissRequest = { deletingPurchase = null },
            title = { Text("Delete purchase") },
            text = { Text("Delete this purchase? The linked expense will also be removed from dashboard totals.") },
            confirmButton = {
                TextButton(
                    enabled = !state.isSaving,
                    onClick = { onDeletePurchase(purchase.id) { success, _ -> if (success) deletingPurchase = null } },
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deletingPurchase = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PurchaseRow(
    purchase: Purchase,
    productName: String,
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
                Text(productName, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${purchase.quantity.asDecimal()} ${purchase.quantity.unit} • ${purchase.purchaseDate}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("Tap to edit", style = MaterialTheme.typography.labelSmall)
            }
            Text(formatMoney(purchase.totalAmount), style = MaterialTheme.typography.titleSmall)
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete purchase")
            }
        }
    }
}

@Composable
private fun ProductFormDialog(
    title: String,
    confirmLabel: String,
    initial: Product?,
    categories: List<com.financialapp.domain.model.Category> = emptyList(),
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, Long?, (Boolean, String?) -> Unit) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var unit by remember { mutableStateOf(initial?.defaultUnit.orEmpty()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var category by remember { mutableStateOf(categories.firstOrNull { it.id == initial?.categoryId }) }
    var categoryText by remember { mutableStateOf(category?.name.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Product name") },
                    singleLine = true,
                    isError = fieldErrorMessage(errorMessage, "name") != null,
                    supportingText = { fieldErrorMessage(errorMessage, "name")?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                TypeaheadField(
                    value = categoryText,
                    onValueChange = { categoryText = it; category = null },
                    label = "Category (optional)",
                    suggestions = categories.map { it.name },
                    onSuggestionSelected = { name -> category = categories.first { it.name == name }; categoryText = name },
                )
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("Default unit (e.g. kg, L, pcs)") },
                    singleLine = true,
                    isError = fieldErrorMessage(errorMessage, "unit") != null,
                    supportingText = { fieldErrorMessage(errorMessage, "unit")?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSaving,
                onClick = {
                    if (name.isBlank() || unit.isBlank()) {
                        errorMessage = "Product name and unit are required"
                    } else {
                        onSave(name, unit, category?.id) { success, message -> if (!success) errorMessage = message }
                    }
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun PurchaseFormDialog(
    title: String,
    confirmLabel: String,
    state: PurchasesUiState,
    initial: Purchase?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onCreateProduct: () -> Unit,
    onCreateCategory: () -> Unit,
    pendingProductId: Long?,
    pendingCategoryId: Long?,
    onSave: (Long, Long, Long, String, String, Long, LocalDate, String?, String?, (Boolean, String?) -> Unit) -> Unit,
) {
    val products = state.products
    val accounts = state.accounts
    val categories = state.expenseCategories
    var selectedProduct by remember { mutableStateOf(products.firstOrNull { it.id == initial?.productId }) }
    var productText by remember { mutableStateOf(selectedProduct?.name.orEmpty()) }
    var selectedAccount by remember { mutableStateOf(accounts.firstOrNull()) }
    var selectedCategory by remember {
        mutableStateOf(categories.firstOrNull { it.id == (initial?.categoryId ?: selectedProduct?.categoryId) })
    }
    var categoryText by remember { mutableStateOf(selectedCategory?.name.orEmpty()) }
    var quantity by remember { mutableStateOf(initial?.quantity?.asDecimal()?.stripTrailingZeros()?.toPlainString().orEmpty()) }
    var unitPrice by remember { mutableStateOf(initial?.let { formatRupeesFromMinorUnits(it.unitPrice.minorUnits) }.orEmpty()) }
    var date by remember { mutableStateOf(initial?.purchaseDate ?: LocalDate.now()) }
    var merchant by remember { mutableStateOf(initial?.merchantName.orEmpty()) }
    var notes by remember { mutableStateOf(initial?.notes.orEmpty()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(pendingProductId, products) {
        pendingProductId?.let { id -> products.firstOrNull { it.id == id }?.let { selectedProduct = it; productText = it.name } }
    }
    androidx.compose.runtime.LaunchedEffect(pendingCategoryId, categories) {
        pendingCategoryId?.let { id -> categories.firstOrNull { it.id == id }?.let { selectedCategory = it; categoryText = it.name } }
    }
    val productResolved = selectedProduct?.name == productText && productText.isNotBlank()
    val categoryResolved = selectedCategory?.name == categoryText && categoryText.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (products.isEmpty()) {
                    Text("Add a product first.", color = MaterialTheme.colorScheme.error)
                }
                if (accounts.isEmpty()) {
                    Text("Add an account first so this purchase can count as an expense.", color = MaterialTheme.colorScheme.error)
                }
                if (categories.isEmpty()) {
                    Text("Add an expense category first.", color = MaterialTheme.colorScheme.error)
                }
                TypeaheadField(
                    value = productText,
                    onValueChange = { productText = it; selectedProduct = null },
                    label = "Product",
                    suggestions = products.map { it.name },
                    onSuggestionSelected = { name -> selectedProduct = products.first { it.name == name }; productText = name },
                    createLabel = "Create product \"$productText\"",
                    onCreate = onCreateProduct,
                    errorText = fieldErrorMessage(errorMessage, "product"),
                )
                SelectionDropdownField(
                    label = "Paid from account",
                    selectedText = selectedAccount?.name.orEmpty(),
                    placeholder = "Select account",
                    options = accounts,
                    optionLabel = { it.name },
                    isError = fieldErrorMessage(errorMessage, "account") != null,
                    supportingText = fieldErrorMessage(errorMessage, "account"),
                    onOptionSelected = { selectedAccount = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                TypeaheadField(
                    value = categoryText,
                    onValueChange = { categoryText = it; selectedCategory = null },
                    label = "Expense category",
                    suggestions = categories.map { it.name },
                    onSuggestionSelected = { name -> selectedCategory = categories.first { it.name == name }; categoryText = name },
                    createLabel = "Create category \"$categoryText\"",
                    onCreate = onCreateCategory,
                    errorText = fieldErrorMessage(errorMessage, "category"),
                )
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text("Quantity (in ${selectedProduct?.defaultUnit ?: "product unit"})") },
                    singleLine = true,
                    isError = fieldErrorMessage(errorMessage, "quantity") != null,
                    supportingText = { fieldErrorMessage(errorMessage, "quantity")?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = unitPrice,
                    onValueChange = { unitPrice = it },
                    label = { Text("Unit price (INR)") },
                    singleLine = true,
                    isError = fieldErrorMessage(errorMessage, "price") != null,
                    supportingText = { fieldErrorMessage(errorMessage, "price")?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                DatePickerField(
                    value = date,
                    onValueChange = { date = it },
                    label = "Date",
                    isError = fieldErrorMessage(errorMessage, "date") != null,
                    supportingText = { fieldErrorMessage(errorMessage, "date")?.let { Text(it) } },
                )
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("Merchant (optional)") },
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
                enabled = !isSaving && products.isNotEmpty() && productResolved && categoryResolved,
                onClick = {
                    val product = selectedProduct
                    val account = selectedAccount
                    val category = selectedCategory
                    val unitPriceMinor = parseRupeesToMinorUnits(unitPrice)
                    when {
                        !productResolved || product == null -> errorMessage = "Select a product from the list"
                        account == null -> errorMessage = "Select an account"
                        !categoryResolved || category == null -> errorMessage = "Select an expense category from the list"
                        quantity.toBigDecimalOrNull() == null || quantity.toBigDecimalOrNull()!! <= java.math.BigDecimal.ZERO ->
                            errorMessage = "Enter a valid positive quantity"
                        unitPriceMinor == null -> errorMessage = "Enter a valid positive unit price"
                        else -> {
                            onSave(
                                account.id,
                                category.id,
                                product.id,
                                quantity,
                                product.defaultUnit,
                                unitPriceMinor,
                                date,
                                merchant,
                                notes,
                            ) { success, message -> if (!success) errorMessage = message }
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

private fun String.toBigDecimalOrNull(): java.math.BigDecimal? = try {
    java.math.BigDecimal(trim())
} catch (e: NumberFormatException) {
    null
}
