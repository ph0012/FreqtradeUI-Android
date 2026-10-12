package com.freqtradeui.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Altura util do header, sem contar a barra de status. */
private val HEADER_HEIGHT = 52.dp

/**
 * Largura minima reservada em cada lado do titulo, para que ele fique
 * centralizado de verdade mesmo com um lado vazio (ex.: tela raiz sem voltar).
 */
private val SIDE_SLOT = 48.dp

/**
 * Header padrao do app: compacto, com altura previsivel em todas as telas.
 *
 * **Por que nao `TopAppBar`:** os tokens do Material 3 tratam `expandedHeight`
 * como piso (nao da para encolher a barra abaixo do padrao) e somam o inset da
 * barra de status por dentro. O resultado era espaco em branco entre a barra de
 * status e o titulo. Aqui a altura e explicita: inset da barra de status **fora**
 * e [HEADER_HEIGHT] de conteudo, sem acumular.
 *
 * A superficie pinta tambem a area da barra de status, entao o topo fica
 * continuo em vez de mostrar um bloco de cor diferente.
 *
 * @param title texto do titulo, centralizado.
 * @param modifier modificador de layout.
 * @param onBack acao do botao de voltar; `null` esconde o botao.
 * @param actions acoes a direita (icones).
 */
@Composable
fun CompactTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.windowInsetsPadding(
                WindowInsets.statusBars.only(WindowInsetsSides.Top),
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HEADER_HEIGHT)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.widthIn(min = SIDE_SLOT),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Voltar",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(
                    modifier = Modifier.widthIn(min = SIDE_SLOT),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }
        }
    }
}
