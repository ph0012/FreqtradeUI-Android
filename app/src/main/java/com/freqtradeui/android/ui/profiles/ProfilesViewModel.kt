package com.freqtradeui.android.ui.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freqtradeui.android.FreqtradeApplication
import com.freqtradeui.android.data.models.ConnectionProfile
import com.freqtradeui.android.data.models.ConnectionSnapshot
import com.freqtradeui.android.data.models.ProfileSecret
import com.freqtradeui.android.data.models.TunnelStatus
import com.freqtradeui.android.data.repository.ConnectionProfileRepository
import com.freqtradeui.android.ssh.PendingHostKey
import com.freqtradeui.android.ssh.SshConnectionService
import com.freqtradeui.android.data.repository.FreqtradeRepository
import com.freqtradeui.android.ssh.SshTunnelManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Estado da lista de perfis.
 *
 * @param profiles perfis cadastrados, ordenados por nome.
 * @param snapshot estado **real** do túnel, vindo do gerenciador compartilhado
 *        com o serviço — nunca uma cópia local otimista.
 * @param loading `true` enquanto a leitura inicial não chega.
 * @param switchTarget perfil para o qual o usuário quer trocar enquanto outro
 *        está conectado; a troca só ocorre após confirmação.
 * @param deleteTarget perfil aguardando confirmação de exclusão.
 * @param message aviso de operação concluída (não é erro de conexão).
 */
data class ProfilesUiState(
    val profiles: List<ConnectionProfile> = emptyList(),
    val snapshot: ConnectionSnapshot = ConnectionSnapshot(),
    val loading: Boolean = true,
    val switchTarget: ConnectionProfile? = null,
    val deleteTarget: ConnectionProfile? = null,
    val message: String? = null,
    val pendingHostKey: PendingHostKey? = null,
) {
    /** `true` quando não há perfis salvos (dispara a tela de boas-vindas). */
    val isEmpty: Boolean get() = profiles.isEmpty() && !loading

    /** `true` quando há túnel em andamento para algum perfil. */
    val hasActiveTunnel: Boolean get() = snapshot.status.isActive

    /** Perfil referenciado pelo snapshot, se ainda existir na lista. */
    val activeProfile: ConnectionProfile?
        get() = snapshot.profileId?.let { id -> profiles.firstOrNull { it.id == id } }

    /** Indica se o perfil é o que está conectado/conectando. */
    fun isActive(profile: ConnectionProfile): Boolean = snapshot.refersTo(profile.id)

    /**
     * Estado do perfil para exibição.
     *
     * Perfis sem túnel aparecem como desconectados mesmo que [lastConnectedAt]
     * exista: "conectou antes" não é "está conectado".
     */
    fun statusOf(profile: ConnectionProfile) =
        if (isActive(profile)) snapshot.status else null

    /** `true` quando há perfis cadastrados (exibe a lista). */
    val hasProfiles: Boolean get() = profiles.isNotEmpty()

    /** Identificador do perfil ao qual o snapshot se refere. */
    val activeProfileId: String? get() = snapshot.profileId

    /** Perfil aguardando confirmação de exclusão (identificador). */
    val pendingDelete: String? get() = deleteTarget?.id

    /** Perfil para o qual a troca aguarda confirmação (identificador). */
    val pendingProfileSwitch: String? get() = switchTarget?.id
}


/**
 * ViewModel da seleção de perfis e do ciclo de vida do túnel.
 *
 * O estado nunca é inventado pela UI: tudo vem de [tunnelManager], a mesma
 * instância usada pelo serviço em foreground.
 *
 * Conectar sempre passa pelo [SshConnectionService], porque exige consentimento
 * explícito do usuário e o túnel precisa sobreviver à destruição da Activity.
 *
 * @param repo repositório de perfis e segredos.
 * @param tunnelManager gerenciador do túnel SSH.
 */
class ProfilesViewModel(
    private val repo: ConnectionProfileRepository,
    private val tunnelManager: SshTunnelManager,
    private val api: FreqtradeRepository,
) : ViewModel() {

    /** Decisões locais pendentes (confirmações), separadas do estado real. */
    private val local = MutableStateFlow(LocalState())

    /** Estado combinado: perfis + estado real do túnel. */
    val uiState: StateFlow<ProfilesUiState> = combine(
        repo.observeProfiles(),
        tunnelManager.state,
        local,
    ) { profiles, snapshot, loc ->
        ProfilesUiState(
            profiles = profiles,
            snapshot = snapshot,
            loading = false,
            switchTarget = loc.switchTargetId?.let { id ->
                profiles.firstOrNull { it.id == id }
            },
            deleteTarget = loc.deleteTargetId?.let { id ->
                profiles.firstOrNull { it.id == id }
            },
            message = loc.message,
            pendingHostKey = if (snapshot.hasPendingHostKey) tunnelManager.pendingHostKey else null,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfilesUiState(),
    )

    /**
     * Seleciona um perfil.
     *
     * Se outro túnel está ativo, apenas registra a intenção de troca: derrubar
     * a conexão atual em silêncio nunca é aceitável.
     */
    fun onProfileSelected(profileId: String) {
        val state = uiState.value
        if (state.hasActiveTunnel && state.snapshot.profileId != profileId) {
            local.value = local.value.copy(switchTargetId = profileId)
        }
    }

    /** Confirma a troca: encerra o túnel atual e conecta ao novo. */
    fun confirmSwitch() {
        val target = local.value.switchTargetId ?: return
        local.value = local.value.copy(switchTargetId = null)
        // Encerra antes de conectar: só um túnel ativo por vez nesta etapa.
        tunnelManager.disconnect()
        connect(target)
    }

    /** Cancela a troca pendente. */
    fun cancelSwitch() {
        local.value = local.value.copy(switchTargetId = null)
    }

    /**
     * Conecta ao perfil, iniciando o serviço em foreground.
     *
     * @return `false` se o perfil não tem chave privada gravada — a UI deve
     *         orientar o usuário a editar o perfil.
     */
    fun connect(profileId: String) {
        local.value = local.value.copy(message = null)
        val profile = uiState.value.profiles.firstOrNull { it.id == profileId } ?: return
        viewModelScope.launch {
            // Sem chave não há o que conectar: evita um erro de autenticação
            // óbvio e explica o que falta.
            if (!repo.hasSecret(profileId)) {
                local.value = local.value.copy(
                    message = "Perfil \"${profile.name}\" está sem chave privada. " +
                        "Edite o perfil e selecione o arquivo da chave.",
                )
                return@launch
            }
            SshConnectionService.start(FreqtradeApplication.appContext, profileId)
        }
    }

    /** Desconecta o túnel ativo. */
    fun disconnect() {
        tunnelManager.disconnect()
    }

    /** Aprova a chave de host pendente (TOFU). */
    fun approveHostKey() {
        tunnelManager.approveHostKey()
    }

    /** Rejeita a chave de host pendente. */
    fun rejectHostKey() {
        tunnelManager.rejectHostKey()
    }

    /**
     * Perfil pelo identificador, para o editor pré-preencher o formulário.
     *
     * Chamado dentro de `LaunchedEffect`; a leitura é rápida (DataStore já
     * aquecido), mas mesmo assim roda fora da thread principal.
     */
    suspend fun profile(profileId: String): ConnectionProfile? = repo.profile(profileId)

    /**
     * Grava a credencial no cofre cifrado.
     *
     * Feito antes de salvar o perfil: assim a chave já está indexada pelo id
     * que o formulário gerou.
     */
    fun saveSecret(profileId: String, secret: ProfileSecret) = repo.saveSecret(profileId, secret)

    /**
     * Usuario da API ja gravado para o perfil, para pre-preencher o formulario.
     *
     * A **senha nunca e devolvida**: depois de salva, nao ha caminho para
     * exibi-la de novo. Para troca-la, o usuario digita uma nova.
     */
    fun apiUsername(profileId: String): String = api.username(profileId).orEmpty()

    /** Indica se o perfil ja possui credenciais da API configuradas. */
    fun hasApiCredentials(profileId: String): Boolean = api.hasCredentials(profileId)

    /**
     * Grava as credenciais da API do perfil, no cofre cifrado.
     *
     * A sessao do perfil e descartada: as credenciais mudaram, o token anterior
     * nao vale mais.
     */
    fun saveApiCredentials(profileId: String, username: String, password: String) {
        api.saveCredentials(profileId, username, password)
        api.clearSession()
    }

    /**
     * Persiste o perfil (novo ou edição) e navega com o id salvo.
     *
     * A edição preserva o `id`, mantendo credencial e chave de host aprovada
     * associadas ao mesmo perfil. Falhas de validação vão para
     * [ProfilesUiState.message] — nada é silenciado.
     *
     * @param draft rascunho validado pelo formulário.
     * @param originalId id do perfil sendo editado, ou `null` em cadastro.
     * @param onDone chamado após gravar, com o id persistido.
     */
    fun save(draft: ConnectionProfile, originalId: String?, onDone: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val saved = if (originalId == null) {
                    repo.createProfile(draft)
                } else {
                    repo.saveProfile(draft)
                    draft
                }
                onDone(saved.id)
            } catch (e: IllegalArgumentException) {
                local.value = local.value.copy(
                    message = e.message ?: "Perfil inválido.",
                )
            }
        }
    }

    /** Solicita confirmação de exclusão. */
    fun requestDelete(profileId: String) {
        local.value = local.value.copy(deleteTargetId = profileId)
    }

    /** Cancela a exclusão. */
    fun cancelDelete() {
        local.value = local.value.copy(deleteTargetId = null)
    }

    /**
     * Exclui o perfil. Se há túnel ativo para ele, encerra-o primeiro — remover
     * a credencial com a sessão aberta a derrubaria sem aviso.
     */
    fun confirmDelete() {
        val profileId = local.value.deleteTargetId ?: return
        local.value = local.value.copy(deleteTargetId = null, message = "Perfil excluído.")
        viewModelScope.launch {
            val wasActive = uiState.value.snapshot.profileId == profileId
            if (wasActive) {
                tunnelManager.disconnect()
                // Espera a limpeza da sessão antes de apagar a credencial.
                tunnelManager.state.first { it.status == TunnelStatus.Disconnected }
            }
            repo.deleteProfile(profileId, isTunnelActive = false)
        }
    }

    /** Limpa o aviso exibido. */
    fun consumeMessage() {
        local.value = local.value.copy(message = null)
    }

    /** Decisões locais pendentes. */
    private data class LocalState(
        val switchTargetId: String? = null,
        val deleteTargetId: String? = null,
        val message: String? = null,
    )
}

