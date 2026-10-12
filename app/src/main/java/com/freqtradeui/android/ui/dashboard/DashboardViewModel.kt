package com.freqtradeui.android.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.freqtradeui.android.data.models.ConnectionState
import com.freqtradeui.android.security.SecureCredentialStore
import com.freqtradeui.android.ssh.SshTunnelManager
import com.freqtradeui.android.ssh.TunnelState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Estado imutável da tela de Dashboard.
 *
 * Os dados financeiros permanecem nulos: a integração com a API do Freqtrade
 * ainda não foi implementada. O estado do túnel SSH, no entanto, já é real e
 * refletido aqui — o usuário vê o resultado verdadeiro da tentativa de conexão.
 *
 * @param tunnel estado atual do túnel SSH.
 * @param api estado da API (placeholder: desconectado até a próxima etapa).
 */
data class DashboardUiState(
    val tunnel: TunnelState = TunnelState(),
    val api: ConnectionState = ConnectionState.Disconnected,
) {
    val sshState: ConnectionState get() = tunnel.ssh
    val apiState: ConnectionState get() = api
    val pendingHostKey get() = tunnel.pendingHostKey
    val statusMessage: String? get() = tunnel.message
}

/**
 * ViewModel do Dashboard.
 *
 * Orquestra o túnel SSH e publica seu estado para a UI. A API segue como
 * placeholder (desconectada) até a próxima etapa. As credenciais são lidas do
 * armazenamento seguro apenas quando o usuário solicita a conexão.
 *
 * @param sshTunnelManager gerenciador do túnel SSH (compartilhado).
 * @param credentialStore armazenamento seguro de credenciais.
 */
class DashboardViewModel(
    private val sshTunnelManager: SshTunnelManager,
    private val credentialStore: SecureCredentialStore,
) : ViewModel() {

    private val apiState = MutableStateFlow(ConnectionState.Disconnected)

    /** Estado de UI combinando o túnel real com o placeholder da API. */
    val uiState: StateFlow<DashboardUiState> =
        combine(sshTunnelManager.state, apiState) { tunnel, api ->
            DashboardUiState(tunnel = tunnel, api = api)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DashboardUiState(),
        )

    /**
     * Solicita a conexão do túnel SSH usando a configuração salva.
     * Se não houver configuração, orienta o usuário a configurar a conexão.
     */
    fun connect() {
        val config = credentialStore.loadConfig()
        if (config == null) {
            // Sem configuração salva: orienta sem tentar conectar.
            return
        }
        viewModelScope.launch {
            sshTunnelManager.connect(config)
        }
    }

    /** Encerra o túnel SSH. */
    fun disconnect() {
        sshTunnelManager.disconnect()
        apiState.value = ConnectionState.Disconnected
    }

    /** Aprova a chave de host pendente (TOFU) e reconecta. */
    fun approveHostKey() = sshTunnelManager.approveHostKey()

    /** Rejeita a chave de host pendente. */
    fun rejectHostKey() = sshTunnelManager.rejectHostKey()

    companion object {
        fun factory(
            sshTunnelManager: SshTunnelManager,
            credentialStore: SecureCredentialStore,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras,
            ): T {
                require(modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
                    "ViewModel desconhecido: ${modelClass.name}"
                }
                return DashboardViewModel(sshTunnelManager, credentialStore) as T
            }
        }
    }
}
