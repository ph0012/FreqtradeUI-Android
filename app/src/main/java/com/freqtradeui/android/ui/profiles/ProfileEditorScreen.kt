package com.freqtradeui.android.ui.profiles

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.freqtradeui.android.data.models.ConnectionProfile
import com.freqtradeui.android.data.models.ProfileSecret
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Editor de perfis: cadastro e edicao pelo mesmo formulario.
 *
 * Recebe [profileId] ja carregado do repositorio, entao o usuario altera apenas
 * o que precisa - nada e perdido ao reabrir o app. Se o perfil estiver
 * conectado, a tela avisa e exige desconexao antes de aplicar mudancas que
 * possam invalidar o tunel.
 *
 * @param profileId identificador do perfil, ou `null` para cadastro novo.
 * @param viewModel acesso a perfis e segredos.
 * @param isConnected indica se o perfil possui tunel ativo.
 * @param onDone chamado apos salvar, com o id persistido.
 * @param onCancel volta sem salvar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorScreen(
    profileId: String?,
    viewModel: ProfilesViewModel,
    isConnected: Boolean,
    onDone: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var loaded by remember(profileId) { mutableStateOf<ConnectionProfile?>(null) }
    LaunchedEffect(profileId) {
        loaded = if (profileId == null) null else viewModel.profile(profileId)
    }

    if (profileId != null && loaded == null) {
        // Aguarda o carregamento: evita exibir formulario vazio por engano.
        Scaffold(topBar = { TopAppBar(title = { Text("Editar perfil") }) }) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Carregando perfil...", style = MaterialTheme.typography.bodyLarge)
            }
        }
        return
    }

    ProfileEditorForm(
        existing = loaded,
        isNew = profileId == null,
        isConnected = isConnected,
        viewModel = viewModel,
        onSave = { draft -> viewModel.save(draft, profileId, onDone) },
        onCancel = onCancel,
    )
}

/**
 * Formulario de cadastro/edicao.
 *
 * A chave privada e importada pelo seletor de documentos do Android e fica
 * apenas em memoria ate o salvamento; o campo exibe o **nome do arquivo**, nao
 * o conteudo.
 */
@OptIn(ExperimentalMaterial3Api::class)

@Composable
private fun ProfileEditorForm(
    existing: ConnectionProfile?,
    isNew: Boolean,
    isConnected: Boolean,
    viewModel: ProfilesViewModel,
    onSave: (ConnectionProfile) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val scope = remember { MainScope() }

    var name by remember(existing?.id) { mutableStateOf(existing?.name ?: "") }
    var host by remember(existing?.id) { mutableStateOf(existing?.host ?: "") }
    var sshPort by remember(existing?.id) {
        mutableStateOf((existing?.sshPort ?: ConnectionProfile.DEFAULT_SSH_PORT).toString())
    }
    var username by remember(existing?.id) { mutableStateOf(existing?.username ?: "") }
    var localPort by remember(existing?.id) {
        mutableStateOf((existing?.localPort ?: ConnectionProfile.DEFAULT_LOCAL_PORT).toString())
    }
    var remoteHost by remember(existing?.id) {
        mutableStateOf(existing?.remoteHost ?: ConnectionProfile.DEFAULT_REMOTE_HOST)
    }
    var remotePort by remember(existing?.id) {
        mutableStateOf((existing?.remotePort ?: ConnectionProfile.DEFAULT_REMOTE_PORT).toString())
    }
    var passphrase by remember(existing?.id) { mutableStateOf("") }
    var keyFileName by remember(existing?.id) { mutableStateOf(existing?.privateKeyFileName) }
    var privateKey by remember(existing?.id) { mutableStateOf<String?>(null) }
    var message by remember(existing?.id) { mutableStateOf<String?>(null) }

    // Seletor de documentos: o acesso a chave e concedido pelo usuario ao
    // escolher o arquivo, sem nenhuma permissao extra no manifesto.
    val pickKey = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        message = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val content = context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        ?: error("vazio")
                    val displayName = context.contentResolver.query(
                        uri,
                        arrayOf(OpenableColumns.DISPLAY_NAME),
                        null,
                        null,
                        null,
                    )?.use { cursor ->
                        val col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (col >= 0 && cursor.moveToFirst()) cursor.getString(col) else null
                    }
                    content to (displayName ?: uri.lastPathSegment)
                }
            }
            result.onSuccess { (content, fileName) ->
                if (content.isBlank()) {
                    message = "O arquivo selecionado esta vazio."
                } else {
                    privateKey = content
                    keyFileName = fileName
                }
            }.onFailure {
                message = "Nao foi possivel ler o arquivo selecionado."
            }
        }
    }

    fun buildProfile(): ConnectionProfile = ConnectionProfile(
        id = existing?.id ?: ConnectionProfile.newId(),
        name = name.trim(),
        host = host.trim(),
        sshPort = sshPort.toIntOrNull() ?: ConnectionProfile.DEFAULT_SSH_PORT,
        username = username.trim(),
        remoteHost = remoteHost.trim(),
        remotePort = remotePort.toIntOrNull() ?: ConnectionProfile.DEFAULT_REMOTE_PORT,
        localPort = localPort.toIntOrNull() ?: ConnectionProfile.DEFAULT_LOCAL_PORT,
        privateKeyFileName = keyFileName,
        hostKeyFingerprint = existing?.hostKeyFingerprint,
        lastConnectedAt = existing?.lastConnectedAt,
    )

    Scaffold(
        topBar = { TopAppBar(title = { Text(if (isNew) "Novo perfil" else "Editar perfil") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isConnected) {
                Card {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "Perfil conectado",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            "Alteracoes de endereco, porta ou chave so tem efeito " +
                                "depois de desconectar. Desconecte antes de salvar " +
                                "para que o tunel atualizado seja estabelecido.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = onCancel) { Text("Voltar") }
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome do perfil *") },
                singleLine = true,
                enabled = !isConnected,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Endereco da VPS (IP ou hostname) *") },
                singleLine = true,
                enabled = !isConnected,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = sshPort,
                onValueChange = { sshPort = it.filter(Char::isDigit).take(5) },
                label = { Text("Porta SSH") },
                singleLine = true,
                enabled = !isConnected,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Usuario SSH *") },
                singleLine = true,
                enabled = !isConnected,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                "Autenticacao",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            OutlinedTextField(
                value = keyFileName ?: "",
                onValueChange = {},
                label = { Text("Chave privada (arquivo)") },
                readOnly = true,
                singleLine = true,
                enabled = !isConnected,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { pickKey.launch(arrayOf("*/*")) },
                    enabled = !isConnected,
                ) {
                    Text(if (keyFileName == null) "Selecionar arquivo" else "Substituir arquivo")
                }
                if (keyFileName != null) {
                    TextButton(
                        onClick = {
                            keyFileName = null
                            privateKey = null
                        },
                        enabled = !isConnected,
                    ) { Text("Limpar") }
                }
            }
            if (message != null) {
                Text(
                    text = message!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            OutlinedTextField(
                value = passphrase,
                onValueChange = { passphrase = it },
                label = { Text("Frase secreta da chave (se houver)") },
                singleLine = true,
                enabled = !isConnected,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                "Encaminhamento de porta",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = localPort,
                    onValueChange = { localPort = it.filter(Char::isDigit).take(5) },
                    label = { Text("Porta local") },
                    singleLine = true,
                    enabled = !isConnected,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = remoteHost,
                    onValueChange = { remoteHost = it },
                    label = { Text("Host remoto") },
                    singleLine = true,
                    enabled = !isConnected,
                    modifier = Modifier.weight(1.5f),
                )
                OutlinedTextField(
                    value = remotePort,
                    onValueChange = { remotePort = it.filter(Char::isDigit).take(5) },
                    label = { Text("Porta remota") },
                    singleLine = true,
                    enabled = !isConnected,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = "O tunel ficara disponivel em http://127.0.0.1:$localPort no " +
                    "dispositivo, encaminhando para $remoteHost:$remotePort na VPS.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val draft = buildProfile()
                        val err = draft.validationError()
                        if (err != null) {
                            message = err
                            return@Button
                        }
                        // Chave importada vai direto ao cofre cifrado.
                        privateKey?.let {
                            viewModel.saveSecret(
                                draft.id,
                                ProfileSecret(it, passphrase.ifBlank { null }),
                            )
                        }
                        onSave(draft)
                    },
                    enabled = !isConnected,
                ) { Text("Salvar") }
                TextButton(onClick = onCancel) { Text("Cancelar") }
            }

            if (!isNew) {
                Card {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "Identidade do servidor",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = existing?.hostKeyFingerprint
                                ?: "Sera solicitada na primeira conexao.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
