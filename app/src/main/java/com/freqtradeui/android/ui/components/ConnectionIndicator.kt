package com.freqtradeui.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.freqtradeui.android.data.models.ConnectionState
import com.freqtradeui.android.ui.theme.AmberWarn
import com.freqtradeui.android.ui.theme.LossRed
import com.freqtradeui.android.ui.theme.ProfitGreen
import com.freqtradeui.android.ui.theme.TextMuted
import com.freqtradeui.android.ui.theme.TextSecondary

/**
 * Indicador circular colorido de status de conexão.
 *
 * @param state estado atual exibido.
 * @param modifier modificador de layout.
 */
@Composable
fun StatusDot(
    state: ConnectionState,
    modifier: Modifier = Modifier,
) {
    val color: Color = when (state) {
        ConnectionState.Connected -> ProfitGreen
        ConnectionState.Connecting -> AmberWarn
        ConnectionState.Error -> LossRed
        ConnectionState.Disconnected -> TextMuted
    }
    Box(
        modifier = modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color),
    )
}


/**
 * Indicador composto de conexão: ponto colorido + rótulo textual.
 *
 * @param label rótulo descritivo (ex.: "SSH", "API").
 * @param state estado de conexão exibido.
 */
@Composable
fun ConnectionIndicator(
    label: String,
    state: ConnectionState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StatusDot(state = state)
        Text(
            text = "$label: ${state.label}",
            style = MaterialTheme.typography.labelSmall,
            color = if (state == ConnectionState.Connected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                TextSecondary
            },
        )
    }
}
