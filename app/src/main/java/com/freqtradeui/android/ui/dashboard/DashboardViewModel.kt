package com.freqtradeui.android.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.freqtradeui.android.data.models.ConnectionProfile
import com.freqtradeui.android.data.models.ConnectionSnapshot
import com.freqtradeui.android.data.repository.ConnectionProfileRepository
import com.freqtradeui.android.ssh.PendingHostKey
import com.freqtradeui.android.ssh.SshTunnelManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Estado imutável da tela de Dashboard.
 *
 * Os dados financeiros continuam nulos: a integração com a API do Freqtrade é
 * a próxima etapa. O estado do túnel, porém, já é real — vem do serviço, não de
 * uma cópia local otimista da UI.
 *
 * @param profile perfil ao qual o túnel se refere, quando há um.
 * @param snapshot estado real da conexão.
 * @param pendingHostKey chave de host aguardando decisão TOFU, quando houver.
 */
data class DashboardUiState(
    val profile: ConnectionProfile? = null,
    val snapshot: ConnectionSnapshot = ConnectionSnapshot(),
    val pendingHostKey: PendingHostKey? = null,
)

/**
 * ViewModel do Dashboard.
 *
 * Expõe o perfil ativo e o estado do túnel para a UI. Não decide conectar:
 * essa ação parte da tela de perfis, porque exige consentimento explícito.
 *
 * @param repository acesso aos perfis salvos.
 * @param tunnelManager gerenciador do túnel (compartilhado com o serviço).
 */
class DashboardViewModel(
    private val repository: ConnectionProfileRepository,
    private val tunnelManager: SshTunnelManager,
) : ViewModel() {

    /**
     * Combina o perfil referenciado pelo túnel com o estado da conexão.
     *
     * Se o serviço não estiver ativo, o estado vem desconectado — mesmo que o
     * perfil estivesse conectado antes de o app fechar. A UI nunca apresenta
     * uma conexão que não existe.
     */
    val uiState: StateFlow<DashboardUiState> =
        combine(repository.observeProfiles(), tunnelManager.state) { profiles, snapshot ->
            val profile = snapshot.profileId?.let { id ->
                profiles.firstOrNull { it.id == id }
            }
            DashboardUiState(
                profile = profile,
                snapshot = snapshot,
                pendingHostKey = if (snapshot.hasPendingHostKey) {
                    tunnelManager.pendingHostKey
                } else {
                    null
                },
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DashboardUiState(),
        )

    /** Encerra o túnel ativo. */
    fun disconnect() {
        tunnelManager.disconnect()
    }

    /** Aprova a chave de host pendente (TOFU), após conferência do usuário. */
    fun approveHostKey() {
        tunnelManager.approveHostKey()
    }

    /** Rejeita a chave de host pendente e encerra a tentativa. */
    fun rejectHostKey() {
        tunnelManager.rejectHostKey()
    }

    companion object {
        fun factory(
            repository: ConnectionProfileRepository,
            tunnelManager: SshTunnelManager,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras,
            ): T {
                require(modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
                    "ViewModel desconhecido: ${modelClass.name}"
                }
                return DashboardViewModel(repository, tunnelManager) as T
            }
        }
    }
}

