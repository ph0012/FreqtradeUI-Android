package com.freqtradeui.android.security

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Armazena as chaves de host SSH já confiadas pelo usuário (Trust On First
 * Use). Persistido de forma criptografada; garante que, após a primeira
 * aprovação manual, uma chave de host diferente para o mesmo host seja
 * detectada — protegendo contra ataques man-in-the-middle.
 */
class HostKeyStore(context: Context) {

    private val prefs: android.content.SharedPreferences by lazy {
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
     * Retorna a chave confiada para [host] (codificada em Base64) ou `null`.
     */
    fun getTrustedKey(host: String): ByteArray? {
        val encoded = prefs.getString(host, null) ?: return null
        return Base64.decode(encoded, Base64.NO_WRAP)
    }

    /**
     * Grava/aprova a chave de host para [host].
     */
    fun trustKey(host: String, key: ByteArray) {
        prefs.edit().putString(host, Base64.encodeToString(key, Base64.NO_WRAP)).apply()
    }

    /**
     * Remove a chave confiada de [host].
     */
    fun remove(host: String) {
        prefs.edit().remove(host).apply()
    }

    private companion object {
        const val PREFS_NAME = "freqtrade_hostkeys"
    }
}
