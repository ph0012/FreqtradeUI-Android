package com.freqtradeui.android

import com.freqtradeui.android.data.models.ConnectionEffect
import com.freqtradeui.android.data.models.ConnectionEvent
import com.freqtradeui.android.data.models.ConnectionStateMachine
import com.freqtradeui.android.data.models.FailureKind
import com.freqtradeui.android.data.models.RandomSource
import com.freqtradeui.android.data.models.ReconnectPolicy
import com.freqtradeui.android.data.models.TunnelStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes da maquina de estados da conexao.
 *
 * A maquina e pura (sem I/O), entao cada transicao e cada efeito pedido sao
 * verificados diretamente - inclusive as garantias de seguranca:
 * desconexao nunca vira reconexao, e falha permanente nao tenta de novo.
 */
class ConnectionStateMachineTest {

    /** Jitter fixo em 0.5 => sem variacao: esperas exatas e deterministicas. */
    private fun machine() = ConnectionStateMachine(
        ReconnectPolicy(random = RandomSource.fixed(0.5)),
    )

    @Test
    fun conectar_levaParaConnecting_ePedeAberturaDoTunel() {
        val m = machine()
        val t = m.reduce(ConnectionEvent.Connect("p1"))

        assertEquals(TunnelStatus.Connecting, t.state.status)
        assertEquals("p1", t.state.profileId)
        assertEquals(ConnectionEffect.OpenTunnel, t.effect)
    }

    @Test
    fun conectarDireto_levaParaConnecting_semAbrirTunel() {
        val m = machine()
        val t = m.reduce(ConnectionEvent.ConnectDirect("p-direto"))

        assertEquals(TunnelStatus.Connecting, t.state.status)
        assertEquals("p-direto", t.state.profileId)
        assertEquals(ConnectionEffect.None, t.effect)
    }

    @Test
    fun conectadoDireto_registraSemPortaLocal() {
        val m = machine()
        m.reduce(ConnectionEvent.ConnectDirect("p-direto"))
        val t = m.reduce(ConnectionEvent.Connected(null))

        assertEquals(TunnelStatus.Connected, t.state.status)
        assertNull(t.state.localPort)
        assertNull(t.state.error)
    }

    @Test
    fun conectado_registraPortaEClearErro() {
        val m = machine()
        m.reduce(ConnectionEvent.Connect("p1"))
        val t = m.reduce(ConnectionEvent.Connected(18080))

        assertEquals(TunnelStatus.Connected, t.state.status)
        assertEquals(18080, t.state.localPort)
        assertNull(t.state.error)
        assertEquals(ConnectionEffect.None, t.effect)
    }

    @Test
    fun falhaTransitoria_agendaReconexaoComBackoff() {
        val m = machine()
        m.reduce(ConnectionEvent.Connect("p1"))
        val t = m.reduce(ConnectionEvent.Failed(FailureKind.Transient, "socket timeout"))

        assertEquals(TunnelStatus.Reconnecting, t.state.status)
        assertEquals(1, t.state.attempt)
        assertEquals(2_000L, t.state.nextRetryInMs)
        assertTrue(t.effect is ConnectionEffect.ScheduleRetry)
        assertEquals(2_000L, (t.effect as ConnectionEffect.ScheduleRetry).delayMs)
    }

    @Test
    fun retryDue_reabreTunel_mantendoPerfil() {
        val m = machine()
        m.reduce(ConnectionEvent.Connect("p1"))
        m.reduce(ConnectionEvent.Failed(FailureKind.Transient, null))
        val t = m.reduce(ConnectionEvent.RetryDue)

        assertEquals(TunnelStatus.Connecting, t.state.status)
        assertEquals("p1", t.state.profileId)
        assertEquals(ConnectionEffect.OpenTunnel, t.effect)
        assertNull(t.state.nextRetryInMs)
    }

    @Test
    fun falhaTransitoriaConsecutiva_incrementaTentativa_semDuplicarEspera() {
        val m = machine()
        m.reduce(ConnectionEvent.Connect("p1"))
        m.reduce(ConnectionEvent.Failed(FailureKind.Transient, null))
        val t = m.reduce(ConnectionEvent.Failed(FailureKind.Transient, null))

        assertEquals(2, t.state.attempt)
        assertEquals(4_000L, t.state.nextRetryInMs)
        // Um unico efeito de espera por vez: o gerenciador substitui o
        // agendamento anterior, nunca rodando dois loops de reconexao.
        assertTrue(t.effect is ConnectionEffect.ScheduleRetry)
    }

    @Test
    fun falhaDeAutenticacao_ePermanente_vaiParaError() {
        val m = machine()
        m.reduce(ConnectionEvent.Connect("p1"))
        val t = m.reduce(ConnectionEvent.Failed(FailureKind.Authentication, "Auth fail"))

        assertEquals(TunnelStatus.Error, t.state.status)
        assertEquals(ConnectionEffect.CloseTunnel, t.effect)
        assertNotNull(t.state.error)
    }

    @Test
    fun chaveDeHostAlterada_nuncaReconectaAutomaticamente() {
        val m = machine()
        m.reduce(ConnectionEvent.Connect("p1"))
        val t = m.reduce(ConnectionEvent.Failed(FailureKind.ChangedHostKey, null))

        assertEquals(TunnelStatus.Error, t.state.status)
        assertEquals(ConnectionEffect.CloseTunnel, t.effect)
    }

    @Test
    fun chaveDeHostDesconhecida_aguardaDecisao_semRetry() {
        val m = machine()
        m.reduce(ConnectionEvent.Connect("p1"))
        val t = m.reduce(ConnectionEvent.Failed(FailureKind.UnknownHostKey, null))

        // Nem erro terminal nem nova tentativa: depende do usuario.
        assertEquals(TunnelStatus.Disconnected, t.state.status)
        assertNotNull(t.state.error)
    }

    @Test
    fun tunelCaiAposConectado_disparaReconexao() {
        val m = machine()
        m.reduce(ConnectionEvent.Connect("p1"))
        m.reduce(ConnectionEvent.Connected(18080))
        val t = m.reduce(ConnectionEvent.TunnelLost("session is down"))

        assertEquals(TunnelStatus.Reconnecting, t.state.status)
        assertEquals(1, t.state.attempt)
        assertTrue(t.effect is ConnectionEffect.ScheduleRetry)
    }

    @Test
    fun desconexao_duranteEspera_naoReconecta() {
        val m = machine()
        m.reduce(ConnectionEvent.Connect("p1"))
        m.reduce(ConnectionEvent.Failed(FailureKind.Transient, null))
        val t = m.reduce(ConnectionEvent.Disconnect)

        assertEquals(TunnelStatus.Stopping, t.state.status)
        assertEquals(ConnectionEffect.CloseTunnel, t.effect)

        // O RetryDue atrasado chega depois: deve ser ignorado.
        val late = m.reduce(ConnectionEvent.RetryDue)
        assertEquals(ConnectionEffect.None, late.effect)
        assertEquals(TunnelStatus.Stopping, late.state.status)
    }

    @Test
    fun falhaAposDesconexaoSolicitada_naoViraReconexao() {
        val m = machine()
        m.reduce(ConnectionEvent.Connect("p1"))
        m.reduce(ConnectionEvent.Disconnect)
        val t = m.reduce(ConnectionEvent.TunnelLost("closed"))

        assertEquals(TunnelStatus.Stopping, t.state.status)
        assertEquals(ConnectionEffect.None, t.effect)
    }

    @Test
    fun limpezaConcluida_pedeParadaDoServico() {
        val m = machine()
        m.reduce(ConnectionEvent.Connect("p1"))
        m.reduce(ConnectionEvent.Disconnect)
        val t = m.reduce(ConnectionEvent.CleanupDone)

        assertEquals(TunnelStatus.Disconnected, t.state.status)
        assertNull(t.state.profileId)
        assertEquals(ConnectionEffect.StopService, t.effect)
    }

    @Test
    fun retryDue_semEsperaAtiva_eNeutro() {
        val m = machine()
        val t = m.reduce(ConnectionEvent.RetryDue)

        assertEquals(ConnectionEffect.None, t.effect)
        assertEquals(TunnelStatus.Disconnected, t.state.status)
    }

    @Test
    fun novoCicloAposLimpeza_conectaDeNovo() {
        // Regressão do bug "Encerrando" travado: após Disconnect + CleanupDone
        // a máquina precisa aceitar um novo Connect limpo.
        val m = machine()
        m.reduce(ConnectionEvent.Connect("p1"))
        m.reduce(ConnectionEvent.Disconnect)
        val cleanup = m.reduce(ConnectionEvent.CleanupDone)
        assertEquals(TunnelStatus.Disconnected, cleanup.state.status)
        assertNull(cleanup.state.profileId)

        val t = m.reduce(ConnectionEvent.Connect("p2"))

        assertEquals(TunnelStatus.Connecting, t.state.status)
        assertEquals("p2", t.state.profileId)
        assertEquals(ConnectionEffect.OpenTunnel, t.effect)
    }
}