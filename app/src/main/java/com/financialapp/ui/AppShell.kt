package com.financialapp.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.financialapp.ui.backup.BackupRoute
import com.financialapp.ui.dashboard.DashboardRoute

private enum class AppDestination(
    val route: String,
    val label: String,
) {
    DASHBOARD("dashboard", "Dashboard"),
    BACKUP("backup", "Backup"),
}

@Composable
fun AppShell() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                AppDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = when (destination) {
                                    AppDestination.DASHBOARD -> Icons.Outlined.Dashboard
                                    AppDestination.BACKUP -> Icons.Outlined.Backup
                                },
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
            composable(AppDestination.BACKUP.route) { BackupRoute() }
        }
    }
}