package com.example.model

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable

enum class CaptureSourceMode {
    WEBVIEW,
    GALLERY
}

enum class SignalDirection {
    CALL_UP,
    PUT_DOWN,
    NEUTRAL
}

enum class BoSensitivity(
    val label: String,
    val description: String,
    val minConfidence: Int
) {
    MODERATE(
        label = "Moderado",
        description = "Mais entradas com trava contra mercado lateral (mín. 64%)",
        minConfidence = 64
    ),
    CONSERVATIVE(
        label = "Alta Precisão",
        description = "IA Turbo: só entra com tendência estruturada e confluência (mín. 72%)",
        minConfidence = 72
    ),
    SNIPER(
        label = "Sniper",
        description = "Ultra seletivo: exige 4/5 confluências, tendência forte e zero armadilhas (mín. 80%)",
        minConfidence = 80
    )
}

enum class BrokerAccountType(val label: String) {
    DEMO("Conta demo"),
    REAL("Conta real")
}

enum class TradingAsset(val label: String, val badgeText: String, val promptHint: String) {
    AUTO("Automático (do gráfico)", "AUTO", "o ativo exibido no gráfico"),
    GOLD_SILVER("Ouro/Prata", "XAU", "Ouro/Prata (XAU/XAG)"),
    EUR_USD("EUR/USD", "EUR", "EUR/USD"),
    EUR_USD_OTC("EUR/USD (OTC)", "OTC", "EUR/USD OTC"),
    GBP_USD("GBP/USD", "GBP", "GBP/USD"),
    USD_JPY("USD/JPY", "JPY", "USD/JPY"),
    BTC_USD("BTC/USD", "BTC", "Bitcoin (BTC/USD)")
}

enum class TechnicalIndicator(val label: String, val shortLabel: String, val promptFocus: String) {
    RSI("RSI", "RSI", "dê atenção especial ao oscilador RSI (sobrecompra >70, sobrevenda <30 e inclinação acima/abaixo de 50)"),
    EMA_RSI("RSI + Médias Móveis (EMA)", "EMA+RSI", "combine o cruzamento/inclinação das médias móveis (EMA) com o momento do RSI"),
    BOLLINGER_RSI("Bandas de Bollinger + RSI", "BB+RSI", "analise toques ou rompimentos nas Bandas de Bollinger em conjunto com o RSI"),
    MACD_TREND("MACD + Tendência", "MACD", "priorize o histograma e cruzamento de linhas do MACD junto à direção dos candles"),
    PRICE_ACTION("Price Action (Velas + S/R)", "PA", "priorize leitura pura de velas, força compradora/vendedora e zonas de suporte e resistência")
}

enum class RiskStrategy(
    val title: String,
    val riskLabel: String,
    val description: String
) {
    AI_RECOVERY(
        title = "Recuperação Inteligente IA",
        riskLabel = "IA Adaptativa (Payout 85%)",
        description = "A própria IA calcula o valor exato para recuperar perdas acumuladas, eleva o filtro de tendência na recuperação e protege sua banca."
    ),
    FIXED(
        title = "Valor fixo",
        riskLabel = "Risco baixo",
        description = "Mantém sempre o mesmo valor inicial em todas as entradas."
    ),
    MARTINGALE(
        title = "Dobrar na perda (Martingale)",
        riskLabel = "Risco alto",
        description = "Dobra a entrada (2x) ao perder e volta para o valor inicial ao acertar."
    ),
    SOROS(
        title = "Soros (Reinvestir lucro)",
        riskLabel = "Risco moderado",
        description = "Reinveste entrada + lucro estimado (85%) ao acertar e volta ao inicial ao perder."
    )
}

enum class AiRecoveryMode(val label: String) {
    FULL_RECOVERY_NOW("Recuperação Total (1 entrada)"),
    SPLIT_RECOVERY_2X("Recuperação Dividida (2 etapas)"),
    BREAKEVEN_SHIELD("Empate Seguro (Zerar perda)"),
    PAUSE_PROTECT("Proteção de Banca (Pausa IA)")
}

@Immutable
data class AiRecoveryPlan(
    val mode: AiRecoveryMode,
    val recommendedStake: Double,
    val minRequiredConfidence: Int,
    val extraWaitSeconds: Int = 0,
    val rationale: String,
    val isFromGemini: Boolean = false
)

@Immutable
data class AiRecoveryRequest(
    val accumulatedDeficit: Double,
    val initialStake: Double,
    val payoutRate: Double,
    val consecutiveLosses: Int,
    val maxSteps: Int,
    val liveBalance: Double?,
    val currencySymbol: String,
    val baseMinConfidence: Int,
    val recentMarketContext: String?
)

val ADAPTIVE_EXPIRY_OPTIONS_SEC: List<Int> = listOf(30, 60, 120, 300)

fun normalizeAdaptiveExpirySeconds(seconds: Int): Int {
    return when {
        seconds <= 45 -> 30
        seconds <= 90 -> 60
        seconds <= 210 -> 120
        else -> 300
    }
}

fun formatExpiryLabel(seconds: Int): String = when (seconds) {
    0 -> "Tempo real"
    30 -> "30s"
    60 -> "M1 (1 min)"
    120 -> "M2 (2 min)"
    300 -> "M5 (5 min)"
    900 -> "M15 (15 min)"
    else -> if (seconds >= 60) "${seconds / 60} min" else "${seconds}s"
}

fun formatExpiryShortLabel(seconds: Int): String = when (seconds) {
    0 -> "Tempo real"
    30 -> "30s"
    60 -> "1 min"
    120 -> "2 min"
    300 -> "5 min"
    900 -> "15 min"
    else -> if (seconds >= 60) "${seconds / 60} min" else "${seconds}s"
}

@Immutable
data class BoConfig(
    val expirySeconds: Int = 60,
    val autoExpiryEnabled: Boolean = true,
    val sensitivity: BoSensitivity = BoSensitivity.CONSERVATIVE,
    val tradingAsset: TradingAsset = TradingAsset.AUTO,
    val technicalIndicator: TechnicalIndicator = TechnicalIndicator.EMA_RSI,
    val strictThreeCandleRsiStrategy: Boolean = true,
    val waitForCandleClose: Boolean = true
) {
    val expiryLabel: String
        get() = formatExpiryLabel(expirySeconds)

    val durationShortLabel: String
        get() = formatExpiryShortLabel(expirySeconds)
}

@Immutable
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)

    fun normalized(): NormalizedRect {
        val l = minOf(left, right).coerceIn(0f, 1f)
        val r = maxOf(left, right).coerceIn(0f, 1f)
        val t = minOf(top, bottom).coerceIn(0f, 1f)
        val b = maxOf(top, bottom).coerceIn(0f, 1f)
        return NormalizedRect(l, t, r, b)
    }

    fun isValid(minExtent: Float = 0.06f): Boolean {
        val norm = normalized()
        return norm.width >= minExtent && norm.height >= minExtent
    }
}

@Immutable
data class ChartAnalysisResult(
    val id: String,
    val direction: SignalDirection,
    val confidencePercent: Int,
    val assetLabel: String,
    val timeframeLabel: String,
    val rsiValue: Int,
    val trendLabel: String,
    val patternLabel: String,
    val supportResistanceLabel: String,
    val summary: String,
    val timestampLabel: String,
    val isDemoMode: Boolean = true,
    val noticeMessage: String,
    val trendStrengthScore: Int = 0,
    val confluenceCount: Int = 0,
    val recommendedExpirySeconds: Int? = null,
    val expirySelectionReason: String = "",
    val consecutiveSameDirectionCandles: Int = 0,
    val rsiTurnState: String = "",
    val candleCloseConfirmed: Boolean = true,
    val strategyRulesMetCount: Int = 0
)

sealed interface ChartAnalysisOutcome {
    data class ValidChart(val result: ChartAnalysisResult) : ChartAnalysisOutcome
    data class InvalidChart(val reason: String) : ChartAnalysisOutcome
}

sealed interface AnalysisUiState {
    data object Unavailable : AnalysisUiState
    data object Analyzing : AnalysisUiState
    data class Ready(
        val result: ChartAnalysisResult,
        val capturedPreview: Bitmap?
    ) : AnalysisUiState
    data class NotAChart(
        val reason: String,
        val capturedPreview: Bitmap?
    ) : AnalysisUiState
    data class Error(
        val message: String,
        val capturedPreview: Bitmap? = null
    ) : AnalysisUiState
}

enum class TradeOutcome {
    WIN,
    LOSS
}

@Immutable
data class MartingaleConfig(
    val enabled: Boolean = true,
    val initialStake: Double = 2.0,
    val multiplier: Double = 2.0,
    val maxSteps: Int = 7,
    val riskStrategy: RiskStrategy = RiskStrategy.AI_RECOVERY,
    val sorosMaxLevels: Int = 2,
    val currencySymbol: String = "R$",
    val estimatedPayoutRate: Double = 0.85,
    val sessionMaxLossLimit: Double = 20.0,
    val twoLossPauseSeconds: Int = 300
) {
    val effectiveStrategy: RiskStrategy
        get() = when {
            !enabled && riskStrategy != RiskStrategy.FIXED -> RiskStrategy.FIXED
            enabled && riskStrategy == RiskStrategy.FIXED -> RiskStrategy.AI_RECOVERY
            else -> riskStrategy
        }

    fun stakeForStep(step: Int): Double {
        return when (effectiveStrategy) {
            RiskStrategy.FIXED -> kotlin.math.round(initialStake * 100.0) / 100.0
            RiskStrategy.AI_RECOVERY -> {
                val safeStep = step.coerceIn(0, maxSteps)
                if (safeStep == 0) {
                    kotlin.math.round(initialStake * 100.0) / 100.0
                } else {
                    val payout = estimatedPayoutRate.coerceIn(0.65, 0.95)
                    var deficit = 0.0
                    var stake = initialStake
                    repeat(safeStep) {
                        deficit += stake
                        val targetProfit = initialStake * payout
                        stake = ceilToTwoDecimals((deficit + targetProfit) / payout)
                    }
                    stake.coerceAtLeast(initialStake)
                }
            }
            RiskStrategy.MARTINGALE -> {
                val safeStep = step.coerceIn(0, maxSteps)
                if (kotlin.math.abs(initialStake - 2.0) < 0.01 &&
                    kotlin.math.abs(multiplier - 2.0) < 0.01
                ) {
                    when (safeStep) {
                        0 -> 2.0
                        1 -> 4.0
                        2 -> 8.0
                        3 -> 16.0
                        4 -> 32.0
                        5 -> 65.0
                        6 -> 130.0
                        7 -> 260.0
                        else -> kotlin.math.round(260.0 * Math.pow(2.0, (safeStep - 7).toDouble()) * 100.0) / 100.0
                    }
                } else {
                    val raw = initialStake * Math.pow(multiplier, safeStep.toDouble())
                    kotlin.math.round(raw * 100.0) / 100.0
                }
            }
            RiskStrategy.SOROS -> {
                val safeLevel = step.coerceIn(0, sorosMaxLevels)
                val raw = initialStake * Math.pow(1.85, safeLevel.toDouble())
                kotlin.math.round(raw * 100.0) / 100.0
            }
        }
    }

    /**
     * Exact payout-adjusted stake to recover accumulated loss deficit according to the AI's selected recovery mode,
     * bounded safely against the user's known broker balance and configured maxSteps ceiling.
     */
    fun computeRecoveryStake(
        accumulatedDeficit: Double,
        mode: AiRecoveryMode,
        liveBalance: Double? = null
    ): Double {
        val base = kotlin.math.round(initialStake * 100.0) / 100.0
        if (accumulatedDeficit <= 0.005 || mode == AiRecoveryMode.PAUSE_PROTECT) {
            return base
        }
        val payout = estimatedPayoutRate.coerceIn(0.65, 0.95)
        val targetProfit = base * payout
        val rawNeeded = when (mode) {
            AiRecoveryMode.FULL_RECOVERY_NOW -> (accumulatedDeficit + targetProfit) / payout
            AiRecoveryMode.SPLIT_RECOVERY_2X -> (accumulatedDeficit * 0.55 + targetProfit * 0.5) / payout
            AiRecoveryMode.BREAKEVEN_SHIELD -> accumulatedDeficit / payout
            AiRecoveryMode.PAUSE_PROTECT -> base
        }
        val exactNeeded = ceilToTwoDecimals(rawNeeded).coerceAtLeast(base)
        val stepCap = stakeForStep(maxSteps).coerceAtLeast(base * 2.0)
        val balanceCap = if (liveBalance != null && liveBalance >= base) {
            // Never risk more than 35% of known broker balance on a single recovery entry
            kotlin.math.max(base, kotlin.math.floor(liveBalance * 0.35 * 100.0) / 100.0)
        } else {
            Double.MAX_VALUE
        }
        return exactNeeded.coerceAtMost(minOf(stepCap, balanceCap)).coerceAtLeast(base)
    }

    fun martingaleSequencePreview(): String {
        val count = maxSteps.coerceIn(1, 8)
        return (0..count).joinToString(" → ") { formatStakeAmount(stakeForStep(it)) }
    }

    fun recommendedBankroll(): Double {
        return when (effectiveStrategy) {
            RiskStrategy.FIXED -> kotlin.math.max(50.0, initialStake * 25.0)
            RiskStrategy.AI_RECOVERY -> {
                val safeSteps = maxSteps.coerceAtMost(5)
                var cycleSum = 0.0
                for (i in 0..safeSteps) {
                    cycleSum += stakeForStep(i)
                }
                kotlin.math.round(kotlin.math.max(50.0, cycleSum * 1.5) * 100.0) / 100.0
            }
            RiskStrategy.MARTINGALE -> {
                var cycleSum = 0.0
                for (i in 0..maxSteps) {
                    cycleSum += stakeForStep(i)
                }
                kotlin.math.round(kotlin.math.max(50.0, cycleSum * 2.0) * 100.0) / 100.0
            }
            RiskStrategy.SOROS -> kotlin.math.max(50.0, initialStake * 20.0)
        }
    }

    private fun ceilToTwoDecimals(value: Double): Double =
        kotlin.math.ceil(value * 100.0 - 1e-9) / 100.0
}

fun formatStakeAmount(amount: Double): String {
    val rounded = kotlin.math.round(amount * 100.0) / 100.0
    val asLong = rounded.toLong()
    return if (kotlin.math.abs(rounded - asLong.toDouble()) < 0.005) {
        asLong.toString()
    } else {
        val ptBr = java.util.Locale.Builder().setLanguage("pt").setRegion("BR").build()
        String.format(ptBr, "%.2f", rounded)
    }
}


