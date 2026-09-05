package com.financialapp.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.financialapp.ui.accounts.AccountsRoute
import com.financialapp.ui.backup.BackupRoute
import com.financialapp.ui.categories.CategoriesRoute
import com.financialapp.ui.dashboard.DashboardRoute
import com.financialapp.ui.mutualfunds.MutualFundsRoute
import com.financialapp.ui.purchases.PurchasesRoute
import com.financialapp.ui.transactions.TransactionsRoute
import kotlinx.coroutines.launch

private enum class AppDestination(
    val route: String,
    val label: String,
    // Shown directly in the bottom bar for one-tap access; the rest are only reachable
    // through the navigation drawer to keep the bottom bar from being overcrowded.
    val inBottomBar: Boolean,
) {
    DASHBOARD("dashboard", "Dashboard", inBottomBar = true),
    TRANSACTIONS("transactions", "Transactions", inBottomBar = true),
    ACCOUNTS("accounts", "Accounts", inBottomBar = true),
    PURCHASES("purchases", "Purchases", inBottomBar = true),
    CATEGORIES("categories", "Categories", inBottomBar = false),
    MUTUAL_FUNDS("mutual_funds", "Mutual Funds", inBottomBar = false),
    BACKUP("backup", "Backup", inBottomBar = false),
}

private fun AppDestination.icon() = when (this) {
    AppDestination.DASHBOARD -> Icons.Outlined.Dashboard
    AppDestination.ACCOUNTS -> Icons.Outlined.AccountBalance
    AppDestination.TRANSACTIONS -> Icons.Outlined.ReceiptLong
    AppDestination.CATEGORIES -> Icons.Outlined.Category
    AppDestination.PURCHASES -> Icons.Outlined.ShoppingCart
    AppDestination.MUTUAL_FUNDS -> Icons.Outlined.ShowChart
    AppDestination.BACKUP -> Icons.Outlined.Backup
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    fun navigateTo(destination: AppDestination) {
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        coroutineScope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                AppDestination.entries.forEach { destination ->
                    NavigationDrawerItem(
                        label = { Text(destination.label) },
                        icon = { Icon(imageVector = destination.icon(), contentDescription = null) },
                        selected = currentRoute == destination.route,
                        onClick = { navigateTo(destination) },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(AppDestination.entries.firstOrNull { it.route == currentRoute }?.label ?: "")
                    },
                    navigationIcon = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(imageVector = Icons.Filled.Menu, contentDescription = "Open navigation menu")
                        }
                    },
                )
            },
            bottomBar = {
                NavigationBar {
                    AppDestination.entries.filter { it.inBottomBar }.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = { navigateTo(destination) },
                            icon = {
                                Icon(
                                    imageVector = destination.icon(),
                                    contentDescription = destination.label,
                                )
                            },
                            label = { Text(destination.label) },
                        )
                    }
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = AppDestination.DASHBOARD.route,
                modifier = Modifier.padding(innerPadding),
            ) {
                composable(AppDestination.DASHBOARD.route) { DashboardRoute() }
                composable(AppDestination.ACCOUNTS.route) { AccountsRoute() }
                composable(AppDestination.TRANSACTIONS.route) { TransactionsRoute() }
                composable(AppDestination.CATEGORIES.route) { CategoriesRoute() }
                composable(AppDestination.PURCHASES.route) { PurchasesRoute() }
                composable(AppDestination.MUTUAL_FUNDS.route) { MutualFundsRoute() }
                composable(AppDestination.BACKUP.route) { BackupRoute() }
            }
        }
    }
}