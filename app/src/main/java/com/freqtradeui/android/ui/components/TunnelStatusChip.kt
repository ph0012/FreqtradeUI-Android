package com.freqtradeui.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.freqtradeui.android.data.models.TunnelStatus

/**
 * Indicador visual do estado do túnel, com rótulo textual.
 *
 * O ícone não carrega a informação sozinho: o texto acompanha sempre, para que
 * leitores de tela e leitura rápida funcionem igualmente.
 *
 * @param status estado real da conexão.
 * @param modifier modificador de layout.
 */
@Composable
fun TunnelStatusChip(
    status: TunnelStatus,
    modifier: Modifier = Modifier,
) {
    val label = status.label
    val tint = status.color()

    Surface(
        modifier = modifier.semantics { contentDescription = "Estado da conexão: $label" },
        shape = RoundedCornerShape(50),
        color = tint.copy(alpha = 0.15f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = status.chipIcon(),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
            )
        }
    }
}

/**
 * Ícone de cada estado.
 *
 * Privado deste arquivo para não colidir com as extensões de
 * [TunnelStatus] declaradas em `ConnectionIndicator.kt`.
 */
private fun TunnelStatus.chipIcon() = when (this) {
    TunnelStatus.Connected -> Icons.Filled.Lock
    TunnelStatus.Connecting, TunnelStatus.Reconnecting -> Icons.Filled.Sync
    TunnelStatus.Error -> Icons.Filled.Error
    else -> Icons.Filled.CheckCircle
}
