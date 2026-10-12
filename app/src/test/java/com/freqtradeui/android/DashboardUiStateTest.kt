package com.freqtradeui.android

import com.freqtradeui.android.data.models.ConnectionState
import com.freqtradeui.android.data.models.ConnectionStatus
import com.freqtradeui.android.security.HostKeyFingerprint
import com.freqtradeui.android.ui.dashboard.DashboardUiState
import com.freqtradeui.android.ssh.TunnelState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes de unidade do estado da UI.
 *
 * Garantem que, sem conexão estabelecida, o estado parte desconectado e sem
 * dados financeiros — a UI nunca deve exibir valores simulados como reais.
 */
class DashboardUiStateTest {

    @Test
    fun estadoInicial_estaDesconectado() {
        val state = DashboardUiState()
        assertEquals(ConnectionState.Disconnected, state.sshState)
        assertEquals(ConnectionState.Disconnected, state.apiState)
        assertNull(state.pendingHostKey)
        assertNull(state.statusMessage)
    }

    @Test
    fun statusConexao_padraoDesconectado() {
        val status = ConnectionStatus()
        assertEquals(ConnectionState.Disconnected, status.ssh)
        assertEquals(ConnectionState.Disconnected, status.api)
    }

    @Test
    fun tunelInicial_semPortaLocal() {
        val tunnel = TunnelState()
        assertEquals(ConnectionState.Disconnected, tunnel.ssh)
        assertNull(tunnel.localPort)
        assertNull(tunnel.pendingHostKey)
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
}

