package com.freqtradeui.android.ui.comparison

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freqtradeui.android.data.models.TunnelStatus
import com.freqtradeui.android.ui.components.CompactTopAppBar
import com.freqtradeui.android.ui.components.EmptyState
import com.freqtradeui.android.ui.components.TunnelStatusChip
import com.freqtradeui.android.ui.theme.LossRed
import com.freqtradeui.android.ui.theme.ProfitGreen
import com.freqtradeui.android.ui.theme.TextMuted
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BotComparisonScreen(
    viewModel: BotComparisonViewModel,
    onMenu: () -> Unit,
    onOpenProfiles: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val connected = state.snapshot.status == TunnelStatus.Connected

    Scaffold(
        modifier = modifier,
        topBar = {
            CompactTopAppBar(
                title = "Bot Comparison",
                onMenu = onMenu,
                actions = {
                    if (state.loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(10.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(
                            onClick = viewModel::refresh,
                            enabled = connected && state.hasCredentials,
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Atualizar")
                        }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.profile?.destination ?: "Nenhum perfil selecionado",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TunnelStatusChip(state.snapshot.status)
            }

            state.error?.let { error ->
                val message = if (state.lastUpdatedAt != null) {
                    "Dados podem estar desatualizados: $error"
                } else {
                    error
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.lastUpdatedAt != null) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        LossRed
                    },
                )
            }

            if (!connected && state.lastUpdatedAt == null) {
                EmptyState(
                    message = "Conecte um perfil e configure as credenciais da API " +
                        "para carregar os indicadores.",
                )
                androidx.compose.material3.OutlinedButton(
                    onClick = onOpenProfiles,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text("Abrir perfis e conexões")
                }
            } else if (connected && !state.hasCredentials) {
                EmptyState(
                    message = "As credenciais da API deste perfil não estão configuradas.",
                )
                androidx.compose.material3.OutlinedButton(
                    onClick = onOpenProfiles,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text("Abrir perfis e conexões")
                }
            } else if (connected && state.loading && state.lastUpdatedAt == null) {
                EmptyState(message = "Carregando indicadores...")
            }

            ComparisonSummaryCard(
                profileName = state.profile?.name ?: "Bot",
                dryRun = state.dryRun,
                openTrades = state.openTradeCount,
                closedTrades = state.closedTradeCount,
                slotsTotal = state.slotsTotal,
                winningTrades = state.winningTrades,
                losingTrades = state.losingTrades,
                openProfitPercent = state.openProfitPercent,
                openProfit = state.openProfitCoin,
                closedProfitPercent = state.closedProfitPercent,
                closedProfit = state.closedProfitCoin,
                profitCurrency = state.stakeCurrency,
                balance = state.balanceTotal,
                balanceCurrency = state.balanceCurrency,
                connected = connected,
                hasData = state.lastUpdatedAt != null,
            )

            state.lastUpdatedAt?.let { updatedAt ->
                Text(
                    text = "Última atualização: ${
                        SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                            .format(Date(updatedAt))
                    }",
                    modifier = Modifier.align(Alignment.End),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
            }
        }
    }
}

@Composable
private fun ComparisonSummaryCard(
profileName: String,
dryRun: Boolean?,
openTrades: Int?,
closedTrades: Int?,
slotsTotal: Int?,
winningTrades: Int?,
losingTrades: Int?,
openProfitPercent: Double?,
openProfit: Double?,
closedProfitPercent: Double?,
closedProfit: Double?,
profitCurrency: String?,
balance: Double?,
balanceCurrency: String?,
connected: Boolean,
hasData: Boolean,
) {
androidx.compose.material3.Card(
    modifier = Modifier.fillMaxWidth(),
    colors = androidx.compose.material3.CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
    ),
) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = profileName,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = when (dryRun) {
                    true -> "Dry"
                    false -> "Live"
                    null -> "Modo indisponível"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SummaryMetric(
                label = "All Trades",
                value = if (openTrades != null && closedTrades != null) {
                    (openTrades + closedTrades).toString()
                } else {
                    "—"
                },
                modifier = Modifier.weight(1f),
            )
            SummaryMetric(
                label = "Open",
                value = "${openTrades?.toString() ?: "—"} / ${slotsTotal?.toString() ?: "—"}",
                modifier = Modifier.weight(1f),
            )
            SummaryMetric(
                label = "W / L",
                value = "${winningTrades?.toString() ?: "—"} / ${losingTrades?.toString() ?: "—"}",
                modifier = Modifier.weight(1f),
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SummaryProfitRow(
            label = "Open Profit",
            percent = openProfitPercent,
            amount = openProfit,
            currency = profitCurrency,
        )
        SummaryProfitRow(
            label = "Closed Profit",
            percent = closedProfitPercent,
            amount = closedProfit,
            currency = profitCurrency,
        )
        SummaryProfitRow(
            label = "Balance",
            percent = null,
            amount = balance,
            currency = balanceCurrency,
        )
        if (!connected && hasData) {
            Text(
                text = "Dados desatualizados: bot desconectado",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}
}

@Composable
private fun SummaryMetric(
label: String,
value: String,
modifier: Modifier = Modifier,
) {
Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(2.dp),
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = value,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}
}

@Composable
private fun SummaryProfitRow(
label: String,
percent: Double?,
amount: Double?,
currency: String?,
) {
val color = when {
    amount == null -> MaterialTheme.colorScheme.onSurface
    amount > 0.0 -> ProfitGreen
    amount < 0.0 -> LossRed
    else -> MaterialTheme.colorScheme.onSurface
}
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = amount?.let { "${formatNumber(it)} ${currency ?: ""}".trim() }
                ?: "Indisponível",
            style = MaterialTheme.typography.titleSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
        if (percent != null) {
            Text(
                text = "${formatNumber(percent)}%",
                style = MaterialTheme.typography.labelSmall,
                color = color,
            )
        }
    }
}
}

private fun formatNumber(value: Double): String =
String.format(Locale.getDefault(), "%.2f", value)
