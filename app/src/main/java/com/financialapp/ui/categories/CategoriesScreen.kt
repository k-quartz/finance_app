package com.financialapp.ui.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
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
import com.financialapp.domain.model.CategoryType
import com.financialapp.ui.common.DropdownMenuMaxHeight
import com.financialapp.ui.common.fieldErrorMessage
import com.financialapp.ui.common.TypeaheadField

@Composable
fun CategoriesRoute(
    viewModel: CategoriesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Re-query on resume so categories created elsewhere are reflected.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    CategoriesScreen(
        state = state,
        onCreateCategory = viewModel::createCategory,
        onUpdateCategory = viewModel::updateCategory,
        onDeleteCategory = viewModel::deleteCategory,
    )
}

@Composable
fun CategoriesScreen(
    state: CategoriesUiState,
    onCreateCategory: (String, CategoryType, Long?, (Boolean, Long?, String?) -> Unit) -> Unit,
    onUpdateCategory: (Long, String, CategoryType, Long?, (Boolean, String?) -> Unit) -> Unit,
    onDeleteCategory: (Long, (Boolean, String?) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<FlatCategory?>(null) }
    var deleting by remember { mutableStateOf<FlatCategory?>(null) }
    var showParentDialog by remember { mutableStateOf(false) }
    var pendingParentId by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add category")
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
            Text("Categories", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Income, expense, and transfer classification hierarchy.",
                style = MaterialTheme.typography.bodyMedium,
            )
            state.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp))
            } else if (state.flat.isEmpty()) {
                Text("No categories yet. Tap + to create one.", modifier = Modifier.padding(top = 24.dp))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(state.flat, key = { it.category.id }) { flatCategory ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("  ".repeat(flatCategory.depth))
                                Column(modifier = Modifier.weight(1f).clickable { editing = flatCategory }) {
                                    Text(flatCategory.category.name, style = MaterialTheme.typography.titleSmall)
                                    Text("${flatCategory.category.type.name} · tap to edit", style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(onClick = { deleting = flatCategory }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete category")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        CategoryFormDialog(
            title = "New category",
            confirmLabel = "Create",
            candidateParents = state.flat,
            initial = null,
            isSaving = state.isSaving,
            onDismiss = { showAddDialog = false },
            onCreateParent = { showParentDialog = true },
            pendingParentId = pendingParentId,
            onSave = { name, type, parentId, done ->
                onCreateCategory(name, type, parentId) { success, _, message ->
                    if (success) showAddDialog = false
                    done(success, message)
                }
            },
        )
    }
    if (showParentDialog) {
        CategoryFormDialog("New parent category", "Create", emptyList(), null, state.isSaving, { showParentDialog = false }, onCreateParent = null, pendingParentId = null) { name, type, parentId, done ->
            onCreateCategory(name, type, parentId) { success, id, message ->
                if (success) { pendingParentId = id; showParentDialog = false }
                done(success, message)
            }
        }
    }
    editing?.let { current ->
        CategoryFormDialog(
            title = "Edit category",
            confirmLabel = "Save",
            candidateParents = state.flat.filter { it.category.id != current.category.id },
            initial = current,
            isSaving = state.isSaving,
            onDismiss = { editing = null },
            onSave = { name, type, parentId, done ->
                onUpdateCategory(current.category.id, name, type, parentId) { success, message ->
                    if (success) editing = null
                    done(success, message)
                }
            },
        )
    }
    deleting?.let { current ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete category") },
            text = { Text("Delete ${current.category.name}? Historical references stay valid.") },
            confirmButton = {
                TextButton(
                    enabled = !state.isSaving,
                    onClick = { onDeleteCategory(current.category.id) { success, _ -> if (success) deleting = null } },
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CategoryFormDialog(
    title: String,
    confirmLabel: String,
    candidateParents: List<FlatCategory>,
    initial: FlatCategory?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onCreateParent: (() -> Unit)? = null,
    pendingParentId: Long? = null,
    onSave: (String, CategoryType, Long?, (Boolean, String?) -> Unit) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.category?.name.orEmpty()) }
    var typeExpanded by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf(initial?.category?.type ?: CategoryType.EXPENSE) }
    var selectedParent by remember {
        mutableStateOf(candidateParents.firstOrNull { it.category.id == initial?.category?.parentId })
    }
    var parentText by remember { mutableStateOf(selectedParent?.category?.name.orEmpty()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(pendingParentId, candidateParents) {
        pendingParentId?.let { id -> candidateParents.firstOrNull { it.category.id == id }?.let { selectedParent = it; parentText = it.category.name } }
    }

    val eligibleParents = candidateParents.filter { it.category.type == selectedType }
    val parentResolved = parentText.isBlank() || selectedParent?.category?.name == parentText

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Category name") },
                    singleLine = true,
                    isError = fieldErrorMessage(errorMessage, "name") != null,
                    supportingText = { fieldErrorMessage(errorMessage, "name")?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                Box {
                    OutlinedTextField(
                        value = selectedType.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category type") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false },
                        modifier = Modifier.heightIn(max = DropdownMenuMaxHeight),
                    ) {
                        CategoryType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name) },
                                onClick = {
                                    selectedType = type
                                    selectedParent = null
                                    typeExpanded = false
                                },
                            )
                        }
                    }
                }
                TypeaheadField(
                    value = parentText,
                    onValueChange = { parentText = it; selectedParent = null },
                    label = "Parent category (optional)",
                    suggestions = eligibleParents.filter { it.category.name.contains(parentText, true) }.map { it.category.name },
                    onSuggestionSelected = { name -> selectedParent = eligibleParents.first { it.category.name == name }; parentText = name },
                    createLabel = "Create parent category \"$parentText\"",
                    onCreate = onCreateParent,
                )
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSaving && parentResolved,
                onClick = {
                    if (!parentResolved) {
                        errorMessage = "Select a parent category from the list or clear the field"
                    } else if (name.isBlank()) {
                        errorMessage = "Category name is required"
                    } else {
                        onSave(name, selectedType, selectedParent?.category?.id) { success, message ->
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
