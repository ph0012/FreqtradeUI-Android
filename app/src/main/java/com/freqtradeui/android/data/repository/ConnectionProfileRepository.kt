package com.freqtradeui.android.data.repository

import com.freqtradeui.android.data.models.ConnectionProfile
import com.freqtradeui.android.data.models.ProfileSecret
import com.freqtradeui.android.security.SecretStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Única fronteira da interface com os perfis de conexão.
 *
 * Reúne dois repositórios de naturezas distintas: [ProfileStorage] guarda a
 * configuração (não sensível) e [SecretStore] guarda a chave privada e a
 * frase secreta (sensível, cifrado). A UI nunca fala com eles diretamente.
 *
 * Regras de segurança aplicadas aqui:
 * - O `id` de um perfil é gerado pelo app e **preservado em toda edição**, para
 *   que credenciais e chaves de host aprovadas não fiquem órfãs.
 * - [deleteProfile] só apaga os segredos **depois** de confirmar que o túnel
 *   está encerrado — chamador passa `isTunnelActive` obtido do serviço.
 * - [deleteProfile] exige confirmação explícita (`confirmed`) para remover um
 *   perfil que possui túnel ativo.
 */
class ConnectionProfileRepository(
    private val storage: ProfileStorage,
    private val secrets: SecretStore,
) {

    private val mutex = Mutex()

    /** Perfis salvos, emitindo a cada alteração. */
    fun observeProfiles(): Flow<List<ConnectionProfile>> = storage.observeProfiles()

    /** Lista pontual dos perfis salvos. */
    suspend fun profiles(): List<ConnectionProfile> = storage.getProfiles()

    /** Perfil por identificador. */
    suspend fun profile(profileId: String): ConnectionProfile? = storage.find(profileId)

    /** Indica se existe algum perfil salvo (decide entre welcome e lista). */
    suspend fun hasProfiles(): Boolean = storage.getProfiles().isNotEmpty()

    /** Segredos do perfil, ou `null` se não houver chave gravada. */
    fun secret(profileId: String): ProfileSecret? = secrets.load(profileId)

    /** Indica se o perfil possui chave privada armazenada. */
    fun hasSecret(profileId: String): Boolean = secrets.hasSecret(profileId)

    /**
     * Cria um perfil.
     *
     * O [ConnectionProfile.id] do rascunho é **preservado** quando já vier
     * preenchido: o editor gera o id antes de gravar a chave no cofre, e
     * trocá-lo aqui deixaria a credencial órfã.
     *
     * @param secret credencial opcional; quando nula, a chave é gravada
     *   separadamente por [saveSecret].
     * @return o perfil persistido.
     */
    suspend fun createProfile(
        draft: ConnectionProfile,
        secret: ProfileSecret? = null,
    ): ConnectionProfile = mutex.withLock {
        val error = draft.validationError()
        require(error == null) { error ?: "Perfil inválido." }
        val profile = if (draft.id.isBlank()) {
            draft.copy(id = ConnectionProfile.newId())
        } else {
            draft
        }
        storage.upsert(profile)
        if (secret != null) secrets.save(profile.id, secret)
        profile
    }

    /** Grava somente a credencial do perfil, no cofre cifrado. */
    fun saveSecret(profileId: String, secret: ProfileSecret) = secrets.save(profileId, secret)

    /**
     * Atualiza um perfil preservando o `id` e, por consequência, a chave de
     * host já aprovada e a credencial gravada.
     *
     * Metadados que o formulário não edita nunca são apagados por engano:
     * uma cópia desatualizada do perfil não pode remover a impressão digital
     * aprovada (isso faria o app esquecer a identidade do servidor) nem o
     * histórico da última conexão.
     *
     * @param secret nova credencial, ou `null` para manter a atual.
     * @throws IllegalArgumentException se o perfil estiver inválido.
     */
    suspend fun saveProfile(draft: ConnectionProfile, secret: ProfileSecret? = null) =
        mutex.withLock {
            val error = draft.validationError()
            require(error == null) { error ?: "Perfil inválido." }
            val stored = storage.find(draft.id)
            val merged = if (stored == null) {
                draft
            } else {
                draft.copy(
                    hostKeyFingerprint = draft.hostKeyFingerprint ?: stored.hostKeyFingerprint,
                    lastConnectedAt = draft.lastConnectedAt ?: stored.lastConnectedAt,
                )
            }
            storage.upsert(merged)
            if (secret != null) secrets.save(merged.id, secret)
            Unit
        }

    /**
     * Remove um perfil.
     *
     * @param isTunnelActive indica se este perfil possui túnel em execução.
     * @param confirmed confirmação explícita do usuário. Exigida quando há
     *        túnel ativo, para nunca encerrar uma sessão sem pedido.
     * @return `false` quando a exclusão foi recusada (túnel ativo sem
     *   confirmação); `true` quando o perfil e seus segredos foram removidos.
     *
     * Os segredos só são apagados após a remoção da configuração e **somente**
     * quando não há sessão ativa: remover a credencial de um túnel vivo o
     * derrubaria.
     */
    suspend fun deleteProfile(
        profileId: String,
        isTunnelActive: Boolean = false,
        confirmed: Boolean = false,
    ): Boolean = mutex.withLock {
        if (isTunnelActive && !confirmed) return false
        storage.delete(profileId)
        if (!isTunnelActive) secrets.delete(profileId)
        true
    }

    /**
     * Grava a impressão digital da chave de host aprovada pelo usuário.
     *
     * Persistir a decisão TOFU no perfil permite exibir o estado de verificação
     * do servidor na lista sem consultar o cofre de chaves.
     */
    suspend fun saveApprovedHostKey(profileId: String, fingerprint: String) =
        mutex.withLock {
            storage.find(profileId)?.let {
                storage.upsert(it.copy(hostKeyFingerprint = fingerprint))
            }
            Unit
        }

    /** Registra o instante da última conexão bem sucedida. */
    suspend fun markConnected(profileId: String, timestampMillis: Long) = mutex.withLock {
        storage.find(profileId)?.let {
            storage.upsert(it.copy(lastConnectedAt = timestampMillis))
        }
        Unit
    }
}
