package com.freqtradeui.android.ui.trades

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.freqtradeui.android.ui.components.EmptyState

/**
 * Tela de operações (Trades).
 *
 * Nesta etapa inicial não há dados: a lista de operações abertas será obtida
 * da API do Freqtrade após a implementação do túnel SSH. Apresenta apenas o
 * estado vazio.
 *
 * @param modifier modificador de layout.
 */
@Composable
fun TradesScreen(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Operações",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Histórico e operações em aberto do seu bot.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        EmptyState(message = "Nenhuma operação para exibir.")
    }
}
