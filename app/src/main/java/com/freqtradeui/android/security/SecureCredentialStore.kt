package com.freqtradeui.android.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import com.freqtradeui.android.ssh.SshConfig

/**
 * Armazenamento seguro de credenciais SSH usando EncryptedSharedPreferences
 * (respaldo criptográfico do Tink, com a chave-mestre no Android Keystore).
 *
 * Nenhuma credencial é escrita em texto puro nem registrada em log. Este
 * componente é a única fronteira de persistência de segredos do aplicativo.
 *
 * @param context contexto de aplicação.
 */
class SecureCredentialStore(context: Context) {

    // A partir de security-crypto 1.1.0, MasterKey está obsoleto: basta
    // informar o alias da chave-mestre, que o Tink a cria no Android Keystore.
    private val prefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            PREFS_NAME,
            MASTER_KEY_ALIAS,
            context.applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /**
     * Persiste a configuração SSH de forma criptografada.
     */
    fun saveConfig(config: SshConfig) {
        prefs.edit()
            .putString(KEY_HOST, config.host)
            .putInt(KEY_SSH_PORT, config.sshPort)
            .putString(KEY_USERNAME, config.username)
            .putString(KEY_PRIVATE_KEY, config.privateKey)
            .putString(KEY_PASSPHRASE, config.privateKeyPassphrase)
            .putString(KEY_REMOTE_HOST, config.remoteHost)
            .putInt(KEY_REMOTE_PORT, config.remotePort)
            .putInt(KEY_LOCAL_PORT, config.localPort)
            .putString(KEY_PRIVATE_KEY_FILE_NAME, config.privateKeyFileName)
            .apply()
    }

    /**
     * Retorna a configuração SSH persistida ou `null` se ainda não houver uma.
     */
    fun loadConfig(): SshConfig? {
        val host = prefs.getString(KEY_HOST, null) ?: return null
        val username = prefs.getString(KEY_USERNAME, null) ?: return null
        val privateKey = prefs.getString(KEY_PRIVATE_KEY, null) ?: return null
        return SshConfig(
            host = host,
            sshPort = prefs.getInt(KEY_SSH_PORT, SshConfig.DEFAULT_SSH_PORT),
            username = username,
            privateKey = privateKey,
            privateKeyPassphrase = prefs.getString(KEY_PASSPHRASE, null),
            remoteHost = prefs.getString(KEY_REMOTE_HOST, "127.0.0.1") ?: "127.0.0.1",
            remotePort = prefs.getInt(KEY_REMOTE_PORT, SshConfig.DEFAULT_REMOTE_PORT),
            localPort = prefs.getInt(KEY_LOCAL_PORT, SshConfig.DEFAULT_LOCAL_PORT),
            privateKeyFileName = prefs.getString(KEY_PRIVATE_KEY_FILE_NAME, null),
        )
    }

    /**
     * Indica se existe uma configuração armazenada.
     */
    fun hasConfig(): Boolean = prefs.contains(KEY_HOST)

    /**
     * Remove todas as credenciais armazenadas.
     */
    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_NAME = "freqtrade_secure_prefs"
        const val MASTER_KEY_ALIAS = "_freqtrade_master_key_"
        const val KEY_HOST = "ssh_host"
        const val KEY_SSH_PORT = "ssh_port"
        const val KEY_USERNAME = "ssh_username"
        const val KEY_PRIVATE_KEY = "ssh_private_key"
        const val KEY_PASSPHRASE = "ssh_passphrase"
        const val KEY_REMOTE_HOST = "ssh_remote_host"
        const val KEY_REMOTE_PORT = "ssh_remote_port"
        const val KEY_LOCAL_PORT = "ssh_local_port"
        const val KEY_PRIVATE_KEY_FILE_NAME = "ssh_private_key_file_name"
    }
}
