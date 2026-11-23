package com.example.data

import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.util.Log
import com.example.model.AiRecoveryMode
import com.example.model.AiRecoveryPlan
import com.example.model.AiRecoveryRequest
import com.example.model.BoConfig
import com.example.model.ChartAnalysisOutcome
import com.example.model.ChartAnalysisResult
import com.example.model.MartingaleConfig
import com.example.model.SignalDirection
import com.example.model.formatExpiryLabel
import com.example.model.normalizeAdaptiveExpirySeconds
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.ContentBlockedException
import com.google.firebase.ai.type.FirebaseAIException
import com.google.firebase.ai.type.PromptBlockedException
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.RequestTimeoutException
import com.google.firebase.ai.type.ResponseStoppedException
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.SerializationException
import com.google.firebase.ai.type.ServerException
import com.google.firebase.ai.type.ServiceDisabledException
import com.google.firebase.ai.type.UnsupportedUserLocationException
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

private const val TAG = "FirebaseAiChartRepo"
private val QUOTA_COOLDOWN_STEPS_MS = longArrayOf(15_000L, 30_000L, 60_000L, 90_000L)
private const val SHORT_COOLDOWN_WAIT_THRESHOLD_MS = 10_000L
private const val MIN_CHART_REQUEST_SPACING_MS = 4_000L
private const val BALANCE_OCR_MIN_INTERVAL_MS = 45_000L
private const val STAKE_LOCATE_MIN_INTERVAL_MS = 60_000L
private const val AUXILIARY_POST_QUOTA_MUTE_MS = 180_000L

internal fun shouldPreferLitePrimary(liteCooling: Boolean, flashCooling: Boolean): Boolean =
    !liteCooling || flashCooling

internal fun isQuotaExceededThrowable(error: Throwable?): Boolean {
    var current: Throwable? = error
    var depth = 0
    while (current != null && depth < 5) {
        if (current is QuotaExceededException || current is AiQuotaCooldownException) {
            return true
        }
        val msg = current.message.orEmpty()
        if (msg.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ||
            msg.contains("quota", ignoreCase = true) ||
            msg.contains("rate limit", ignoreCase = true) ||
            msg.contains("too many requests", ignoreCase = true) ||
            Regex("""\b429\b""").containsMatchIn(msg)
        ) {
            return true
        }
        current = current.cause
        depth++
    }
    return false
}

internal fun computeProgressiveQuotaCooldownMs(consecutiveHits: Int): Long {
    val idx = (consecutiveHits - 1).coerceIn(0, QUOTA_COOLDOWN_STEPS_MS.lastIndex)
    return QUOTA_COOLDOWN_STEPS_MS[idx]
}

class AiQuotaCooldownException(
    val remainingSeconds: Int,
    message: String
) : IllegalStateException(message)

/**
 * Normalized coordinates of the stake/amount box ("Valor" / "$ 2") detected visually on canvas-based traderooms.
 */
data class VisualStakeBoxHit(
    val normX: Float,
    val normY: Float,
    val currentAmount: Double? = null
)

/**
 * Single interface routing all chart analysis requests.
 */
interface AiRepository {
    suspend fun analyzeChartRegion(
        bitmap: Bitmap,
        sourceDescription: String,
        boConfig: BoConfig = BoConfig(),
        previousReadingHint: String? = null
    ): ChartAnalysisOutcome

    suspend fun readBrokerBalanceFromBitmap(bitmap: Bitmap): Double? = null

    suspend fun locateStakeBoxFromBitmap(lowerPanelBitmap: Bitmap): VisualStakeBoxHit? = null

    suspend fun planLossRecoveryStrategy(request: AiRecoveryRequest): AiRecoveryPlan? = null

    fun clearQuotaCooldowns() {}
}

/**
 * Production repository using Firebase AI Logic (Gemini 3.8 Flash / 3.5 Flash-Lite) multimodal vision
 * with structured JSON output (`Schema.obj`) and App Check attestation.
 */
class FirebaseAiChartRepository : AiRepository {

    @Volatile
    private var liteQuotaCooldownUntilMs: Long = 0L

    @Volatile
    private var flashQuotaCooldownUntilMs: Long = 0L

    @Volatile
    private var consecutiveQuotaHits: Int = 0

    @Volatile
    private var lastQuotaHitAtMs: Long = 0L

    @Volatile
    private var lastChartRequestAtMs: Long = 0L

    @Volatile
    private var lastBalanceOcrAtMs: Long = 0L

    @Volatile
    private var lastStakeLocateAtMs: Long = 0L

    private fun isLiteInQuotaCooldown(nowMs: Long = SystemClock.elapsedRealtime()): Boolean =
        nowMs < liteQuotaCooldownUntilMs

    private fun isFlashInQuotaCooldown(nowMs: Long = SystemClock.elapsedRealtime()): Boolean =
        nowMs < flashQuotaCooldownUntilMs

    private fun isAnyModelInQuotaCooldown(nowMs: Long = SystemClock.elapsedRealtime()): Boolean =
        isLiteInQuotaCooldown(nowMs) || isFlashInQuotaCooldown(nowMs)

    private fun isAuxiliaryQuotaMuted(nowMs: Long = SystemClock.elapsedRealtime()): Boolean {
        if (isAnyModelInQuotaCooldown(nowMs)) return true
        if (lastQuotaHitAtMs > 0L && (nowMs - lastQuotaHitAtMs) in 0L..AUXILIARY_POST_QUOTA_MUTE_MS) {
            return true
        }
        return lastChartRequestAtMs > 0L && (nowMs - lastChartRequestAtMs) in 0L..MIN_CHART_REQUEST_SPACING_MS
    }

    private fun recordQuotaError(
        isLiteModel: Boolean,
        coolBothModels: Boolean = false,
        nowMs: Long = SystemClock.elapsedRealtime()
    ): Long {
        consecutiveQuotaHits = (consecutiveQuotaHits + 1).coerceAtMost(QUOTA_COOLDOWN_STEPS_MS.size)
        lastQuotaHitAtMs = nowMs
        val cooldownMs = computeProgressiveQuotaCooldownMs(consecutiveQuotaHits)
        if (isLiteModel || coolBothModels) {
            liteQuotaCooldownUntilMs = maxOf(liteQuotaCooldownUntilMs, nowMs + cooldownMs)
        }
        if (!isLiteModel || coolBothModels) {
            flashQuotaCooldownUntilMs = maxOf(flashQuotaCooldownUntilMs, nowMs + cooldownMs)
        }
        return cooldownMs
    }

    private fun recordSuccessfulChartCall(nowMs: Long = SystemClock.elapsedRealtime()) {
        consecutiveQuotaHits = 0
        lastChartRequestAtMs = nowMs
    }

    override fun clearQuotaCooldowns() {
        liteQuotaCooldownUntilMs = 0L
        flashQuotaCooldownUntilMs = 0L
        consecutiveQuotaHits = 0
        lastQuotaHitAtMs = 0L
    }

    private val chartAnalysisSchema = Schema.obj(
        properties = mapOf(
            "isValidChart" to Schema.boolean(
                description = "True if image shows a financial price chart (candlesticks, bars, Heikin-Ashi, or line/area); false only for non-chart screens."
            ),
            "invalidReason" to Schema.string(
                description = "Short Portuguese reason if isValidChart is false; empty otherwise."
            ),
            "marketStructure" to Schema.enumeration(
                values = listOf("HH_HL_UPTREND", "LH_LL_DOWNTREND", "CONSOLIDATION_RANGE", "REVERSAL_TRANSITION"),
                description = "Recent 15-30 candle structure: HH_HL_UPTREND, LH_LL_DOWNTREND, CONSOLIDATION_RANGE (flat box), or REVERSAL_TRANSITION (fresh turn)."
            ),
            "macroTrend" to Schema.enumeration(
                values = listOf("UP", "DOWN", "SIDEWAYS"),
                description = "Overall slope of visible price wave: UP, DOWN, or SIDEWAYS."
            ),
            "microMomentum" to Schema.enumeration(
                values = listOf("UP", "DOWN", "CHOPPY"),
                description = "Momentum of the rightmost 3-6 candles: UP, DOWN, or CHOPPY."
            ),
            "candleDominance" to Schema.enumeration(
                values = listOf("BUYERS_CONTROL", "SELLERS_CONTROL", "MIXED_INDECISION"),
                description = "Dominance of rightmost 5-8 candle bodies: BUYERS_CONTROL, SELLERS_CONTROL, or MIXED_INDECISION."
            ),
            "trendStrengthScore" to Schema.integer(
                description = "Trend clarity 0..100: <=49 flat/choppy, 50..59 weak, 60..79 clear trend, 80..100 strong clean trend."
            ),
            "confluenceCount" to Schema.integer(
                description = "Aligned pillars 0..5: (1) structure, (2) slope/EMA, (3) candle body dominance, (4) S/R room, (5) healthy RSI/momentum."
            ),
            "entryQuality" to Schema.enumeration(
                values = listOf("STRONG", "MODERATE", "WEAK_AVOID"),
                description = "STRONG for clean trend/breakout/pullback, MODERATE for clear bias, WEAK_AVOID for flat/choppy/doji/counter-trend."
            ),
            "riskFlag" to Schema.enumeration(
                values = listOf(
                    "NONE",
                    "EXHAUSTION_CANDLE",
                    "OPPOSING_WICK",
                    "SR_WALL",
                    "CHOPPY_SIDEWAYS",
                    "DOJI_INDECISION",
                    "COUNTER_TREND"
                ),
                description = "NONE if clean; else primary trap (EXHAUSTION_CANDLE, OPPOSING_WICK >55%, SR_WALL, CHOPPY_SIDEWAYS, DOJI_INDECISION, COUNTER_TREND)."
            ),
            "trendLabel" to Schema.string(
                description = "Short Portuguese label: 'Tendência de alta', 'Tendência de baixa', or 'Mercado lateral'."
            ),
            "patternLabel" to Schema.string(
                description = "Short Portuguese note (max 8 words) on recent candles/structure."
            ),
            "supportResistanceLabel" to Schema.string(
                description = "Short Portuguese S/R note (max 6 words)."
            ),
            "rsiEstimated" to Schema.integer(
                description = "Estimated RSI 15..85."
            ),
            "confidencePercent" to Schema.integer(
                description = "Confluence 45..95: 73..92 for clear aligned trend, 64..72 moderate trend, <=58 flat/choppy."
            ),
            "direction" to Schema.enumeration(
                values = listOf("CALL_UP", "PUT_DOWN", "NEUTRAL"),
                description = "CALL_UP for clear bullish trend/breakout, PUT_DOWN for clear bearish trend/breakout, NEUTRAL for flat/choppy/counter-trend."
            ),
            "recommendedExpirySeconds" to Schema.integer(
                description = "Optimal expiry in seconds for highest win rate based on trend velocity, volatility, and room to S/R: 30 (30s fast impulse/scalp), 60 (1 min steady momentum), 120 (2 min structured trend with pullback room), or 300 (5 min strong macro trend with wide S/R space)."
            ),
            "expiryRationale" to Schema.string(
                description = "Short Portuguese note (max 8 words) explaining why 30s, 1 min, 2 min, or 5 min fits the current trend."
            ),
            "consecutiveSameDirCandles" to Schema.integer(
                description = "Exact count (0..10) of consecutive rightmost candles in the SAME direction with clear defined body strength."
            ),
            "rsiTurnState" to Schema.enumeration(
                values = listOf(
                    "LEAVING_OVERSOLD_UP",
                    "LEAVING_OVERBOUGHT_DOWN",
                    "MOMENTUM_ALIGNED_UP",
                    "MOMENTUM_ALIGNED_DOWN",
                    "FLAT_OR_MIDDLE",
                    "AGAINST_SIGNAL"
                ),
                description = "RSI/momentum alignment: LEAVING_OVERSOLD_UP when RSI exits oversold turning UP (CALL); MOMENTUM_ALIGNED_UP when 3+ solid bullish candles confirm rising RSI/momentum (even if RSI panel is hidden); LEAVING_OVERBOUGHT_DOWN when RSI exits overbought turning DOWN (PUT); MOMENTUM_ALIGNED_DOWN when 3+ solid bearish candles confirm falling RSI/momentum; FLAT_OR_MIDDLE only when sideways/flat; AGAINST_SIGNAL when opposing."
            ),
            "candleCloseConfirmed" to Schema.boolean(
                description = "True when the 3rd signal candle is closed/completed with solid body; false if still mid-formation without close confirmation."
            ),
            "summary" to Schema.string(
                description = "1 concise Portuguese sentence citing trend structure and confluence."
            )
        ),
        optionalProperties = listOf(
            "invalidReason",
            "marketStructure",
            "macroTrend",
            "microMomentum",
            "candleDominance",
            "trendStrengthScore",
            "confluenceCount",
            "entryQuality",
            "riskFlag",
            "recommendedExpirySeconds",
            "expiryRationale",
            "consecutiveSameDirCandles",
            "rsiTurnState",
            "candleCloseConfirmed"
        )
    )

    private val flashStructuredModel by lazy { createModel("gemini-3.8-flash", withSchema = true) }
    private val flashJsonModel by lazy { createModel("gemini-3.8-flash", withSchema = false) }
    private val liteStructuredModel by lazy { createModel("gemini-3.5-flash-lite", withSchema = true) }
    private val liteJsonModel by lazy { createModel("gemini-3.5-flash-lite", withSchema = false) }

    private val balanceReaderSchema = Schema.obj(
        properties = mapOf(
            "found" to Schema.boolean(
                description = "True if the broker account balance number is clearly readable in the header."
            ),
            "balanceText" to Schema.string(
                description = "Exact numeric account balance with optional currency, e.g. '1257.63' or 'R$ 100,00'."
            )
        ),
        optionalProperties = listOf("balanceText")
    )

    private val balanceReaderModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = balanceReaderSchema
                temperature = 0f
            }
        )
    }

    private val stakeLocatorSchema = Schema.obj(
        properties = mapOf(
            "found" to Schema.boolean(
                description = "True ONLY if the stake/investment box (e.g. 'Valor ($) 2', '$ 2', 'R$ 10', or number under 'Valor ($)') is clearly visible."
            ),
            "normX" to Schema.double(
                description = "Normalized center X coordinate (0.0 to 1.0) of the center value inside the Valor ($) box (avoiding '-' and '+')."
            ),
            "normY" to Schema.double(
                description = "Normalized center Y coordinate (0.0 to 1.0) of the Valor ($) box."
            ),
            "amountText" to Schema.string(
                description = "Exact text or amount shown inside the stake box, e.g. 'Valor ($) 2', '$ 2', or '2'."
            )
        ),
        optionalProperties = listOf("normX", "normY", "amountText")
    )

    private val stakeLocatorModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = stakeLocatorSchema
                temperature = 0f
            }
        )
    }

    private val recoveryPlannerSchema = Schema.obj(
        properties = mapOf(
            "mode" to Schema.enumeration(
                values = listOf("FULL_RECOVERY_NOW", "SPLIT_RECOVERY_2X", "BREAKEVEN_SHIELD", "PAUSE_PROTECT"),
                description = "Best recovery tactic: FULL_RECOVERY_NOW (recover 100% deficit + target profit in 1 trade), SPLIT_RECOVERY_2X (recover across 2 smaller safer trades), BREAKEVEN_SHIELD (recover exact loss to zero deficit), or PAUSE_PROTECT (pause auto-trading if risk is too high for balance)."
            ),
            "recommendedStake" to Schema.double(
                description = "Exact next stake amount in user currency to execute the chosen recovery tactic given payout rate."
            ),
            "minRequiredConfidence" to Schema.integer(
                description = "Minimum chart confidence (72..90) required before firing the recovery trade so the robot only enters high-conviction trends."
            ),
            "extraWaitSeconds" to Schema.integer(
                description = "Extra cool-down seconds (0..60) before firing recovery trade to let choppy candles settle."
            ),
            "rationale" to Schema.string(
                description = "Concise 1-sentence Portuguese explanation of the AI recovery strategy."
            )
        )
    )

    private val recoveryPlannerModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = recoveryPlannerSchema
                temperature = 0f
            },
            systemInstruction = content {
                text(
                    "Você é um Gestor Quantitativo de Risco para Opções Binárias. " +
                        "Sua missão é montar a estratégia matemática mais inteligente para recuperar perdas acumuladas considerando o payout real (~85%) e protegendo a banca. " +
                        "Na 1ª ou 2ª perda com banca confortável, prefira FULL_RECOVERY_NOW (valor exato = (déficit + lucro alvo) / payout) elevando a confiança mínima da entrada. " +
                        "A partir da 3ª perda seguida ou quando o valor único ultrapassar 20% da banca, prefira SPLIT_RECOVERY_2X ou BREAKEVEN_SHIELD com filtro rigoroso (>=80%). " +
                        "Se o saldo conhecido for insuficiente para recuperar com segurança (>35% da banca), selecione PAUSE_PROTECT."
                )
            }
        )
    }

    private fun createModel(modelName: String, withSchema: Boolean) =
        Firebase.ai.generativeModel(
            modelName = modelName,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                if (withSchema) {
                    responseSchema = chartAnalysisSchema
                }
                temperature = 0.0f
            },
            systemInstruction = content {
                text(
                    "Analista técnico especialista em Price Action e Opções Binárias. " +
                        "Identifique tendências reais com rapidez e precisão: retorne NEUTRAL em mercado lateral/serrote ou contra-tendência, " +
                        "mas emita CALL_UP ou PUT_DOWN decisivamente sempre que houver inclinação visível e domínio claro de compradores ou vendedores nas velas recentes (mesmo com 1 vela de respiro/pullback normal)."
                )
            }
        )

    override suspend fun readBrokerBalanceFromBitmap(bitmap: Bitmap): Double? = withContext(Dispatchers.IO) {
        if (bitmap.isRecycled || bitmap.width < 24 || bitmap.height < 16) return@withContext null
        val nowMs = SystemClock.elapsedRealtime()
        // Reserve Gemini quota strictly for main chart analysis when cooling down or recently queried
        if (isAuxiliaryQuotaMuted(nowMs)) return@withContext null
        if (lastBalanceOcrAtMs > 0L && (nowMs - lastBalanceOcrAtMs) in 0L..BALANCE_OCR_MIN_INTERVAL_MS) {
            return@withContext null
        }
        lastBalanceOcrAtMs = nowMs
        val uploadBitmap = prepareBitmapForUpload(bitmap)
        try {
            val response = withTimeout(12_000L) {
                balanceReaderModel.generateContent(
                    content {
                        image(uploadBitmap)
                        text(
                            "Leia APENAS o saldo da conta da corretora exibido no topo da tela (ex: 1257.63 ou R$ 100,00). Ignore hora, porcentagem ou outros números."
                        )
                    }
                )
            }
            val raw = response.text?.trim().orEmpty()
            val startIdx = raw.indexOf('{')
            val endIdx = raw.lastIndexOf('}')
            val clean = if (startIdx >= 0 && endIdx > startIdx) raw.substring(startIdx, endIdx + 1) else raw
            val json = JSONObject(clean)
            if (!json.optBoolean("found", false)) return@withContext null
            com.example.ui.components.parseBrokerMoneyValue(json.optString("balanceText", ""))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isQuotaExceededThrowable(e)) {
                recordQuotaError(isLiteModel = true)
                Log.w(TAG, "Visual broker balance skipped due to quota protection")
            } else {
                Log.w(TAG, "Visual broker balance read skipped: ${e.message}")
            }
            null
        } finally {
            if (uploadBitmap !== bitmap && !uploadBitmap.isRecycled) {
                uploadBitmap.recycle()
            }
        }
    }

    override suspend fun locateStakeBoxFromBitmap(lowerPanelBitmap: Bitmap): VisualStakeBoxHit? = withContext(Dispatchers.IO) {
        if (lowerPanelBitmap.isRecycled || lowerPanelBitmap.width < 24 || lowerPanelBitmap.height < 16) {
            return@withContext null
        }
        val nowMs = SystemClock.elapsedRealtime()
        if (isAuxiliaryQuotaMuted(nowMs)) return@withContext null
        if (lastStakeLocateAtMs > 0L && (nowMs - lastStakeLocateAtMs) in 0L..STAKE_LOCATE_MIN_INTERVAL_MS) {
            return@withContext null
        }
        lastStakeLocateAtMs = nowMs
        val uploadBitmap = prepareBitmapForUpload(lowerPanelBitmap)
        try {
            val response = withTimeout(10_000L) {
                stakeLocatorModel.generateContent(
                    content {
                        image(uploadBitmap)
                        text(
                            "Localize APENAS o centro do botão de VALOR/INVESTIMENTO da operação (ex: caixa 'Valor ($)' com número '2' ou '$ 2' entre os sinais '-' e '+', geralmente à direita de 'Horário'). " +
                                "Aponte normX/normY para o número central dentro da caixa Valor ($), evitando '-' e '+'. " +
                                "NUNCA selecione 'Horário'/'Expiração' (ex: '1 min', '2 min', '30 s'), NUNCA selecione lucro ('+89%'), e NUNCA selecione Acima/Abaixo."
                        )
                    }
                )
            }
            parseVisualStakeBoxJson(response.text?.trim().orEmpty())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isQuotaExceededThrowable(e)) {
                recordQuotaError(isLiteModel = true)
                Log.w(TAG, "Visual stake box localization skipped due to quota protection")
            } else {
                Log.w(TAG, "Visual stake box localization skipped: ${e.message}")
            }
            null
        } finally {
            if (uploadBitmap !== lowerPanelBitmap && !uploadBitmap.isRecycled) {
                uploadBitmap.recycle()
            }
        }
    }

    internal fun parseVisualStakeBoxJson(raw: String): VisualStakeBoxHit? {
        if (raw.isBlank()) return null
        return try {
            val startIdx = raw.indexOf('{')
            val endIdx = raw.lastIndexOf('}')
            val clean = if (startIdx >= 0 && endIdx > startIdx) raw.substring(startIdx, endIdx + 1) else raw
            val json = JSONObject(clean)
            if (!json.optBoolean("found", false)) return null
            val nx = json.optDouble("normX", -1.0).toFloat()
            val ny = json.optDouble("normY", -1.0).toFloat()
            if (nx !in 0.05f..0.95f || ny !in 0.05f..0.95f) return null

            val amountText = json.optString("amountText", "").trim()
            if (amountText.isBlank()) return null
            val lower = amountText.lowercase(Locale.ROOT)
            if (Regex("(min|seg|sec|hora|hor[aá]rio|hour|%|acima|abaixo|call|put|saldo|balance|:)").containsMatchIn(lower)) {
                return null
            }
            val parsedAmount = com.example.ui.components.parseBrokerMoneyValue(amountText)
            if (parsedAmount == null || parsedAmount <= 0.0) return null

            VisualStakeBoxHit(normX = nx, normY = ny, currentAmount = parsedAmount)
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun planLossRecoveryStrategy(request: AiRecoveryRequest): AiRecoveryPlan? =
        withContext(Dispatchers.IO) {
            if (request.accumulatedDeficit <= 0.005) return@withContext null
            // Preserve Gemini RPM strictly for live chart trend analysis whenever quota is tight
            if (isAuxiliaryQuotaMuted()) return@withContext null
            try {
                val payout = request.payoutRate.coerceIn(0.65, 0.95)
                val targetProfit = request.initialStake * payout
                val exactFullStake = kotlin.math.ceil(((request.accumulatedDeficit + targetProfit) / payout) * 100.0) / 100.0
                val exactSplitStake = kotlin.math.ceil(((request.accumulatedDeficit * 0.55 + targetProfit * 0.5) / payout) * 100.0) / 100.0
                val exactBreakevenStake = kotlin.math.ceil((request.accumulatedDeficit / payout) * 100.0) / 100.0
                val balanceInfo = request.liveBalance?.let { "${request.currencySymbol} ${com.example.model.formatStakeAmount(it)}" }
                    ?: "desconhecido"
                val marketInfo = request.recentMarketContext?.takeIf { it.isNotBlank() } ?: "sem leituras recentes"

                val prompt = buildString {
                    appendLine("Monte o plano de recuperação de perdas para a próxima operação:")
                    appendLine("• Déficit acumulado na sessão: ${request.currencySymbol} ${com.example.model.formatStakeAmount(request.accumulatedDeficit)}")
                    appendLine("• Entrada inicial: ${request.currencySymbol} ${com.example.model.formatStakeAmount(request.initialStake)} | Payout estimado: ${(payout * 100).roundToInt()}%")
                    appendLine("• Perdas consecutivas: ${request.consecutiveLosses} (limite configurado: ${request.maxSteps})")
                    appendLine("• Saldo atual da corretora: $balanceInfo | Confiança base: ${request.baseMinConfidence}%")
                    appendLine("• Leituras recentes do gráfico: $marketInfo")
                    appendLine("• Referências matemáticas exatas: FULL_RECOVERY_NOW=${com.example.model.formatStakeAmount(exactFullStake)}, SPLIT_RECOVERY_2X=${com.example.model.formatStakeAmount(exactSplitStake)}, BREAKEVEN_SHIELD=${com.example.model.formatStakeAmount(exactBreakevenStake)}")
                    appendLine("Escolha mode, recommendedStake, minRequiredConfidence (74..88), extraWaitSeconds (0..45) e rationale em PT-BR (1 frase curta).")
                }

                val response = withTimeout(30_000L) {
                    recoveryPlannerModel.generateContent(prompt)
                }
                parseAiRecoveryPlanJson(
                    raw = response.text?.trim().orEmpty(),
                    request = request
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isQuotaExceededThrowable(e)) {
                    recordQuotaError(isLiteModel = true)
                    Log.w(TAG, "AI recovery planner deferred due to quota protection")
                } else {
                    Log.w(TAG, "AI recovery planner fallback triggered: ${e.message}")
                }
                null
            }
        }

    internal fun parseAiRecoveryPlanJson(
        raw: String,
        request: AiRecoveryRequest
    ): AiRecoveryPlan? {
        if (raw.isBlank()) return null
        return try {
            val startIdx = raw.indexOf('{')
            val endIdx = raw.lastIndexOf('}')
            val clean = if (startIdx >= 0 && endIdx > startIdx) raw.substring(startIdx, endIdx + 1) else raw
            val json = JSONObject(clean)

            val modeStr = json.optString("mode", "FULL_RECOVERY_NOW").trim().uppercase(Locale.US)
            val mode = when (modeStr) {
                "FULL_RECOVERY_NOW" -> AiRecoveryMode.FULL_RECOVERY_NOW
                "SPLIT_RECOVERY_2X" -> AiRecoveryMode.SPLIT_RECOVERY_2X
                "BREAKEVEN_SHIELD" -> AiRecoveryMode.BREAKEVEN_SHIELD
                "PAUSE_PROTECT" -> AiRecoveryMode.PAUSE_PROTECT
                else -> AiRecoveryMode.FULL_RECOVERY_NOW
            }

            val helperConfig = MartingaleConfig(
                enabled = true,
                initialStake = request.initialStake,
                maxSteps = request.maxSteps,
                riskStrategy = com.example.model.RiskStrategy.AI_RECOVERY,
                currencySymbol = request.currencySymbol,
                estimatedPayoutRate = request.payoutRate
            )
            val deterministicStake = helperConfig.computeRecoveryStake(
                accumulatedDeficit = request.accumulatedDeficit,
                mode = mode,
                liveBalance = request.liveBalance
            )
            val rawStake = json.optDouble("recommendedStake", deterministicStake)
            val stepCap = helperConfig.stakeForStep(request.maxSteps).coerceAtLeast(request.initialStake * 2.0)
            val balanceCap = if (request.liveBalance != null && request.liveBalance >= request.initialStake) {
                kotlin.math.max(request.initialStake, kotlin.math.floor(request.liveBalance * 0.35 * 100.0) / 100.0)
            } else {
                Double.MAX_VALUE
            }
            val safeCap = minOf(stepCap, balanceCap).coerceAtLeast(request.initialStake)

            val clampedStake = if (mode == AiRecoveryMode.PAUSE_PROTECT) {
                request.initialStake
            } else if (!rawStake.isFinite() || rawStake < request.initialStake) {
                deterministicStake
            } else {
                val rounded = kotlin.math.ceil(rawStake * 100.0 - 1e-9) / 100.0
                // Ensure AI stake is at least the exact mathematical minimum for the chosen mode (unless capped by bankroll)
                val minForMode = deterministicStake.coerceAtMost(safeCap)
                rounded.coerceIn(minForMode, safeCap)
            }

            val minConf = parseFlexibleInt(
                json,
                "minRequiredConfidence",
                (request.baseMinConfidence + 4).coerceAtMost(84)
            ).coerceIn(maxOf(request.baseMinConfidence, 72), 88)

            val waitSec = parseFlexibleInt(json, "extraWaitSeconds", 0).coerceIn(0, 60)
            val rationale = json.optString("rationale", "").trim().ifBlank {
                "IA calculou ${request.currencySymbol} ${com.example.model.formatStakeAmount(clampedStake)} (${mode.label}) com filtro ≥ $minConf%."
            }

            AiRecoveryPlan(
                mode = mode,
                recommendedStake = clampedStake,
                minRequiredConfidence = minConf,
                extraWaitSeconds = waitSec,
                rationale = rationale,
                isFromGemini = true
            )
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun analyzeChartRegion(
        bitmap: Bitmap,
        sourceDescription: String,
        boConfig: BoConfig,
        previousReadingHint: String?
    ): ChartAnalysisOutcome = withContext(Dispatchers.IO) {
        if (bitmap.isRecycled || bitmap.width < 24 || bitmap.height < 24) {
            return@withContext ChartAnalysisOutcome.InvalidChart(
                "A área marcada é muito pequena para identificar um gráfico de velas."
            )
        }

        var nowMs = SystemClock.elapsedRealtime()
        // Smooth burst protection: prevent rapid back-to-back Gemini calls within 10s that trip RPM limits
        val sinceLastChartMs = if (lastChartRequestAtMs > 0L) nowMs - lastChartRequestAtMs else Long.MAX_VALUE
        if (sinceLastChartMs in 0L until MIN_CHART_REQUEST_SPACING_MS) {
            delay(MIN_CHART_REQUEST_SPACING_MS - sinceLastChartMs)
            nowMs = SystemClock.elapsedRealtime()
        }

        var liteCooling = isLiteInQuotaCooldown(nowMs)
        var flashCooling = isFlashInQuotaCooldown(nowMs)
        if (liteCooling && flashCooling) {
            val remainingMs = (minOf(liteQuotaCooldownUntilMs, flashQuotaCooldownUntilMs) - nowMs).coerceAtLeast(0L)
            if (remainingMs in 1L..SHORT_COOLDOWN_WAIT_THRESHOLD_MS) {
                delay(remainingMs + 400L)
                nowMs = SystemClock.elapsedRealtime()
                liteCooling = isLiteInQuotaCooldown(nowMs)
                flashCooling = isFlashInQuotaCooldown(nowMs)
            } else if (remainingMs > SHORT_COOLDOWN_WAIT_THRESHOLD_MS) {
                val remainingSec = ((remainingMs / 1000L).coerceIn(15L, 360L)).toInt()
                throw AiQuotaCooldownException(
                    remainingSeconds = remainingSec,
                    message = "Proteção de cota da IA Gemini ativa — aguardando ${remainingSec}s para estabilizar o limite (ou toque em Despausar IA agora)."
                )
            }
        }

        val uploadBitmap = prepareBitmapForUpload(bitmap)
        try {
            // Prioritize high-RPM, ultra-fast gemini-3.5-flash-lite for continuous live scanning,
            // failing over seamlessly to gemini-3.8-flash whenever lite is cooling down or fails.
            val preferLitePrimary = shouldPreferLitePrimary(
                liteCooling = liteCooling,
                flashCooling = flashCooling
            )
            val primaryStructured = if (preferLitePrimary) liteStructuredModel else flashStructuredModel
            val primaryJson = if (preferLitePrimary) liteJsonModel else flashJsonModel
            val fallbackStructured = if (preferLitePrimary) flashStructuredModel else liteStructuredModel
            val fallbackJson = if (preferLitePrimary) flashJsonModel else liteJsonModel
            val primaryLabel = if (preferLitePrimary) "gemini-3.5-flash-lite" else "gemini-3.8-flash"
            val fallbackLabel = if (preferLitePrimary) "gemini-3.8-flash" else "gemini-3.5-flash-lite"

            suspend fun executeAttempt(
                structured: com.google.firebase.ai.GenerativeModel,
                jsonOnly: com.google.firebase.ai.GenerativeModel,
                modelLabel: String
            ): ChartAnalysisOutcome {
                lastChartRequestAtMs = SystemClock.elapsedRealtime()
                val response = runWithSchemaFallback(
                    structured = structured,
                    jsonOnly = jsonOnly,
                    uploadBitmap = uploadBitmap,
                    sourceDescription = sourceDescription,
                    boConfig = boConfig,
                    previousReadingHint = previousReadingHint,
                    timeoutMs = 18_000L
                )
                val jsonText = response.text?.takeIf { it.isNotBlank() }
                    ?: run {
                        val finishReason = response.candidates.firstOrNull()?.finishReason?.name
                        val blockReason = response.promptFeedback?.blockReason?.name
                        val detail = listOfNotNull(finishReason, blockReason).joinToString(" / ")
                        throw IllegalStateException(
                            if (detail.isNotBlank()) {
                                "A IA Gemini ($modelLabel) não retornou texto ($detail)."
                            } else {
                                "A IA Gemini ($modelLabel) retornou uma resposta vazia."
                            }
                        )
                    }
                recordSuccessfulChartCall()
                return parseGeminiOutcome(
                    jsonText = jsonText,
                    sourceDescription = sourceDescription,
                    boConfig = boConfig
                )
            }

            try {
                executeAttempt(primaryStructured, primaryJson, primaryLabel)
            } catch (timeoutErr: TimeoutCancellationException) {
                if (!currentCoroutineContext().isActive) throw timeoutErr
                Log.w(TAG, "$primaryLabel timed out, retrying immediately on $fallbackLabel", timeoutErr)
                executeAttempt(fallbackStructured, fallbackJson, fallbackLabel)
            } catch (cancelErr: CancellationException) {
                throw cancelErr
            } catch (primaryErr: Exception) {
                if (primaryErr is PromptBlockedException || primaryErr is ContentBlockedException) {
                    throw primaryErr
                }
                val isPrimaryQuota = isQuotaExceededThrowable(primaryErr)
                if (isPrimaryQuota) {
                    val cooldownMs = recordQuotaError(
                        isLiteModel = preferLitePrimary,
                        coolBothModels = false
                    )
                    val fallbackInCooldown = if (preferLitePrimary) isFlashInQuotaCooldown() else isLiteInQuotaCooldown()
                    if (fallbackInCooldown) {
                        val cooldownSec = (cooldownMs / 1000L).toInt().coerceAtLeast(15)
                        throw AiQuotaCooldownException(
                            remainingSeconds = cooldownSec,
                            message = "Limite de requisições da IA Gemini atingido — pausa inteligente de ${cooldownSec}s para evitar bloqueios."
                        )
                    }
                }
                val fallbackInCooldown = if (preferLitePrimary) isFlashInQuotaCooldown() else isLiteInQuotaCooldown()
                if (fallbackInCooldown) {
                    throw primaryErr
                }
                Log.w(TAG, "$primaryLabel failed (${primaryErr.javaClass.simpleName}), falling back to $fallbackLabel", primaryErr)
                try {
                    executeAttempt(fallbackStructured, fallbackJson, fallbackLabel)
                } catch (fallbackErr: Exception) {
                    if (isQuotaExceededThrowable(fallbackErr)) {
                        val cooldownMs = recordQuotaError(
                            isLiteModel = !preferLitePrimary,
                            coolBothModels = true
                        )
                        val cooldownSec = (cooldownMs / 1000L).toInt().coerceAtLeast(15)
                        throw AiQuotaCooldownException(
                            remainingSeconds = cooldownSec,
                            message = "Limite de requisições da IA Gemini atingido — pausa inteligente de ${cooldownSec}s para evitar bloqueios."
                        )
                    }
                    throw fallbackErr
                }
            }
        } catch (e: TimeoutCancellationException) {
            if (!currentCoroutineContext().isActive) throw e
            Log.e(TAG, "Gemini chart analysis timed out after fallback", e)
            throw IllegalStateException(
                "Tempo esgotado ao consultar a IA Gemini. Verifique sua conexão e tente novamente.",
                e
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: AiQuotaCooldownException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI chart analysis failed", e)
            throw IllegalStateException(formatFirebaseAiError(e), e)
        } finally {
            if (uploadBitmap !== bitmap && !uploadBitmap.isRecycled) {
                uploadBitmap.recycle()
            }
        }
    }

    private suspend fun runWithSchemaFallback(
        structured: com.google.firebase.ai.GenerativeModel,
        jsonOnly: com.google.firebase.ai.GenerativeModel,
        uploadBitmap: Bitmap,
        sourceDescription: String,
        boConfig: BoConfig,
        previousReadingHint: String?,
        timeoutMs: Long = 28_000L
    ) = try {
        val prompt = buildAnalysisPrompt(
            sourceDescription = sourceDescription,
            boConfig = boConfig,
            previousReadingHint = previousReadingHint,
            includeJsonSchemaHint = false
        )
        withTimeout(timeoutMs) {
            structured.generateContent(
                content {
                    image(uploadBitmap)
                    text(prompt)
                }
            )
        }
    } catch (e: SerializationException) {
        Log.w(TAG, "Structured schema serialization fallback triggered", e)
        val fallbackPrompt = buildAnalysisPrompt(
            sourceDescription = sourceDescription,
            boConfig = boConfig,
            previousReadingHint = previousReadingHint,
            includeJsonSchemaHint = true
        )
        withTimeout(timeoutMs) {
            jsonOnly.generateContent(
                content {
                    image(uploadBitmap)
                    text(fallbackPrompt)
                }
            )
        }
    } catch (e: ResponseStoppedException) {
        Log.w(TAG, "Structured response stopped (${e.message}), retrying with unconstrained JSON model", e)
        val fallbackPrompt = buildAnalysisPrompt(
            sourceDescription = sourceDescription,
            boConfig = boConfig,
            previousReadingHint = previousReadingHint,
            includeJsonSchemaHint = true
        )
        withTimeout(timeoutMs) {
            jsonOnly.generateContent(
                content {
                    image(uploadBitmap)
                    text(fallbackPrompt)
                }
            )
        }
    } catch (e: ServerException) {
        if (e.message?.contains("schema", ignoreCase = true) == true ||
            e.message?.contains("INVALID_ARGUMENT", ignoreCase = true) == true
        ) {
            Log.w(TAG, "Server schema fallback triggered", e)
            val fallbackPrompt = buildAnalysisPrompt(
                sourceDescription = sourceDescription,
                boConfig = boConfig,
                previousReadingHint = previousReadingHint,
                includeJsonSchemaHint = true
            )
            withTimeout(timeoutMs) {
                jsonOnly.generateContent(
                    content {
                        image(uploadBitmap)
                        text(fallbackPrompt)
                    }
                )
            }
        } else {
            throw e
        }
    }

    private fun buildAnalysisPrompt(
        sourceDescription: String,
        boConfig: BoConfig,
        previousReadingHint: String?,
        includeJsonSchemaHint: Boolean
    ): String = buildString {
        val expModeText = if (boConfig.autoExpiryEnabled) {
            "Auto IA (escolher melhor entre 30s, 1 min, 2 min ou 5 min; atual ${boConfig.expiryLabel})"
        } else {
            "Fixo: ${boConfig.expiryLabel}"
        }
        appendLine("ANÁLISE RÁPIDA BO ($sourceDescription) | Ativo: ${boConfig.tradingAsset.promptHint} | Exp: $expModeText | Modo: ${boConfig.sensitivity.label} (mín. ${boConfig.sensitivity.minConfidence}%) | Foco: ${boConfig.technicalIndicator.label}.")
        if (!previousReadingHint.isNullOrBlank()) {
            appendLine("Leituras anteriores: $previousReadingHint.")
        }
        appendLine("Regras objetivas (leia da esquerda para a direita):")
        appendLine("1) Gráfico válido: aceite velas, barras, Heikin-Ashi ou linha/área (área vazia à direita é normal). isValidChart=false só sem gráfico de preço.")
        if (boConfig.strictThreeCandleRsiStrategy) {
            appendLine("2) ESTRATÉGIA DE 3 CONDIÇÕES SIMULTÂNEAS (emita CALL_UP ou PUT_DOWN decisivamente assim que as 3 acontecerem juntas):")
            appendLine("   • CONDIÇÃO 1 (TENDÊNCIA PRINCIPAL): conte quantas velas consecutivas mais recentes estão na MESMA direção com corpos firmes em consecutiveSameDirCandles. EXIGIDO >= 3 velas consecutivas na mesma direção (se < 3, retorne NEUTRAL).")
            appendLine("   • CONDIÇÃO 2 (RSI / MOMENTUM): para COMPRA (CALL_UP), use rsiTurnState='LEAVING_OVERSOLD_UP' quando o RSI estiver saindo da sobrevenda virando para cima, OU 'MOMENTUM_ALIGNED_UP' quando 3+ velas verdes firmes confirmarem aceleração de alta (mesmo que o painel de RSI não esteja desenhado na tela). Para VENDA (PUT_DOWN), use 'LEAVING_OVERBOUGHT_DOWN' quando o RSI sair da sobrecompra virando para baixo, OU 'MOMENTUM_ALIGNED_DOWN' quando 3+ velas vermelhas firmes confirmarem aceleração de baixa. Só use 'FLAT_OR_MIDDLE' em mercado realmente lateral/travado ou 'AGAINST_SIGNAL' quando contra.")
            appendLine("   • CONDIÇÃO 3 (CONFIRMAÇÃO DO CANDLE): informe candleCloseConfirmed=true quando a 3ª vela estiver com corpo sólido definido/fechada; só marque false se ainda estiver minúscula/recém-aberta no meio da formação.")
            appendLine("3) REGRA DE FILTRO (MERCADO LATERAL = FORA): se o mercado estiver lateralizado/caixa (CONSOLIDATION_RANGE, macroTrend=SIDEWAYS, CHOPPY_SIDEWAYS, velas alternando MIXED_INDECISION ou Doji indeciso), retorne OBRIGATORIAMENTE direction=NEUTRAL (confidence <=58, trendStrengthScore <=45) e NÃO opere.")
        } else {
            appendLine("2) Tendência REAL (emita CALL_UP ou PUT_DOWN sem hesitar): fundos ascendentes/inclinação positiva + compradores dominando velas recentes (CALL_UP), ou topos descendentes/inclinação negativa + vendedores dominando (PUT_DOWN). 1 vela pequena de respiro/pullback ou ausência de indicador na tela NÃO invalida uma tendência real.")
            appendLine("3) Bloqueie com NEUTRAL (confidence <=58, trendStrengthScore <=45): caixa lateral horizontal (CONSOLIDATION_RANGE / CHOPPY_SIDEWAYS), velas alternando verde-vermelha sem deslocamento (MIXED_INDECISION), Doji indeciso, vela de exaustão gigante (>2.8x média), ou sinal contra a estrutura principal.")
        }
        appendLine("4) Pontue 5 pilares (confluenceCount 0..5): estrutura, inclinação, domínio dos corpos, espaço S/R, momentum/RSI. Quando as 3 condições + tendência clara (trendStrengthScore>=58) alinharem, use confidencePercent 75..92 e direction CALL_UP/PUT_DOWN sem hesitar.")
        if (boConfig.autoExpiryEnabled) {
            appendLine("5) TEMPO ADAPTATIVO (recommendedExpirySeconds = 30, 60, 120 ou 300): escolha a expiração com maior precisão para esta tendência: 30 (30s) em impulso micro explosivo/rompimento veloz ou S/R próximo; 60 (1 min) em tendência limpa de momentum padrão; 120 (2 min) em tendência estruturada gradual/pullback com respiro de 1 vela; 300 (5 min) em macrotendência forte e sustentada (HH_HL/LH_LL) com amplo espaço aberto até S/R principal onde tempo maior filtra ruído curto. Justifique em expiryRationale (curto em PT-BR).")
        }
        appendLine("Responda em PT-BR bem direto (1 frase curta em summary).")
        if (includeJsonSchemaHint) {
            appendLine("""Retorne APENAS JSON: {"isValidChart":boolean,"invalidReason":string,"marketStructure":"HH_HL_UPTREND"|"LH_LL_DOWNTREND"|"CONSOLIDATION_RANGE"|"REVERSAL_TRANSITION","macroTrend":"UP"|"DOWN"|"SIDEWAYS","microMomentum":"UP"|"DOWN"|"CHOPPY","candleDominance":"BUYERS_CONTROL"|"SELLERS_CONTROL"|"MIXED_INDECISION","trendStrengthScore":int,"confluenceCount":int,"entryQuality":"STRONG"|"MODERATE"|"WEAK_AVOID","riskFlag":"NONE"|"EXHAUSTION_CANDLE"|"OPPOSING_WICK"|"SR_WALL"|"CHOPPY_SIDEWAYS"|"DOJI_INDECISION"|"COUNTER_TREND","consecutiveSameDirCandles":int,"rsiTurnState":"LEAVING_OVERSOLD_UP"|"LEAVING_OVERBOUGHT_DOWN"|"MOMENTUM_ALIGNED_UP"|"MOMENTUM_ALIGNED_DOWN"|"FLAT_OR_MIDDLE"|"AGAINST_SIGNAL","candleCloseConfirmed":boolean,"trendLabel":string,"patternLabel":string,"supportResistanceLabel":string,"rsiEstimated":int,"confidencePercent":int,"direction":"CALL_UP"|"PUT_DOWN"|"NEUTRAL","recommendedExpirySeconds":30|60|120|300,"expiryRationale":string,"summary":string}""")
        }
    }

    private fun formatFirebaseAiError(e: Exception): String {
        val rawMsg = e.localizedMessage.orEmpty()
        if (isQuotaExceededThrowable(e)) {
            return "Limite momentâneo da IA Gemini atingido — pausa inteligente ativada para estabilizar a cota automaticamente."
        }
        return when (e) {
            is ServiceDisabledException ->
                "Serviço Firebase AI Logic não habilitado para este projeto (${rawMsg.ifBlank { "ServiceDisabled" }})."
            is UnsupportedUserLocationException ->
                "A IA Gemini não está disponível nesta região/rede."
            is RequestTimeoutException ->
                "Tempo limite esgotado na conexão com a IA Gemini. Tente novamente."
            is PromptBlockedException, is ContentBlockedException ->
                "A imagem enviada foi bloqueada pelos filtros da IA. Recorte apenas a área das velas e tente novamente."
            is ResponseStoppedException ->
                "A resposta da IA foi interrompida antes de concluir (${rawMsg.ifBlank { "ResponseStopped" }})."
            else -> {
                when {
                    rawMsg.contains("403") ||
                        rawMsg.contains("AppCheck", ignoreCase = true) ||
                        rawMsg.contains("App Check", ignoreCase = true) ||
                        rawMsg.contains("attestation", ignoreCase = true) ->
                        "Falha de verificação App Check (403) ao chamar a IA Gemini. Reinstale a versão atualizada ou verifique a rede."
                    rawMsg.contains("UnknownHost", ignoreCase = true) ||
                        rawMsg.contains("Unable to resolve host", ignoreCase = true) ||
                        rawMsg.contains("ConnectException", ignoreCase = true) ->
                        "Sem conexão com a internet para consultar a IA Gemini."
                    else -> rawMsg.ifBlank { "Falha ao consultar a IA Gemini." }
                }
            }
        }
    }

    internal fun parseGeminiOutcome(
        jsonText: String,
        sourceDescription: String,
        boConfig: BoConfig
    ): ChartAnalysisOutcome {
        val startIdx = jsonText.indexOf('{')
        val endIdx = jsonText.lastIndexOf('}')
        val cleanJson = if (startIdx >= 0 && endIdx > startIdx) {
            jsonText.substring(startIdx, endIdx + 1)
        } else {
            jsonText
        }
        val json = JSONObject(cleanJson)
        val isValidChart = json.optBoolean("isValidChart", true)
        if (!isValidChart) {
            val reason = json.optString("invalidReason", "")
                .takeIf { it.isNotBlank() && it != "null" }
                ?: "A área selecionada não contém um gráfico de velas válido."
            return ChartAnalysisOutcome.InvalidChart(reason)
        }

        val rawDir = json.optString("direction", "NEUTRAL").trim().uppercase(Locale.US)
        val parsedDirection = when (rawDir) {
            "CALL_UP", "CALL", "UP", "ACIMA", "ALTA", "BUY", "COMPRA", "COMPRAR", "HIGHER", "ABOVE" ->
                SignalDirection.CALL_UP
            "PUT_DOWN", "PUT", "DOWN", "ABAIXO", "BAIXA", "SELL", "VENDA", "VENDER", "LOWER", "BELOW" ->
                SignalDirection.PUT_DOWN
            else -> SignalDirection.NEUTRAL
        }

        val rawConfidence = parseFlexibleInt(json, "confidencePercent", 55).coerceIn(45, 95)
        val rsi = parseFlexibleInt(json, "rsiEstimated", 50).coerceIn(15, 85)
        val minRequired = boConfig.sensitivity.minConfidence
        val isSniper = boConfig.sensitivity == com.example.model.BoSensitivity.SNIPER
        val isConservative = boConfig.sensitivity == com.example.model.BoSensitivity.CONSERVATIVE
        val isStrictTrendMode = isSniper || isConservative

        val marketStructure = json.optString("marketStructure", "").trim().uppercase(Locale.US)
        val macroTrend = json.optString("macroTrend", "").trim().uppercase(Locale.US)
        val microMomentum = json.optString("microMomentum", "").trim().uppercase(Locale.US)
        val candleDominance = json.optString("candleDominance", "").trim().uppercase(Locale.US)
        val rawTrendStrength = if (json.has("trendStrengthScore")) {
            parseFlexibleInt(json, "trendStrengthScore", -1).coerceIn(0, 100)
        } else {
            -1
        }
        val rawConfluenceCount = if (json.has("confluenceCount")) {
            parseFlexibleInt(json, "confluenceCount", -1).coerceIn(0, 5)
        } else {
            -1
        }
        val entryQuality = json.optString("entryQuality", "").trim().uppercase(Locale.US)
        val riskFlag = json.optString("riskFlag", "NONE").trim().uppercase(Locale.US)
        val rawConsecutiveCandles = if (json.has("consecutiveSameDirCandles") && !json.isNull("consecutiveSameDirCandles")) {
            parseFlexibleInt(json, "consecutiveSameDirCandles", -1).coerceIn(0, 10)
        } else {
            -1
        }
        val rawRsiTurnState = json.optString("rsiTurnState", "").trim().uppercase(Locale.US)
            .takeIf { it.isNotBlank() && it != "NULL" }
            .orEmpty()
        val candleCloseConfirmed = if (json.has("candleCloseConfirmed") && !json.isNull("candleCloseConfirmed")) {
            json.optBoolean("candleCloseConfirmed", true)
        } else {
            true
        }

        // Institutional multi-layer trend & anti-chop guardrails + 3-condition strategy enforcement:
        // Strictly reject sideways, choppy, transitioning, counter-trend, or low-confluence setups.
        var blockReason: String? = null
        var adjustedConfidence = rawConfidence

        if (parsedDirection != SignalDirection.NEUTRAL) {
            val hasVerifiedThreeCandleRsiReversal = boConfig.strictThreeCandleRsiStrategy &&
                rawConsecutiveCandles >= 3 &&
                ((parsedDirection == SignalDirection.CALL_UP &&
                    rawRsiTurnState == "LEAVING_OVERSOLD_UP" &&
                    microMomentum == "UP" &&
                    candleDominance == "BUYERS_CONTROL") ||
                    (parsedDirection == SignalDirection.PUT_DOWN &&
                        rawRsiTurnState == "LEAVING_OVERBOUGHT_DOWN" &&
                        microMomentum == "DOWN" &&
                        candleDominance == "SELLERS_CONTROL")) &&
                marketStructure != "CONSOLIDATION_RANGE" &&
                riskFlag == "NONE"

            // 0. User's 3-condition simultaneous strategy enforcement (when fields are reported by Gemini)
            if (boConfig.strictThreeCandleRsiStrategy) {
                if (rawConsecutiveCandles in 0..2) {
                    blockReason = "Regra 1/3: exige 3 velas seguidas na mesma direção (atual: $rawConsecutiveCandles/3)"
                }
                if (blockReason == null && rawRsiTurnState.isNotBlank()) {
                    val rsiAligned = when (parsedDirection) {
                        SignalDirection.CALL_UP ->
                            rawRsiTurnState == "LEAVING_OVERSOLD_UP" || rawRsiTurnState == "MOMENTUM_ALIGNED_UP"
                        SignalDirection.PUT_DOWN ->
                            rawRsiTurnState == "LEAVING_OVERBOUGHT_DOWN" || rawRsiTurnState == "MOMENTUM_ALIGNED_DOWN"
                        SignalDirection.NEUTRAL -> false
                    }
                    if (!rsiAligned) {
                        blockReason = when (parsedDirection) {
                            SignalDirection.CALL_UP ->
                                "Regra 2/3 (RSI): aguardando RSI sair da sobrevenda e virar para cima"
                            SignalDirection.PUT_DOWN ->
                                "Regra 2/3 (RSI): aguardando RSI sair da sobrecompra e virar para baixo"
                            SignalDirection.NEUTRAL -> null
                        }
                    }
                }
            }

            // 1. Entry quality & micro momentum check
            if (blockReason == null && entryQuality == "WEAK_AVOID") {
                blockReason = "Qualidade fraca — aguardando tendência mais limpa"
            }

            if (blockReason == null && microMomentum == "CHOPPY") {
                blockReason = "Mercado sem direção nas últimas velas — entrada evitada"
            }

            // 2. Market structure lock (blocks consolidation boxes, structural contradictions, and unconfirmed transitions)
            if (blockReason == null && marketStructure.isNotBlank()) {
                val confirmedReversal = (!isSniper &&
                    rawTrendStrength >= 60 &&
                    (rawConfluenceCount < 0 || rawConfluenceCount >= 3) &&
                    ((parsedDirection == SignalDirection.CALL_UP && candleDominance == "BUYERS_CONTROL" && microMomentum == "UP") ||
                        (parsedDirection == SignalDirection.PUT_DOWN && candleDominance == "SELLERS_CONTROL" && microMomentum == "DOWN"))) ||
                    hasVerifiedThreeCandleRsiReversal
                when (marketStructure) {
                    "CONSOLIDATION_RANGE" ->
                        blockReason = "Mercado lateralizado — fique fora, não opere"
                    "REVERSAL_TRANSITION" -> if (!confirmedReversal) {
                        blockReason = "Zona de transição incerta — aguardando tendência firmar"
                    }
                    "LH_LL_DOWNTREND" -> if (parsedDirection == SignalDirection.CALL_UP && !hasVerifiedThreeCandleRsiReversal) {
                        blockReason = "Estrutura de baixa (topos descendentes) — compra bloqueada"
                    }
                    "HH_HL_UPTREND" -> if (parsedDirection == SignalDirection.PUT_DOWN && !hasVerifiedThreeCandleRsiReversal) {
                        blockReason = "Estrutura de alta (fundos ascendentes) — venda bloqueada"
                    }
                }
            }

            // 3. Candle body dominance check (blocks mixed alternating red/green candles)
            if (blockReason == null && candleDominance.isNotBlank()) {
                when (candleDominance) {
                    "MIXED_INDECISION" ->
                        blockReason = "Velas alternadas/indecisas — mercado lateralizado, não opere"
                    "SELLERS_CONTROL" -> if (parsedDirection == SignalDirection.CALL_UP) {
                        blockReason = "Vendedores dominando as velas atuais — compra bloqueada"
                    }
                    "BUYERS_CONTROL" -> if (parsedDirection == SignalDirection.PUT_DOWN) {
                        blockReason = "Compradores dominando as velas atuais — venda bloqueada"
                    }
                }
            }

            // 4. Macro sideways lock (strict sideways filter: NEVER trade sideways market when strict strategy is ON)
            if (blockReason == null && macroTrend == "SIDEWAYS") {
                val strongModerateBreakout = !boConfig.strictThreeCandleRsiStrategy &&
                    !isStrictTrendMode &&
                    entryQuality == "STRONG" &&
                    rawTrendStrength >= 68
                if (!strongModerateBreakout) {
                    blockReason = "Mercado lateralizado — fique fora, não opere"
                }
            }

            // 5. Macro / Micro alignment lock (zero counter-trend entries unless verified 3-candle RSI oversold/overbought reversal)
            if (blockReason == null) {
                val macroConflict = (parsedDirection == SignalDirection.CALL_UP && macroTrend == "DOWN") ||
                    (parsedDirection == SignalDirection.PUT_DOWN && macroTrend == "UP")
                val microConflict = (parsedDirection == SignalDirection.CALL_UP && microMomentum == "DOWN") ||
                    (parsedDirection == SignalDirection.PUT_DOWN && microMomentum == "UP")
                if (microConflict || (macroConflict && isStrictTrendMode && !hasVerifiedThreeCandleRsiReversal)) {
                    blockReason = "Contra a tendência principal — entrada bloqueada"
                } else if (macroConflict && !hasVerifiedThreeCandleRsiReversal) {
                    adjustedConfidence = (adjustedConfidence - 10).coerceAtLeast(45)
                }
            }

            // 6. Quantitative trend strength gate (when reported by Gemini)
            val minTrendStrength = when (boConfig.sensitivity) {
                com.example.model.BoSensitivity.SNIPER -> 74
                com.example.model.BoSensitivity.CONSERVATIVE -> 56
                com.example.model.BoSensitivity.MODERATE -> 52
            }
            if (blockReason == null && rawTrendStrength in 0 until minTrendStrength) {
                blockReason = "Tendência fraca ($rawTrendStrength/100) — exigido ≥ $minTrendStrength"
            }

            // 7. Five-pillar confluence gate (when reported by Gemini)
            val minPillars = if (isSniper) 4 else 3
            if (blockReason == null && rawConfluenceCount in 0 until minPillars) {
                blockReason = "Confluência insuficiente ($rawConfluenceCount/5 pilares) — aguardando"
            }

            // 8. Surgical trap detection
            if (blockReason == null && riskFlag.isNotBlank() && riskFlag != "NONE") {
                val riskLabel = when (riskFlag) {
                    "EXHAUSTION_CANDLE" -> "Vela de exaustão (risco de retração)"
                    "OPPOSING_WICK" -> "Pavio de rejeição contra o sinal"
                    "SR_WALL" -> "Preço colado em suporte/resistência"
                    "CHOPPY_SIDEWAYS" -> "Mercado lateral/travado"
                    "DOJI_INDECISION" -> "Vela de indecisão (Doji)"
                    "COUNTER_TREND" -> "Operação contra a tendência principal"
                    else -> "Risco técnico detectado ($riskFlag)"
                }
                val isAlwaysBlockedTrap = riskFlag == "EXHAUSTION_CANDLE" ||
                    riskFlag == "CHOPPY_SIDEWAYS" ||
                    riskFlag == "DOJI_INDECISION" ||
                    riskFlag == "COUNTER_TREND"
                val hasSolidTrendDespiteMinorRisk = isConservative &&
                    (riskFlag == "OPPOSING_WICK" || riskFlag == "SR_WALL") &&
                    rawTrendStrength >= 62 &&
                    (rawConfluenceCount < 0 || rawConfluenceCount >= 3)
                if (isAlwaysBlockedTrap || isSniper || (isConservative && entryQuality != "STRONG" && !hasSolidTrendDespiteMinorRisk)) {
                    blockReason = "Bloqueado: $riskLabel"
                } else {
                    adjustedConfidence = (adjustedConfidence - 3).coerceAtLeast(45)
                }
            }

            // 9. Extreme RSI exhaustion guard
            val maxCallRsi = when (boConfig.sensitivity) {
                com.example.model.BoSensitivity.SNIPER -> 75
                com.example.model.BoSensitivity.CONSERVATIVE -> 78
                com.example.model.BoSensitivity.MODERATE -> 80
            }
            val minPutRsi = when (boConfig.sensitivity) {
                com.example.model.BoSensitivity.SNIPER -> 25
                com.example.model.BoSensitivity.CONSERVATIVE -> 22
                com.example.model.BoSensitivity.MODERATE -> 20
            }
            if (blockReason == null && parsedDirection == SignalDirection.CALL_UP && rsi >= maxCallRsi) {
                blockReason = "Bloqueado: sobrecompra extrema (RSI $rsi)"
            } else if (blockReason == null && parsedDirection == SignalDirection.PUT_DOWN && rsi <= minPutRsi) {
                blockReason = "Bloqueado: sobrevenda extrema (RSI $rsi)"
            }

            // 10. Clean-alignment precision boost when structured pillars agree cleanly (never bypassing blocks)
            if (blockReason == null && riskFlag == "NONE") {
                if (rawConfluenceCount >= 4 && rawTrendStrength >= 78) {
                    adjustedConfidence = (adjustedConfidence + 2).coerceAtMost(95)
                } else if (rawConfluenceCount >= 3 && rawTrendStrength >= 62 && adjustedConfidence in 66..71) {
                    adjustedConfidence = maxOf(adjustedConfidence + 4, minRequired).coerceAtMost(95)
                }
            }
        }

        val guardrailCheckedDirection = when {
            blockReason != null -> SignalDirection.NEUTRAL
            parsedDirection != SignalDirection.NEUTRAL && adjustedConfidence < minRequired -> SignalDirection.NEUTRAL
            else -> parsedDirection
        }

        val rawTrendLabel = json.optString("trendLabel", "").trim()
        val trendLower = rawTrendLabel.lowercase(Locale.ROOT)
        val hasContextualModifier = trendLower.contains("após") ||
            trendLower.contains("revers") ||
            trendLower.contains("recuper") ||
            trendLower.contains("repique") ||
            trendLower.contains("correç") ||
            trendLower.contains("correc") ||
            trendLower.contains("pullback") ||
            trendLower.contains("romp")

        // Only coerce genuine opposite-direction contradictions (never nuanced phrases like "Recuperação após baixa")
        val contradictsDirection = when (guardrailCheckedDirection) {
            SignalDirection.CALL_UP ->
                (!hasContextualModifier && trendLower.contains("baixa") && !trendLower.contains("alta")) ||
                    (!hasContextualModifier && trendLower.contains("lateral") && !trendLower.contains("alta"))
            SignalDirection.PUT_DOWN ->
                (!hasContextualModifier && trendLower.contains("alta") && !trendLower.contains("baixa")) ||
                    (!hasContextualModifier && trendLower.contains("lateral") && !trendLower.contains("baixa"))
            SignalDirection.NEUTRAL -> false
        }

        // Only allow gentle label rescue in MODERATE when structure/macro/candles are NOT lateral or choppy
        val hasLateralStructure = marketStructure == "CONSOLIDATION_RANGE" ||
            marketStructure == "REVERSAL_TRANSITION" ||
            macroTrend == "SIDEWAYS" ||
            microMomentum == "CHOPPY" ||
            candleDominance == "MIXED_INDECISION"

        val rescuedFromLabel = if (guardrailCheckedDirection == SignalDirection.NEUTRAL &&
            !isStrictTrendMode &&
            blockReason == null &&
            !hasLateralStructure &&
            (riskFlag.isBlank() || riskFlag == "NONE") &&
            entryQuality != "WEAK_AVOID" &&
            adjustedConfidence >= minRequired &&
            !hasContextualModifier &&
            !trendLower.contains("lateral") &&
            !trendLower.contains("indefin") &&
            !trendLower.contains("neutr")
        ) {
            when {
                trendLower.contains("alta") && !trendLower.contains("baixa") -> SignalDirection.CALL_UP
                trendLower.contains("baixa") && !trendLower.contains("alta") -> SignalDirection.PUT_DOWN
                else -> SignalDirection.NEUTRAL
            }
        } else {
            SignalDirection.NEUTRAL
        }

        val finalDirection = when {
            contradictsDirection -> SignalDirection.NEUTRAL
            rescuedFromLabel != SignalDirection.NEUTRAL -> rescuedFromLabel
            else -> guardrailCheckedDirection
        }

        val finalConfidence = if (finalDirection == SignalDirection.NEUTRAL) {
            adjustedConfidence.coerceAtMost(minRequired - 1).coerceIn(45, 79)
        } else {
            adjustedConfidence
        }

        val resolvedTrendStrength = when {
            rawTrendStrength >= 0 -> rawTrendStrength
            finalDirection == SignalDirection.NEUTRAL -> (finalConfidence - 10).coerceIn(25, 58)
            else -> finalConfidence.coerceIn(60, 95)
        }
        val resolvedConfluence = when {
            rawConfluenceCount >= 0 -> rawConfluenceCount
            finalDirection == SignalDirection.NEUTRAL -> if (blockReason != null) 1 else 2
            finalConfidence >= 84 -> 5
            finalConfidence >= 76 -> 4
            else -> 3
        }

        val trendLabel = rawTrendLabel.takeIf { it.isNotBlank() }
            ?: when (finalDirection) {
                SignalDirection.CALL_UP -> "Tendência de alta"
                SignalDirection.PUT_DOWN -> "Tendência de baixa"
                SignalDirection.NEUTRAL -> "Mercado lateral"
            }

        val patternLabel = when {
            blockReason != null -> blockReason
            parsedDirection != SignalDirection.NEUTRAL && finalDirection == SignalDirection.NEUTRAL ->
                "Sinal filtrado (${boConfig.sensitivity.label}: mín. $minRequired%)"
            else -> json.optString("patternLabel", "").takeIf { it.isNotBlank() }
                ?: "Leitura estrutural de price action"
        }

        val srLabel = json.optString("supportResistanceLabel", "").takeIf { it.isNotBlank() }
            ?: "Zona intermediária"

        val rawRecommendedExpiry = if (json.has("recommendedExpirySeconds") && !json.isNull("recommendedExpirySeconds")) {
            val parsed = parseFlexibleInt(json, "recommendedExpirySeconds", -1)
            if (parsed > 0) normalizeAdaptiveExpirySeconds(parsed) else null
        } else {
            null
        }
        val rawExpiryRationale = json.optString("expiryRationale", "").trim()
            .takeIf { it.isNotBlank() && it != "null" }
            .orEmpty()

        val hasStructuredTrendFields = rawTrendStrength >= 0 ||
            marketStructure.isNotBlank() ||
            microMomentum.isNotBlank() ||
            entryQuality.isNotBlank()

        val resolvedRecommendedExpiry: Int? = when {
            !boConfig.autoExpiryEnabled || finalDirection == SignalDirection.NEUTRAL -> null
            rawRecommendedExpiry != null -> rawRecommendedExpiry
            hasStructuredTrendFields -> chooseAdaptiveExpiryFromStructure(
                direction = finalDirection,
                marketStructure = marketStructure,
                entryQuality = entryQuality,
                riskFlag = riskFlag,
                trendStrength = resolvedTrendStrength,
                confluenceCount = resolvedConfluence,
                rsi = rsi
            )
            else -> null
        }

        val effectiveExpiryLabel = resolvedRecommendedExpiry?.let { formatExpiryLabel(it) } ?: boConfig.expiryLabel
        val resolvedExpiryReason = if (resolvedRecommendedExpiry != null) {
            rawExpiryRationale.ifBlank { defaultExpiryRationaleFor(resolvedRecommendedExpiry) }
        } else {
            ""
        }

        val summary = if (blockReason != null) {
            val baseSummary = json.optString("summary", "").takeIf { it.isNotBlank() }
            if (baseSummary != null) "$blockReason. $baseSummary" else blockReason
        } else {
            json.optString("summary", "").takeIf { it.isNotBlank() }
                ?: "Análise Gemini BO $effectiveExpiryLabel ($sourceDescription): $trendLabel com $finalConfidence% de confluência ($resolvedConfluence/5 pilares)."
        }

        val timeFormatted = BrazilLocaleConfig.formatBrasiliaTime()

        val assetDisplay = if (boConfig.tradingAsset == com.example.model.TradingAsset.AUTO) {
            sourceDescription
        } else {
            boConfig.tradingAsset.label
        }

        val rule1CandlesMet = rawConsecutiveCandles >= 3 || (rawConsecutiveCandles < 0 && finalDirection != SignalDirection.NEUTRAL)
        val rule2RsiMet = when (finalDirection) {
            SignalDirection.CALL_UP ->
                rawRsiTurnState == "LEAVING_OVERSOLD_UP" || rawRsiTurnState == "MOMENTUM_ALIGNED_UP" || rawRsiTurnState.isBlank()
            SignalDirection.PUT_DOWN ->
                rawRsiTurnState == "LEAVING_OVERBOUGHT_DOWN" || rawRsiTurnState == "MOMENTUM_ALIGNED_DOWN" || rawRsiTurnState.isBlank()
            SignalDirection.NEUTRAL ->
                rawRsiTurnState == "LEAVING_OVERSOLD_UP" ||
                    rawRsiTurnState == "LEAVING_OVERBOUGHT_DOWN" ||
                    rawRsiTurnState == "MOMENTUM_ALIGNED_UP" ||
                    rawRsiTurnState == "MOMENTUM_ALIGNED_DOWN"
        }
        val rule3CandleCloseMet = candleCloseConfirmed && finalDirection != SignalDirection.NEUTRAL
        val strategyRulesMetCount = listOf(rule1CandlesMet, rule2RsiMet, rule3CandleCloseMet).count { it }

        return ChartAnalysisOutcome.ValidChart(
            ChartAnalysisResult(
                id = UUID.randomUUID().toString(),
                direction = finalDirection,
                confidencePercent = finalConfidence,
                assetLabel = "$assetDisplay • ${boConfig.technicalIndicator.shortLabel} • $effectiveExpiryLabel",
                timeframeLabel = effectiveExpiryLabel,
                rsiValue = rsi,
                trendLabel = trendLabel,
                patternLabel = patternLabel,
                supportResistanceLabel = srLabel,
                summary = summary,
                timestampLabel = timeFormatted,
                isDemoMode = false,
                noticeMessage = "IA Gemini Avançada analisando 3 velas consecutivas, virada do RSI, fechamento e filtro lateral.",
                trendStrengthScore = resolvedTrendStrength,
                confluenceCount = resolvedConfluence,
                recommendedExpirySeconds = resolvedRecommendedExpiry,
                expirySelectionReason = resolvedExpiryReason,
                consecutiveSameDirectionCandles = rawConsecutiveCandles.coerceAtLeast(0),
                rsiTurnState = rawRsiTurnState,
                candleCloseConfirmed = candleCloseConfirmed,
                strategyRulesMetCount = strategyRulesMetCount
            )
        )
    }

    internal fun chooseAdaptiveExpiryFromStructure(
        direction: SignalDirection,
        marketStructure: String,
        entryQuality: String,
        riskFlag: String,
        trendStrength: Int,
        confluenceCount: Int,
        rsi: Int
    ): Int {
        val nearRsiEdge = (direction == SignalDirection.CALL_UP && rsi >= 70) ||
            (direction == SignalDirection.PUT_DOWN && rsi <= 30)
        val isMacroTrend = marketStructure == "HH_HL_UPTREND" || marketStructure == "LH_LL_DOWNTREND"
        return when {
            riskFlag == "SR_WALL" ||
                nearRsiEdge ||
                (marketStructure == "REVERSAL_TRANSITION" && entryQuality == "STRONG" && trendStrength >= 70) -> 30
            isMacroTrend && trendStrength >= 84 && confluenceCount >= 4 && riskFlag == "NONE" && rsi in 36..64 -> 300
            isMacroTrend && (riskFlag == "OPPOSING_WICK" || entryQuality == "MODERATE" || trendStrength in 72..83) -> 120
            else -> 60
        }
    }

    private fun defaultExpiryRationaleFor(expirySeconds: Int): String = when (expirySeconds) {
        30 -> "Impulso rápido de curto prazo (scalp 30s)"
        60 -> "Momentum direto ideal para M1 (1 min)"
        120 -> "Tendência estruturada com respiro em M2 (2 min)"
        300 -> "Macrotendência forte com espaço amplo em M5 (5 min)"
        else -> "Tempo ajustado pela IA (${formatExpiryLabel(expirySeconds)})"
    }

    private fun prepareBitmapForUpload(source: Bitmap): Bitmap {
        val nonHardware = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
            source.config == Bitmap.Config.HARDWARE
        ) {
            source.copy(Bitmap.Config.ARGB_8888, false) ?: source
        } else {
            source
        }

        // Crisp 640px cap keeps candlestick wicks, EMAs & RSI sharp while speeding up upload and Gemini inference
        val maxDim = 640
        if (nonHardware.width <= maxDim && nonHardware.height <= maxDim) {
            return nonHardware
        }
        val scale = maxDim.toFloat() / maxOf(nonHardware.width, nonHardware.height).toFloat()
        val targetW = (nonHardware.width * scale).roundToInt().coerceAtLeast(1)
        val targetH = (nonHardware.height * scale).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(nonHardware, targetW, targetH, true)
        if (scaled !== nonHardware && nonHardware !== source) {
            nonHardware.recycle()
        }
        return scaled
    }

    private fun parseFlexibleInt(json: JSONObject, key: String, fallback: Int): Int {
        val rawVal = json.opt(key) ?: return fallback
        return when (rawVal) {
            is Number -> rawVal.toInt()
            is String -> Regex("-?[0-9]+").find(rawVal)?.value?.toIntOrNull() ?: fallback
            else -> fallback
        }
    }
}

class DemoPlaceholderAiRepository : AiRepository {

    override suspend fun analyzeChartRegion(
        bitmap: Bitmap,
        sourceDescription: String,
        boConfig: BoConfig,
        previousReadingHint: String?
    ): ChartAnalysisOutcome = withContext(Dispatchers.Default) {
        delay(300L)
        analyzeBitmapForBo(bitmap, sourceDescription, boConfig)
    }

    internal fun analyzeBitmapForBo(
        bitmap: Bitmap,
        sourceDescription: String,
        boConfig: BoConfig
    ): ChartAnalysisOutcome {
        val readableBitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
            bitmap.config == Bitmap.Config.HARDWARE
        ) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
        } else {
            bitmap
        }

        val width = readableBitmap.width
        val height = readableBitmap.height
        if (width < 24 || height < 24) {
            return ChartAnalysisOutcome.InvalidChart(
                "A área marcada é muito pequena para identificar um gráfico de velas."
            )
        }

        // Inset by 2% on each side so the selection overlay's dashed border never pollutes pixel stats
        val marginX = (width * 0.02f).toInt().coerceAtMost(width / 10)
        val marginY = (height * 0.02f).toInt().coerceAtMost(height / 10)
        val sampleW = (width - marginX * 2).coerceAtLeast(16)
        val sampleH = (height - marginY * 2).coerceAtLeast(16)

        val gridW = 160.coerceAtMost(sampleW)
        val gridH = 160.coerceAtMost(sampleH)
        val totalSamples = gridW * gridH

        val rGrid = IntArray(totalSamples)
        val gGrid = IntArray(totalSamples)
        val bGrid = IntArray(totalSamples)
        val bucketCounts = HashMap<Int, Int>(256)

        for (gy in 0 until gridH) {
            val py = (marginY + ((gy + 0.5f) * sampleH / gridH).toInt()).coerceIn(0, height - 1)
            for (gx in 0 until gridW) {
                val px = (marginX + ((gx + 0.5f) * sampleW / gridW).toInt()).coerceIn(0, width - 1)
                val pixel = readableBitmap.getPixel(px, py)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val idx = gy * gridW + gx
                rGrid[idx] = r
                gGrid[idx] = g
                bGrid[idx] = b

                val bucketKey = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
                bucketCounts[bucketKey] = (bucketCounts[bucketKey] ?: 0) + 1
            }
        }

        // 1. Dominant uniform canvas check (trading charts have a uniform dark/light canvas background)
        val topBucketEntry = bucketCounts.maxByOrNull { it.value }
            ?: return ChartAnalysisOutcome.InvalidChart("Não foi possível ler os pixels da imagem.")
        val domKey = topBucketEntry.key
        val domR4 = (domKey shr 8) and 0xF
        val domG4 = (domKey shr 4) and 0xF
        val domB4 = domKey and 0xF

        var bgClusterSamples = 0
        var bgSumR = 0L
        var bgSumG = 0L
        var bgSumB = 0L

        for (i in 0 until totalSamples) {
            val r4 = rGrid[i] shr 4
            val g4 = gGrid[i] shr 4
            val b4 = bGrid[i] shr 4
            if (abs(r4 - domR4) <= 1 && abs(g4 - domG4) <= 1 && abs(b4 - domB4) <= 1) {
                bgClusterSamples++
                bgSumR += rGrid[i]
                bgSumG += gGrid[i]
                bgSumB += bGrid[i]
            }
        }

        val bgCoverage = bgClusterSamples.toFloat() / totalSamples.toFloat()
        if (bgCoverage < 0.28f) {
            return ChartAnalysisOutcome.InvalidChart(
                "A imagem não parece ser um gráfico financeiro (fundo muito variado ou foto comum)."
            )
        }
        if (bgCoverage > 0.994f) {
            return ChartAnalysisOutcome.InvalidChart(
                "A área selecionada está vazia ou uniforme demais para conter velas."
            )
        }

        val bgR = (bgSumR / bgClusterSamples).toInt()
        val bgG = (bgSumG / bgClusterSamples).toInt()
        val bgB = (bgSumB / bgClusterSamples).toInt()

        // 2. Photo smooth-gradient vs sharp chart check
        var flatEdges = 0
        var smoothEdges = 0
        var totalEdges = 0
        for (gy in 0 until gridH) {
            for (gx in 0 until gridW) {
                val idx = gy * gridW + gx
                if (gx + 1 < gridW) {
                    val next = idx + 1
                    val d = maxOf(
                        abs(rGrid[idx] - rGrid[next]),
                        abs(gGrid[idx] - gGrid[next]),
                        abs(bGrid[idx] - bGrid[next])
                    )
                    totalEdges++
                    if (d <= 6) flatEdges++ else if (d in 7..25) smoothEdges++
                }
                if (gy + 1 < gridH) {
                    val down = idx + gridW
                    val d = maxOf(
                        abs(rGrid[idx] - rGrid[down]),
                        abs(gGrid[idx] - gGrid[down]),
                        abs(bGrid[idx] - bGrid[down])
                    )
                    totalEdges++
                    if (d <= 6) flatEdges++ else if (d in 7..25) smoothEdges++
                }
            }
        }
        val flatRatio = if (totalEdges > 0) flatEdges.toFloat() / totalEdges else 0f
        val smoothRatio = if (totalEdges > 0) smoothEdges.toFloat() / totalEdges else 0f
        if (flatRatio < 0.26f && smoothRatio > 0.38f) {
            return ChartAnalysisOutcome.InvalidChart(
                "Detectada textura fotográfica ou gradiente natural em vez de um gráfico de candles."
            )
        }

        // 3. Symmetric Bullish / Bearish pixel classification (handles dark/light charts & compression)
        // 0 = neutral/bg, 1 = bullish (green/emerald/teal), -1 = bearish (red/crimson/coral)
        val colorClass = IntArray(totalSamples)
        var totalBullPixels = 0
        var totalBearPixels = 0

        for (i in 0 until totalSamples) {
            val r = rGrid[i]
            val g = gGrid[i]
            val b = bGrid[i]
            val distBg = maxOf(abs(r - bgR), abs(g - bgG), abs(b - bgB))
            if (distBg <= 22) continue

            val isBull = g >= 62 &&
                (g - r) >= 16 &&
                (g - b) >= -14 &&
                g >= r * 1.24f &&
                b <= g + 18

            val isBear = !isBull &&
                r >= 68 &&
                (r - g) >= 18 &&
                (r - b) >= 10 &&
                r >= g * 1.36f &&
                (g - b) <= (r - g) * 0.42f &&
                !(g > 110 && b > 100 && (g - b) > 10 && r < g * 1.55f)

            if (isBull) {
                colorClass[i] = 1
                totalBullPixels++
            } else if (isBear) {
                colorClass[i] = -1
                totalBearPixels++
            }
        }

        val totalCandlePixels = totalBullPixels + totalBearPixels
        val candleCoverage = totalCandlePixels.toFloat() / totalSamples.toFloat()
        if (candleCoverage < 0.003f) {
            return ChartAnalysisOutcome.InvalidChart(
                "Nenhum padrão de candles (velas verdes/vermelhas) encontrado na área marcada."
            )
        }
        if (candleCoverage > 0.42f) {
            return ChartAnalysisOutcome.InvalidChart(
                "Excesso de preenchimento colorido (${(candleCoverage * 100).toInt()}%) — a imagem não corresponde a velas sobre fundo de gráfico."
            )
        }

        // 4. Mask out thin horizontal indicator/RSI/reference/sentiment lines
        val isHorizontalLineRow = BooleanArray(gridH)
        for (gy in 0 until gridH) {
            var rowBull = 0
            var rowBear = 0
            val rowOffset = gy * gridW
            for (gx in 0 until gridW) {
                when (colorClass[rowOffset + gx]) {
                    1 -> rowBull++
                    -1 -> rowBear++
                }
            }
            val rowColored = rowBull + rowBear
            if (rowColored >= gridW * 0.38f) {
                val aboveColored = if (gy >= 4) {
                    minOf(
                        countRowColored(colorClass, gridW, gy - 3),
                        countRowColored(colorClass, gridW, gy - 4)
                    )
                } else 0
                val belowColored = if (gy + 4 < gridH) {
                    minOf(
                        countRowColored(colorClass, gridW, gy + 3),
                        countRowColored(colorClass, gridW, gy + 4)
                    )
                } else 0
                if (aboveColored < rowColored * 0.45f && belowColored < rowColored * 0.45f) {
                    isHorizontalLineRow[gy] = true
                }
            }
        }

        // 5. Extract vertical candle segments per column
        val minRunRows = (gridH * 0.015f).roundToInt().coerceAtLeast(2)
        val rawSegments = ArrayList<ColumnSegment>(gridW * 2)
        var validColumnCandlePixels = 0
        val columnsWithCandles = BooleanArray(gridW)

        for (gx in 0 until gridW) {
            var runCount = 0
            var bestBullRunLen = 0
            var bestBullMinY = gridH
            var bestBullMaxY = 0
            var bestBullYSum = 0

            var bestBearRunLen = 0
            var bestBearMinY = gridH
            var bestBearMaxY = 0
            var bestBearYSum = 0

            var gy = 0
            while (gy < gridH) {
                if (isHorizontalLineRow[gy]) {
                    gy++
                    continue
                }
                val cls = colorClass[gy * gridW + gx]
                if (cls == 0) {
                    gy++
                    continue
                }

                var runLen = 0
                var runYSum = 0
                var minY = gy
                var maxY = gy
                var endY = gy
                while (endY < gridH) {
                    if (isHorizontalLineRow[endY]) {
                        endY++
                        continue
                    }
                    val cur = colorClass[endY * gridW + gx]
                    if (cur == cls) {
                        runLen++
                        runYSum += endY
                        if (endY < minY) minY = endY
                        if (endY > maxY) maxY = endY
                        endY++
                    } else if (endY + 1 < gridH && colorClass[(endY + 1) * gridW + gx] == cls) {
                        // Bridge 1-row crossing (e.g. thin MA line cutting across candle body)
                        endY++
                    } else {
                        break
                    }
                }

                if (runLen >= 2) {
                    runCount++
                    if (cls == 1 && runLen > bestBullRunLen) {
                        bestBullRunLen = runLen
                        bestBullMinY = minY
                        bestBullMaxY = maxY
                        bestBullYSum = runYSum
                    } else if (cls == -1 && runLen > bestBearRunLen) {
                        bestBearRunLen = runLen
                        bestBearMinY = minY
                        bestBearMaxY = maxY
                        bestBearYSum = runYSum
                    }
                }
                gy = endY
            }

            val dominantLen = maxOf(bestBullRunLen, bestBearRunLen)
            val dominantHeightFraction = dominantLen.toFloat() / gridH.toFloat()

            if (dominantLen < minRunRows || dominantHeightFraction > 0.72f || runCount !in 1..10) {
                continue
            }

            val isBullishColumn: Boolean
            val chosenRunLen: Int
            val chosenMinY: Int
            val chosenMaxY: Int
            val chosenYSum: Int
            when {
                bestBullRunLen > bestBearRunLen -> {
                    isBullishColumn = true
                    chosenRunLen = bestBullRunLen
                    chosenMinY = bestBullMinY
                    chosenMaxY = bestBullMaxY
                    chosenYSum = bestBullYSum
                }
                bestBearRunLen > bestBullRunLen -> {
                    isBullishColumn = false
                    chosenRunLen = bestBearRunLen
                    chosenMinY = bestBearMinY
                    chosenMaxY = bestBearMaxY
                    chosenYSum = bestBearYSum
                }
                else -> continue
            }

            columnsWithCandles[gx] = true
            validColumnCandlePixels += chosenRunLen
            rawSegments.add(
                ColumnSegment(
                    gx = gx,
                    minRow = chosenMinY,
                    maxRow = chosenMaxY,
                    centerRow = chosenYSum.toFloat() / chosenRunLen.toFloat(),
                    runLen = chosenRunLen,
                    isBullish = isBullishColumn
                )
            )
        }

        val validColumnCount = columnsWithCandles.count { it }
        if (validColumnCount < 4) {
            return ChartAnalysisOutcome.InvalidChart(
                "Nenhum padrão de candles (velas verdes/vermelhas) encontrado na área marcada."
            )
        }

        val structuredRatio = validColumnCandlePixels.toFloat() / totalCandlePixels.coerceAtLeast(1).toFloat()
        if (structuredRatio < 0.14f) {
            return ChartAnalysisOutcome.InvalidChart(
                "As cores estão dispersas de forma irregular; selecione apenas a área do gráfico de velas."
            )
        }

        // 6. Detect bottom RSI / sentiment sub-panel so labels ("70%", "30%", "65%/35%") never corrupt price reading
        var effectiveMaxRow = gridH
        val subPanelStartCandidate = (gridH * 0.62f).toInt()
        for (gy in subPanelStartCandidate until gridH) {
            if (isHorizontalLineRow[gy]) {
                val aboveCandles = clusterSegments(
                    rawSegments.filter { it.centerRow < gy },
                    gridW,
                    gy
                )
                val belowCandles = clusterSegments(
                    rawSegments.filter { it.centerRow > gy },
                    gridW,
                    gridH
                )
                if (aboveCandles.size >= 5 && belowCandles.size <= 3) {
                    effectiveMaxRow = gy
                    break
                }
            }
        }

        val paneSegments = rawSegments.filter { it.centerRow < effectiveMaxRow }
        var candles = clusterSegments(paneSegments, gridW, effectiveMaxRow)

        // Drop tiny 1-2px isolated MA/text fragments if taller real candles exist
        val tallerCandles = candles.filter { it.maxRunLen >= 3 || it.widthCols >= 3 }
        if (tallerCandles.size >= 4) {
            candles = tallerCandles
        }

        // Drop isolated small UI text/price tags on the far right (e.g. "617", "70%") after a wide empty gap
        candles = removeTrailingUiArtifacts(candles)

        if (candles.size < 4) {
            return ChartAnalysisOutcome.InvalidChart(
                "Poucas velas sequenciais na horizontal para leitura de tendência BO."
            )
        }

        val xSpan = candles.last().xNorm - candles.first().xNorm
        if (xSpan < 0.10f) {
            return ChartAnalysisOutcome.InvalidChart(
                "Poucas velas sequenciais na horizontal para leitura de tendência BO."
            )
        }

        // 7. Reject flat UI buttons (e.g., "Acima" / "Abaixo" buttons with zero vertical price movement)
        val minPriceY = candles.minOf { it.bodyPriceY }
        val maxPriceY = candles.maxOf { it.bodyPriceY }
        val priceRange = maxPriceY - minPriceY
        val distinctYLevels = candles.map { (it.bodyPriceY * 22).toInt() }.distinct().size
        val distinctHeights = candles.map { (it.heightNorm * 25).toInt() }.distinct().size

        if (priceRange < 0.025f && distinctYLevels < 2 && distinctHeights < 3) {
            return ChartAnalysisOutcome.InvalidChart(
                "A área marcada parece conter botões ou faixas fixas, não velas com variação de preço."
            )
        }

        return ChartAnalysisOutcome.ValidChart(
            buildBoAnalysisResult(
                candles = candles,
                sourceDescription = sourceDescription,
                boConfig = boConfig
            )
        )
    }

    private fun clusterSegments(
        segments: List<ColumnSegment>,
        gridW: Int,
        effectiveMaxRow: Int
    ): List<CandleBar> {
        if (segments.isEmpty()) return emptyList()
        val sorted = segments.sortedBy { it.gx }
        val denomY = (effectiveMaxRow - 1).coerceAtLeast(1).toFloat()
        val denomX = (gridW - 1).coerceAtLeast(1).toFloat()

        val result = ArrayList<CandleBar>()
        val group = ArrayList<ColumnSegment>()

        fun flushGroup() {
            if (group.isEmpty()) return
            val avgGx = group.map { it.gx }.average().toFloat()
            val minRow = group.minOf { it.minRow }
            val maxRow = group.maxOf { it.maxRow }
            val maxRunLen = group.maxOf { it.runLen }

            // Use the thickest columns (body) to determine the true candle body center
            val bodyThreshold = (maxRunLen * 0.55f).toInt().coerceAtLeast(1)
            val bodyCols = group.filter { it.runLen >= bodyThreshold }.ifEmpty { group }
            val bodyAvgRow = bodyCols.map { it.centerRow }.average().toFloat()

            val bodyPriceY = (1f - bodyAvgRow / denomY).coerceIn(0f, 1f)
            val highPriceY = (1f - minRow / denomY).coerceIn(0f, 1f)
            val lowPriceY = (1f - maxRow / denomY).coerceIn(0f, 1f)
            val widthCols = (group.last().gx - group.first().gx) + 1

            result.add(
                CandleBar(
                    xNorm = (avgGx / denomX).coerceIn(0f, 1f),
                    bodyPriceY = bodyPriceY,
                    highPriceY = highPriceY,
                    lowPriceY = lowPriceY,
                    heightNorm = (maxRunLen.toFloat() / effectiveMaxRow.toFloat()).coerceIn(0f, 1f),
                    maxRunLen = maxRunLen,
                    widthCols = widthCols,
                    isBullish = group.first().isBullish
                )
            )
            group.clear()
        }

        for (seg in sorted) {
            if (group.isEmpty()) {
                group.add(seg)
            } else {
                val prev = group.last()
                val sameColor = seg.isBullish == prev.isBullish
                val adjacentX = (seg.gx - prev.gx) <= 1
                val verticalDist = abs(seg.centerRow - prev.centerRow) / denomY
                val overlapsVertically = seg.minRow <= prev.maxRow + 2 && seg.maxRow >= prev.minRow - 2
                if (sameColor && adjacentX && (overlapsVertically || verticalDist <= 0.14f)) {
                    group.add(seg)
                } else {
                    flushGroup()
                    group.add(seg)
                }
            }
        }
        flushGroup()
        return result
    }

    private fun removeTrailingUiArtifacts(candles: List<CandleBar>): List<CandleBar> {
        if (candles.size < 6) return candles
        // Look from right to left for a large empty horizontal gap (> 16% of viewport) leaving <= 3 small edge items
        var cutIndex = candles.size
        for (i in candles.size - 1 downTo 4) {
            val gap = candles[i].xNorm - candles[i - 1].xNorm
            if (gap > 0.16f && candles[i].xNorm > 0.75f) {
                val trailingCount = candles.size - i
                if (trailingCount <= 3) {
                    cutIndex = i
                    break
                }
            }
        }
        return if (cutIndex < candles.size && cutIndex >= 4) {
            candles.subList(0, cutIndex)
        } else {
            candles
        }
    }

    private fun countRowColored(colorClass: IntArray, gridW: Int, gy: Int): Int {
        var count = 0
        val offset = gy * gridW
        for (gx in 0 until gridW) {
            if (colorClass[offset + gx] != 0) count++
        }
        return count
    }

    private data class TrendRegression(
        val deltaY: Float,
        val rSquared: Float
    )

    private fun computeWindowRegression(candles: List<CandleBar>): TrendRegression {
        val n = candles.size
        if (n < 2) return TrendRegression(0f, 0f)

        val xs = FloatArray(n) { i -> i.toFloat() / (n - 1).coerceAtLeast(1) }
        val ys = FloatArray(n) { i -> candles[i].bodyPriceY }

        val meanX = 0.5f
        val meanY = ys.average().toFloat()

        var num = 0f
        var denX = 0f
        var denY = 0f
        for (i in 0 until n) {
            val dx = xs[i] - meanX
            val dy = ys[i] - meanY
            num += dx * dy
            denX += dx * dx
            denY += dy * dy
        }
        if (denX < 1e-5f) return TrendRegression(0f, 0f)

        val slope = num / denX
        val rSquared = if (denY < 1e-6f) {
            0f
        } else {
            ((num * num) / (denX * denY)).coerceIn(0f, 1f)
        }
        return TrendRegression(deltaY = slope, rSquared = rSquared)
    }

    private fun buildBoAnalysisResult(
        candles: List<CandleBar>,
        sourceDescription: String,
        boConfig: BoConfig
    ): ChartAnalysisResult {
        val count = candles.size

        // 1. Multi-horizon linear regressions (normalized to each window's span, immune to empty right chart margin)
        val macroReg = computeWindowRegression(candles)

        val swingSize = (count * 0.58f).roundToInt().coerceIn(4, minOf(14, count))
        val swingWindow = candles.takeLast(swingSize)
        val swingReg = computeWindowRegression(swingWindow)

        val microSize = (count * 0.34f).roundToInt().coerceIn(3, minOf(7, count))
        val microWindow = candles.takeLast(microSize)
        val microReg = computeWindowRegression(microWindow)

        // 2. Recent candle polarity balance (weighted exponentially toward newest candles)
        val recentCount = minOf(6, count)
        val recentCandles = candles.takeLast(recentCount)
        var recentBullWeight = 0f
        var recentBearWeight = 0f
        recentCandles.forEachIndexed { idx, c ->
            val recencyWeight = 1f + (idx.toFloat() / recentCount.toFloat()) * 1.4f
            val weight = c.heightNorm.coerceAtLeast(0.025f) * recencyWeight
            if (c.isBullish) recentBullWeight += weight else recentBearWeight += weight
        }
        val totalRecentWeight = (recentBullWeight + recentBearWeight).coerceAtLeast(1e-4f)
        val recentBullRatio = recentBullWeight / totalRecentWeight
        val polarityBalance = (recentBullWeight - recentBearWeight) / totalRecentWeight

        val overallBullRatio = candles.count { it.isBullish }.toFloat() / count.toFloat()

        // 3. Swing market structure: Higher Highs / Higher Lows vs Lower Highs / Lower Lows
        val half = (swingWindow.size / 2).coerceAtLeast(2)
        val firstHalf = swingWindow.take(half)
        val secondHalf = swingWindow.takeLast(swingWindow.size - half)
        val firstHigh = firstHalf.maxOf { it.highPriceY }
        val firstLow = firstHalf.minOf { it.lowPriceY }
        val secondHigh = secondHalf.maxOf { it.highPriceY }
        val secondLow = secondHalf.minOf { it.lowPriceY }
        val firstMid = firstHalf.map { it.bodyPriceY }.average().toFloat()
        val secondMid = secondHalf.map { it.bodyPriceY }.average().toFloat()

        val structureScore = when {
            secondMid > firstMid + 0.015f && secondHigh > firstHigh + 0.01f && secondLow > firstLow + 0.01f -> 12f
            secondMid > firstMid + 0.015f && (secondHigh > firstHigh + 0.01f || secondLow > firstLow + 0.01f) -> 6f
            secondMid < firstMid - 0.015f && secondHigh < firstHigh - 0.01f && secondLow < firstLow - 0.01f -> -12f
            secondMid < firstMid - 0.015f && (secondHigh < firstHigh - 0.01f || secondLow < firstLow - 0.01f) -> -6f
            else -> 0f
        }

        // 4. Candle-to-candle step consistency in swing window
        var upSteps = 0
        var downSteps = 0
        for (i in 1 until swingWindow.size) {
            val diff = swingWindow[i].bodyPriceY - swingWindow[i - 1].bodyPriceY
            if (diff > 0.008f) upSteps++ else if (diff < -0.008f) downSteps++
        }
        val totalSteps = (upSteps + downSteps).coerceAtLeast(1)
        val stepBalance = (upSteps - downSteps).toFloat() / totalSteps.toFloat()

        // 5. RSI estimate over actual discrete candles
        var gainSum = 0f
        var lossSum = 0f
        for (i in 1 until count) {
            val diff = candles[i].bodyPriceY - candles[i - 1].bodyPriceY
            if (diff > 0f) gainSum += diff else lossSum += -diff
        }
        val rawRsi = if (gainSum + lossSum < 1e-4f) {
            50f
        } else {
            val rs = (gainSum + 0.001f) / (lossSum + 0.001f)
            100f - (100f / (1f + rs))
        }
        val rsiEstimate = (rawRsi * 0.6f + (recentBullRatio * 70f + 15f) * 0.4f)
            .roundToInt()
            .coerceIn(18, 84)

        // 6. Position in recent swing range (protects against stale signals after sudden collapse/bounce)
        val swingMinP = swingWindow.minOf { it.bodyPriceY }
        val swingMaxP = swingWindow.maxOf { it.bodyPriceY }
        val swingRangeP = (swingMaxP - swingMinP).coerceAtLeast(0.05f)
        val latestTwoAvg = candles.takeLast(minOf(2, count)).map { it.bodyPriceY }.average().toFloat()
        val posInSwingRange = ((latestTwoAvg - swingMinP) / swingRangeP).coerceIn(0f, 1f)

        val macroMinP = candles.minOf { it.bodyPriceY }
        val macroMaxP = candles.maxOf { it.bodyPriceY }
        val macroRangeP = (macroMaxP - macroMinP).coerceAtLeast(0.05f)
        val posInMacroRange = ((latestTwoAvg - macroMinP) / macroRangeP).coerceIn(0f, 1f)

        // 7. Combine directional evidence
        val swingScore = (swingReg.deltaY * 68f * (0.45f + 0.55f * swingReg.rSquared)).coerceIn(-36f, 36f)
        val microScore = (microReg.deltaY * 52f * (0.45f + 0.55f * microReg.rSquared)).coerceIn(-28f, 28f)

        val rawMacroScore = (macroReg.deltaY * 32f * (0.45f + 0.55f * macroReg.rSquared)).coerceIn(-18f, 18f)
        val isReversal = (rawMacroScore * swingScore < 0f) && abs(swingScore) >= 8f
        val macroScore = if (isReversal) rawMacroScore * 0.20f else rawMacroScore

        val polarityScore = (polarityBalance * 24f).coerceIn(-24f, 24f)
        val stepScore = (stepBalance * 14f).coerceIn(-14f, 14f)

        var totalScore = swingScore + microScore + macroScore + polarityScore + structureScore + stepScore

        // Guard against sideways chop (small net delta & low linearity)
        val isSidewaysChop = abs(swingReg.deltaY) < 0.08f &&
            abs(microReg.deltaY) < 0.08f &&
            swingReg.rSquared < 0.25f
        if (isSidewaysChop) {
            totalScore = totalScore.coerceIn(-10f, 10f)
        }

        // Guard against stale signals when latest candles already retraced deeply against the swing slope
        if (totalScore > 0f && posInSwingRange < 0.35f && microReg.deltaY <= 0f) {
            totalScore *= 0.40f
        } else if (totalScore < 0f && posInSwingRange > 0.65f && microReg.deltaY >= 0f) {
            totalScore *= 0.40f
        }

        val rawConfidence = (50 + (abs(totalScore) * 0.55f).roundToInt()).coerceIn(50, 93)

        val minRequired = boConfig.sensitivity.minConfidence
        val minScoreThreshold = if (minRequired >= 70) 34f else 20f
        val direction = when {
            totalScore >= minScoreThreshold && rawConfidence >= minRequired -> SignalDirection.CALL_UP
            totalScore <= -minScoreThreshold && rawConfidence >= minRequired -> SignalDirection.PUT_DOWN
            else -> SignalDirection.NEUTRAL
        }

        val confidence = if (direction == SignalDirection.NEUTRAL) {
            rawConfidence.coerceAtMost(minRequired - 1).coerceIn(46, 70)
        } else {
            rawConfidence
        }

        val trendLabel = when (direction) {
            SignalDirection.CALL_UP -> "Alta (${boConfig.expiryLabel})"
            SignalDirection.PUT_DOWN -> "Baixa (${boConfig.expiryLabel})"
            SignalDirection.NEUTRAL -> "Lateral / Sem confluência"
        }

        val patternLabel = when (direction) {
            SignalDirection.CALL_UP ->
                "Força compradora (${(recentBullRatio * 100).toInt()}% pressão recente)"
            SignalDirection.PUT_DOWN ->
                "Pressão vendedora (${((1f - recentBullRatio) * 100).toInt()}% pressão recente)"
            SignalDirection.NEUTRAL ->
                if (rawConfidence < minRequired && abs(totalScore) >= 20f) {
                    "Sinal filtrado pelo modo ${boConfig.sensitivity.label}"
                } else {
                    "Velas mistas (${(overallBullRatio * 100).toInt()}% alta)"
                }
        }

        val supportResistanceLabel = when {
            posInMacroRange >= 0.78f && direction == SignalDirection.CALL_UP -> "Rompimento de resistência"
            posInMacroRange >= 0.78f -> "Próximo à resistência superior"
            posInMacroRange <= 0.22f && direction == SignalDirection.PUT_DOWN -> "Rompimento de suporte"
            posInMacroRange <= 0.22f -> "Próximo ao suporte inferior"
            else -> "Zona intermediária (${(posInMacroRange * 100).toInt()}% da faixa)"
        }

        val summary = when (direction) {
            SignalDirection.CALL_UP ->
                "BO ${boConfig.expiryLabel} ($sourceDescription): $count candles analisados com estrutura ascendente e ${(recentBullRatio * 100).toInt()}% de força compradora recente. Entrada sugerida: ACIMA (CALL)."
            SignalDirection.PUT_DOWN ->
                "BO ${boConfig.expiryLabel} ($sourceDescription): $count candles analisados com estrutura descendente e ${((1f - recentBullRatio) * 100).toInt()}% de pressão vendedora recente. Entrada sugerida: ABAIXO (PUT)."
            SignalDirection.NEUTRAL ->
                "BO ${boConfig.expiryLabel} ($sourceDescription): $count candles detectados, mas sem confluência suficiente para entrada ${boConfig.sensitivity.label.lowercase()}. Recomenda-se AGUARDAR confirmação."
        }

        val timeFormatted = BrazilLocaleConfig.formatBrasiliaTime()

        return ChartAnalysisResult(
            id = UUID.randomUUID().toString(),
            direction = direction,
            confidencePercent = confidence,
            assetLabel = "BO ${boConfig.expiryLabel} • ${boConfig.sensitivity.label}",
            timeframeLabel = boConfig.expiryLabel,
            rsiValue = rsiEstimate,
            trendLabel = trendLabel,
            patternLabel = patternLabel,
            supportResistanceLabel = supportResistanceLabel,
            summary = summary,
            timestampLabel = timeFormatted,
            isDemoMode = true,
            noticeMessage = "Modo demonstrativo local: análise por visão computacional do Firebase AI Logic (Gemini) requer Firebase habilitado."
        )
    }

    private data class ColumnSegment(
        val gx: Int,
        val minRow: Int,
        val maxRow: Int,
        val centerRow: Float,
        val runLen: Int,
        val isBullish: Boolean
    )

    private data class CandleBar(
        val xNorm: Float,
        val bodyPriceY: Float,
        val highPriceY: Float,
        val lowPriceY: Float,
        val heightNorm: Float,
        val maxRunLen: Int,
        val widthCols: Int,
        val isBullish: Boolean
    )
}
