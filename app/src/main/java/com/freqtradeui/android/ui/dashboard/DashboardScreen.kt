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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.freqtradeui.android.ui.components.ConnectionIndicator
import com.freqtradeui.android.ui.components.EmptyState
import com.freqtradeui.android.ui.components.PlaceholderValueCard
import com.freqtradeui.android.ui.theme.ProfitGreen

/**
 * Tela principal (Dashboard).
 *
 * Exibe indicadores de conexão (sempre desconectados nesta etapa), cards de
 * saldo e lucro/prejuízo como placeholders e a área de operações abertas em
 * estado vazio. Nenhum dado simulado é apresentado como real.
 *
 * @param onConfigureConnection callback para abrir a tela de configurações.
 * @param modifier modificador de layout.
 * @param viewModel ViewModel do dashboard (padrão: instância gerenciada).
 */
@Composable
fun DashboardScreen(
    onConfigureConnection: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
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

        // Indicadores de conexão SSH e API.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ConnectionIndicator(label = "SSH", state = state.sshState)
            ConnectionIndicator(label = "API", state = state.apiState)
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

        // Botão para configurar a conexão.
        Button(
            onClick = onConfigureConnection,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "Configurar conexão")
        }

        Text(
            text = "O botão acima abre as configurações. Nenhuma conexão SSH é " +
                "estabelecida automaticamente nesta versão.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start,
        )
    }
}
