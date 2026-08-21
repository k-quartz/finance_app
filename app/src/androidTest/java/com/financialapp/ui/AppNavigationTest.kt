package com.financialapp.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class AppNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<DashboardActivity>()

    @Test
    fun launcherProvidesReachableBackupScreen() {
        composeRule.onNodeWithText("Financial Dashboard").assertIsDisplayed()
        composeRule.onNodeWithText("Backup").performClick()
        composeRule.onNodeWithText("Backup and restore").assertIsDisplayed()
        composeRule.onNodeWithText("Create backup").assertIsDisplayed()
        composeRule.onNodeWithText("Restore backup").assertIsDisplayed()
    }
}