package com.freqtradeui.android.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.freqtradeui.android.FreqtradeApplication
import com.freqtradeui.android.data.models.ConnectionProfile
import com.freqtradeui.android.data.models.TunnelStatus
import com.freqtradeui.android.ui.components.TunnelStatusChip
import com.freqtradeui.android.ui.dashboard.DashboardScreen
import com.freqtradeui.android.ui.dashboard.DashboardViewModel
import com.freqtradeui.android.ui.profiles.ProfileEditorScreen
import com.freqtradeui.android.ui.profiles.ProfilesScreen
import com.freqtradeui.android.ui.profiles.ProfilesViewModel
import kotlinx.coroutines.launch

private const val ROUTE_PROFILES = "profiles"
private const val ROUTE_PROFILE_EDITOR = "profile_editor?profileId={profileId}"
private const val ROUTE_DASHBOARD = "dashboard"
private const val ARG_PROFILE_ID = "profileId"

/**
 * Navegação principal e menu lateral da área autenticada.
 *
 * Na inicialização, as telas ficam atrás de um estado de carregamento até que
 * a conexão anterior seja validada por uma chamada autenticada à API ou a
 * restauração falhe e seja necessária a seleção de perfil.
 */
@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val application = context.applicationContext as FreqtradeApplication
    val viewModelFactory = remember(application) { application.viewModelFactory }

    // Mantém os mesmos estados de perfil, túnel e API entre os destinos.
    val profilesViewModel: ProfilesViewModel = viewModel(factory = viewModelFactory)
    val dashboardViewModel: DashboardViewModel = viewModel(factory = viewModelFactory)
    val profilesState by profilesViewModel.uiState.collectAsStateWithLifecycle()
    val drawerState = androidx.compose.material3.rememberDrawerState(
        initialValue = androidx.compose.material3.DrawerValue.Closed,
    )
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    var authenticatedArea by rememberSaveable { mutableStateOf(false) }
    var startupGatePassed by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(profilesViewModel) {
        profilesViewModel.restorePreviousSession()
    }

    LaunchedEffect(profilesState.startupComplete, profilesState.restoredProfileId) {
        if (!profilesState.startupComplete) return@LaunchedEffect

        val targetRoute = if (profilesState.restoredProfileId != null) {
            authenticatedArea = true
            ROUTE_DASHBOARD
        } else {
            ROUTE_PROFILES
        }
        if (navController.currentDestination?.route != targetRoute) {
            navController.navigate(targetRoute) {
                popUpTo(ROUTE_PROFILES) { inclusive = false }
                launchSingleTop = true
            }
        }
        startupGatePassed = true
    }

    LaunchedEffect(currentRoute) {
        if (currentRoute == ROUTE_DASHBOARD && profilesState.startupComplete) {
            authenticatedArea = true
        }
    }

    fun navigateToProfiles(clearBackStack: Boolean = false) {
        navController.navigate(ROUTE_PROFILES) {
            if (clearBackStack) {
                popUpTo(ROUTE_PROFILES) { inclusive = false }
            }
            launchSingleTop = true
        }
    }

    fun openDrawer() {
        scope.launch { drawerState.open() }
    }

    fun closeDrawer(action: () -> Unit) {
        scope.launch {
            drawerState.close()
            action()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = authenticatedArea &&
            currentRoute in setOf(ROUTE_DASHBOARD, ROUTE_PROFILES),
        // O conteúdo do drawer nunca pode ficar vazio enquanto o
        // ModalNavigationDrawer estiver composto: ele deriva a âncora "fechado"
        // da largura medida deste slot. Esvaziá-lo colapsava as duas âncoras na
        // mesma posição e o estado era agarrado em Open, deixando o scrim
        // composto — invisível, mas consumindo todo toque da tela, e sem como
        // fechá-lo porque gesturesEnabled estava em false. Mantendo o conteúdo
        // sempre composto, as âncoras ficam estáveis e o drawer nunca abre
        // sozinho. Fora da área autenticada ele continua inacessível:
        // gesturesEnabled e o botão de menu são desligados.
        drawerContent = {
            AppDrawer(
                currentRoute = currentRoute,
                state = profilesState,
                onDashboard = {
                    closeDrawer {
                        navController.navigate(ROUTE_DASHBOARD) {
                            launchSingleTop = true
                        }
                    }
                },
                onProfiles = {
                    closeDrawer { navigateToProfiles() }
                },
                onProfileSelected = { profile ->
                    closeDrawer {
                        selectProfile(
                            profile = profile,
                            state = profilesState,
                            viewModel = profilesViewModel,
                            navigateToProfiles = { navigateToProfiles() },
                            navigateToDashboard = {
                                navController.navigate(ROUTE_DASHBOARD) {
                                    launchSingleTop = true
                                }
                            },
                        )
                    }
                },
                onDisconnect = {
                    closeDrawer {
                        profilesViewModel.disconnect()
                        authenticatedArea = false
                        navigateToProfiles(clearBackStack = true)
                    }
                },
            )
        },
    ) {
        NavHost(
            navController = navController,
            startDestination = ROUTE_PROFILES,
            modifier = modifier,
        ) {
            composable(ROUTE_PROFILES) {
                ProfilesScreen(
                    state = profilesState.copy(switchTarget = null),
                    onAddProfile = {
                        navController.navigate("profile_editor?$ARG_PROFILE_ID=")
                    },
                    onEditProfile = { profile ->
                        navController.navigate("profile_editor?$ARG_PROFILE_ID=${profile.id}")
                    },
                    onOpenPanel = {
                        navController.navigate(ROUTE_DASHBOARD) {
                            launchSingleTop = true
                        }
                    },
                    onConnect = profilesViewModel::connect,
                    onDisconnect = profilesViewModel::disconnect,
                    onRequestDelete = profilesViewModel::requestDelete,
                    onConfirmDelete = profilesViewModel::confirmDelete,
                    onCancelDelete = profilesViewModel::cancelDelete,
                    onSwitchConfirm = { profilesViewModel.confirmSwitch() },
                    onSwitchCancel = profilesViewModel::cancelSwitch,
                    onApproveHostKey = profilesViewModel::approveHostKey,
                    onRejectHostKey = profilesViewModel::rejectHostKey,
                    showMenu = authenticatedArea,
                    onMenu = ::openDrawer,
                )
            }
            composable(ROUTE_DASHBOARD) {
                if (startupGatePassed) {
                    DashboardScreen(
                        viewModel = dashboardViewModel,
                        onMenu = ::openDrawer,
                    )
                } else {
                    StartupLoadingScreen()
                }
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
                val tunnelActiveForProfile = profileId != null &&
                    profilesState.snapshot.refersTo(profileId) &&
                    (profilesState.snapshot.status == TunnelStatus.Connecting ||
                        profilesState.snapshot.status.isActive)
                ProfileEditorScreen(
                    profileId = profileId,
                    viewModel = profilesViewModel,
                    isConnected = tunnelActiveForProfile,
                    onDone = { navController.popBackStack() },
                    onCancel = { navController.popBackStack() },
                )
            }
        }

        if (!startupGatePassed || !profilesState.startupComplete) {
            StartupLoadingScreen()
        }
    }

    val switchTarget = profilesState.switchTarget
    if (switchTarget != null) {
        AlertDialog(
            onDismissRequest = profilesViewModel::cancelSwitch,
            title = { Text("Trocar conexão?") },
            text = {
                Text(
                    "O túnel atual será encerrado e a conexão passará para " +
                        "\"${switchTarget.name}\".",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        profilesViewModel.confirmSwitch()
                        navigateToProfiles()
                    },
                ) {
                    Text("Trocar")
                }
            },
            dismissButton = {
                TextButton(onClick = profilesViewModel::cancelSwitch) {
                    Text("Manter atual")
                }
            },
        )
    }
}

@Composable
private fun AppDrawer(
    currentRoute: String?,
    state: com.freqtradeui.android.ui.profiles.ProfilesUiState,
    onDashboard: () -> Unit,
    onProfiles: () -> Unit,
    onProfileSelected: (ConnectionProfile) -> Unit,
    onDisconnect: () -> Unit,
) {
    ModalDrawerSheet {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "FreqtradeUI",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = state.activeProfile?.name ?: "Nenhum perfil ativo",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.snapshot.profileId != null) {
                TunnelStatusChip(state.snapshot.status)
            }
        }

        NavigationDrawerItem(
            label = { Text("Painel") },
            icon = { Icon(Icons.Filled.Dashboard, contentDescription = null) },
            selected = currentRoute == ROUTE_DASHBOARD,
            onClick = onDashboard,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        NavigationDrawerItem(
            label = { Text("Perfis e conexões") },
            icon = { Icon(Icons.Filled.People, contentDescription = null) },
            selected = currentRoute == ROUTE_PROFILES,
            onClick = onProfiles,
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        if (state.profiles.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "PERFIS",
                modifier = Modifier.padding(start = 28.dp, top = 8.dp, bottom = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.profiles.forEach { profile ->
                NavigationDrawerItem(
                    label = {
                        Column {
                            Text(profile.name, maxLines = 1)
                            Text(
                                profile.destination,
                                maxLines = 1,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    selected = state.snapshot.profileId == profile.id,
                    onClick = { onProfileSelected(profile) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }

        Spacer(Modifier.weight(1f))
        if (state.hasActiveTunnel) {
            NavigationDrawerItem(
                label = { Text("Desconectar") },
                icon = { Icon(Icons.Filled.PowerSettingsNew, contentDescription = null) },
                selected = false,
                onClick = onDisconnect,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            )
        }
    }
}

private fun selectProfile(
    profile: ConnectionProfile,
    state: com.freqtradeui.android.ui.profiles.ProfilesUiState,
    viewModel: ProfilesViewModel,
    navigateToProfiles: () -> Unit,
    navigateToDashboard: () -> Unit,
) {
    when {
        state.snapshot.status == TunnelStatus.Connected &&
            state.snapshot.profileId == profile.id -> navigateToDashboard()
        state.hasActiveTunnel -> {
            viewModel.onProfileSelected(profile.id)
            navigateToProfiles()
        }
        else -> {
            viewModel.connect(profile.id)
            navigateToProfiles()
        }
    }
}

@Composable
private fun StartupLoadingScreen() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Verificando conexão anterior...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
