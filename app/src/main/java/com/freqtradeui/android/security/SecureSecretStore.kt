package com.freqtradeui.android.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import com.freqtradeui.android.data.models.ProfileSecret

/**
 * Cofre de segredos SSH por perfil, usando EncryptedSharedPreferences
 * (Tink com a chave-mestre protegida pelo Android Keystore).
 *
 * Guarda a chave privada e a frase secreta indexadas pelo `id` do perfil, e
 * nada além disso. Separar os segredos do restante da configuração significa
 * que os dados de perfil podem ser lidos ou migrados sem jamais expor
 * material de autenticação.
 *
 * Nenhum segredo é registrado em log — nem em caso de erro.
 *
 * **Limitação documentada:** a proteção vale contra acesso ao sistema de
 * arquivos (outro app, backup, extração via ADB em dispositivo bloqueado). Um
 * código malicioso executando como o mesmo usuário, ou um dispositivo com
 * root, pode conseguir que o Keystore descriptografe. Por isso a chave
 * privada continua sendo segredo do usuário, e o app nunca a exibe na tela.
 *
 * @param context contexto de aplicação.
 */
class SecureSecretStore(context: Context) : SecretStore {

    // security-crypto 1.1.0: basta o alias da chave-mestre, que o Tink cria
    // no Android Keystore. MasterKey está obsoleto nessa versão.
    private val prefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            PREFS_NAME,
            MASTER_KEY_ALIAS,
            context.applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun save(profileId: String, secret: ProfileSecret) {
        prefs.edit()
            .putString(privateKeyKey(profileId), secret.privateKey)
            .putString(passphraseKey(profileId), secret.passphrase)
            .apply()
    }

    override fun load(profileId: String): ProfileSecret? {
        val privateKey = prefs.getString(privateKeyKey(profileId), null) ?: return null
        return ProfileSecret(
            privateKey = privateKey,
            passphrase = prefs.getString(passphraseKey(profileId), null),
        )
    }

    override fun hasSecret(profileId: String): Boolean =
        prefs.contains(privateKeyKey(profileId))

    override fun delete(profileId: String) {
        prefs.edit()
            .remove(privateKeyKey(profileId))
            .remove(passphraseKey(profileId))
            .apply()
    }

    private fun privateKeyKey(profileId: String) = "$profileId.$F_PRIVATE_KEY"
    private fun passphraseKey(profileId: String) = "$profileId.$F_PASSPHRASE"

    private companion object {
        const val PREFS_NAME = "freqtrade_profile_secrets"
        const val MASTER_KEY_ALIAS = "_freqtrade_secrets_master_key_"
        const val F_PRIVATE_KEY = "privateKey"
        const val F_PASSPHRASE = "passphrase"
    }
}
