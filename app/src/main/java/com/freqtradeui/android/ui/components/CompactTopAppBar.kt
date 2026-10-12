package com.freqtradeui.android.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Altura da barra superior do app.
 *
 * Menor que os 64 dp do padrão, mas ainda com folga para o alvo de toque de
 * 48 dp dos botões. Definida uma única vez para que todas as telas tenham o
 * mesmo peso visual.
 */
private val COMPACT_BAR_HEIGHT = 52.dp

/**
 * Barra superior padrão do app.
 *
 * Centralizada e compacta: título em `titleSmall` com uma linha só, então
 * nomes longos não esticam a barra. Passe [onBack] para exibir a seta de
 * voltar à esquerda; sem ela, a tela é considerada raiz.
 *
 * Os insets da barra de status são aplicados aqui e zerados na barra, para não
 * serem somados duas vezes pelo `Scaffold`.
 *
 * @param title texto do título.
 * @param modifier modificador de layout.
 * @param onBack ação do botão de voltar; `null` esconde o botão.
 * @param actions ações à direita (ícones).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    CenterAlignedTopAppBar(
        modifier = modifier.windowInsetsPadding(
            WindowInsets.statusBars.only(WindowInsetsSides.Top),
        ),
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
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
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            // Fundo sólido desde o topo: a lista/rolagem não deve parecer
            // "entrar" por baixo da barra.
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
        ),
        expandedHeight = COMPACT_BAR_HEIGHT,
        // Insets tratados no modifier acima.
        windowInsets = WindowInsets(0, 0, 0, 0),
    )
}
