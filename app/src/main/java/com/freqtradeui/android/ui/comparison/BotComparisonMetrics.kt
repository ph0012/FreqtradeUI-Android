package com.freqtradeui.android.ui.comparison

import com.freqtradeui.android.data.models.CountResponse
import com.freqtradeui.android.data.models.BalanceResponse
import com.freqtradeui.android.data.models.ConfigResponse
import com.freqtradeui.android.data.models.ProfitResponse
import com.freqtradeui.android.data.models.Trade

/** Métricas consolidadas da API, sem estimar campos que não foram retornados. */
data class BotComparisonMetrics(
    val openTradeCount: Int,
    val closedTradeCount: Int?,
    val slotsTotal: Int?,
    val openProfitCoin: Double?,
    val openProfitPercent: Double?,
    val closedProfitCoin: Double?,
    val closedProfitPercent: Double?,
    val stakeCurrency: String?,
    val winningTrades: Int?,
    val losingTrades: Int?,
    val dryRun: Boolean?,
    val balanceTotal: Double?,
    val balanceCurrency: String?,
)

/**
 * Calcula somente a rentabilidade aberta ponderada a partir dos valores
 * absolutos e do stake efetivamente informado para cada posição.
 */
object BotComparisonMetricsCalculator {

    fun calculate(
        openTrades: List<Trade>,
        count: CountResponse,
        profit: ProfitResponse,
        config: ConfigResponse,
        balance: BalanceResponse,
    ): BotComparisonMetrics {
        val trades = openTrades.filter(Trade::isOpen)
        val profitValues = trades.mapNotNull(Trade::profitAbs)
        val stakeValues = trades.mapNotNull(Trade::stakeAmount)
        val openProfitCoin = when {
            trades.isEmpty() -> 0.0
            profitValues.size == trades.size -> profitValues.sum()
            else -> null
        }
        val openProfitPercent = when {
            trades.isEmpty() -> 0.0
            profitValues.size == trades.size && stakeValues.size == trades.size -> {
                val totalStake = stakeValues.sum()
                if (totalStake > 0.0) {
                    100.0 * profitValues.sum() / totalStake
                } else {
                    null
                }
            }
            else -> null
        }

        return BotComparisonMetrics(
            openTradeCount = trades.size,
            closedTradeCount = profit.closedTradeCount,
            slotsTotal = count.max?.takeIf { it >= 0 },
            openProfitCoin = openProfitCoin,
            openProfitPercent = openProfitPercent,
            closedProfitCoin = profit.profitClosedCoin,
            closedProfitPercent = profit.profitClosedPercent,
            stakeCurrency = profit.stakeCurrency.takeIf(String::isNotBlank),
            winningTrades = profit.winningTrades,
            losingTrades = profit.losingTrades,
            dryRun = config.dryRun,
            balanceTotal = balance.total,
            balanceCurrency = balance.currency.takeIf(String::isNotBlank),
        )
    }
}
