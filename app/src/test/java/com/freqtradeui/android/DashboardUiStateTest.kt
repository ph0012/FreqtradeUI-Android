package com.freqtradeui.android

import com.freqtradeui.android.data.models.ConnectionSnapshot
import com.freqtradeui.android.data.models.TunnelStatus
import com.freqtradeui.android.security.HostKeyFingerprint
import com.freqtradeui.android.ui.dashboard.DashboardUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes de unidade do estado da UI do Dashboard.
 *
 * Garantem que, sem conexão estabelecida, o estado parte desconectado e sem
 * dados financeiros — a UI nunca deve exibir valores simulados como reais, nem
 * apresentar um perfil como conectado quando o serviço está parado.
 */
class DashboardUiStateTest {

    @Test
    fun estadoInicial_estaDesconectadoSemPerfil() {
        val state = DashboardUiState()

        assertNull(state.profile)
        assertNull(state.pendingHostKey)
        assertEquals(TunnelStatus.Disconnected, state.snapshot.status)
        assertNull(state.snapshot.profileId)
        assertNull(state.snapshot.localPort)
        assertNull(state.snapshot.error)
    }

    @Test
    fun snapshotInicial_semTunelNemDecisaoPendente() {
        val snapshot = ConnectionSnapshot()

        assertEquals(TunnelStatus.Disconnected, snapshot.status)
        assertNull(snapshot.localPort)
        assertNull(snapshot.pendingHostKey)
        assertFalse(snapshot.hasPendingHostKey)
        assertFalse(snapshot.isTerminalError)
    }

    // --- Fingerprint de chave de host (base da verificação TOFU) ---

    @Test
    fun fingerprint_geraSha256ComPrefixo() {
        val fp = HostKeyFingerprint.sha256(byteArrayOf(1, 2, 3, 4))
        assertTrue(fp.startsWith("SHA256:"))
        assertEquals(fp, HostKeyFingerprint.sha256(byteArrayOf(1, 2, 3, 4)))
    }

    @Test
    fun fingerprint_chavesDistintas_geramValoresDistintos() {
        val a = HostKeyFingerprint.sha256(byteArrayOf(1, 2, 3))
        val b = HostKeyFingerprint.sha256(byteArrayOf(9, 9, 9))
        assertFalse(a == b)
    }

    @Test
    fun algoritmo_leNomeDoBlobSsh() {
        // Blob: uint32(11) + "ssh-ed25519"
        val name = "ssh-ed25519"
        val blob = java.nio.ByteBuffer.allocate(4 + name.length)
            .putInt(name.length)
            .put(name.toByteArray(Charsets.US_ASCII))
            .array()
        assertEquals("ssh-ed25519", HostKeyFingerprint.algorithm(blob))
    }

    @Test
    fun algoritmo_blobInvalido_retornaDesconhecido() {
        assertEquals("desconhecido", HostKeyFingerprint.algorithm(byteArrayOf(1, 2)))
    }

    @Test
    fun comparacaoTempoConstante_funcionaComoEsperado() {
        val a = byteArrayOf(1, 2, 3)
        assertTrue(HostKeyFingerprint.constantTimeEquals(a, byteArrayOf(1, 2, 3)))
        assertFalse(HostKeyFingerprint.constantTimeEquals(a, byteArrayOf(1, 2, 4)))
    }

    @Test
    fun perfilAtivo_refleteApenasOSnapshot() {
        // O estado só "refere-se" a um perfil quando o snapshot aponta para ele.
        val snapshot = ConnectionSnapshot(
            status = TunnelStatus.Connected,
            profileId = "abc",
            localPort = 18080,
        )
        assertTrue(snapshot.refersTo("abc"))
        assertFalse(snapshot.refersTo("xyz"))
        assertFalse(snapshot.refersTo(null))
    }
}


