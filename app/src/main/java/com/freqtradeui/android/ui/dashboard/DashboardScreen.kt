package com.freqtradeui.android.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freqtradeui.android.data.models.TunnelStatus
import com.freqtradeui.android.ui.components.CompactTopAppBar
import com.freqtradeui.android.ui.components.ConnectionIndicator
import com.freqtradeui.android.ui.components.EmptyState
import com.freqtradeui.android.ui.components.InfoCard
import com.freqtradeui.android.ui.components.StatusDot
import com.freqtradeui.android.ui.theme.LossRed
import com.freqtradeui.android.ui.theme.ProfitGreen
import com.freqtradeui.android.ui.theme.TextMuted
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Painel do perfil conectado.
 *
 * Exibe apenas dados reais vindos da API do Freqtrade. Enquanto os dados não
 * chegam, mostra estado de carregamento ou indisponibilidade — nunca um valor
 * estimado ou simulado.
 *
 * @param viewModel ViewModel do painel.
 * @param onBack volta para a lista de perfis, quando disponível.
 * @param onMenu abre a navegação principal.
 * @param modifier modificador de layout.
 */
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onBack: (() -> Unit)? = null,
    onMenu: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pending = state.pendingHostKey
    val tunnelConnected = state.snapshot.status == TunnelStatus.Connected

    Scaffold(
        modifier = modifier,
        topBar = {
            CompactTopAppBar(
                title = state.profile?.name ?: "Painel",
                onBack = onBack,
                onMenu = onMenu,
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Atualizar")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ConnectionCard(
                tunnelStatus = state.snapshot.status,
                apiError = state.error,
                apiReady = tunnelConnected && state.hasCredentials &&
                    state.error == null && state.lastUpdatedAt != null,
                apiBaseUrl = state.apiBaseUrl,
                lastUpdatedAt = state.lastUpdatedAt,
            )

            state.snapshot.error?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = LossRed,
                )
            }

            if (!tunnelConnected) {
                EmptyState(
                    message = "O túnel não está ativo. Volte para a lista de perfis " +
                        "para reconectar.",
                )
                return@Column
            }

            if (!state.hasCredentials) {
                EmptyState(
                    message = "Credenciais da API não configuradas. Edite este perfil " +
                        "e informe usuário e senha da API do Freqtrade.",
                )
                return@Column
            }

            if (state.loading && state.isEmpty) {
                EmptyState(message = "Carregando dados do bot...")
            }

            DataCards(state = state)
        }
    }

    if (pending != null) {
        HostKeyDialog(
            host = pending.host,
            fingerprint = pending.fingerprint,
            keyType = pending.keyType,
            onApprove = viewModel::approveHostKey,
            onReject = viewModel::rejectHostKey,
        )
    }
}

/** Card de estado: túnel SSH, comunicação com a API e última atualização. */
@Composable
private fun ConnectionCard(
    tunnelStatus: TunnelStatus,
    apiError: String?,
    apiReady: Boolean,
    apiBaseUrl: String,
    lastUpdatedAt: Long?,
) {
    InfoCard(title = "Conexão") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ConnectionIndicator(label = "Túnel SSH", status = tunnelStatus)

            val apiColor = when {
                apiError != null -> LossRed
                apiReady -> ProfitGreen
                else -> TextMuted
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusDot(status = tunnelStatus, size = 8.dp)
                Text(
                    text = "API: " + when {
                        apiError != null -> "falha"
                        apiReady -> "ok"
                        else -> "indisponível"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = apiColor,
                )
            }

            apiError?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = LossRed,
                )
            }

            if (apiBaseUrl.isNotBlank()) {
                Text(
                    text = apiBaseUrl,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = lastUpdatedAt?.let {
                    "Última atualização: " + TIME_FORMAT.format(Date(it))
                } ?: "Sem atualização bem-sucedida.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Cards com os dados da API, ou indisponibilidade explícita. */
@Composable
private fun DataCards(state: DashboardUiState) {
    val unavailable = "—"

    DashboardValueCard(
        title = "Bot",
        value = state.botState.ifBlank { unavailable },
        subtitle = "estado",
    )
    DashboardValueCard(
        title = "Freqtrade",
        value = state.version.ifBlank { unavailable },
        subtitle = state.health,
    )
    DashboardValueCard(
        title = "Operações abertas",
        value = state.openTrades?.toString() ?: unavailable,
        subtitle = "trades",
    )
    DashboardValueCard(
        title = "Saldo",
        value = state.balanceTotal.ifBlank { unavailable },
        subtitle = state.balanceCurrency.ifBlank { "—" },
    )
    DashboardValueCard(
        title = "Lucro / Prejuízo",
        value = state.profitTotal.ifBlank { unavailable },
        subtitle = state.profitPercent.ifBlank { "—" },
        valueColor = if (state.profitTotal.startsWith("-")) LossRed else ProfitGreen,
    )
}

/** Diálogo de decisão TOFU da chave de host. */
@Composable
private fun HostKeyDialog(
    host: String,
    fingerprint: String,
    keyType: String,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onReject,
        title = { Text("Chave de host não reconhecida") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "O servidor $host apresentou uma chave de host que ainda " +
                        "não foi confiada neste dispositivo.",
                )
                Text(text = "Tipo: $keyType", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "Impressão digital (SHA256):",
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(text = fingerprint, style = MaterialTheme.typography.bodySmall)
                Text(
                    text = "Confira a impressão digital por um canal seguro antes de " +
                        "confiar. Aprovar só é seguro se você reconhecer esta chave.",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onApprove) { Text("Confiar") }
        },
        dismissButton = {
            TextButton(onClick = onReject) { Text("Recusar") }
        },
    )
}

/** Formatação fixa de data/hora, independente do idioma do aparelho. */
private val TIME_FORMAT = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US)