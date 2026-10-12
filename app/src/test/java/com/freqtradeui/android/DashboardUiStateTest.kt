package com.freqtradeui.android

import com.freqtradeui.android.data.models.ConnectionState
import com.freqtradeui.android.data.models.ConnectionStatus
import com.freqtradeui.android.ui.dashboard.DashboardUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes de unidade do estado inicial. Valida que, sem conexão implementada,
 * o estado parte desconectado e sem dados financeiros — a UI não deve exibir
 * valores simulados como reais.
 */
class DashboardUiStateTest {

    @Test
    fun estadoInicial_estaDesconectado() {
        val state = DashboardUiState()
        assertEquals(ConnectionState.Disconnected, state.sshState)
        assertEquals(ConnectionState.Disconnected, state.apiState)
    }

    @Test
    fun estadoInicial_semDadosFinanceiros() {
        val state = DashboardUiState()
        org.junit.Assert.assertNull(state.balance)
        org.junit.Assert.assertNull(state.profit)
        assertTrue(state.openTrades.isEmpty())
    }

    @Test
    fun statusConexao_padraoDesconectado() {
        val status = ConnectionStatus()
        assertEquals(ConnectionState.Disconnected, status.ssh)
        assertEquals(ConnectionState.Disconnected, status.api)
    }
}
