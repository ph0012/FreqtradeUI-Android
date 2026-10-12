package com.freqtradeui.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freqtradeui.android.security.SecureCredentialStore
import com.freqtradeui.android.ssh.SshConfig
import com.freqtradeui.android.ssh.SshTunnelManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Campos do formulário de configuração SSH.
 *
 * @param hasSavedConfig indica se já existe uma configuração persistida
 *        (usado para exibir/ocultar a chave salva sem expô-la na UI).
 * @param isConnected indica se o túnel está ativo.
 * @param isSaving indica que uma operação de salvamento está em andamento.
 * @param error mensagem de erro de validação/persistência, se houver.
 * @param savedMessage mensagem de confirmação de salvamento, se houver.
 */
data class SettingsUiState(
    val hasSavedConfig: Boolean = false,
    val isConnected: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val savedMessage: String? = null,
)

/**
 * ViewModel de configurações.
 *
 * Persiste a configuração SSH de forma criptografada e dispara a conexão do
 * túnel. A chave privada é entregue apenas em memória ao gerenciador de túnel
 * e gravada cifrada pelo [SecureCredentialStore]; nunca é exposta na interface.
 *
 * @param credentialStore armazenamento seguro de credenciais.
 * @param sshTunnelManager gerenciador do túnel SSH.
 */
class SettingsViewModel(
    private val credentialStore: SecureCredentialStore,
    private val sshTunnelManager: SshTunnelManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        _uiState.value = _uiState.value.copy(hasSavedConfig = credentialStore.hasConfig())
    }

    /**
     * Salva a configuração SSH informada (campos vazios inválidos são rejeitados)
     * e, opcionalmente, inicia a conexão do túnel.
     *
     * @param host host da VPS.
     * @param sshPort porta SSH.
     * @param username usuário SSH.
     * @param privateKey chave privada no formato OpenSSH/PEM.
     * @param passphrase senha da chave privada, se houver.
     * @param remoteHost host remoto (loopback do Freqtrade na VPS).
     * @param remotePort porta do Freqtrade na VPS.
     * @param connectAfterSave inicia o túnel logo após salvar.
     */
    fun save(
        host: String,
        sshPort: String,
        username: String,
        privateKey: String,
        passphrase: String,
        remoteHost: String,
        remotePort: String,
        connectAfterSave: Boolean,
    ) {
        val parsedPort = sshPort.toIntOrNull()
        val parsedRemotePort = remotePort.toIntOrNull()
        if (parsedPort == null || parsedRemotePort == null) {
            _uiState.value = _uiState.value.copy(error = "Portas devem ser números inteiros.")
            return
        }
        if (host.isBlank() || username.isBlank() || privateKey.isBlank()) {
            _uiState.value = _uiState.value.copy(
                error = "Host, usuário e chave privada são obrigatórios.",
            )
            return
        }

        val config = try {
            SshConfig(
                host = host.trim(),
                sshPort = parsedPort,
                username = username.trim(),
                privateKey = privateKey,
                privateKeyPassphrase = passphrase.takeIf { it.isNotBlank() },
                remoteHost = remoteHost.trim().ifBlank { "127.0.0.1" },
                remotePort = parsedRemotePort,
            )
        } catch (e: IllegalArgumentException) {
            _uiState.value = _uiState.value.copy(error = e.message ?: "Configuração inválida.")
            return
        }

        _uiState.value = _uiState.value.copy(isSaving = true, error = null, savedMessage = null)
        viewModelScope.launch {
            credentialStore.saveConfig(config)
            _uiState.value = _uiState.value.copy(
                isSaving = false,
                hasSavedConfig = true,
                savedMessage = "Configuração salva com segurança.",
            )
            if (connectAfterSave) {
                sshTunnelManager.connect(config)
            }
        }
    }

    /** Remove a configuração salva e encerra o túnel. */
    fun clearSavedConfig() {
        credentialStore.clear()
        sshTunnelManager.disconnect()
        _uiState.value = SettingsUiState()
    }

    /** Limpa mensagens transitórias de erro/confirmação. */
    fun consumeMessages() {
        _uiState.value = _uiState.value.copy(error = null, savedMessage = null)
    }
}
