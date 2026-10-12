package com.freqtradeui.android

import com.freqtradeui.android.data.models.RandomSource
import com.freqtradeui.android.data.models.ReconnectPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes da política de backoff exponencial com teto e jitter.
 *
 * Com aleatoriedade fixa em 0.5 o jitter é exatamente zero, permitindo
 * verificar a progressão nominal (2s, 4s, 8s...).
 */
class ReconnectPolicyTest {

    private fun deterministic() = ReconnectPolicy(random = RandomSource.fixed(0.5))

    @Test
    fun progressaoExponencial_nominal() {
        val p = deterministic()
        assertEquals(2_000L, p.delayFor(1))
        assertEquals(4_000L, p.delayFor(2))
        assertEquals(8_000L, p.delayFor(3))
        assertEquals(16_000L, p.delayFor(4))
        assertEquals(32_000L, p.delayFor(5))
    }

    @Test
    fun esperaTemTetoMaximo() {
        val p = deterministic()
        assertEquals(60_000L, p.delayFor(6))
        assertEquals(60_000L, p.delayFor(7))
        assertEquals(60_000L, p.delayFor(99))
    }

    @Test
    fun tentativaInvalida_eTratadaComoPrimeira() {
        val p = deterministic()
        assertEquals(2_000L, p.delayFor(0))
        assertEquals(2_000L, p.delayFor(-5))
    }

    @Test
    fun jitter_variaParaBaixoSemCairAbaixoDoMinimo() {
        // random 0.0 => offset no limite inferior (-20%): 1600ms.
        val p = ReconnectPolicy(random = RandomSource.fixed(0.0))
        assertEquals(1_600L, p.delayFor(1))
    }

    @Test
    fun jitter_variaParaCimaDentroDoLimite() {
        val p = ReconnectPolicy(random = RandomSource.fixed(0.9999))
        val delay = p.delayFor(1)
        assertTrue("delay=$delay fora da faixa esperada", delay in 2_000L..2_400L)
    }

    @Test
    fun jitterExcessivo_eLimitadoAoMinimo() {
        // Jitter de ±90% com random 0.0 daria 200ms — coercido para 1000ms.
        val p = ReconnectPolicy(random = RandomSource.fixed(0.0), jitterRatio = 0.9)
        assertEquals(1_000L, p.delayFor(1))
    }

    @Test
    fun maxAttempts_refleteTamanhoDaProgressao() {
        val p = deterministic()
        assertTrue(p.maxAttempts >= 5)
    }
}
