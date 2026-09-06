package com.financialapp.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
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
    val canCreate =
        createSuggestionLabel != null &&
            createAction != null &&
            value.isNotBlank() &&
            suggestions.none { it.equals(value, ignoreCase = true) }
    val hasMenuContent = suggestions.isNotEmpty() || canCreate
    val isMenuExpanded = expanded && value.isNotBlank() && hasMenuContent

    LaunchedEffect(value, hasMenuContent) {
        if (value.isBlank() || !hasMenuContent) {
            expanded = false
        }
    }

    Column(modifier = modifier) {
        Box {
            OutlinedTextField(
                value = value,
                onValueChange = {
                    onValueChange(it)
                    expanded = it.isNotBlank()
                },
                label = { androidx.compose.material3.Text(label) },
                singleLine = true,
                isError = errorText != null,
                supportingText = { errorText?.let { androidx.compose.material3.Text(it) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownMenu(
                expanded = isMenuExpanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = DropdownMenuMaxHeight),
            ) {
                suggestions.forEach { suggestion ->
                    DropdownMenuItem(
                        text = { androidx.compose.material3.Text(suggestion) },
                        onClick = {
                            onSuggestionSelected(suggestion)
                            expanded = false
                            focusManager.clearFocus()
                        },
                    )
                }
                if (canCreate) {
                    DropdownMenuItem(
                        text = { androidx.compose.material3.Text(createSuggestionLabel ?: "") },
                        onClick = {
                            createAction?.invoke()
                            expanded = false
                            focusManager.clearFocus()
                        },
                    )
                }
            }
        }
    }
}
