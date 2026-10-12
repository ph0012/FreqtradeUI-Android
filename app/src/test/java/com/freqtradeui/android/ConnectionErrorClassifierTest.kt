package com.freqtradeui.android

import com.freqtradeui.android.data.models.ConnectionErrorClassifier
import com.freqtradeui.android.data.models.FailureKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes da classificação de falhas: o que pode ser tentado de novo e o que
 * depende do usuário.
 */
class ConnectionErrorClassifierTest {

    @Test
    fun timeoutsERedesSaoTransitorios() {
        val samples = listOf(
            "timeout",
            "Connection timed out",
            "Socket closed",
            "Connection reset by peer",
            "Network is unreachable",
            "No route to host",
            "session is down",
            "channel is not opened",
        )
        samples.forEach { message ->
            val kind = ConnectionErrorClassifier.classify(message)
            assertEquals("mensagem: $message", FailureKind.Transient, kind)
            assertTrue(ConnectionErrorClassifier.isRetryable(kind))
        }
    }

    @Test
    fun falhaDeAutenticacao_ePermanente() {
        val samples = listOf(
            "Auth fail",
            "userauth fails",
            "Invalid privatekey",
            "Permission denied (publickey)",
        )
        samples.forEach { message ->
            val kind = ConnectionErrorClassifier.classify(message)
            assertEquals("mensagem: $message", FailureKind.Authentication, kind)
            assertFalse(ConnectionErrorClassifier.isRetryable(kind))
        }
    }

    @Test
    fun chaveDeHostAlterada_eDiferenteDeDesconhecida() {
        assertEquals(
            FailureKind.ChangedHostKey,
            ConnectionErrorClassifier.classify("Host key has changed"),
        )
        assertEquals(
            FailureKind.ChangedHostKey,
            ConnectionErrorClassifier.classify("hostkey has changed"),
        )
        assertEquals(
            FailureKind.UnknownHostKey,
            ConnectionErrorClassifier.classify("UnknownHostKey: myhost"),
        )
        assertFalse(
            ConnectionErrorClassifier.isRetryable(FailureKind.ChangedHostKey),
        )
    }

    @Test
    fun portaLocalOcupada_eErroDeConfiguracao() {
        val kind = ConnectionErrorClassifier.classify("java.net.BindException: Address already in use")
        assertEquals(FailureKind.Configuration, kind)
        assertFalse(ConnectionErrorClassifier.isRetryable(kind))
    }

    @Test
    fun semMensagem_assumeTransitorio() {
        assertEquals(FailureKind.Transient, ConnectionErrorClassifier.classify(null))
    }

    @Test
    fun mensagemNaoReconhecida_assumeTransitorio() {
        // Melhor uma tentativa barrada pelo backoff do que deixar o usuário
        // sem túnel por causa de um texto inesperado.
        assertEquals(FailureKind.Transient, ConnectionErrorClassifier.classify("xyz"))
    }

    @Test
    fun mensagensAmigaveis_naoVazamDetalhesInternos() {
        val auth = ConnectionErrorClassifier.userMessage(FailureKind.Authentication)
        assertTrue(auth.contains("autenticação", ignoreCase = true))
        // Mensagem permanente sem detalhe não expõe o texto original.
        val changed = ConnectionErrorClassifier.userMessage(FailureKind.ChangedHostKey)
        assertTrue(changed.contains("bloqueada", ignoreCase = true))
    }
}
