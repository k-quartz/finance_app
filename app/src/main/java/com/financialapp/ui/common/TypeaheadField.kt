package com.financialapp.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
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
    val expanded = suggestions.isNotEmpty() && value.isNotBlank()
    val focusManager = LocalFocusManager.current
    Column(modifier = modifier) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = {}) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { androidx.compose.material3.Text(label) },
                singleLine = true,
                isError = errorText != null,
                supportingText = { errorText?.let { androidx.compose.material3.Text(it) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { focusManager.clearFocus() }) {
                suggestions.forEach { suggestion ->
                    DropdownMenuItem(
                        text = { androidx.compose.material3.Text(suggestion) },
                        onClick = { onSuggestionSelected(suggestion); focusManager.clearFocus() },
                    )
                }
                if (createLabel != null && onCreate != null && suggestions.none { it.equals(value, ignoreCase = true) }) {
                    DropdownMenuItem(
                        text = { androidx.compose.material3.Text(createLabel) },
                        onClick = { onCreate(); focusManager.clearFocus() },
                    )
                }
            }
        }
    }
}
