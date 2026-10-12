package com.freqtradeui.android.data.repository

import com.freqtradeui.android.data.models.ConnectionProfile
import kotlinx.coroutines.flow.Flow

/**
 * Fronteira de persistência dos perfis.
 *
 * Declarada como interface para que os testes de unidade usem uma
 * implementação em memória, sem depender do Android ou do DataStore.
 *
 * **Segredos não passam por aqui.** A chave privada e a frase secreta são
 * gravadas separadamente pelo `SecureSecretStore`; esta camada lida apenas com
 * dados de configuração.
 */
interface ProfileStorage {

    /** Fluxo dos perfis salvos, emitindo novamente a cada alteração. */
    fun observeProfiles(): Flow<List<ConnectionProfile>>

    /** Lista atual dos perfis (leitura pontual). */
    suspend fun getProfiles(): List<ConnectionProfile>

    /** Insere ou substitui um perfil pelo [ConnectionProfile.id]. */
    suspend fun upsert(profile: ConnectionProfile)

    /** Remove o perfil. Não apaga os segredos — use `SecureSecretStore.delete`. */
    suspend fun delete(profileId: String)

    /** Perfil identificado por [profileId], ou `null`. */
    suspend fun find(profileId: String): ConnectionProfile?
}
