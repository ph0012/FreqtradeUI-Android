package com.freqtradeui.android.security

import com.freqtradeui.android.data.models.ProfileSecret

/**
 * Contrato do cofre de segredos SSH por perfil.
 *
 * Declarado como interface para que os testes de unidade usem uma
 * implementação em memória, sem depender do Android Keystore.
 *
 * **Nenhum método registra segredos em log**, nem em caso de falha.
 */
interface SecretStore {

    /** Grava os segredos do perfil, substituindo os anteriores. */
    fun save(profileId: String, secret: ProfileSecret)

    /** Segredos do perfil, ou `null` se não houver chave gravada. */
    fun load(profileId: String): ProfileSecret?

    /** Indica se o perfil possui chave privada armazenada. */
    fun hasSecret(profileId: String): Boolean

    /**
     * Apaga os segredos do perfil.
     *
     * Só deve ser chamado depois de o túnel ser encerrado: remover a
     * credencial de uma sessão ativa derrubaria a conexão.
     */
    fun delete(profileId: String)
}
