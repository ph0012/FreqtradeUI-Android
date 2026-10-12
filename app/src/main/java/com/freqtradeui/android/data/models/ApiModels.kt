package com.freqtradeui.android.data.models

import com.squareup.moshi.Json

/**
 * Credenciais da API REST do Freqtrade.
 *
 * Não confundir com [ProfileSecret] (chave privada SSH + passphrase). São
 * credenciais de naturezas diferentes e ficam em cofres separados.
 *
 * @param username usuário da API configurado na VPS.
 * @param password senha da API (nunca a chave SSH).
 */
data class ApiCredentials(
    val username: String,
    val password: String,
)

/**
 * Tokens JWT devolvidos por `POST /token/login`.
 *
 * @param accessToken token de acesso, enviado como `Bearer`.
 * @param refreshToken token de renovação, quando a API o fornece.
 * @param expiresInMs validade do access_token em milissegundos, quando informada.
 * @param tokenType tipo do token; a API usa "Bearer".
 * @param obtainedAtMs instante local em que o token foi obtido.
 */
data class ApiAuthTokens(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "refresh_token") val refreshToken: String? = null,
    @Json(name = "expires_in") val expiresInMs: Long? = null,
    @Json(name = "token_type") val tokenType: String = "Bearer",
    val obtainedAtMs: Long = System.currentTimeMillis(),
) {
    /** Instante absoluto de expiração, quando a API informa a validade. */
    val absoluteExpiryMs: Long?
        get() = expiresInMs?.let { obtainedAtMs + it }

    /**
     * Indica se o token expirou (ou está a menos de 30 s de expirar).
     *
     * Sem `expires_in` na resposta não há como afirmar: retorna `false` e a
     * expiração real será descoberta por um 401.
     */
    fun isExpired(nowMs: Long = System.currentTimeMillis()): Boolean =
        absoluteExpiryMs?.let { nowMs >= it - EXPIRY_MARGIN_MS } ?: false

    private companion object {
        /** Margem para renovar antes do vencimento. */
        const val EXPIRY_MARGIN_MS = 30_000L
    }
}

/** Corpo de `POST /token/login`. */
data class LoginRequest(
    @Json(name = "username") val username: String,
    @Json(name = "password") val password: String,
)

/** Resposta de `GET /ping`. */
data class PingResponse(
    @Json(name = "status") val status: String,
)

/** Resposta de `GET /version`. */
data class VersionResponse(
    @Json(name = "version") val version: String,
)

/** Resposta de `GET /health`. */
data class HealthResponse(
    @Json(name = "status") val status: String,
)

/**
 * Configuração do bot (`GET /show_config`).
 *
 * Apenas os campos usados pela interface são tipados; campos desconhecidos são
 * ignorados pela desserialização, o que mantém a compatibilidade entre versões.
 */
data class ConfigResponse(
    @Json(name = "dry_run") val dryRun: Boolean = false,
    @Json(name = "stake_currency") val stakeCurrency: String = "",
    @Json(name = "stake_amount") val stakeAmount: Double? = null,
    @Json(name = "timeframe") val timeframe: String? = null,
    @Json(name = "state") val state: String? = null,
    @Json(name = "max_open_trades") val maxOpenTrades: Int? = null,
    @Json(name = "exchange") val exchange: String? = null,
    @Json(name = "pair_whitelist") val pairWhitelist: List<String> = emptyList(),
)

/** Resposta de `GET /count`. */
data class CountResponse(
    @Json(name = "current") val current: Int = 0,
    @Json(name = "total") val total: Int = 0,
    @Json(name = "open") val open: Int = 0,
)

/** Resposta de `GET /status`. */
data class StatusResponse(
    @Json(name = "state") val state: String = "",
    @Json(name = "message") val message: String? = null,
    @Json(name = "max_open_trades") val maxOpenTrades: Int? = null,
    @Json(name = "available_capital") val availableCapital: Double? = null,
)

/**
 * Resumo de lucro/prejuízo (`GET /profit`).
 *
 * São os **cálculos oficiais do Freqtrade**: não devem ser recalculados a partir
 * dos trades nem somados de forma diferente pela interface.
 */
data class ProfitResponse(
    @Json(name = "stake_currency") val stakeCurrency: String = "",
    @Json(name = "profit_closed_coin") val profitClosedCoin: Double? = null,
    @Json(name = "profit_closed_percent") val profitClosedPercent: Double? = null,
    @Json(name = "profit_closed_fiat") val profitClosedFiat: Double? = null,
    @Json(name = "profit_all_coin") val profitAllCoin: Double? = null,
    @Json(name = "profit_all_percent") val profitAllPercent: Double? = null,
    @Json(name = "profit_all_fiat") val profitAllFiat: Double? = null,
    @Json(name = "trade_count") val tradeCount: Int? = null,
    @Json(name = "closed_trade_count") val closedTradeCount: Int? = null,
    @Json(name = "latest_trade_timestamp") val latestTradeTimestamp: Long? = null,
)

/** Saldo da exchange (`GET /balance`). */
data class BalanceResponse(
    @Json(name = "currency") val currency: String = "",
    @Json(name = "free") val free: Double = 0.0,
    @Json(name = "used") val used: Double = 0.0,
    @Json(name = "total") val total: Double = 0.0,
)

/** Item de performance por par (`GET /performance`). */
data class PerformanceItem(
    @Json(name = "pair") val pair: String = "",
    @Json(name = "profit_abs") val profitAbs: Double? = null,
    @Json(name = "profit_pct") val profitPct: Double? = null,
    @Json(name = "profit_fiat") val profitFiat: Double? = null,
    @Json(name = "count") val count: Int? = null,
)

/** Item de resultado diário (`GET /daily`). */
data class DailyProfitItem(
    @Json(name = "date") val date: String = "",
    @Json(name = "abs_profit") val absProfit: Double? = null,
    @Json(name = "rel_profit") val relProfit: Double? = null,
    @Json(name = "fiat_profit") val fiatProfit: Double? = null,
    @Json(name = "trade_count") val tradeCount: Int? = null,
)

/** Lista de performance por par: `GET /performance` responde um vetor. */
typealias PerformanceResponse = List<PerformanceItem>

/**
 * Operação individual (`GET /trades`).
 *
 * Todos os campos numéricos são opcionais: diferentes versões do Freqtrade
 * omitem chaves distintas, e a interface trata cada ausência como
 * indisponibilidade em vez de zero.
 */
data class Trade(
    @Json(name = "trade_id") val tradeId: Int = 0,
    @Json(name = "pair") val pair: String = "",
    @Json(name = "is_open") val isOpen: Boolean = false,
    @Json(name = "exchange") val exchange: String = "",
    @Json(name = "stake_amount") val stakeAmount: Double? = null,
    @Json(name = "amount") val amount: Double? = null,
    @Json(name = "open_rate") val openRate: Double? = null,
    @Json(name = "open_timestamp") val openTimestamp: Long? = null,
    @Json(name = "close_rate") val closeRate: Double? = null,
    @Json(name = "close_timestamp") val closeTimestamp: Long? = null,
    @Json(name = "close_profit") val closeProfit: Double? = null,
    @Json(name = "close_profit_abs") val closeProfitAbs: Double? = null,
    @Json(name = "realized_profit") val realizedProfit: Double? = null,
    @Json(name = "strategy") val strategy: String? = null,
    @Json(name = "enter_tag") val enterTag: String? = null,
    @Json(name = "exit_reason") val exitReason: String? = null,
    @Json(name = "leverage") val leverage: Double? = null,
)

/** Resposta paginada de `GET /trades`. */
data class TradesResponse(
    @Json(name = "trades") val trades: List<Trade> = emptyList(),
    @Json(name = "total") val total: Int? = null,
)

/** Resposta de `GET /whitelist`. */
data class WhitelistResponse(
    @Json(name = "whitelist") val whitelist: List<String> = emptyList(),
)

/** Resposta de `GET /logs`. */
data class LogResponse(
    @Json(name = "logs") val logs: List<String> = emptyList(),
)

/**
 * Resultado de uma chamada REST.
 *
 * Distinguir as categorias de falha evita tratar "o túnel caiu" como "a senha
 * está errada" e mantém mensagens técnicas fora da interface.
 */
sealed class ApiResult<out T> {

    /** Chamada concluída. */
    data class Success<T>(val data: T) : ApiResult<T>()

    /** Erro de rede: timeout, DNS, conexão encerrada. */
    data class ConnectionError(
        val message: String,
        val exception: Throwable? = null,
    ) : ApiResult<Nothing>()

    /** O túnel SSH não está ativo: nada saiu do aparelho. */
    data class TunnelUnavailable(
        val message: String,
        val exception: Throwable? = null,
    ) : ApiResult<Nothing>()

    /** Credenciais da API ausentes ou recusadas. */
    data class Unauthorized(
        val message: String,
        val exception: Throwable? = null,
    ) : ApiResult<Nothing>()

    /** Sessão expirada: é necessário autenticar novamente. */
    data class TokenExpired(
        val message: String,
        val exception: Throwable? = null,
    ) : ApiResult<Nothing>()

    /** Endpoint inexistente nesta versão da API. */
    data class Unsupported(
        val message: String,
        val exception: Throwable? = null,
    ) : ApiResult<Nothing>()

    /** Resposta HTTP de erro fora das categorias acima. */
    data class HttpError(
        val code: Int,
        val message: String,
        val body: String? = null,
        val exception: Throwable? = null,
    ) : ApiResult<Nothing>()

    /** A resposta não corresponde ao esquema esperado. */
    data class DeserializationError(
        val message: String,
        val exception: Throwable? = null,
    ) : ApiResult<Nothing>()

    /** Falha não classificada. */
    data class UnexpectedError(
        val message: String,
        val exception: Throwable? = null,
    ) : ApiResult<Nothing>()
}
