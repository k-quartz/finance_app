package com.financialapp.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions

@Composable
fun TypeaheadField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suggestions: List<String>,
    onSuggestionSelected: (String) -> Unit,
    createLabel: String? = null,
    onCreate: (() -> Unit)? = null,
    errorText: String? = null,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val createAction = onCreate
    val createSuggestionLabel = createLabel
    val searchable = suggestions.size > DROPDOWN_MAX_VISIBLE_ITEMS
    val filteredSuggestions = suggestions.filter { it.contains(value, ignoreCase = true) }
    val canCreate =
        createSuggestionLabel != null &&
            createAction != null &&
            value.isNotBlank() &&
            suggestions.none { it.equals(value, ignoreCase = true) }
    val hasMenuContent = filteredSuggestions.isNotEmpty() || canCreate
    val isMenuExpanded = expanded && (searchable || value.isNotBlank()) && hasMenuContent

    LaunchedEffect(value, hasMenuContent) {
        if ((!searchable && value.isBlank()) || !hasMenuContent) {
            expanded = false
        }
    }

    Column(modifier = modifier) {
        Box {
            OutlinedTextField(
                value = value,
                onValueChange = {
                    onValueChange(it)
                    expanded = if (searchable) true else it.isNotBlank()
                },
                readOnly = searchable,
                label = { androidx.compose.material3.Text(label) },
                singleLine = true,
                isError = errorText != null,
                supportingText = { errorText?.let { androidx.compose.material3.Text(it) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (searchable) {
                Box(
                    modifier =
                        Modifier.matchParentSize().clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { expanded = true },
                )
            }
            DropdownMenu(
                expanded = isMenuExpanded,
                onDismissRequest = { expanded = false },
            ) {
                if (searchable) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = {
                            onValueChange(it)
                            expanded = true
                        },
                        label = { androidx.compose.material3.Text("Search...") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                LazyColumn(modifier = Modifier.heightIn(max = DropdownMenuMaxHeight)) {
                    items(filteredSuggestions) { suggestion ->
                        DropdownMenuItem(
                            text = { androidx.compose.material3.Text(suggestion) },
                            onClick = {
                                onSuggestionSelected(suggestion)
                                expanded = false
                                focusManager.clearFocus(force = true)
                            },
                        )
                    }
                    if (canCreate) {
                        item {
                            DropdownMenuItem(
                                text = { androidx.compose.material3.Text(createSuggestionLabel ?: "") },
                                onClick = {
                                    createAction?.invoke()
                                    expanded = false
                                    focusManager.clearFocus(force = true)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
