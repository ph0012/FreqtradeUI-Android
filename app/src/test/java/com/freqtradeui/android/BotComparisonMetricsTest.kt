package com.freqtradeui.android

import com.freqtradeui.android.data.models.CountResponse
import com.freqtradeui.android.data.models.BalanceResponse
import com.freqtradeui.android.data.models.ConfigResponse
import com.freqtradeui.android.data.models.ProfitResponse
import com.freqtradeui.android.data.models.Trade
import com.freqtradeui.android.ui.comparison.BotComparisonMetricsCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BotComparisonMetricsTest {

    @Test
    fun calculaOpenProfitPonderadoPeloStakeEConservaResumoDeFechados() {
        val result = BotComparisonMetricsCalculator.calculate(
            openTrades = listOf(
                Trade(isOpen = true, stakeAmount = 100.0, profitAbs = 10.0),
                Trade(isOpen = true, stakeAmount = 300.0, profitAbs = -3.0),
                Trade(isOpen = false, stakeAmount = 50.0, profitAbs = 40.0),
            ),
            count = CountResponse(current = 99, max = 5),
            profit = ProfitResponse(
                stakeCurrency = "USDT",
                profitClosedCoin = 24.5,
                profitClosedPercent = 8.16,
                closedTradeCount = 7,
                winningTrades = 5,
                losingTrades = 2,
            ),
            config = ConfigResponse(dryRun = true),
            balance = BalanceResponse(currency = "USDT", total = 123.45),
        )

        assertEquals(2, result.openTradeCount)
        assertEquals(7, result.closedTradeCount)
        assertEquals(5, result.slotsTotal)
        assertEquals(7.0, result.openProfitCoin!!, 1e-9)
        assertEquals(1.75, result.openProfitPercent!!, 1e-9)
        assertEquals(24.5, result.closedProfitCoin!!, 1e-9)
        assertEquals(8.16, result.closedProfitPercent!!, 1e-9)
        assertEquals("USDT", result.stakeCurrency)
        assertEquals(5, result.winningTrades)
        assertEquals(2, result.losingTrades)
        assertEquals(true, result.dryRun)
        assertEquals(123.45, result.balanceTotal!!, 1e-9)
        assertEquals("USDT", result.balanceCurrency)
    }

    @Test
    fun semTradesAbertosRetornaZeroSemDividirPorZero() {
        val result = BotComparisonMetricsCalculator.calculate(
            openTrades = emptyList(),
            count = CountResponse(max = -1),
            profit = ProfitResponse(
                profitClosedCoin = 12.0,
                profitClosedPercent = 5.0,
                closedTradeCount = 2,
            ),
            config = ConfigResponse(),
            balance = BalanceResponse(),
        )

        assertEquals(0, result.openTradeCount)
        assertEquals(0.0, result.openProfitCoin!!, 0.0)
        assertEquals(0.0, result.openProfitPercent!!, 0.0)
        assertNull(result.slotsTotal)
        assertEquals(12.0, result.closedProfitCoin!!, 1e-9)
    }

    @Test
    fun missingStakeOrOpenProfitDoesNotInventPercentual() {
        val result = BotComparisonMetricsCalculator.calculate(
            openTrades = listOf(
                Trade(isOpen = true, stakeAmount = 0.0, profitAbs = 5.0),
                Trade(isOpen = true, stakeAmount = null, profitAbs = null),
            ),
            count = CountResponse(max = 4),
            profit = ProfitResponse(),
            config = ConfigResponse(),
            balance = BalanceResponse(),
        )

        assertEquals(2, result.openTradeCount)
        assertNull(result.openProfitCoin)
        assertNull(result.openProfitPercent)
        assertNull(result.closedProfitCoin)
        assertNull(result.closedProfitPercent)
    }
}
