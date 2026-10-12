package com.freqtradeui.android.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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

/**
 * Destino de navegação de topo.
 *
 * Hoje só Conexões; `label` e `icon` ficam prontos para uma futura barra
 * inferior (Trades volta quando o painel REST do Freqtrade existir).
 */
sealed class Destination(val route: String, val label: String, val icon: ImageVector) {
    data object Profiles : Destination("profiles", "Conexões", Icons.Filled.Dashboard)
}

/** Rota do editor de perfis. */
private const val ROUTE_PROFILE_EDITOR = "profile_editor?profileId={profileId}"
private const val ARG_PROFILE_ID = "profileId"

/** Rota do painel do perfil conectado. */
private const val ROUTE_DASHBOARD = "dashboard"

/**
 * Esqueleto de navegação do aplicativo.
 *
 * Sem barra inferior por enquanto: há um único destino de topo (Conexões) e
 * as telas internas (painel, editor) têm retorno próprio na barra superior.
 * O destino Trades será recolocado quando o painel REST existir.
 *
 * A tela de conexões é a entrada do app: sem perfis cadastrados, ela apresenta
 * a mensagem de boas-vindas em vez de um dashboard vazio.
 */
@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()

    // Factory compartilhada pela aplicação: injeta o repositório de perfis e o
    // gerenciador do túnel nas telas.
    val context = LocalContext.current
    val application = context.applicationContext as FreqtradeApplication
    val viewModelFactory = remember(application) { application.viewModelFactory }

    // Sem Scaffold externo: cada tela tem o proprio, com um unico responsavel
    // pelos insets. Um Scaffold aqui, sem topBar, empurrava o conteudo pela
    // altura da barra de status e o header da tela somava o mesmo inset outra
    // vez — o topo ficava com o dobro do espaco.
    NavHost(
        navController = navController,
        startDestination = Destination.Profiles.route,
        modifier = modifier,
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
        // Painel do perfil conectado: só é alcançado pelo card conectado,
        // com retorno próprio na barra superior.
        composable(ROUTE_DASHBOARD) {
            val vm: DashboardViewModel = viewModel(factory = viewModelFactory)
            DashboardScreen(
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

