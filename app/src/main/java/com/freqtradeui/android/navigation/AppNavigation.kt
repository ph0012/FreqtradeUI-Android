package com.freqtradeui.android.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.freqtradeui.android.FreqtradeApplication
import com.freqtradeui.android.ui.dashboard.DashboardScreen
import com.freqtradeui.android.ui.settings.SettingsScreen
import com.freqtradeui.android.ui.trades.TradesScreen

/**
 * Rotas de navegação de nível superior.
 */
sealed class Destination(val route: String, val label: String, val icon: ImageVector) {
    data object Dashboard : Destination("dashboard", "Dashboard", Icons.Filled.Dashboard)
    data object Trades : Destination("trades", "Trades", Icons.AutoMirrored.Filled.ShowChart)
    data object Settings : Destination("settings", "Configurações", Icons.Filled.Settings)
}

private val topDestinations = listOf(
    Destination.Dashboard,
    Destination.Trades,
    Destination.Settings,
)

/**
 * Esqueleto de navegação do aplicativo com barra inferior entre Dashboard,
 * Trades e Configurações.
 */
@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // Factory compartilhada pela aplicação: injeta o gerenciador de túnel e o
    // armazenamento seguro nas telas que precisam deles.
    val context = LocalContext.current
    val application = context.applicationContext as FreqtradeApplication
    val viewModelFactory = remember(application) { application.viewModelFactory }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                topDestinations.forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any {
                        it.route == destination.route
                    } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Dashboard.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Destination.Dashboard.route) {
                DashboardScreen(
                    onConfigureConnection = {
                        navController.navigate(Destination.Settings.route)
                    },
                    viewModel = viewModel(factory = viewModelFactory),
                )
            }
            composable(Destination.Trades.route) {
                TradesScreen()
            }
            composable(Destination.Settings.route) {
                SettingsScreen(viewModel = viewModel(factory = viewModelFactory))
            }
        }
    }
}
