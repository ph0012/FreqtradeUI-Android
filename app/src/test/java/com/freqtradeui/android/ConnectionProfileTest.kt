package com.freqtradeui.android

import com.freqtradeui.android.data.models.ConnectionProfile
import com.freqtradeui.android.data.models.ConnectionMode
import com.freqtradeui.android.data.models.ConnectionSnapshot
import com.freqtradeui.android.data.models.ProfileSecret
import com.freqtradeui.android.data.models.TunnelStatus
import com.freqtradeui.android.ui.profiles.ProfilesUiState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes de validação de perfis, regras de troca e persistência.
 */
class ConnectionProfileTest {

    private fun validProfile() = ConnectionProfile(
        id = ConnectionProfile.newId(),
        name = "VPS principal",
        host = "203.0.113.10",
        username = "freq",
    )

    // --- Validação ---

    @Test
    fun perfilCompleto_eValido() {
        assertNull(validProfile().validationError())
        assertTrue(validProfile().isValid)
    }

    @Test
    fun camposObrigatorios_vazios_saoRejeitados() {
        assertTrue(validProfile().copy(name = " ").validationError()!!.contains("nome"))
        assertTrue(validProfile().copy(host = "").validationError()!!.contains("endereço"))
        assertTrue(validProfile().copy(username = "").validationError()!!.contains("usuário"))
    }

    @Test
    fun portasForaDosLimites_saoRejeitadas() {
        assertTrue(validProfile().copy(sshPort = 0).validationError() != null)
        assertTrue(validProfile().copy(sshPort = 65536).validationError() != null)
        assertTrue(validProfile().copy(remotePort = 70000).validationError() != null)
        // Portas privilegiadas (< 1024) não podem ser abertas por um app comum.
        assertTrue(validProfile().copy(localPort = 80).validationError() != null)
        assertTrue(validProfile().copy(localPort = 1024).validationError() == null)
        assertTrue(validProfile().copy(localPort = 65535).validationError() == null)
    }

    @Test
    fun perfilDireto_exigeUrlHttpValida() {
        val direct = validProfile().copy(
            connectionMode = ConnectionMode.Direct,
            directUrl = "https://freqtrade.example.com",
        )
        assertNull(direct.validationError())
        assertEquals("https://freqtrade.example.com", direct.destination)

        assertTrue(direct.copy(directUrl = "ftp://freqtrade.example.com")
            .validationError()!!.contains("http://"))
        assertTrue(direct.copy(directUrl = "https://user:pass@freqtrade.example.com")
            .validationError()!!.contains("sem usuário"))
        assertTrue(direct.copy(directUrl = "https://freqtrade.example.com/api/v1")
            .validationError()!!.contains("sem caminho"))
    }

    // --- Regras de seleção/troca de perfil ---

    @Test
    fun estadoSemTunel_nenhumPerfilApareceAtivo() {
        val a = validProfile()
        val state = ProfilesUiState(profiles = listOf(a), loading = false)

        assertFalse(state.hasActiveTunnel)
        assertFalse(state.isActive(a))
        assertNull(state.statusOf(a))
        assertNull(state.activeProfileId)
    }

    @Test
    fun tunelAtivo_noPerfilA_diferenciaDoPerfilB() {
        val a = validProfile()
        val b = validProfile().copy(name = "VPS secundária")
        val state = ProfilesUiState(
            profiles = listOf(a, b),
            snapshot = ConnectionSnapshot(
                status = TunnelStatus.Connected,
                profileId = a.id,
                localPort = 18080,
            ),
            loading = false,
        )

        assertTrue(state.hasActiveTunnel)
        assertEquals(a.id, state.activeProfileId)
        assertTrue(state.isActive(a))
        assertFalse(state.isActive(b))
        // O perfil B nunca conectou aqui — e mesmo que tivesse, "conectou
        // antes" não é "está conectado".
        assertNull(state.statusOf(b))
    }

    @Test
    fun ultimoEstadoConhecido_naoConfundeComConexaoAtiva() {
        val a = validProfile().copy(
            lastConnectedAt = 1_700_000_000_000L,
            hostKeyFingerprint = "SHA256:abc",
        )
        val state = ProfilesUiState(profiles = listOf(a), loading = false)

        // Serviço parado => nada conectado, mesmo com histórico salvo.
        assertEquals(TunnelStatus.Disconnected, state.snapshot.status)
        assertNull(state.statusOf(a))
    }

    @Test
    fun semPerfis_eSinalDeBoasVindas() {
        val state = ProfilesUiState(profiles = emptyList(), loading = false)
        assertTrue(state.isEmpty)
        assertFalse(state.hasProfiles)
    }

    // --- Persistência (repositório com armazenamento em memória) ---

    @Test
    fun criarPerfil_preservaIdEGravaSegredoSeparado() = runBlocking {
        val (repo, storage) = inMemoryRepository()
        val draft = validProfile()
        val secret = ProfileSecret(privateKey = "-----BEGIN KEY-----")

        val saved = repo.createProfile(draft, secret)

        assertEquals(draft.id, saved.id)
        assertNotNull(storage.find(saved.id))
        // Segredo vive só no cofre: o storage de configuração não o contém.
        assertTrue(repo.hasSecret(saved.id))
        assertEquals(secret, repo.secret(saved.id))
    }

    @Test
    fun editarPerfil_preservaIdCredencialEHostKey() = runBlocking {
        val (repo, storage) = inMemoryRepository()
        val saved = repo.createProfile(validProfile(), ProfileSecret("key-material"))
        repo.saveApprovedHostKey(saved.id, "SHA256:aprovada")

        repo.saveProfile(saved.copy(name = "Renomeado"))

        val after = storage.find(saved.id)
        assertNotNull(after)
        assertEquals("Renomeado", after!!.name)
        assertEquals("SHA256:aprovada", after.hostKeyFingerprint)
        assertTrue(repo.hasSecret(saved.id))
    }

    @Test
    fun excluirSemConfirmacao_comTunelAtivo_eRecusado() = runBlocking {
        val (repo, storage) = inMemoryRepository()
        val saved = repo.createProfile(validProfile(), ProfileSecret("key"))

        val removed = repo.deleteProfile(saved.id, isTunnelActive = true, confirmed = false)

        assertFalse(removed)
        // Nem perfil nem segredo podem sair enquanto a sessão depende deles.
        assertNotNull(storage.find(saved.id))
        assertTrue(repo.hasSecret(saved.id))
    }

    @Test
    fun excluirPerfil_removeConfigESegredos() = runBlocking {
        val (repo, storage) = inMemoryRepository()
        val saved = repo.createProfile(validProfile(), ProfileSecret("key"))

        val removed = repo.deleteProfile(saved.id)

        assertTrue(removed)
        assertNull(storage.find(saved.id))
        assertFalse(repo.hasSecret(saved.id))
        assertNull(repo.secret(saved.id))
    }

    @Test
    fun salvarPerfilInvalido_eRejeitado() = runBlocking {
        val (repo, _) = inMemoryRepository()

        var thrown = false
        try {
            repo.saveProfile(validProfile().copy(name = ""))
        } catch (e: IllegalArgumentException) {
            thrown = true
        }
        assertTrue(thrown)
    }
}
