package com.financialapp.ui.backup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class BackupScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun backupActionsAreExposed() {
        var createClicks = 0
        var restoreClicks = 0
        composeRule.setContent {
            BackupScreen(
                state = BackupUiState(),
                onCreateBackup = { createClicks++ },
                onRestoreBackup = { restoreClicks++ },
            )
        }

        composeRule.onNodeWithText("Backup and restore").assertIsDisplayed()
        composeRule.onNodeWithText("Create backup").performClick()
        composeRule.onNodeWithText("Restore backup").performClick()

        assert(createClicks == 1)
        assert(restoreClicks == 1)
    }
}