package com.freqtradeui.android.ui.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.freqtradeui.android.data.models.ConnectionProfile
import com.freqtradeui.android.data.models.TunnelStatus
import com.freqtradeui.android.ssh.PendingHostKey
import com.freqtradeui.android.ui.components.TunnelStatusChip

/**
 * Tela principal de seleção de perfis.
 *
 * Não guarda estado local de conexão: exibe exatamente o que
 * [ProfilesUiState.snapshot] informa, que vem do serviço.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    state: ProfilesUiState,
    onAddProfile: () -> Unit,
    onEditProfile: (ConnectionProfile) -> Unit,
    onOpenPanel: (String) -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onRequestDelete: (String) -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onSwitchConfirm: (String) -> Unit,
    onSwitchCancel: () -> Unit,
    onApproveHostKey: () -> Unit,
    onRejectHostKey: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("FreqtradeUI") },
                actions = {
                    IconButton(onClick = onAddProfile) {
                        Icon(Icons.Filled.Add, contentDescription = "Adicionar perfil")
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.hasProfiles) {
                ExtendedFloatingActionButton(onClick = onAddProfile) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Novo perfil")
                }
            }
        },
    ) { padding ->
        if (state.hasProfiles) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        text = "Perfis de conexão",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                items(state.profiles, key = { it.id }) { profile ->
                    ProfileCard(
                        profile = profile,
                        isActive = state.activeProfileId == profile.id,
                        status = state.snapshot.status,
                        onConnect = { onConnect(profile.id) },
                        onDisconnect = onDisconnect,
                        onEdit = { onEditProfile(profile) },
                        onDelete = { onRequestDelete(profile.id) },
                        onOpenPanel = { onOpenPanel(profile.id) },
                    )
                }
            }
        } else {
            WelcomeScreen(
                onConfigureProfile = onAddProfile,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }

    ConfirmDeleteDialog(state, onConfirmDelete, onCancelDelete)
    ConfirmSwitchDialog(state, onSwitchConfirm, onSwitchCancel)
    HostKeyTrustDialog(state.pendingHostKey, onApproveHostKey, onRejectHostKey)
}

/**
 * Card de um perfil, com estado real da conexão e ações contextuais.
 *
 * @param profile o perfil.
 * @param isActive indica se este perfil detém a conexão.
 * @param status estado real do túnel.
 */
@Composable
private fun ProfileCard(
    profile: ConnectionProfile,
    isActive: Boolean,
    status: com.freqtradeui.android.data.models.TunnelStatus,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenPanel: () -> Unit,
) {
    val connected = isActive && status == com.freqtradeui.android.data.models.TunnelStatus.Connected
    val busy = isActive && (
        status == com.freqtradeui.android.data.models.TunnelStatus.Connecting ||
            status == com.freqtradeui.android.data.models.TunnelStatus.Reconnecting
        )

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = profile.destination,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Só o nome do arquivo de origem da chave — nunca seu conteúdo.
                    profile.privateKeyFileName?.let { fileName ->
                        Text(
                            text = "Chave: $fileName",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (isActive) {
                    TunnelStatusChip(status = status)
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    // Conectado: abrir o painel é a ação principal; desconectar
                    // fica acessível ao lado, sem derrubar a sessão por engano.
                    connected -> {
                        Button(onClick = onOpenPanel) {
                            Text("Abrir painel")
                        }
                        OutlinedButton(onClick = onDisconnect) {
                            Icon(
                                Icons.Filled.Stop,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Desconectar")
                        }
                    }

                    busy -> OutlinedButton(onClick = onDisconnect, enabled = false) {
                        Text("Aguarde…")
                    }

                    else -> OutlinedButton(onClick = onConnect) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Conectar")
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Editar perfil")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Excluir perfil")
                }
            }

            if (profile.hostKeyFingerprint != null) {
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "Servidor verificado",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
    }
}

/** Confirmação de exclusão, avisando quando há túnel ativo. */
@Composable
private fun ConfirmDeleteDialog(
    state: ProfilesUiState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val id = state.pendingDelete ?: return
    val profile = state.profiles.firstOrNull { it.id == id }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Excluir perfil?") },
        text = {
            Text(
                if (state.activeProfileId == id) {
                    "A conexão de \"${profile?.name.orEmpty()}\" está ativa: será " +
                        "encerrada e as credenciais serão removidas do dispositivo."
                } else {
                    "O perfil \"${profile?.name.orEmpty()}\" e suas credenciais serão " +
                        "removidos do dispositivo. Esta ação não pode ser desfeita."
                },
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Excluir") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancelar") } },
    )
}

/** Confirmação de troca, para não derrubar a conexão atual em silêncio. */
@Composable
private fun ConfirmSwitchDialog(
    state: ProfilesUiState,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val targetId = state.pendingProfileSwitch ?: return
    val target = state.profiles.firstOrNull { it.id == targetId }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Trocar conexão?") },
        text = {
            Text(
                "Há um túnel ativo. Ele será encerrado e a conexão passará para " +
                    "\"${target?.name.orEmpty()}\".",
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(targetId) }) { Text("Trocar") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Manter atual") } },
    )
}

/**
 * Decisão TOFU: apresenta a impressão digital para conferência fora de banda.
 *
 * Não há aprovação automática: a chave só é gravada após toque explícito.
 */
@Composable
private fun HostKeyTrustDialog(
    pending: PendingHostKey?,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    if (pending == null) return
    AlertDialog(
        onDismissRequest = onReject,
        title = { Text("Verificar identidade do servidor") },
        text = {
            Column {
                Text(
                    "Primeira conexão a ${pending.host}. Confirme a impressão " +
                        "digital antes de confiar neste servidor.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = pending.fingerprint,
                    style = MaterialTheme.typography.bodySmall
                        .copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Algoritmo: ${pending.keyType}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Compare com a saída de:\nssh-keygen -lf /etc/ssh/ssh_host_*.pub",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onApprove) { Text("Confiar") } },
        dismissButton = { TextButton(onClick = onReject) { Text("Recusar") } },
    )
}

