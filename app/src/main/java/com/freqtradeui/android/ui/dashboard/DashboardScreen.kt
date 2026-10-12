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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freqtradeui.android.data.models.TunnelStatus
import com.freqtradeui.android.ui.components.CompactTopAppBar
import com.freqtradeui.android.ui.components.ConnectionIndicator
import com.freqtradeui.android.ui.components.EmptyState
import com.freqtradeui.android.ui.components.PlaceholderValueCard
import com.freqtradeui.android.ui.theme.ProfitGreen

/**
 * Tela principal (Dashboard) - painel do perfil conectado.
 *
 * A barra superior e propositalmente compacta: respeita o alvo de toque de
 * 48 dp da seta de voltar, mas reduz altura e titulo para devolver espaco
 * vertical ao conteudo. O nome do perfil ativo aparece no titulo.
 *
 * O retorno para a lista de perfis e feito **apenas** pela seta de voltar:
 * nao ha barra inferior nem atalho redundante para a mesma tela.
 *
 * Abaixo: indicadores reais de conexao SSH/API, cards de saldo e
 * lucro/prejuizo como placeholders e a area de operacoes abertas em estado
 * vazio. Nenhum dado financeiro simulado e apresentado como real.
 *
 * @param viewModel ViewModel do dashboard, criado com a fabrica da aplicacao.
 * @param onBack volta para a lista de perfis.
 * @param modifier modificador de layout.
 */
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val connected = state.snapshot.status == TunnelStatus.Connected
    val pending = state.pendingHostKey


    Scaffold(
        modifier = modifier,
        // Header padrao do app (mesmo componente da tela de perfis).
        topBar = {
            CompactTopAppBar(
                title = state.profile?.name ?: "Painel",
                onBack = onBack,
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ConnectionIndicator(label = "SSH", status = state.snapshot.status)
                // A API ainda nao foi implementada: o indicador permanece
                // desconectado de verdade, sem simular estado.
                ConnectionIndicator(label = "API", status = TunnelStatus.Disconnected)
            }

            state.snapshot.error?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                )
            }

            PlaceholderValueCard(
                title = "Saldo",
                value = "\u2014",
                placeholderNote = "Dados indispon\u00edveis \u2014 conecte-se ao " +
                    "bot para ver o saldo real.",
            )
            PlaceholderValueCard(
                title = "Lucro / Preju\u00edzo",
                value = "\u2014",
                placeholderNote = "Sem dados reais. O resumo aparece ap\u00f3s " +
                    "conectar \u00e0 API.",
                valueColor = ProfitGreen,
            )

            EmptyState(message = "Nenhuma opera\u00e7\u00e3o aberta.")

            Spacer(modifier = Modifier.height(8.dp))

            if (connected) {
                OutlinedButton(
                    onClick = viewModel::disconnect,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Desconectar t\u00fanel SSH")
                }
            } else {
                Text(
                    text = "O t\u00fanel n\u00e3o est\u00e1 ativo. Volte para a lista " +
                        "de perfis para conectar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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

/**
 * Dialogo que apresenta a chave de host desconhecida e pede decisao ao usuario.
 * A impressao digital deve ser conferida fora de banda antes de aprovar.
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
        title = { Text("Chave de host n\u00e3o reconhecida") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "O servidor $host apresentou uma chave de host que " +
                        "ainda n\u00e3o foi confiada neste dispositivo.",
                )
                Text(
                    text = "Tipo: $keyType",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Impress\u00e3o digital (SHA256):",
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = fingerprint,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "Confira a impress\u00e3o digital por um canal seguro " +
                        "antes de confiar. Aprovar s\u00f3 \u00e9 seguro se voc\u00ea " +
                        "reconhecer esta chave.",
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

