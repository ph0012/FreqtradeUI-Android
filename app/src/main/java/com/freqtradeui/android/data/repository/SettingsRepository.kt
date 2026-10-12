package com.freqtradeui.android.data.repository

import com.freqtradeui.android.security.SecureCredentialStore
import com.freqtradeui.android.ssh.SshConfig

/**
 * Repositório de configurações de conexão.
 *
 * É a única camada que a UI usa para ler/gravar a [SshConfig]. Delega a
 * persistência ao [SecureCredentialStore] (credenciais criptografadas),
 * isolando a interface dos detalhes de armazenamento seguro.
 */
class SettingsRepository(
    private val credentialStore: SecureCredentialStore,
) {
    /**
     * Retorna a configuração persistida ou `null` se o usuário ainda não
     * configurou a conexão.
     */
    fun loadConfig(): SshConfig? = credentialStore.loadConfig()

    /**
     * Persiste a configuração de forma criptografada.
     */
    fun saveConfig(config: SshConfig) = credentialStore.saveConfig(config)

    /**
     * Indica se já existe uma configuração armazenada.
     */
    fun hasConfig(): Boolean = credentialStore.hasConfig()

    /**
     * Apaga as credenciais armazenadas (ex.: "esquecer conexão").
     */
    fun clearConfig() = credentialStore.clear()
}
