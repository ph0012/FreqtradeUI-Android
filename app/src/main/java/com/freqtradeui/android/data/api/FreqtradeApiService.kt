package com.freqtradeui.android.data.api

import com.freqtradeui.android.data.models.ApiAuthTokens
import com.freqtradeui.android.data.models.BalanceResponse
import com.freqtradeui.android.data.models.ConfigResponse
import com.freqtradeui.android.data.models.CountResponse
import com.freqtradeui.android.data.models.DailyProfitItem
import com.freqtradeui.android.data.models.HealthResponse
import com.freqtradeui.android.data.models.LogResponse
import com.freqtradeui.android.data.models.LoginRequest
import com.freqtradeui.android.data.models.PerformanceResponse
import com.freqtradeui.android.data.models.PingResponse
import com.freqtradeui.android.data.models.ProfitResponse
import com.freqtradeui.android.data.models.StatusResponse
import com.freqtradeui.android.data.models.TradesResponse
import com.freqtradeui.android.data.models.VersionResponse
import com.freqtradeui.android.data.models.WhitelistResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Cliente REST do Freqtrade.
 *
 * Toda comunicação com a API do Freqtrade passa por esta interface: a UI e os
 * ViewModels nunca falam HTTP diretamente. O endereço base é derivado da porta
 * local do túnel SSH ativo (ex.: `http://127.0.0.1:18080/api/v1/`), nunca do IP
 * público da VPS.
 *
 * Somente operações de leitura e autenticação estão declaradas aqui. Endpoints
 * que alteram o estado do bot (compra, venda, fechamento, start/stop) ficam
 * fora por decisão de escopo e segurança.
 */
interface FreqtradeApiService {

    // ---------------- Diagnóstico ----------------

    /** Verificação de disponibilidade da API. */
    @GET("ping")
    suspend fun ping(): PingResponse

    /** Versão do Freqtrade em execução. */
    @GET("version")
    suspend fun version(): VersionResponse

    /** Estado de saúde reportado pelo bot. */
    @GET("health")
    suspend fun health(): HealthResponse

    // ---------------- Estado e configuração do bot ----------------

    /** Configuração efetiva (dry-run, moeda de stake, exchange, whitelist). */
    @GET("show_config")
    suspend fun showConfig(): ConfigResponse

    /** Contagem de trades abertos e totais. */
    @GET("count")
    suspend fun count(): CountResponse

    /** Estado de execução do bot. */
    @GET("status")
    suspend fun status(): StatusResponse

    // ---------------- Resultados e conta ----------------

    /** Resumo de lucro/prejuízo calculado pelo próprio Freqtrade. */
    @GET("profit")
    suspend fun profit(): ProfitResponse

    /** Saldo da exchange. */
    @GET("balance")
    suspend fun balance(): BalanceResponse

    /** Performance agregada por par. */
    @GET("performance")
    suspend fun performance(): PerformanceResponse

    /**
     * Resultado diário.
     *
     * @param days quantidade de dias consultados.
     */
    @GET("daily")
    suspend fun daily(
        @Query("days") days: Int = DEFAULT_DAILY_DAYS,
    ): List<DailyProfitItem>

    // ---------------- Histórico e informações complementares ----------------

    /**
     * Histórico de trades.
     *
     * @param limit limite de registros (a API impõe um máximo próprio).
     * @param offset deslocamento para paginação.
     */
    @GET("trades")
    suspend fun trades(
        @Query("limit") limit: Int = DEFAULT_TRADES_LIMIT,
        @Query("offset") offset: Int = 0,
    ): TradesResponse

    /** Pares atualmente na whitelist. */
    @GET("whitelist")
    suspend fun whitelist(): WhitelistResponse

    /** Últimas linhas de log do bot. */
    @GET("logs")
    suspend fun logs(): LogResponse

    // ---------------- Autenticação ----------------

    /** Autentica e devolve os tokens JWT. */
    @POST("token/login")
    suspend fun login(@Body request: LoginRequest): ApiAuthTokens

    companion object {
        /** Quantidade padrão de dias em `/daily`. */
        const val DEFAULT_DAILY_DAYS = 7

        /** Limite padrão de registros em `/trades`. */
        const val DEFAULT_TRADES_LIMIT = 50

        /**
         * Caminho padrão da API na instalação do Freqtrade.
         *
         * Montado após a porta local do túnel, ex.:
         * `http://127.0.0.1:18080/api/v1/`.
         */
        const val DEFAULT_API_PATH = "/api/v1"
    }
}