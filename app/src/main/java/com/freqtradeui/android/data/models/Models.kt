package com.freqtradeui.android.data.models

/**
 * Estado de conectividade exibido na interface.
 *
 * Nesta etapa inicial o estado é sempre [Disconnected]: nenhuma conexão SSH
 * ou com a API é estabelecida. O modelo existe para que a camada de dados e
 * os ViewModels possam publicar o estado real no futuro sem alterar a UI.
 */
enum class ConnectionState(val label: String) {
    Disconnected("Desconectado"),
    Connecting("Conectando..."),
    Connected("Conectado"),
    Error("Erro"),
}

/**
 * Estado de conexão do túnel SSH e da API do Freqtrade.
 *
 * @param ssh estado do túnel SSH local.
 * @param api estado da API do Freqtrade acessada via loopback.
 * @param detail mensagem opcional exibida ao usuário (ex.: motivo do erro).
 */
data class ConnectionStatus(
    val ssh: ConnectionState = ConnectionState.Disconnected,
    val api: ConnectionState = ConnectionState.Disconnected,
    val detail: String? = null,
)

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
