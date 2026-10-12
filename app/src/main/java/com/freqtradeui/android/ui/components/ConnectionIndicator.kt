package com.freqtradeui.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.freqtradeui.android.data.models.TunnelStatus
import com.freqtradeui.android.ui.theme.AmberWarn
import com.freqtradeui.android.ui.theme.LossRed
import com.freqtradeui.android.ui.theme.ProfitGreen
import com.freqtradeui.android.ui.theme.TextMuted
import com.freqtradeui.android.ui.theme.TextSecondary

/** Cor associada a cada estado do túnel. */
internal fun TunnelStatus.color(): Color = when (this) {
    TunnelStatus.Connected -> ProfitGreen
    TunnelStatus.Connecting -> AmberWarn
    TunnelStatus.Reconnecting -> AmberWarn
    TunnelStatus.Error -> LossRed
    TunnelStatus.Stopping -> LossRed
    TunnelStatus.Disconnected -> TextMuted
}

/** Rótulo textual de cada estado. */
val TunnelStatus.label: String
    get() = when (this) {
        TunnelStatus.Connected -> "Conectado"
        TunnelStatus.Connecting -> "Conectando"
        TunnelStatus.Reconnecting -> "Reconectando"
        TunnelStatus.Error -> "Erro"
        TunnelStatus.Stopping -> "Encerrando"
        TunnelStatus.Disconnected -> "Desconectado"
    }

/**
 * Indicador circular colorido de um estado.
 *
 * @param status estado exibido.
 * @param size diâmetro do ponto.
 * @param modifier modificador de layout.
 */
@Composable
fun StatusDot(
    status: TunnelStatus,
    modifier: Modifier = Modifier,
    size: Dp = 10.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(status.color()),
    )
}

/**
 * Indicador composto: ponto colorido + rótulo textual.
 *
 * @param label rótulo descritivo (ex.: "SSH", "API").
 * @param status estado exibido.
 * @param modifier modificador de layout.
 */
@Composable
fun ConnectionIndicator(
    label: String,
    status: TunnelStatus,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StatusDot(status = status, size = 8.dp)
        Text(
            text = "$label: ${status.label}",
            style = MaterialTheme.typography.labelSmall,
            color = if (status == TunnelStatus.Connected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                TextSecondary
            },
        )
    }
}

/**
 * Selo do estado da conexão, usado na lista de perfis.
 *
 * Acessível: o `contentDescription` repete o estado em texto corrido, para que
 * leitores de tela não dependam apenas da cor.
 */
@Composable
fun ConnectionStatusBadge(
    status: TunnelStatus,
    modifier: Modifier = Modifier,
) {
    val color = status.color()

    Row(
        modifier = modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics { contentDescription = "Estado da conexão: ${status.label}" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(status = status, size = 8.dp)
        Text(text = status.label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}
