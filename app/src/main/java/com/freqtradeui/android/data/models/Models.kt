package com.freqtradeui.android.data.models

/**
 * Saldo agregado da conta. Sem dados reais nesta etapa: todos os campos são
 * nulos e a interface apresenta placeholders explícitos.
 */
data class Balance(
    val currency: String? = null,
    val total: Double? = null,
    val free: Double? = null,
    val used: Double? = null,
)

/**
 * Resumo de lucro/prejuízo. Também apenas placeholder por enquanto.
 */
data class ProfitSummary(
    val closedProfitRatio: Double? = null,
    val closedProfitAbs: Double? = null,
    val openProfitAbs: Double? = null,
    val tradeCount: Int? = null,
)

/**
 * Operação aberta. A lista real virá da API do Freqtrade posteriormente.
 */
data class OpenTrade(
    val id: Int,
    val pair: String,
    val openRate: Double? = null,
    val currentRate: Double? = null,
    val profitRatio: Double? = null,
)
