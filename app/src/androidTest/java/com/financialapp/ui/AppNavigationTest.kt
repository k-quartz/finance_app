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

    @Test
    fun launcherProvidesReachableWorkflowScreen() {
        composeRule.onNodeWithText("Financial Dashboard").assertIsDisplayed()
        composeRule.onNodeWithText("Manage").performClick()
        composeRule.onNodeWithText("Manage finances").assertIsDisplayed()
        composeRule.onNodeWithText("Accounts").assertIsDisplayed()
        composeRule.onNodeWithText("Transactions").assertIsDisplayed()
    }

    @Test
    fun backFromWorkflowScreenReturnsToDashboard() {
        composeRule.onNodeWithText("Manage").performClick()
        composeRule.onNodeWithText("Manage finances").assertIsDisplayed()
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.onNodeWithText("Financial Dashboard").assertIsDisplayed()
    }

    @Test
    fun workflowScreenExposesLifecycleCorrectionAndHistorySections() {
        composeRule.onNodeWithText("Manage").performClick()
        composeRule.onNodeWithText("Accounts").performClick()
        composeRule.onNodeWithText("New account").assertIsDisplayed()
        composeRule.onNodeWithText("Categories").performClick()
        composeRule.onNodeWithText("New category or subcategory").assertIsDisplayed()
        composeRule.onNodeWithText("Transactions").performClick()
        composeRule.onNodeWithText("Correct income or expense").assertIsDisplayed()
        composeRule.onNodeWithText("Purchases").performClick()
        composeRule.onNodeWithText("Purchase history").assertIsDisplayed()
        composeRule.onNodeWithText("Investments").performClick()
        composeRule.onNodeWithText("NAV history").assertIsDisplayed()
        composeRule.onNodeWithText("Prices").performClick()
        composeRule.onNodeWithText("Price history").assertIsDisplayed()
        composeRule.onNodeWithText("Analytics").performClick()
        composeRule.onNodeWithText("Period and recurring analysis").assertIsDisplayed()
    }
}