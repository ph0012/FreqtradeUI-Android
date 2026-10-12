package com.freqtradeui.android.ui.dashboard

import androidx.lifecycle.ViewModel
import com.freqtradeui.android.data.models.Balance
import com.freqtradeui.android.data.models.ConnectionState
import com.freqtradeui.android.data.models.ConnectionStatus
import com.freqtradeui.android.data.models.OpenTrade
import com.freqtradeui.android.data.models.ProfitSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Estado imutável da tela de Dashboard.
 *
 * Nesta etapa inicial todos os dados financeiros são nulos: o túnel SSH e a
 * integração com a API ainda não foram implementados. O estado existe para
 * que a UI reaja a dados reais no futuro sem refatoração.
 */
data class DashboardUiState(
    val connection: ConnectionStatus = ConnectionStatus(),
    val balance: Balance? = null,
    val profit: ProfitSummary? = null,
    val openTrades: List<OpenTrade> = emptyList(),
) {
    val sshState: ConnectionState get() = connection.ssh
    val apiState: ConnectionState get() = connection.api
}

/**
 * ViewModel do Dashboard.
 *
 * Mantém o estado de UI. Ainda não há repositório injetado: quando a conexão
 * SSH e a API forem implementadas, este ViewModel passará a consumir o
 * repositório responsável, sem mudar o contrato com a tela.
 */
class DashboardViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    /**
     * Marca a conexão como em andamento. Não estabelece conexão real nesta
     * etapa; apenas move o estado para [ConnectionState.Connecting].
     */
    fun onConnecting() {
        _uiState.value = _uiState.value.copy(
            connection = ConnectionStatus(
                ssh = ConnectionState.Connecting,
                api = ConnectionState.Connecting,
            ),
        )
    }
}
