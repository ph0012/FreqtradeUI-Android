package com.freqtradeui.android.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freqtradeui.android.data.models.TunnelStatus
import com.freqtradeui.android.ui.components.ConnectionIndicator
import com.freqtradeui.android.ui.components.EmptyState
import com.freqtradeui.android.ui.components.PlaceholderValueCard
import com.freqtradeui.android.ui.theme.ProfitGreen

/**
 * Tela principal (Dashboard).
 *
 * Exibe os indicadores reais de conexão SSH/API, cards de saldo e
 * lucro/prejuízo como placeholders e a área de operações abertas em estado
 * vazio. Nenhum dado financeiro simulado é apresentado como real.
 *
 * @param onConfigureConnection callback para voltar à gestão de conexões.
 * @param viewModel ViewModel do dashboard, criado com a fábrica da aplicação.
 * @param onBack volta para a lista de perfis.
 * @param modifier modificador de layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onConfigureConnection: () -> Unit,
    viewModel: DashboardViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val connected = state.snapshot.status == TunnelStatus.Connected
    val pending = state.pendingHostKey

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.profile?.name ?: "Painel") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar para a lista de perfis",
                        )
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
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Cabeçalho: nome e subtítulo.
        Text(
            text = "FreqtradeUI",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Controle do seu bot de trading",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Indicadores de conexão SSH e API (SSH reflete o estado real do túnel).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ConnectionIndicator(label = "SSH", status = state.snapshot.status)
            // A API ainda não foi implementada: o indicador permanece
            // desconectado de verdade, sem simular estado.
            ConnectionIndicator(label = "API", status = TunnelStatus.Disconnected)
        }

        // Mensagem de status do túnel, quando houver.
        state.snapshot.error?.let { msg ->
            Text(
                text = msg,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
            )
        }

        // Card de saldo (placeholder).
        PlaceholderValueCard(
            title = "Saldo",
            value = "—",
            placeholderNote = "Dados indisponíveis — conecte-se ao bot para ver o saldo real.",
        )

        // Card de lucro/prejuízo (placeholder).
        PlaceholderValueCard(
            title = "Lucro / Prejuízo",
            value = "—",
            placeholderNote = "Sem dados reais. O resumo aparece após conectar à API.",
            valueColor = ProfitGreen,
        )

        // Área de operações abertas (estado vazio).
        EmptyState(message = "Nenhuma operação aberta.")

        Spacer(modifier = Modifier.height(8.dp))

        // Ações de conexão. Conectar é papel da tela de perfis (exige
        // escolha explícita do perfil); o painel apenas encerra o túnel.
        if (connected) {
            OutlinedButton(
                onClick = viewModel::disconnect,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Desconectar túnel SSH")
            }
        } else {
            Text(
                text = "O túnel não está ativo. Use a aba Conexões para " +
                    "escolher o perfil e conectar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            onClick = onConfigureConnection,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "Configurar conexão")
        }
    }
    }

    // Diálogo de decisão da chave de host (Trust On First Use).
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

/**
 * Diálogo que apresenta a chave de host desconhecida e pede decisão ao usuário.
 * A impressão digital deve ser conferida fora de banda antes de aprovar.
 */
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
                    text = "O servidor $host apresentou uma chave de host que ainda não " +
                        "foi confiada neste dispositivo.",
                )
                Text(
                    text = "Tipo: $keyType",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Impressão digital (SHA256):",
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = fingerprint,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "Confira a impressão digital por um canal seguro antes de confiar. " +
                        "Aprovar só é seguro se você reconhecer esta chave.",
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
