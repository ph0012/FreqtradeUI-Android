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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.freqtradeui.android.FreqtradeApplication
import com.freqtradeui.android.data.models.TunnelStatus
import com.freqtradeui.android.ui.dashboard.DashboardScreen
import com.freqtradeui.android.ui.dashboard.DashboardViewModel
import com.freqtradeui.android.ui.profiles.ProfileEditorScreen
import com.freqtradeui.android.ui.profiles.ProfilesScreen
import com.freqtradeui.android.ui.profiles.ProfilesViewModel
import com.freqtradeui.android.ui.trades.TradesScreen

/**
 * Rotas de navegação de nível superior.
 */
sealed class Destination(val route: String, val label: String, val icon: ImageVector) {
    data object Profiles : Destination("profiles", "Conexões", Icons.Filled.Dashboard)
    data object Trades : Destination("trades", "Trades", Icons.AutoMirrored.Filled.ShowChart)
}

/** Rota do editor de perfis. */
private const val ROUTE_PROFILE_EDITOR = "profile_editor?profileId={profileId}"
private const val ARG_PROFILE_ID = "profileId"

/** Rota do painel do perfil conectado. */
private const val ROUTE_DASHBOARD = "dashboard"

private val topDestinations = listOf(
    Destination.Profiles,
    Destination.Trades,
)

/**
 * Esqueleto de navegação do aplicativo com barra inferior entre Conexões e
 * Trades.
 *
 * A tela de conexões é a entrada do app: sem perfis cadastrados, ela apresenta
 * a mensagem de boas-vindas em vez de um dashboard vazio.
 */
@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // Factory compartilhada pela aplicação: injeta o repositório de perfis e o
    // gerenciador do túnel nas telas.
    val context = LocalContext.current
    val application = context.applicationContext as FreqtradeApplication
    val viewModelFactory = remember(application) { application.viewModelFactory }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            // Barra inferior só nas telas de topo: painel e editor têm suas
            // próprias barras superiores com retorno explícito, então a aba
            // "Conexões" não fica lá como caminho ambíguo de volta.
            val route = currentDestination?.route
            val onTopLevel = topDestinations.any { it.route == route }
            if (onTopLevel) {
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
                        icon = {
                            Icon(destination.icon, contentDescription = destination.label)
                        },
                        label = { Text(destination.label) },
                    )
                }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Profiles.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Destination.Profiles.route) {
                val vm: ProfilesViewModel = viewModel(factory = viewModelFactory)
                val state by vm.uiState.collectAsStateWithLifecycle()
                ProfilesScreen(
                    state = state,
                    onAddProfile = {
                        navController.navigate("profile_editor?$ARG_PROFILE_ID=")
                    },
                    onEditProfile = { profile ->
                        navController.navigate("profile_editor?$ARG_PROFILE_ID=${profile.id}")
                    },
                    onOpenPanel = { navController.navigate(ROUTE_DASHBOARD) },
                    onConnect = vm::connect,
                    onDisconnect = vm::disconnect,
                    onRequestDelete = vm::requestDelete,
                    onConfirmDelete = vm::confirmDelete,
                    onCancelDelete = vm::cancelDelete,
                    onSwitchConfirm = { vm.confirmSwitch() },
                    onSwitchCancel = vm::cancelSwitch,
                    onApproveHostKey = vm::approveHostKey,
                    onRejectHostKey = vm::rejectHostKey,
                )
            }
            composable(Destination.Trades.route) {
                TradesScreen()
            }
            // Painel do perfil conectado: só é alcançado pelo card conectado.
            composable(ROUTE_DASHBOARD) {
                val vm: DashboardViewModel = viewModel(factory = viewModelFactory)
                DashboardScreen(
                    onConfigureConnection = {
                        navController.navigate(Destination.Profiles.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    viewModel = vm,
                    // Volta pela pilha: a lista de perfis está logo abaixo.
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = ROUTE_PROFILE_EDITOR,
                arguments = listOf(
                    navArgument(ARG_PROFILE_ID) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                val profileId = entry.arguments
                    ?.getString(ARG_PROFILE_ID)
                    ?.takeIf { it.isNotBlank() }
                val vm: ProfilesViewModel = viewModel(factory = viewModelFactory)
                val state by vm.uiState.collectAsStateWithLifecycle()
                // Enquanto o perfil tiver túnel em andamento, o formulário fica
                // bloqueado: alterações silenciosas derrubariam a conexão.
                val tunnelActiveForProfile = profileId != null &&
                    state.snapshot.refersTo(profileId) &&
                    (state.snapshot.status == TunnelStatus.Connecting ||
                        state.snapshot.status.isActive)
                ProfileEditorScreen(
                    profileId = profileId,
                    viewModel = vm,
                    isConnected = tunnelActiveForProfile,
                    onDone = { navController.popBackStack() },
                    onCancel = { navController.popBackStack() },
                )
            }
        }
    }
}

