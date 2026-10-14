package com.freqtradeui.android

import com.freqtradeui.android.data.models.ApiAuthTokens
import com.freqtradeui.android.data.models.ApiCredentials
import com.freqtradeui.android.data.models.ApiResult
import com.freqtradeui.android.data.models.ConnectionProfile
import com.freqtradeui.android.data.repository.FreqtradeCredentialStore
import com.freqtradeui.android.data.repository.FreqtradeRepository
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.Credentials
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Testes da camada REST.
 *
 * Usam um servidor HTTP falso no lugar da VPS: validam autenticação, cabeçalho
 * Bearer, isolamento entre perfis, tratamento de falhas e desserialização com
 * campos opcionais ausentes.
 *
 * Nenhum teste aqui toca uma VPS real — as respostas simuladas existem apenas
 * neste escopo de teste.
 */
class FreqtradeRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var store: FakeCredentialStore
    private lateinit var repository: FreqtradeRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        store = FakeCredentialStore()
        repository = FreqtradeRepository(store)
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    // ---------------- Endereço derivado do túnel ----------------

    @Test
    fun baseUrl_vemDaPortaLocalDoPerfil() {
        val profile = profile(id = "p1", port = 18080)

        assertEquals("http://127.0.0.1:18080/api/v1/", repository.baseUrl(profile))
    }

    @Test
    fun baseUrl_nuncaUsaHostPublico() {
        val profile = profile(id = "p1", port = 19001)

        val url = repository.baseUrl(profile)

        assertTrue(url.startsWith("http://127.0.0.1:19001"))
        assertTrue(!url.contains(profile.host))
    }

    // ---------------- Autenticação ----------------

    @Test
    fun login_sucessoGuardaTokenEUsaBearerDepois() = runBlocking {
        store.save("p1", ApiCredentials("api-user", "api-pass"))
        server.enqueue(
            json(
                """{"access_token":"tok-123","refresh_token":"refresh-123","token_type":"Bearer"}""",
            ),
        )
        server.enqueue(json("""{"version":"2024.1"}"""))

        val login = repository.login(profile("p1", server.port))
        assertTrue("login deveria ter sucesso", login is ApiResult.Success)

        val version = repository.version(profile("p1", server.port))
        assertTrue(version is ApiResult.Success)

        val loginRequest = server.takeRequest()
        assertEquals("POST", loginRequest.method)
        assertTrue(loginRequest.path!!.endsWith("/api/v1/token/login"))
        assertEquals(
            Credentials.basic("api-user", "api-pass"),
            loginRequest.getHeader("Authorization"),
        )
        assertEquals("", loginRequest.body.readUtf8())

        val guarded = server.takeRequest()
        assertTrue(guarded.path!!.endsWith("/api/v1/version"))
        assertEquals("Bearer tok-123", guarded.getHeader("Authorization"))
    }

    @Test
    fun tokenDeAcessoExpirado_renovaSessaoERetentaConsulta() = runBlocking {
        store.save("p1", ApiCredentials("api-user", "api-pass"))
        server.enqueue(json("""{"access_token":"old-access","refresh_token":"refresh-123"}"""))
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(json("""{"access_token":"new-access"}"""))
        server.enqueue(json("""{"version":"2026.1"}"""))

        val result = repository.version(profile("p1", server.port))

        assertTrue("consulta deve funcionar após renovar token: $result", result is ApiResult.Success)
        assertEquals("new-access", repository.accessToken("p1"))
        assertTrue(server.takeRequest().path!!.endsWith("/api/v1/token/login"))
        assertTrue(server.takeRequest().path!!.endsWith("/api/v1/version"))

        val refreshRequest = server.takeRequest()
        assertTrue(refreshRequest.path!!.endsWith("/api/v1/token/refresh"))
        assertNotNull(refreshRequest.getHeader("Authorization"))
        assertEquals(1, refreshRequest.headers.values("Authorization").size)

        val retriedRequest = server.takeRequest()
        assertTrue(retriedRequest.path!!.endsWith("/api/v1/version"))
        assertEquals(1, retriedRequest.headers.values("Authorization").size)
    }

    @Test
    fun refreshTokenExpirado_fazLoginNovamenteERetentaConsulta() = runBlocking {
        store.save("p1", ApiCredentials("api-user", "api-pass"))
        server.enqueue(json("""{"access_token":"old-access","refresh_token":"expired-refresh"}"""))
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(json("""{"access_token":"new-access","refresh_token":"new-refresh"}"""))
        server.enqueue(json("""{"version":"2026.1"}"""))

        val result = repository.version(profile("p1", server.port))

        assertTrue("consulta deve funcionar após novo login: $result", result is ApiResult.Success)
        assertEquals("new-access", repository.accessToken("p1"))
        assertTrue(server.takeRequest().path!!.endsWith("/api/v1/token/login"))
        assertTrue(server.takeRequest().path!!.endsWith("/api/v1/version"))
        assertTrue(server.takeRequest().path!!.endsWith("/api/v1/token/refresh"))

        val reloginRequest = server.takeRequest()
        assertTrue(reloginRequest.path!!.endsWith("/api/v1/token/login"))
        assertEquals(
            Credentials.basic("api-user", "api-pass"),
            reloginRequest.getHeader("Authorization"),
        )
        assertTrue(server.takeRequest().path!!.endsWith("/api/v1/version"))
    }

    @Test
    fun login_semCredenciais_naoFalaComARede() {
        val result = runBlocking { repository.login(profile("p1", server.port)) }

        assertTrue(result is ApiResult.Unauthorized)
        assertEquals("nenhuma requisição deve sair sem credenciais", 0, server.requestCount)
    }

    @Test
    fun login_credenciaisRecusadas_retornaUnauthorized() {
        store.save("p1", ApiCredentials("api-user", "senha-errada"))
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"Unauthorized"}"""))

        val result = runBlocking { repository.login(profile("p1", server.port)) }

        assertTrue(result is ApiResult.Unauthorized)
    }

    @Test
    fun credenciais_naoSeMisturamEntrePerfis() = runBlocking {
        store.save("p1", ApiCredentials("user-1", "pass-1"))
        store.save("p2", ApiCredentials("user-2", "pass-2"))
        server.enqueue(json("""{"access_token":"tok-p1"}"""))
        server.enqueue(MockResponse().setResponseCode(401))

        repository.login(profile("p1", server.port))
        assertNotNull(repository.accessToken("p1"))

        val segundo = repository.login(profile("p2", server.port))
        assertTrue(segundo is ApiResult.Unauthorized)

        // O token do primeiro perfil continua sendo dele, e só dele.
        assertNotNull(repository.accessToken("p1"))
        assertNull(repository.accessToken("p2"))
    }

    @Test
    fun trocarDePerfil_descartaSessaoAnterior() = runBlocking {
        store.save("p1", ApiCredentials("user-1", "pass-1"))
        server.enqueue(json("""{"access_token":"tok-p1"}"""))
        repository.login(profile("p1", server.port))

        repository.clearSession()

        assertNull(repository.accessToken("p1"))
    }

    // ---------------- Falhas ----------------

    @Test
    fun tunelIndisponivel_eReportadoComoTunelNaoComoCredencial() {
        store.save("p1", ApiCredentials("api-user", "api-pass"))
        val profile = profile("p1", server.port)

        val result = runBlocking {
            server.shutdown()
            repository.version(profile)
        }

        assertTrue(
            "esperado TunnelUnavailable, veio $result",
            result is ApiResult.TunnelUnavailable,
        )
    }

    @Test
    fun respostaInvalida_viraErroDeDesserializacao() {
        store.save("p1", ApiCredentials("api-user", "api-pass"))
        server.enqueue(json("""{"access_token":"tok-1"}"""))
        server.enqueue(json("""{"isto":"nao e um ping"}"""))

        val result = runBlocking { repository.ping(profile("p1", server.port)) }

        assertTrue(
            "esperado DeserializationError, veio $result",
            result is ApiResult.DeserializationError,
        )
    }

    // ---------------- Dados financeiros ----------------

    @Test
    fun profit_deserializaComCamposOpcionaisAusentes() = runBlocking {
        store.save("p1", ApiCredentials("api-user", "api-pass"))
        server.enqueue(json("""{"access_token":"tok-1"}"""))
        server.enqueue(
            json(
                """{"stake_currency":"USDT","profit_all_coin":12.5,""" +
                    """"profit_all_percent":1.25,"trade_count":4}""",
            ),
        )

        val result = repository.profit(profile("p1", server.port))

        val data = (result as ApiResult.Success).data
        assertEquals("USDT", data.stakeCurrency)
        assertEquals(12.5, data.profitAllCoin!!, 1e-9)
        assertEquals(4, data.tradeCount)
    }

    @Test
    fun balance_deserializaValoresDaApi() = runBlocking {
        store.save("p1", ApiCredentials("api-user", "api-pass"))
        server.enqueue(json("""{"access_token":"tok-1"}"""))
        server.enqueue(json("""{"currency":"BTC","free":1.5,"used":0.5,"total":2.0}"""))

        val result = repository.balance(profile("p1", server.port))

        val data = (result as ApiResult.Success).data
        assertEquals("BTC", data.currency)
        assertEquals(2.0, data.total, 1e-9)
    }

    @Test
    fun status_deserializaListaDeTradesAbertos() = runBlocking {
        store.save("p1", ApiCredentials("api-user", "api-pass"))
        server.enqueue(json("""{"access_token":"tok-1"}"""))
        server.enqueue(json("""[{"trade_id":7,"pair":"BTC/USDT","is_open":true}]"""))

        val result = repository.status(profile("p1", server.port))

        val trades = (result as ApiResult.Success).data
        assertEquals(1, trades.size)
        assertEquals(7, trades.single().tradeId)
        assertEquals("BTC/USDT", trades.single().pair)
    }

    @Test
    fun showConfig_deserializaEstadoDoBot() = runBlocking {
        store.save("p1", ApiCredentials("api-user", "api-pass"))
        server.enqueue(json("""{"access_token":"tok-1"}"""))
        server.enqueue(
            json(
                """{"state":"running","stake_currency":"USDT","stake_amount":"unlimited",""" +
                    """"max_open_trades":"inf"}""",
            ),
        )

        val result = repository.showConfig(profile("p1", server.port))

        assertEquals("running", (result as ApiResult.Success).data.state)
    }

    @Test
    fun health_deserializaHealthReportDoFreqtrade() = runBlocking {
        store.save("p1", ApiCredentials("api-user", "api-pass"))
        server.enqueue(json("""{"access_token":"tok-1"}"""))
        server.enqueue(
            json(
                """{"last_process":"2026-10-14T12:00:00Z","last_process_ts":1791988800,""" +
                    """"bot_start":null,"bot_start_ts":null}""",
            ),
        )

        val result = repository.health(profile("p1", server.port))

        val health = (result as ApiResult.Success).data
        assertEquals("2026-10-14T12:00:00Z", health.lastProcess)
        assertEquals(1791988800L, health.lastProcessTimestamp)
    }

    @Test
    fun count_usaCurrentComoNumeroDeTradesAbertos() = runBlocking {
        store.save("p1", ApiCredentials("api-user", "api-pass"))
        server.enqueue(json("""{"access_token":"tok-1"}"""))
        server.enqueue(json("""{"current":2,"max":5,"total_stake":200.0}"""))

        val result = repository.count(profile("p1", server.port))

        val count = (result as ApiResult.Success).data
        assertEquals(2, count.current)
        assertEquals(5, count.max)
    }

    @Test
    fun token_expiradoQuandoValidadeJaPassou() {
        val expirado = ApiAuthTokens(
            accessToken = "tok",
            expiresInMs = 60_000,
            obtainedAtMs = System.currentTimeMillis() - 120_000,
        )
        val valido = ApiAuthTokens(
            accessToken = "tok",
            expiresInMs = 3_600_000,
            obtainedAtMs = System.currentTimeMillis(),
        )

        assertTrue(expirado.isExpired())
        assertTrue(!valido.isExpired())
    }

    @Test
    fun token_semValidadeInformada_naoAfirmaExpiracao() {
        val token = ApiAuthTokens(accessToken = "tok")

        assertTrue(!token.isExpired())
        assertNull(token.absoluteExpiryMs)
    }

    // ---------------- Apoio ----------------

    private fun json(body: String): MockResponse =
        MockResponse().setResponseCode(200).setBody(body)

    /**
     * Perfil de teste: a porta local é a porta em que o servidor falso escuta,
     * exatamente como acontece com o encaminhamento real do túnel.
     */
    private fun profile(id: String, port: Int) = ConnectionProfile(
        id = id,
        name = "Perfil $id",
        host = "vps.example.com",
        username = "ssh-user",
        localPort = port,
    )
}

/** Cofre em memória, no lugar do EncryptedSharedPreferences/Android Keystore. */
private class FakeCredentialStore : FreqtradeCredentialStore {

    private val entries = mutableMapOf<String, ApiCredentials>()

    override fun save(profileId: String, credentials: ApiCredentials) {
        entries[profileId] = credentials
    }

    override fun load(profileId: String): ApiCredentials? = entries[profileId]

    override fun has(profileId: String): Boolean = entries.containsKey(profileId)

    override fun delete(profileId: String) {
        entries.remove(profileId)
    }
}