package com.freqtradeui.android.ui.comparison

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freqtradeui.android.data.models.ApiResult
import com.freqtradeui.android.data.models.BalanceResponse
import com.freqtradeui.android.data.models.ConfigResponse
import com.freqtradeui.android.data.models.ConnectionProfile
import com.freqtradeui.android.data.models.ConnectionSnapshot
import com.freqtradeui.android.data.models.TunnelStatus
import com.freqtradeui.android.data.repository.ConnectionProfileRepository
import com.freqtradeui.android.data.repository.FreqtradeRepository
import com.freqtradeui.android.ssh.SshTunnelManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BotComparisonUiState(
    val profile: ConnectionProfile? = null,
    val snapshot: ConnectionSnapshot = ConnectionSnapshot(),
    val hasCredentials: Boolean = false,
    val loading: Boolean = false,
    val openTradeCount: Int? = null,
    val closedTradeCount: Int? = null,
    val slotsTotal: Int? = null,
    val openProfitCoin: Double? = null,
    val openProfitPercent: Double? = null,
    val closedProfitCoin: Double? = null,
    val closedProfitPercent: Double? = null,
    val stakeCurrency: String? = null,
    val winningTrades: Int? = null,
    val losingTrades: Int? = null,
    val dryRun: Boolean? = null,
    val balanceTotal: Double? = null,
    val balanceCurrency: String? = null,
    val error: String? = null,
    val lastUpdatedAt: Long? = null,
)

/**
 * Carrega os indicadores do perfil que realmente detém o túnel ativo.
 *
 * Chamadas são sequenciais para compartilhar com segurança a sessão autenticada
 * do repositório. A coleta cancela uma carga anterior quando o perfil muda.
 */
class BotComparisonViewModel(
    private val profiles: ConnectionProfileRepository,
    private val tunnelManager: SshTunnelManager,
    private val api: FreqtradeRepository,
) : ViewModel() {

    private val data = MutableStateFlow(ComparisonData())

    val uiState: StateFlow<BotComparisonUiState> =
        combine(profiles.observeProfiles(), tunnelManager.state, data) { allProfiles, snapshot, current ->
            val displayedProfileId = snapshot.profileId ?: current.profileId
            val profile = displayedProfileId?.let { id ->
                allProfiles.firstOrNull { it.id == id }
            }
            val matchesProfile = profile != null && profile.id == current.profileId
            val connected = snapshot.status == TunnelStatus.Connected &&
                snapshot.profileId == profile?.id
            val staleMessage = if (!connected && current.lastUpdatedAt != null) {
                "A conexão não está ativa; os valores exibidos podem estar desatualizados."
            } else {
                null
            }
            BotComparisonUiState(
                profile = profile,
                snapshot = snapshot,
                hasCredentials = profile?.let { api.hasCredentials(it.id) } ?: false,
                loading = matchesProfile && current.loading,
                openTradeCount = current.metrics.takeIf { matchesProfile }?.openTradeCount,
                closedTradeCount = current.metrics.takeIf { matchesProfile }?.closedTradeCount,
                slotsTotal = current.metrics.takeIf { matchesProfile }?.slotsTotal,
                openProfitCoin = current.metrics.takeIf { matchesProfile }?.openProfitCoin,
                openProfitPercent = current.metrics.takeIf { matchesProfile }?.openProfitPercent,
                closedProfitCoin = current.metrics.takeIf { matchesProfile }?.closedProfitCoin,
                closedProfitPercent = current.metrics.takeIf { matchesProfile }?.closedProfitPercent,
                stakeCurrency = current.metrics.takeIf { matchesProfile }?.stakeCurrency,
                winningTrades = current.metrics.takeIf { matchesProfile }?.winningTrades,
                losingTrades = current.metrics.takeIf { matchesProfile }?.losingTrades,
                dryRun = current.metrics.takeIf { matchesProfile }?.dryRun,
                balanceTotal = current.metrics.takeIf { matchesProfile }?.balanceTotal,
                balanceCurrency = current.metrics.takeIf { matchesProfile }?.balanceCurrency,
                error = if (connected) current.error else staleMessage,
                lastUpdatedAt = current.lastUpdatedAt.takeIf { matchesProfile },
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BotComparisonUiState(),
        )

    init {
        viewModelScope.launch {
            combine(profiles.observeProfiles(), tunnelManager.state) { allProfiles, snapshot ->
                val activeProfile = if (snapshot.status == TunnelStatus.Connected) {
                    snapshot.profileId?.let { id -> allProfiles.firstOrNull { it.id == id } }
                } else {
                    null
                }
                ActiveConnection(activeProfile, snapshot)
            }
                .distinctUntilChangedBy { connection ->
                    connection.profile?.id to connection.snapshot.status
                }
                .collectLatest { connection ->
                    val profile = connection.profile
                    if (profile == null) {
                        handleUnavailableConnection(connection.snapshot)
                    } else if (!api.hasCredentials(profile.id)) {
                        data.value = ComparisonData(
                            profileId = profile.id,
                            error = "Configure as credenciais da API deste perfil.",
                        )
                    } else {
                        load(profile)
                    }
                }
        }
    }

    /** Atualiza manualmente, sem iniciar polling em segundo plano. */
    fun refresh() {
        if (data.value.loading) return
        val snapshot = tunnelManager.state.value
        if (snapshot.status != TunnelStatus.Connected) return
        val profileId = snapshot.profileId ?: return

        val existing = data.value
        data.value = if (existing.profileId == profileId) {
            existing.copy(loading = true, error = null)
        } else {
            ComparisonData(profileId = profileId, loading = true)
        }
        viewModelScope.launch {
            val profile = profiles.profile(profileId)
            if (profile == null) {
                data.value = data.value.copy(
                    loading = false,
                    error = "O perfil conectado não está mais disponível.",
                )
            } else if (!isStillConnectedTo(profileId)) {
                return@launch
            } else if (!api.hasCredentials(profile.id)) {
                data.value = ComparisonData(
                    profileId = profile.id,
                    error = "Configure as credenciais da API deste perfil.",
                )
            } else {
                load(profile)
            }
        }
    }

    private suspend fun load(profile: ConnectionProfile) {
        val existing = data.value
        val base = if (existing.profileId == profile.id) {
            existing
        } else {
            ComparisonData(profileId = profile.id)
        }
        data.value = base.copy(loading = true, error = null)

        try {
            val openTrades = api.status(profile)
            currentCoroutineContext().ensureActive()
            if (!isStillConnectedTo(profile.id)) return
            if (recordFailure(profile.id, openTrades)) return

            val count = api.count(profile)
            currentCoroutineContext().ensureActive()
            if (!isStillConnectedTo(profile.id)) return
            if (recordFailure(profile.id, count)) return

            val profit = api.profit(profile)
            currentCoroutineContext().ensureActive()
            if (recordFailure(profile.id, profit)) return
            if (!isStillConnectedTo(profile.id)) return
            val config = api.showConfig(profile)
            currentCoroutineContext().ensureActive()
            if (recordFailure(profile.id, config)) return
            if (!isStillConnectedTo(profile.id)) return
            val balance = api.balance(profile)
            currentCoroutineContext().ensureActive()
            if (recordFailure(profile.id, balance)) return
            if (!isStillConnectedTo(profile.id)) return

            val metrics = BotComparisonMetricsCalculator.calculate(
                openTrades = (openTrades as ApiResult.Success).data,
                count = (count as ApiResult.Success).data,
                profit = (profit as ApiResult.Success).data,
                config = (config as ApiResult.Success).data,
                balance = (balance as ApiResult.Success).data,
            )
            data.value = ComparisonData(
                profileId = profile.id,
                metrics = metrics,
                lastUpdatedAt = System.currentTimeMillis(),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (isStillConnectedTo(profile.id)) {
                data.value = data.value.copy(
                    loading = false,
                    error = error.message ?: "Não foi possível atualizar os indicadores.",
                )
            }
        }
    }

    private fun handleUnavailableConnection(snapshot: ConnectionSnapshot) {
        val current = data.value
        if (snapshot.profileId != null && snapshot.profileId != current.profileId) {
            data.value = ComparisonData(profileId = snapshot.profileId)
        } else if (current.loading) {
            data.value = current.copy(loading = false)
        }
    }

    private fun isStillConnectedTo(profileId: String): Boolean {
        val snapshot = tunnelManager.state.value
        return snapshot.status == TunnelStatus.Connected && snapshot.profileId == profileId
    }

    private fun recordFailure(profileId: String, result: ApiResult<*>): Boolean {
        val message = when (result) {
            is ApiResult.Success -> return false
            is ApiResult.ConnectionError -> result.message
            is ApiResult.TunnelUnavailable -> result.message
            is ApiResult.Unauthorized -> result.message
            is ApiResult.TokenExpired -> result.message
            is ApiResult.Unsupported -> result.message
            is ApiResult.HttpError -> result.message
            is ApiResult.DeserializationError -> result.message
            is ApiResult.UnexpectedError -> result.message
        }
        if (data.value.profileId == profileId && isStillConnectedTo(profileId)) {
            data.value = data.value.copy(loading = false, error = message)
        }
        return true
    }

    private data class ActiveConnection(
        val profile: ConnectionProfile?,
        val snapshot: ConnectionSnapshot,
    )

    private data class ComparisonData(
        val profileId: String? = null,
        val metrics: BotComparisonMetrics? = null,
        val loading: Boolean = false,
        val error: String? = null,
        val lastUpdatedAt: Long? = null,
    )
}
