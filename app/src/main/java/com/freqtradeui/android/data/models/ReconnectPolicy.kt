package com.freqtradeui.android.data.models

/**
 * Política de espera entre tentativas de reconexão.
 *
 * Backoff exponencial com teto e **jitter** (variação aleatória). O jitter
 * importa: sem ele, todos os clientes desconectados por uma queda de energia
 * tentam reconectar no mesmo instante, sobrecarregando o servidor.
 *
 * A classe é pura — recebe a aleatoriedade por injeção — para que os testes
 * possam verificar a progressão exata das esperas.
 *
 * @param random fonte de aleatoriedade para o jitter.
 * @param jitterRatio fração de variação aplicada sobre a espera base
 *        (0.2 = ±20%).
 */
class ReconnectPolicy(
    private val random: RandomSource = RandomSource.Default,
    private val jitterRatio: Double = JITTER_RATIO,
) {
    private val delays = listOf(
        2_000L,
        4_000L,
        8_000L,
        16_000L,
        32_000L,
        60_000L,
        60_000L,
    )

    /**
     * Espera antes da tentativa [attempt] (1 = primeira reconexão).
     *
     * @return milissegundos a aguardar, já com jitter aplicado e limitado a
     *   [MAX_DELAY_MS].
     */
    fun delayFor(attempt: Int): Long {
        val n = attempt.coerceAtLeast(1)
        val base = delays[(n - 1).coerceAtMost(delays.lastIndex)]
        val jitterRange = base * jitterRatio
        // Variação simétrica em torno da base: evita picos sincronizados.
        val offset = (random.nextDouble() * 2 - 1) * jitterRange
        return (base + offset).toLong().coerceIn(MIN_DELAY_MS, MAX_DELAY_MS)
    }

    /** Número de tentativas antes de a espera atingir o teto. */
    val maxAttempts: Int get() = delays.size

    companion object {
        const val MIN_DELAY_MS = 1_000L
        const val MAX_DELAY_MS = 60_000L
        const val JITTER_RATIO = 0.2
    }
}

/** Abstração da aleatoriedade, para permitir testes determinísticos. */
interface RandomSource {
    /** Valor em `[0.0, 1.0)`. */
    fun nextDouble(): Double

    companion object {
        /** Implementação padrão, baseada em [kotlin.random.Random]. */
        val Default: RandomSource = object : RandomSource {
            private val rnd = kotlin.random.Random.Default
            override fun nextDouble(): Double = rnd.nextDouble()
        }

        /** Fonte fixa, útil em testes. */
        fun fixed(value: Double): RandomSource = object : RandomSource {
            override fun nextDouble(): Double = value
        }
    }
}
