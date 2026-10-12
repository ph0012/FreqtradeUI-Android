package com.freqtradeui.android.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
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

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            PREFS_NAME,
            masterKey,
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
            .putInt(KEY_REMOTE_PORT, config.remotePort)
            .putInt(KEY_LOCAL_PORT, config.localPort)
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
            remotePort = prefs.getInt(KEY_REMOTE_PORT, SshConfig.DEFAULT_REMOTE_PORT),
            localPort = prefs.getInt(KEY_LOCAL_PORT, SshConfig.DEFAULT_LOCAL_PORT),
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
        const val KEY_HOST = "ssh_host"
        const val KEY_SSH_PORT = "ssh_port"
        const val KEY_USERNAME = "ssh_username"
        const val KEY_PRIVATE_KEY = "ssh_private_key"
        const val KEY_PASSPHRASE = "ssh_passphrase"
        const val KEY_REMOTE_PORT = "ssh_remote_port"
        const val KEY_LOCAL_PORT = "ssh_local_port"
    }
}
