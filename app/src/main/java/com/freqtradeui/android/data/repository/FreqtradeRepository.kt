package com.freqtradeui.android.data.repository

import com.freqtradeui.android.data.api.FreqtradeApiClient
import com.freqtradeui.android.data.api.FreqtradeApiService
import com.freqtradeui.android.data.models.ApiAuthTokens
import com.freqtradeui.android.data.models.ApiCredentials
import com.freqtradeui.android.data.models.ApiResult
import com.freqtradeui.android.data.models.BalanceResponse
import com.freqtradeui.android.data.models.ConfigResponse
import com.freqtradeui.android.data.models.ConnectionProfile
import com.freqtradeui.android.data.models.CountResponse
import com.freqtradeui.android.data.models.DailyProfitItem
import com.freqtradeui.android.data.models.HealthResponse
import com.freqtradeui.android.data.models.LogResponse
import com.freqtradeui.android.data.models.PerformanceResponse
import com.freqtradeui.android.data.models.PingResponse
import com.freqtradeui.android.data.models.ProfitResponse
import com.freqtradeui.android.data.models.Trade
import com.freqtradeui.android.data.models.TradesResponse
import com.freqtradeui.android.data.models.VersionResponse
import com.freqtradeui.android.data.models.WhitelistResponse
import com.squareup.moshi.JsonDataException
import okhttp3.Credentials
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import retrofit2.HttpException

/**
 * Guarda as credenciais da API do Freqtrade, por perfil, de forma cifrada.
 *
 * Interface própria para que os testes de unidade usem uma implementação em
 * memória, sem Android Keystore — mesmo padrão do cofre de segredos SSH.
 */
interface FreqtradeCredentialStore {

    /** Grava (ou substitui) usuário e senha da API do perfil. */
    fun save(profileId: String, credentials: ApiCredentials)

    /** Credenciais do perfil, ou `null` se não houver. */
    fun load(profileId: String): ApiCredentials?

    /** Indica se o perfil já tem credenciais da API gravadas. */
    fun has(profileId: String): Boolean

    /** Apaga as credenciais da API do perfil. */
    fun delete(profileId: String)
}

/**
 * Acesso à API REST do Freqtrade através do túnel SSH do perfil.
 *
 * Responsabilidades:
 * - Derivar o endereço base da **porta local** do túnel, nunca do IP público.
 * - Autenticar (`/token/login`) e guardar o token apenas em memória.
 * - Executar as consultas de leitura e traduzir falhas para [ApiResult].
 *
 * A sessão é vinculada ao perfil: trocar de perfil descarta o token, para que
 * dados e credenciais nunca se misturem entre perfis.
 *
 * @param credentials cofre cifrado das credenciais da API.
 */
class FreqtradeRepository(
    private val credentials: FreqtradeCredentialStore,
) {

    private var tokens: ApiAuthTokens? = null
    private var tokensProfileId: String? = null
    private var client: FreqtradeApiClient? = null
    private var clientUrl: String? = null

    // ---------------- Sessão e credenciais ----------------

    /** Indica se o perfil possui credenciais da API configuradas. */
    fun hasCredentials(profileId: String): Boolean = credentials.has(profileId)

    /**
     * Usuário da API gravado para o perfil, ou `null`.
     *
     * A **senha não é exposta** por este método: depois de salva, não existe
     * caminho para lê-la de volta na interface.
     */
    fun username(profileId: String): String? = credentials.load(profileId)?.username

    /** Grava as credenciais da API do perfil. */
    fun saveCredentials(profileId: String, username: String, password: String) {
        credentials.save(profileId, ApiCredentials(username, password))
    }

    /** Apaga credenciais e sessão do perfil. */
    fun deleteCredentials(profileId: String) {
        credentials.delete(profileId)
        if (tokensProfileId == profileId) clearSession()
    }

    /** Descarta o token em memória (troca de perfil, queda do túnel, logout). */
    fun clearSession() {
        tokens = null
        tokensProfileId = null
    }

    /** Token válido para o perfil informado, ou `null`. */
    fun accessToken(profileId: String): String? =
        tokens?.takeIf { tokensProfileId == profileId }?.accessToken

    // ---------------- Endereço derivado do túnel ----------------

    /** URL base do túnel local: `http://127.0.0.1:<porta local>/api/v1/`. */
    fun baseUrl(profile: ConnectionProfile): String =
        "http://$LOOPBACK_HOST:${profile.localPort}$API_PATH/"

    // ---------------- Autenticação ----------------

    /** Autentica no Freqtrade e guarda o token em memória. */
    suspend fun login(profile: ConnectionProfile): ApiResult<ApiAuthTokens> {
        val stored = credentials.load(profile.id)
            ?: return ApiResult.Unauthorized("Credenciais da API não configuradas.")
        return try {
            val response = service(profile)
                .login(Credentials.basic(stored.username, stored.password))
            if (response.accessToken.isBlank()) {
                ApiResult.Unauthorized("A API respondeu sem token de acesso.")
            } else {
                tokens = response
                tokensProfileId = profile.id
                ApiResult.Success(response)
            }
        } catch (e: Exception) {
            if (e is HttpException && e.code() in AUTH_ERROR_CODES) {
                ApiResult.Unauthorized("Usuário ou senha da API inválidos.", e)
            } else {
                classify(e)
            }
        }
    }

    // ---------------- Consultas de leitura ----------------

    suspend fun ping(profile: ConnectionProfile): ApiResult<PingResponse> =
        read(profile) { ping() }

    suspend fun version(profile: ConnectionProfile): ApiResult<VersionResponse> =
        read(profile) { version() }

    suspend fun health(profile: ConnectionProfile): ApiResult<HealthResponse> =
        read(profile) { health() }

    suspend fun showConfig(profile: ConnectionProfile): ApiResult<ConfigResponse> =
        read(profile) { showConfig() }

    suspend fun count(profile: ConnectionProfile): ApiResult<CountResponse> =
        read(profile) { count() }

    suspend fun status(profile: ConnectionProfile): ApiResult<List<Trade>> =
        read(profile) { status() }

    suspend fun profit(profile: ConnectionProfile): ApiResult<ProfitResponse> =
        read(profile) { profit() }

    suspend fun balance(profile: ConnectionProfile): ApiResult<BalanceResponse> =
        read(profile) { balance() }

    suspend fun performance(profile: ConnectionProfile): ApiResult<PerformanceResponse> =
        read(profile) { performance() }

    suspend fun daily(
        profile: ConnectionProfile,
        days: Int = FreqtradeApiService.DEFAULT_DAILY_DAYS,
    ): ApiResult<List<DailyProfitItem>> = read(profile) { daily(days) }

    suspend fun trades(
        profile: ConnectionProfile,
        limit: Int = FreqtradeApiService.DEFAULT_TRADES_LIMIT,
        offset: Int = 0,
    ): ApiResult<TradesResponse> = read(profile) { trades(limit, offset) }

    suspend fun whitelist(profile: ConnectionProfile): ApiResult<WhitelistResponse> =
        read(profile) { whitelist() }

    suspend fun logs(profile: ConnectionProfile): ApiResult<LogResponse> =
        read(profile) { logs() }

    // ---------------- Interno ----------------

    /**
     * Executa uma consulta autenticada, garantindo sessão válida para o perfil.
     *
     * A sessão é criada sob demanda; havendo token do **mesmo** perfil, ele é
     * reutilizado — não se pede credencial a cada chamada.
     */
    private suspend fun <T> read(
        profile: ConnectionProfile,
        block: suspend FreqtradeApiService.() -> T,
    ): ApiResult<T> {
        ensureSession(profile)?.let { failure ->
            @Suppress("UNCHECKED_CAST")
            return failure as ApiResult<T>
        }
        return try {
            ApiResult.Success(service(profile).block())
        } catch (e: HttpException) {
            if (e.code() != HTTP_UNAUTHORIZED) {
                classify(e)
            } else {
                try {
                    refreshSession(profile)
                    ApiResult.Success(service(profile).block())
                } catch (retryError: Exception) {
                    classify(retryError)
                }
            }
        } catch (e: Exception) {
            classify(e)
        }
    }

    /**
     * Renova a sessão após uma resposta 401 e autentica novamente se o refresh
     * token também expirou. O chamador repete a consulta no máximo uma vez.
     */
    private suspend fun refreshSession(profile: ConnectionProfile) {
        val current = tokens?.takeIf { tokensProfileId == profile.id }
            ?: return loginOrThrow(profile)
        val refreshToken = current.refreshToken
        if (refreshToken.isNullOrBlank()) {
            clearSession()
            return loginOrThrow(profile)
        }

        return try {
            val refreshed = service(profile).refreshToken("$BEARER_SCHEME $refreshToken")
            if (refreshed.accessToken.isBlank()) {
                throw ApiAuthRequired("A API respondeu sem token de acesso.")
            } else {
                val updated = refreshed.copy(
                    refreshToken = refreshed.refreshToken ?: refreshToken,
                    tokenType = current.tokenType,
                )
                tokens = updated
                tokensProfileId = profile.id
            }
        } catch (e: HttpException) {
            if (e.code() in AUTH_ERROR_CODES) {
                clearSession()
                loginOrThrow(profile)
            } else {
                throw e
            }
        }
    }

    private suspend fun loginOrThrow(profile: ConnectionProfile) {
        when (val result = login(profile)) {
            is ApiResult.Success -> Unit
            is ApiResult.Unauthorized -> throw ApiAuthRequired(result.message)
            else -> throw result.exceptionOrDefault()
        }
    }

    private fun ApiResult<*>.exceptionOrDefault(): Throwable = when (this) {
        is ApiResult.Success -> IllegalStateException("A chamada deveria ter falhado.")
        is ApiResult.ConnectionError -> exception ?: IOException(message)
        is ApiResult.TunnelUnavailable -> exception ?: ConnectException(message)
        is ApiResult.Unauthorized -> exception ?: ApiAuthRequired(message)
        is ApiResult.TokenExpired -> exception ?: ApiAuthRequired(message)
        is ApiResult.Unsupported -> exception ?: ApiAuthRequired(message)
        is ApiResult.HttpError -> exception ?: IOException(message)
        is ApiResult.DeserializationError -> exception ?: IOException(message)
        is ApiResult.UnexpectedError -> exception ?: IllegalStateException(message)
    }

    /**
     * Garante uma sessão válida para o perfil.
     *
     * @return `null` quando há sessão utilizável; caso contrário, o resultado da
     *   falha **original** — assim uma queda de túnel continua sendo reportada
     *   como túnel indisponível, e não como credencial inválida.
     */
    private suspend fun ensureSession(profile: ConnectionProfile): ApiResult<*>? {
        if (tokens != null && tokensProfileId == profile.id) return null
        return when (val result = login(profile)) {
            is ApiResult.Success -> null
            is ApiResult.Unauthorized -> ApiResult.Unauthorized(
                "Usuário ou senha da API inválidos.",
                result.exception,
            )
            else -> result
        }
    }

    /** Serviço REST do perfil, reaproveitando o cliente quando a URL é a mesma. */
    private fun service(profile: ConnectionProfile): FreqtradeApiService {
        val url = baseUrl(profile)
        val current = client
        val usable = if (current == null || clientUrl != url) {
            FreqtradeApiClient(url) { tokens?.accessToken }.also {
                client = it
                clientUrl = url
            }
        } else {
            current
        }
        return usable.service()
    }

    /** Traduz qualquer falha em um [ApiResult] tipado, sem vazar detalhe técnico. */
    private fun classify(e: Throwable): ApiResult<Nothing> = when (e) {
        is ApiAuthRequired ->
            ApiResult.Unauthorized(e.message ?: "Autenticação necessária.")

        is JsonDataException ->
            ApiResult.DeserializationError("A resposta da API não corresponde ao esperado.", e)

        is HttpException -> when (e.code()) {
            401 -> ApiResult.Unauthorized("A sessão da API expirou ou o token foi recusado.", e)
            403 -> ApiResult.Unauthorized("O usuário não tem permissão para esta operação.", e)
            404 -> ApiResult.Unsupported("Endpoint não suportado por esta versão da API.", e)
            else -> ApiResult.HttpError(e.code(), "A API respondeu com erro ${e.code()}.", null, e)
        }

        is SocketTimeoutException ->
            ApiResult.ConnectionError("Tempo esgotado ao falar com a API.", e)

        is UnknownHostException ->
            ApiResult.ConnectionError("Endereço da API não pôde ser resolvido.", e)

        is ConnectException ->
            ApiResult.TunnelUnavailable("O túnel SSH não está ativo.", e)

        is IOException ->
            ApiResult.ConnectionError("Falha de rede ao falar com a API.", e)

        else -> ApiResult.UnexpectedError(e.message ?: "Erro inesperado ao falar com a API.", e)
    }

    /** Falha interna: sessão ausente ou credenciais recusadas. */
    private class ApiAuthRequired(message: String) : Exception(message)

    companion object {
        /** Loopback: o túnel expõe a API apenas no próprio aparelho. */
        const val LOOPBACK_HOST = "127.0.0.1"

        /** Caminho padrão da API do Freqtrade. */
        const val API_PATH = FreqtradeApiService.DEFAULT_API_PATH

        private const val HTTP_UNAUTHORIZED = 401
        private const val BEARER_SCHEME = "Bearer"
        private val AUTH_ERROR_CODES = setOf(401, 403)
    }
}