package com.financialapp.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@Composable
fun BackupRoute(
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingBackup by remember { mutableStateOf<String?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val content = pendingBackup
        pendingBackup = null
        if (uri == null || content == null) return@rememberLauncherForActivityResult
        scope.launch {
            val saved = runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    writer.write(content)
                } ?: error("Unable to open backup destination")
            }.isSuccess
            if (saved) viewModel.reportBackupSaved() else viewModel.reportBackupSaveFailed()
        }
    }
    val openLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val json = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Unable to read backup")
            }.getOrNull()
            if (json == null) viewModel.reportBackupReadFailed() else viewModel.restoreBackup(json)
        }
    }

    BackupScreen(
        state = state,
        onCreateBackup = {
            scope.launch {
                viewModel.createBackup()?.let { content ->
                    pendingBackup = content
                    saveLauncher.launch("financial-backup.json")
                }
            }
        },
        onRestoreBackup = { openLauncher.launch(arrayOf("application/json", "text/json")) },
    )
}

@Composable
fun BackupScreen(
    state: BackupUiState,
    onCreateBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Backup and restore", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Export the complete local dataset to JSON or restore a compatible backup.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(
            onClick = onCreateBackup,
            enabled = !state.isBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Create backup")
        }
        OutlinedButton(
            onClick = onRestoreBackup,
            enabled = !state.isBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Restore backup")
        }
        if (state.isBusy) CircularProgressIndicator()
        state.message?.let { message ->
            Text(
                message,
                color = if (state.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
    }
}