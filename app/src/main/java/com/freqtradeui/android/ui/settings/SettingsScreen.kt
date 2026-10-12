package com.freqtradeui.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Tela de configurações (placeholder funcional).
 *
 * Descreve os campos que serão necessários para a conexão SSH (host, porta,
 * usuário, chave privada) sem implementar persistência ou conexão. Nenhuma
 * credencial é armazenada ou enviada nesta etapa.
 *
 * @param modifier modificador de layout.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Configurações",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "A conexão com o Freqtrade será feita por um túnel SSH local. " +
                "Os campos abaixo serão habilitados em uma próxima etapa.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        SettingsPlaceholderRow(label = "Host da VPS", value = "—")
        SettingsPlaceholderRow(label = "Porta SSH", value = "—")
        SettingsPlaceholderRow(label = "Usuário SSH", value = "—")
        SettingsPlaceholderRow(label = "Chave privada", value = "não configurada")
        SettingsPlaceholderRow(label = "Porta local", value = "18080")

        Text(
            text = "Nenhuma credencial é salva no aplicativo nesta versão.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Linha simples de configuração (rótulo + valor placeholder).
 */
@Composable
private fun SettingsPlaceholderRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
