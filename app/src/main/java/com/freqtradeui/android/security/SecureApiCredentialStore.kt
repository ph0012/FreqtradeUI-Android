package com.freqtradeui.android.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import com.freqtradeui.android.data.models.ApiCredentials
import com.freqtradeui.android.data.repository.FreqtradeCredentialStore

/**
 * Cofre cifrado das credenciais da API do Freqtrade, por perfil.
 *
 * Usa EncryptedSharedPreferences (Tink com chave-mestre no Android Keystore),
 * a mesma proteção já empregada na chave privada SSH. A senha da API nunca é
 * gravada em texto puro, nunca aparece em log e não é exibida depois de salva.
 *
 * Arquivo separado do cofre SSH: assim a rotação ou a remoção de um não afeta
 * o outro, e uma eventual migração de formato fica isolada.
 *
 * @param context contexto de aplicação.
 */
class SecureApiCredentialStore(context: Context) : FreqtradeCredentialStore {

    private val prefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            PREFS_NAME,
            MASTER_KEY_ALIAS,
            context.applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun save(profileId: String, credentials: ApiCredentials) {
        prefs.edit()
            .putString(userKey(profileId), credentials.username)
            .putString(passwordKey(profileId), credentials.password)
            .apply()
    }

    override fun load(profileId: String): ApiCredentials? {
        if (!has(profileId)) return null
        val username = prefs.getString(userKey(profileId), null) ?: return null
        val password = prefs.getString(passwordKey(profileId), null) ?: return null
        return ApiCredentials(username = username, password = password)
    }

    override fun has(profileId: String): Boolean = prefs.contains(userKey(profileId))

    override fun delete(profileId: String) {
        prefs.edit()
            .remove(userKey(profileId))
            .remove(passwordKey(profileId))
            .apply()
    }

    private fun userKey(profileId: String) = "$profileId.$F_USER"

    private fun passwordKey(profileId: String) = "$profileId.$F_PASSWORD"

    private companion object {
        const val PREFS_NAME = "freqtrade_api_credentials"
        const val MASTER_KEY_ALIAS = "_freqtrade_api_master_key_"
        const val F_USER = "apiUsername"
        const val F_PASSWORD = "apiPassword"
    }
}