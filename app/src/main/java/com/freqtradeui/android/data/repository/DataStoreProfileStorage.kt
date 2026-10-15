package com.freqtradeui.android.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.freqtradeui.android.data.models.ConnectionProfile
import com.freqtradeui.android.data.models.ConnectionMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Implementação de [ProfileStorage] sobre Preferences DataStore.
 *
 * Cada campo do perfil é gravado sob uma chave prefixada pelo `id`, o que
 * permite atualizar um único campo sem reescrever os demais e remove a
 * necessidade de um serializador JSON.
 *
 * **Aqui não há segredos.** Apenas configuração. Chave privada e frase secreta
 * vivem no `SecureSecretStore`, cifradas com chave protegida pelo Android
 * Keystore.
 */
class DataStoreProfileStorage(context: Context) : ProfileStorage {

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
        name = "freqtrade_profiles",
    )
    private val store = context.applicationContext.dataStore

    override fun observeProfiles(): Flow<List<ConnectionProfile>> =
        store.data.map { prefs -> readAll(prefs) }

    override suspend fun getProfiles(): List<ConnectionProfile> =
        readAll(store.data.first())

    override suspend fun upsert(profile: ConnectionProfile) {
        store.edit { prefs ->
            val ids = prefs[KEY_IDS].orEmpty().toMutableSet()
            ids += profile.id
            prefs[KEY_IDS] = ids

            prefs[stringKey(profile.id, F_NAME)] = profile.name
            prefs[stringKey(profile.id, F_HOST)] = profile.host
            prefs[intKey(profile.id, F_SSH_PORT)] = profile.sshPort
            prefs[stringKey(profile.id, F_USERNAME)] = profile.username
            prefs[stringKey(profile.id, F_REMOTE_HOST)] = profile.remoteHost
            prefs[intKey(profile.id, F_REMOTE_PORT)] = profile.remotePort
            prefs[intKey(profile.id, F_LOCAL_PORT)] = profile.localPort
            prefs[stringKey(profile.id, F_CONNECTION_MODE)] = profile.connectionMode.name
            prefs[stringKey(profile.id, F_DIRECT_URL)] = profile.directUrl
            profile.privateKeyFileName?.let {
                prefs[stringKey(profile.id, F_KEY_FILE_NAME)] = it
            }
            profile.hostKeyFingerprint?.let {
                prefs[stringKey(profile.id, F_HOST_KEY_FP)] = it
            }
            profile.lastConnectedAt?.let {
                prefs[longKey(profile.id, F_LAST_CONNECTED)] = it
            }
        }
    }

    override suspend fun delete(profileId: String) {
        store.edit { prefs ->
            val ids = prefs[KEY_IDS].orEmpty().toMutableSet()
            ids -= profileId
            prefs[KEY_IDS] = ids
            ALL_FIELDS.forEach { field ->
                prefs.remove(stringKey(profileId, field))
                prefs.remove(intKey(profileId, field))
                prefs.remove(longKey(profileId, field))
            }
        }
    }

    override suspend fun find(profileId: String): ConnectionProfile? =
        readAll(store.data.first()).firstOrNull { it.id == profileId }

    private fun readAll(prefs: Preferences): List<ConnectionProfile> {
        val ids = prefs[KEY_IDS].orEmpty()
        return ids.mapNotNull { id -> readOne(prefs, id) }
            .sortedBy { it.name.lowercase() }
    }

    private fun readOne(prefs: Preferences, id: String): ConnectionProfile? {
        val name = prefs[stringKey(id, F_NAME)] ?: return null
        val host = prefs[stringKey(id, F_HOST)] ?: return null
        val username = prefs[stringKey(id, F_USERNAME)] ?: return null
        return ConnectionProfile(
            id = id,
            name = name,
            host = host,
            sshPort = prefs[intKey(id, F_SSH_PORT)] ?: ConnectionProfile.DEFAULT_SSH_PORT,
            username = username,
            remoteHost = prefs[stringKey(id, F_REMOTE_HOST)]
                ?: ConnectionProfile.DEFAULT_REMOTE_HOST,
            remotePort = prefs[intKey(id, F_REMOTE_PORT)]
                ?: ConnectionProfile.DEFAULT_REMOTE_PORT,
            localPort = prefs[intKey(id, F_LOCAL_PORT)]
                ?: ConnectionProfile.DEFAULT_LOCAL_PORT,
            privateKeyFileName = prefs[stringKey(id, F_KEY_FILE_NAME)],
            hostKeyFingerprint = prefs[stringKey(id, F_HOST_KEY_FP)],
            lastConnectedAt = prefs[longKey(id, F_LAST_CONNECTED)],
            connectionMode = prefs[stringKey(id, F_CONNECTION_MODE)]
                ?.let { mode -> runCatching { ConnectionMode.valueOf(mode) }.getOrNull() }
                ?: ConnectionMode.SshTunnel,
            directUrl = prefs[stringKey(id, F_DIRECT_URL)].orEmpty(),
        )
    }

    private fun stringKey(id: String, field: String) = stringPreferencesKey("p.$id.$field")
    private fun intKey(id: String, field: String) = intPreferencesKey("p.$id.$field")
    private fun longKey(id: String, field: String) = longPreferencesKey("p.$id.$field")

    private companion object {
        const val F_NAME = "name"
        const val F_HOST = "host"
        const val F_SSH_PORT = "sshPort"
        const val F_USERNAME = "username"
        const val F_REMOTE_HOST = "remoteHost"
        const val F_REMOTE_PORT = "remotePort"
        const val F_LOCAL_PORT = "localPort"
        const val F_KEY_FILE_NAME = "keyFileName"
        const val F_HOST_KEY_FP = "hostKeyFp"
        const val F_LAST_CONNECTED = "lastConnected"
        const val F_CONNECTION_MODE = "connectionMode"
        const val F_DIRECT_URL = "directUrl"
        val ALL_FIELDS = listOf(
            F_NAME, F_HOST, F_SSH_PORT, F_USERNAME, F_REMOTE_HOST, F_REMOTE_PORT,
            F_LOCAL_PORT, F_KEY_FILE_NAME, F_HOST_KEY_FP, F_LAST_CONNECTED,
            F_CONNECTION_MODE, F_DIRECT_URL,
        )
        val KEY_IDS = stringSetPreferencesKey("profile_ids")
    }
}
