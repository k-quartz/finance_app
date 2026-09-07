package com.financialapp.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction

@Composable
fun <T> SelectionDropdownField(
    label: String,
    selectedText: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Select",
    isError: Boolean = false,
    supportingText: String? = null,
    forceStandardDropdown: Boolean = false,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val searchable = !forceStandardDropdown && options.size > DROPDOWN_MAX_VISIBLE_ITEMS
    val filteredOptions =
        if (searchable && searchQuery.isNotBlank()) {
            options.filter { optionLabel(it).contains(searchQuery, ignoreCase = true) }
        } else {
            options
        }

    LaunchedEffect(expanded) {
        if (!expanded) {
            searchQuery = ""
        }
    }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = selectedText,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            singleLine = true,
            enabled = enabled,
            isError = isError,
            supportingText = { supportingText?.let { Text(it) } },
            placeholder = { Text(placeholder) },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    enabled = enabled,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { expanded = true },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (searchable) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search...") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (filteredOptions.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No matches") },
                    enabled = false,
                    onClick = {},
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = DropdownMenuMaxHeight)) {
                    items(filteredOptions) { option ->
                        DropdownMenuItem(
                            text = { Text(optionLabel(option)) },
                            onClick = {
                                onOptionSelected(option)
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
