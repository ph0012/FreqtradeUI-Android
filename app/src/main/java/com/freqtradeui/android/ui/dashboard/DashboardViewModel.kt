package com.freqtradeui.android.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freqtradeui.android.data.models.ApiResult
import com.freqtradeui.android.data.models.BalanceResponse
import com.freqtradeui.android.data.models.ConnectionProfile
import com.freqtradeui.android.data.models.ConnectionSnapshot
import com.freqtradeui.android.data.models.CountResponse
import com.freqtradeui.android.data.models.ProfitResponse
import com.freqtradeui.android.data.models.StatusResponse
import com.freqtradeui.android.data.models.TunnelStatus
import com.freqtradeui.android.data.models.VersionResponse
import com.freqtradeui.android.data.repository.ConnectionProfileRepository
import com.freqtradeui.android.data.repository.FreqtradeRepository
import com.freqtradeui.android.ssh.PendingHostKey
import com.freqtradeui.android.ssh.SshTunnelManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

/**
 * Estado imutável do painel.
 *
 * Todos os campos financeiros começam vazios e só são preenchidos com dados
 * reais da API. Nada é estimado: enquanto não houver resposta, a UI mostra
 * indisponibilidade em vez de um número inventado.
 *
 * @param profile perfil referenciado pelo túnel ativo, quando houver.
 * @param snapshot estado **real** do túnel.
 * @param pendingHostKey chave de host aguardando decisão TOFU.
 * @param apiBaseUrl endereço local da API derivado do túnel.
 * @param version versão do Freqtrade.
 * @param health estado de saúde reportado pelo bot.
 * @param botState estado de execução do bot.
 * @param openTrades quantidade de operações abertas.
 * @param balanceTotal saldo total já formatado, com a moeda do bot.
 * @param balanceCurrency moeda de cotação informada pela API.
 * @param profitTotal lucro/prejuízo total já formatado.
 * @param profitPercent lucro/prejuízo percentual já formatado.
 * @param hasCredentials indica se o perfil tem credenciais da API gravadas.
 * @param loading carregamento em andamento.
 * @param error mensagem amigável da última falha, ou `null`.
 * @param lastUpdatedAt instante da última atualização bem-sucedida.
 */
data class DashboardUiState(
    val profile: ConnectionProfile? = null,
    val snapshot: ConnectionSnapshot = ConnectionSnapshot(),
    val pendingHostKey: PendingHostKey? = null,
    val apiBaseUrl: String = "",
    val version: String = "",
    val health: String = "",
    val botState: String = "",
    val openTrades: Int? = null,
    val balanceTotal: String = "",
    val balanceCurrency: String = "",
    val profitTotal: String = "",
    val profitPercent: String = "",
    val hasCredentials: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val lastUpdatedAt: Long? = null,
) {
    /** `true` enquanto não há nenhum dado real para exibir. */
    val isEmpty: Boolean
        get() = version.isBlank() && health.isBlank() && botState.isBlank() &&
            openTrades == null && balanceTotal.isBlank() && profitTotal.isBlank()
}

/**
 * ViewModel do painel.
 *
 * Combina o estado do túnel (real, vindo do serviço) com os dados da API e
 * dispara a atualização periódica **apenas enquanto a tela está inscrita** —
 * `WhileSubscribed` cancela o trabalho quando a UI sai de cena.
 *
 * A troca de perfil descarta a sessão e todos os dados do perfil anterior.
 *
 * @param repository perfis salvos.
 * @param tunnelManager estado real do túnel (instância compartilhada).
 * @param api acesso REST ao Freqtrade.
 * @param refreshIntervalMs intervalo de atualização periódica.
 */
class DashboardViewModel(
    private val repository: ConnectionProfileRepository,
    private val tunnelManager: SshTunnelManager,
    private val api: FreqtradeRepository,
    private val refreshIntervalMs: Long = DEFAULT_REFRESH_INTERVAL_MS,
) : ViewModel() {

    private val local = MutableStateFlow(DashboardData())

    /** Perfil cujos dados estão carregados; evita misturar perfis. */
    private var loadedProfileId: String? = null

    private var pollingJob: Job? = null

    val uiState: StateFlow<DashboardUiState> =
        combine(repository.observeProfiles(), tunnelManager.state, local) { profiles, snapshot, data ->
            val profile = snapshot.profileId?.let { id -> profiles.firstOrNull { it.id == id } }
            DashboardUiState(
                profile = profile,
                snapshot = snapshot,
                pendingHostKey = if (snapshot.hasPendingHostKey) tunnelManager.pendingHostKey else null,
                apiBaseUrl = profile?.let { api.baseUrl(it) }.orEmpty(),
                version = data.version,
                health = data.health,
                botState = data.botState,
                openTrades = data.openTrades,
                balanceTotal = data.balanceTotal,
                balanceCurrency = data.balanceCurrency,
                profitTotal = data.profitTotal,
                profitPercent = data.profitPercent,
                hasCredentials = profile?.let { api.hasCredentials(it.id) } ?: false,
                loading = data.loading,
                error = data.error,
                lastUpdatedAt = data.lastUpdatedAt,
            )
        }
            .onStart { pollingJob = startPolling() }
            .onCompletion { pollingJob?.cancel() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = DashboardUiState(),
            )

    /** Atualiza manualmente (botão da tela). */
    fun refresh() {
        viewModelScope.launch { reload() }
    }

    /** Aprova a chave de host pendente (TOFU). */
    fun approveHostKey() {
        tunnelManager.approveHostKey()
    }

    /** Rejeita a chave de host pendente e encerra a tentativa. */
    fun rejectHostKey() {
        tunnelManager.rejectHostKey()
    }

    /**
     * Atualização periódica, ativa só enquanto a tela observa o estado.
     *
     * Não é polling agressivo: o primeiro carregamento é imediato e os
     * seguintes respeitam [refreshIntervalMs].
     */
    private fun startPolling(): Job = viewModelScope.launch {
        while (isActive) {
            reload()
            delay(refreshIntervalMs)
        }
    }

    /**
     * Carrega os dados do perfil conectado.
     *
     * Se o túnel não estiver conectado, apenas limpa a sessão e informa a
     * indisponibilidade — sem tentar falar com a rede.
     */
    private suspend fun reload() {
        val snapshot = tunnelManager.state.value
        val profileId = snapshot.profileId
        val profile = if (snapshot.status == TunnelStatus.Connected && profileId != null) {
            repository.profile(profileId)
        } else {
            null
        }

        if (profile == null) {
            api.clearSession()
            loadedProfileId = null
            local.value = DashboardData(
                loading = false,
                error = null,
            )
            return
        }

        // Troca de perfil: descarta dados e sessão do perfil anterior.
        if (loadedProfileId != profile.id) {
            api.clearSession()
            loadedProfileId = profile.id
            local.value = DashboardData()
        }

        if (!api.hasCredentials(profile.id)) {
            local.value = DashboardData(
                loading = false,
                error = "Configure as credenciais da API deste perfil.",
            )
            return
        }

        local.value = local.value.copy(loading = true, error = null)

        val version = api.version(profile)
        val health = api.health(profile)
        val botStatus = api.status(profile)
        val count = api.count(profile)
        val balance = api.balance(profile)
        val profit = api.profit(profile)

        val failures = listOf(
            failureMessage(version),
            failureMessage(health),
            failureMessage(botStatus),
            failureMessage(count),
            failureMessage(balance),
            failureMessage(profit),
        ).filterNotNull()

        local.value = local.value.copy(
            loading = false,
            error = failures.firstOrNull(),
            version = dataOf(version)?.version ?: local.value.version,
            health = dataOf(health)?.status ?: local.value.health,
            botState = dataOf(botStatus)?.state ?: local.value.botState,
            openTrades = dataOf(count)?.open ?: local.value.openTrades,
            balanceTotal = dataOf(balance)?.let { formatAmount(it.total) } ?: local.value.balanceTotal,
            balanceCurrency = dataOf(balance)?.currency ?: local.value.balanceCurrency,
            profitTotal = dataOf(profit)?.let { formatAmount(it.profitAllCoin) } ?: local.value.profitTotal,
            profitPercent = dataOf(profit)?.let { formatPercent(it.profitAllPercent) } ?: local.value.profitPercent,
            lastUpdatedAt = if (failures.isEmpty()) {
                System.currentTimeMillis()
            } else {
                local.value.lastUpdatedAt
            },
        )
    }

    /** Dados agregados que a tela exibe, sem depender do snapshot do túnel. */
    private data class DashboardData(
        val version: String = "",
        val health: String = "",
        val botState: String = "",
        val openTrades: Int? = null,
        val balanceTotal: String = "",
        val balanceCurrency: String = "",
        val profitTotal: String = "",
        val profitPercent: String = "",
        val loading: Boolean = false,
        val error: String? = null,
        val lastUpdatedAt: Long? = null,
    )

    private companion object {
        /** Intervalo entre atualizações automáticas (30 s). */
        const val DEFAULT_REFRESH_INTERVAL_MS = 30_000L

        /** Mantém a atualização viva por 5 s após a tela sair de cena. */
        const val STOP_TIMEOUT_MS = 5_000L

        /** Extrai os dados de um resultado bem-sucedido. */
        fun <T> dataOf(result: ApiResult<T>): T? = (result as? ApiResult.Success<T>)?.data

        /** Mensagem amigável de uma falha, ou `null` em caso de sucesso. */
        fun failureMessage(result: ApiResult<*>): String? = when (result) {
            is ApiResult.Success -> null
            is ApiResult.ConnectionError -> result.message
            is ApiResult.TunnelUnavailable -> result.message
            is ApiResult.Unauthorized -> result.message
            is ApiResult.TokenExpired -> result.message
            is ApiResult.Unsupported -> result.message
            is ApiResult.HttpError -> result.message
            is ApiResult.DeserializationError -> result.message
            is ApiResult.UnexpectedError -> result.message
        }

        /** Formata um valor monetário sem fixar a moeda (a API informa a sua). */
        fun formatAmount(value: Double?): String =
            value?.let { NumberFormat.getNumberInstance(Locale.US).apply { maximumFractionDigits = 8 }.format(it) }
                .orEmpty()

        /** Formata o percentual de lucro, quando a API informa. */
        fun formatPercent(value: Double?): String = value?.let { String.format(Locale.US, "%.2f%%", it) }.orEmpty()
    }
}