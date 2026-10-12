package com.freqtradeui.android.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.provider.OpenableColumns
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.freqtradeui.android.ssh.SshConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tela de configurações da conexão SSH.
 *
 * Coleta host, porta, usuário, chave privada e passphrase e os persiste de
 * forma criptografada. Não há endpoint fictício nem conexão automática: a
 * conexão só ocorre quando o usuário toca em "Salvar e conectar".
 *
 * @param modifier modificador de layout.
 * @param viewModel ViewModel de configurações.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Estado local dos campos (a chave privada vive só em memória da UI).
    var host by remember { mutableStateOf("") }
    var sshPort by remember { mutableStateOf(SshConfig.DEFAULT_SSH_PORT.toString()) }
    var username by remember { mutableStateOf("") }
    var privateKey by remember { mutableStateOf("") }
    // Nome do arquivo escolhido, exibido no campo. O conteúdo da chave fica
    // apenas em memória em [privateKey] e nunca é renderizado na tela.
    var privateKeyFileName by remember { mutableStateOf<String?>(null) }
    var passphrase by remember { mutableStateOf("") }
    var remoteHost by remember { mutableStateOf("127.0.0.1") }
    var remotePort by remember { mutableStateOf(SshConfig.DEFAULT_REMOTE_PORT.toString()) }
    var localPort by remember { mutableStateOf(SshConfig.DEFAULT_LOCAL_PORT.toString()) }

    // Preenche automaticamente os campos com a configuração salva, apenas na
    // primeira vez que ela é recebida. Depois disso o usuário edita livremente;
    // qualquer digitação passa a valer mais que o valor carregado.
    LaunchedEffect(state.loadedConfig) {
        state.loadedConfig?.let { cfg ->
            host = cfg.host
            sshPort = cfg.sshPort.toString()
            username = cfg.username
            privateKey = cfg.privateKey
            privateKeyFileName = cfg.privateKeyFileName
            passphrase = cfg.privateKeyPassphrase.orEmpty()
            remoteHost = cfg.remoteHost
            remotePort = cfg.remotePort.toString()
            localPort = cfg.localPort.toString()
        }
    }

    // Seletor de arquivos do sistema (Storage Access Framework / DocumentsUI).
    // Lê o conteúdo do arquivo escolhido e preenche o campo da chave privada.
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var keyFileError by remember { mutableStateOf<String?>(null) }
    val keyFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val content = context.contentResolver.openInputStream(uri)?.use { input ->
                            input.bufferedReader().readText()
                        } ?: error("Não foi possível abrir o arquivo selecionado.")
                        // Nome amigável do arquivo, exibido no lugar do conteúdo.
                        val displayName = context.contentResolver
                            .query(uri, null, null, null, null)
                            ?.use { cursor ->
                                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                if (index >= 0 && cursor.moveToFirst()) {
                                    cursor.getString(index)
                                } else {
                                    null
                                }
                            }
                            ?: uri.lastPathSegment
                            ?: "arquivo selecionado"
                        displayName to content
                    }
                }
                result
                    .onSuccess { (displayName, content) ->
                        if (content.isNotBlank()) {
                            privateKey = content
                            privateKeyFileName = displayName
                            keyFileError = null
                        } else {
                            keyFileError = "O arquivo selecionado está vazio."
                        }
                    }
                    .onFailure { e ->
                        keyFileError = e.message ?: "Falha ao ler o arquivo da chave."
                    }
            }
        }
    }

    val save: (Boolean) -> Unit = { connectAfterSave ->
        viewModel.save(
            host = host,
            sshPort = sshPort,
            username = username,
            privateKey = privateKey,
            passphrase = passphrase,
            remoteHost = remoteHost,
            remotePort = remotePort,
            localPort = localPort,
            privateKeyFileName = privateKeyFileName,
            connectAfterSave = connectAfterSave,
        )
    }

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
            text = "A conexão é feita por um túnel SSH local: a porta " +
                "${SshConfig.DEFAULT_LOCAL_PORT} do aparelho é encaminhada para " +
                "${SshConfig.DEFAULT_REMOTE_PORT} na sua VPS.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("Host da VPS") },
            placeholder = { Text("ex.: meuservidor.exemplo.com") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = sshPort,
                onValueChange = { sshPort = it },
                label = { Text("Porta SSH") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Usuário SSH") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            value = privateKeyFileName.orEmpty(),
            onValueChange = { },
            readOnly = true,
            label = { Text("Chave privada (OpenSSH/PEM)") },
            placeholder = { Text("Nenhum arquivo selecionado") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedButton(
                onClick = { keyFileLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = if (privateKey.isBlank()) {
                        "Selecionar arquivo"
                    } else {
                        "Substituir arquivo"
                    },
                )
            }
            if (!privateKeyFileName.isNullOrBlank()) {
                TextButton(
                    onClick = {
                        privateKey = ""
                        privateKeyFileName = null
                        keyFileError = null
                    },
                ) {
                    Text(text = "Limpar")
                }
            }
        }
        Text(
            text = if (privateKey.isBlank()) {
                "Selecione o arquivo da chave privada. O conteúdo não é exibido por segurança."
            } else {
                "Chave carregada (${privateKey.length} caracteres)."
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        keyFileError?.let { msg ->
            Text(
                text = msg,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        OutlinedTextField(
            value = passphrase,
            onValueChange = { passphrase = it },
            label = { Text("Senha da chave (opcional)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Text(
            text = "Encaminhamento de porta (Freqtrade na VPS)",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = localPort,
                onValueChange = { localPort = it },
                label = { Text("Porta local") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = remoteHost,
                onValueChange = { remoteHost = it },
                label = { Text("Host remoto") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = remotePort,
                onValueChange = { remotePort = it },
                label = { Text("Porta remota") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = "A API do Freqtrade ficará acessível em http://127.0.0.1:$localPort " +
                "no dispositivo enquanto o túnel estiver ativo.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        // Ações.
        Button(
            onClick = { save(true) },
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "Salvar e conectar")
        }
        OutlinedButton(
            onClick = { save(false) },
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "Salvar sem conectar")
        }

        if (state.hasSavedConfig) {
            TextButton(onClick = viewModel::clearSavedConfig) {
                Text(text = "Apagar credenciais salvas")
            }
        }

        // Mensagens de feedback.
        state.error?.let { msg ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
        state.savedMessage?.let { msg ->
            Text(
                text = msg,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Text(
            text = "As credenciais são armazenadas criptografadas (EncryptedSharedPreferences) " +
                "e usadas apenas para estabelecer o túnel SSH.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
