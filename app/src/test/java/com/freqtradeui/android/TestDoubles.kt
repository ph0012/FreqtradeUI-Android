package com.freqtradeui.android

import com.freqtradeui.android.data.models.ConnectionProfile
import com.freqtradeui.android.data.models.ProfileSecret
import com.freqtradeui.android.data.repository.ConnectionProfileRepository
import com.freqtradeui.android.data.repository.ProfileStorage
import com.freqtradeui.android.security.SecretStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Duplos de teste das camadas de persistencia.
 *
 * Ficam no conjunto de testes - nao no principal - para que o `shrinkResources`
 * nao os remova do APK e para que nao sejam distribuidas com o app.
 *
 * Usam apenas Kotlin padrao e corrotinas, entao rodam na JVM sem emulador.
 */

class InMemoryProfileStorage(
    initial: List<ConnectionProfile> = emptyList(),
) : ProfileStorage {

    private val profiles = MutableStateFlow(initial.sortedBy { it.name.lowercase() })

    override fun observeProfiles(): Flow<List<ConnectionProfile>> = profiles.asStateFlow()

    override suspend fun getProfiles(): List<ConnectionProfile> = profiles.value

    override suspend fun upsert(profile: ConnectionProfile) {
        val current = profiles.value.toMutableList()
        val index = current.indexOfFirst { it.id == profile.id }
        if (index >= 0) current[index] = profile else current += profile
        profiles.value = current.sortedBy { it.name.lowercase() }
    }

    override suspend fun delete(profileId: String) {
        profiles.value = profiles.value.filterNot { it.id == profileId }
    }

    override suspend fun find(profileId: String): ConnectionProfile? =
        profiles.value.firstOrNull { it.id == profileId }
}

class InMemorySecretStore : SecretStore {

    private val secrets = mutableMapOf<String, ProfileSecret>()

    override fun save(profileId: String, secret: ProfileSecret) {
        secrets[profileId] = secret
    }

    override fun load(profileId: String): ProfileSecret? = secrets[profileId]

    override fun hasSecret(profileId: String): Boolean = secrets.containsKey(profileId)

    override fun delete(profileId: String) {
        secrets.remove(profileId)
    }
}

fun inMemoryRepository(
    initial: List<ConnectionProfile> = emptyList(),
): Pair<ConnectionProfileRepository, InMemoryProfileStorage> {
    val storage = InMemoryProfileStorage(initial)
    return ConnectionProfileRepository(storage, InMemorySecretStore()) to storage
}
