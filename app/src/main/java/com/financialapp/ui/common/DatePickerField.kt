package com.financialapp.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    value: LocalDate,
    onValueChange: (LocalDate) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: (@Composable () -> Unit)? = null,
) {
    var showDatePicker by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value.toIsoString(),
        onValueChange = {},
        readOnly = true,
        singleLine = true,
        label = { Text(label) },
        isError = isError,
        supportingText = supportingText,
        modifier = modifier
            .fillMaxWidth()
            .clickable { showDatePicker = true },
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = value.toUtcStartOfDayMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selected ->
                            onValueChange(selected.toLocalDateFromUtcMillis())
                        }
                        showDatePicker = false
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private fun LocalDate.toUtcStartOfDayMillis(): Long =
    atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

private fun Long.toLocalDateFromUtcMillis(): LocalDate =
    Instant.ofEpochMilli(this).atOffset(ZoneOffset.UTC).toLocalDate()
