package com.example.ui

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AiRepository
import com.example.data.FirebaseAiChartRepository
import com.example.data.VisualStakeBoxHit
import com.example.model.AiRecoveryMode
import com.example.model.AiRecoveryPlan
import com.example.model.AiRecoveryRequest
import com.example.model.AnalysisUiState
import com.example.model.BoConfig
import com.example.model.BoSensitivity
import com.example.model.BrokerAccountType
import com.example.model.CaptureSourceMode
import com.example.model.ChartAnalysisOutcome
import com.example.model.ChartAnalysisResult
import com.example.model.MartingaleConfig
import com.example.model.NormalizedRect
import com.example.model.RiskStrategy
import com.example.model.SignalDirection
import com.example.model.TechnicalIndicator
import com.example.model.ADAPTIVE_EXPIRY_OPTIONS_SEC
import com.example.model.TradeOutcome
import com.example.model.TradingAsset
import com.example.model.formatExpiryShortLabel
import com.example.model.formatStakeAmount
import com.example.model.normalizeAdaptiveExpirySeconds
import com.example.ui.components.BotExecutionResult
import com.example.ui.components.BotPoint
import com.example.ui.components.resolveTradeOutcomeFromBalance
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class TrendLensUiState(
    val sourceMode: CaptureSourceMode = CaptureSourceMode.WEBVIEW,
    val currentUrl: String = TRADEROOM_URL,
    val displayHost: String = "iqoption.com",
    val webProgress: Int = 0,
    val isWebLoading: Boolean = true,
    val webError: String? = null,
    val canGoBack: Boolean = false,
    val isMarkingArea: Boolean = false,
    val draftSelection: NormalizedRect? = null,
    val markedArea: NormalizedRect? = null,
    val autoChartAreaEnabled: Boolean = true,
    val autoScanEnabled: Boolean = true,
    val autoScanIntervalSec: Int = 60,
    val countdownSec: Int = FAST_TREND_HUNT_INTERVAL_SEC,
    val boConfig: BoConfig = BoConfig(
        expirySeconds = 60,
        sensitivity = BoSensitivity.CONSERVATIVE,
        technicalIndicator = TechnicalIndicator.EMA_RSI
    ),
    val voiceEnabled: Boolean = true,
    val keepRunningInBackground: Boolean = true,
    val autoClickEnabled: Boolean = true,
    val autoClickCooldownSec: Int = 60,
    val lastAutoClickSummary: String? = null,
    val accountType: BrokerAccountType = BrokerAccountType.DEMO,
    val liveBrokerBalance: Double? = null,
    val martingaleConfig: MartingaleConfig = MartingaleConfig(),
    val currentMartingaleStep: Int = 0,
    val sessionWins: Int = 0,
    val sessionLosses: Int = 0,
    val consecutiveLosses: Int = 0,
    val sessionNetLoss: Double = 0.0,
    val isSessionStopLossHit: Boolean = false,
    val lossPauseUntilEpochMs: Long = 0L,
    val lossPauseRemainingSec: Int = 0,
    val pendingCandleCloseDirection: SignalDirection? = null,
    val pendingCandleCloseRemainingSec: Int = 0,
    val accumulatedLossDeficit: Double = 0.0,
    val aiRecoveryPlan: AiRecoveryPlan? = null,
    val isPlanningRecovery: Boolean = false,
    val pendingTradeDirection: SignalDirection? = null,
    val pendingTradeStake: Double? = null,
    val pendingTradeBalanceBefore: Double? = null,
    val pendingTradeOpenedAtMs: Long = 0L,
    val lastOutcomeMessage: String? = null,
    val isRobotConfigOpen: Boolean = false,
    val calibratingButton: SignalDirection? = null,
    val calibratingAmountField: Boolean = false,
    val calibratedCallPoint: BotPoint? = null,
    val calibratedPutPoint: BotPoint? = null,
    val autoDetectedCallPoint: BotPoint? = null,
    val autoDetectedPutPoint: BotPoint? = null,
    val calibratedAmountPoint: BotPoint? = null,
    val autoDetectedAmountPoint: BotPoint? = null,
    val selectedGalleryBitmap: Bitmap? = null,
    val analysisState: AnalysisUiState = AnalysisUiState.Unavailable,
    val isAnalysisExpanded: Boolean = false,
    val isHelpDialogVisible: Boolean = false,
    val history: List<ChartAnalysisResult> = emptyList()
) {
    val currentBotStake: Double
        get() = if (martingaleConfig.effectiveStrategy == RiskStrategy.AI_RECOVERY) {
            when {
                accumulatedLossDeficit <= 0.005 -> kotlin.math.round(martingaleConfig.initialStake * 100.0) / 100.0
                aiRecoveryPlan != null -> aiRecoveryPlan.recommendedStake
                else -> martingaleConfig.computeRecoveryStake(
                    accumulatedDeficit = accumulatedLossDeficit,
                    mode = AiRecoveryMode.FULL_RECOVERY_NOW,
                    liveBalance = liveBrokerBalance
                )
            }
        } else {
            martingaleConfig.stakeForStep(currentMartingaleStep)
        }

    companion object {
        const val TRADEROOM_URL = "https://iqoption.com/pwa/traderoom"
        val DEFAULT_WEBVIEW_CHART_AREA = NormalizedRect(0.03f, 0.15f, 0.97f, 0.68f)
    }
}

private const val VM_TAG = "TrendLensViewModel"
internal const val FAST_TREND_HUNT_INTERVAL_SEC = 14
internal const val QUOTA_BACKOFF_INTERVAL_SEC = 18
internal const val MAX_QUOTA_BACKOFF_INTERVAL_SEC = 90
private const val STATIC_FRAME_SKIP_WINDOW_MS = 18_000L
private const val VISUAL_CHANGE_SKIP_THRESHOLD = 0.018f

data class AutoClickCommand(
    val direction: SignalDirection,
    val calibratedPoint: BotPoint?,
    val calibratedAmountPoint: BotPoint? = null,
    val checkPendingBalanceFirst: Boolean = false,
    val stakeAmount: Double? = null,
    val martingaleStep: Int = 0,
    val expirySecondsToApply: Int? = null
)

class TrendLensViewModel(
    private val aiRepository: AiRepository = FirebaseAiChartRepository(),
    private val clockEpochMs: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrendLensUiState())
    val uiState: StateFlow<TrendLensUiState> = _uiState.asStateFlow()

    private val _captureTrigger = MutableSharedFlow<NormalizedRect>(extraBufferCapacity = 1)
    val captureTrigger: SharedFlow<NormalizedRect> = _captureTrigger.asSharedFlow()

    private val _speechTrigger = MutableSharedFlow<ChartAnalysisResult>(extraBufferCapacity = 1)
    val speechTrigger: SharedFlow<ChartAnalysisResult> = _speechTrigger.asSharedFlow()

    private val _autoClickTrigger = MutableSharedFlow<AutoClickCommand>(extraBufferCapacity = 1)
    val autoClickTrigger: SharedFlow<AutoClickCommand> = _autoClickTrigger.asSharedFlow()

    private val _balanceCheckTrigger = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val balanceCheckTrigger: SharedFlow<Boolean> = _balanceCheckTrigger.asSharedFlow()

    private val _applyStakeTrigger = MutableSharedFlow<Double>(extraBufferCapacity = 4)
    val applyStakeTrigger: SharedFlow<Double> = _applyStakeTrigger.asSharedFlow()

    private val _applyExpiryTrigger = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val applyExpiryTrigger: SharedFlow<Int> = _applyExpiryTrigger.asSharedFlow()

    private var autoScanJob: Job? = null
    private var analysisJob: Job? = null
    private var pendingOutcomeCheckJob: Job? = null
    private var recoveryPlanJob: Job? = null
    private var pendingCandleCloseJob: Job? = null
    private var lossPauseTickerJob: Job? = null
    private val recentReadingHints = ArrayDeque<String>(4)
    private var lastDirectionalSignal: SignalDirection? = null
    private var lastDirectionalSignalAtMs: Long = 0L
    private var lastSpokenDirection: SignalDirection? = null
    private var lastSpokenAtMs: Long = 0L
    private var lastAutoClickAtMs: Long = 0L
    private var pendingManualTrigger: Boolean = false
    private var userClearedMarkedArea: Boolean = false
    private var restoreAutoClickOnResume: Boolean = false
    private var savedWebViewMarkedArea: NormalizedRect? = null
    private var savedWebViewAutoArea: Boolean = true
    private var lastStaticFrameSignature: IntArray? = null
    private var lastStaticFrameCapturedAtMs: Long = 0L
    private var lastStaticFrameBoConfig: BoConfig? = null
    private var consecutiveQuotaErrors: Int = 0

    private fun clearReadingMemory() {
        recentReadingHints.clear()
        lastDirectionalSignal = null
        lastDirectionalSignalAtMs = 0L
        lastStaticFrameSignature = null
        lastStaticFrameCapturedAtMs = 0L
        lastStaticFrameBoConfig = null
    }

    internal fun computeNextQuotaBackoffSec(hintRemainingSec: Int = 0): Int {
        consecutiveQuotaErrors = (consecutiveQuotaErrors + 1).coerceAtMost(4)
        val stepped = when (consecutiveQuotaErrors) {
            1 -> QUOTA_BACKOFF_INTERVAL_SEC
            2 -> 35
            3 -> 60
            else -> MAX_QUOTA_BACKOFF_INTERVAL_SEC
        }
        return maxOf(stepped, hintRemainingSec + 3).coerceAtMost(MAX_QUOTA_BACKOFF_INTERVAL_SEC)
    }

    private fun formatRecentMemoryHint(): String? =
        recentReadingHints.takeIf { it.isNotEmpty() }?.joinToString(" -> ")

    fun setSourceMode(mode: CaptureSourceMode) {
        val prev = _uiState.value
        if (prev.sourceMode == mode) return
        if (prev.sourceMode == CaptureSourceMode.WEBVIEW && prev.markedArea != null) {
            savedWebViewMarkedArea = prev.markedArea
            savedWebViewAutoArea = prev.autoChartAreaEnabled
        }
        stopAutoScanTicker()
        analysisJob?.cancel()
        clearReadingMemory()
        lastSpokenDirection = null
        lastSpokenAtMs = 0L
        pendingManualTrigger = false
        userClearedMarkedArea = false
        val restoredWebArea = if (mode == CaptureSourceMode.WEBVIEW) savedWebViewMarkedArea else null
        val restoredAuto = if (mode == CaptureSourceMode.WEBVIEW) savedWebViewAutoArea else prev.autoChartAreaEnabled
        _uiState.update {
            it.copy(
                sourceMode = mode,
                isMarkingArea = false,
                draftSelection = null,
                markedArea = restoredWebArea,
                autoChartAreaEnabled = restoredAuto,
                analysisState = AnalysisUiState.Unavailable
            )
        }
        if (mode == CaptureSourceMode.WEBVIEW && restoredWebArea != null && _uiState.value.autoScanEnabled) {
            startAutoScanTicker()
        }
    }

    /**
     * Restores the previously persisted WebView chart area immediately on app startup so the user
     * never has to manually redefine the chart region after reopening the app.
     */
    fun restoreSavedChartArea(rect: NormalizedRect, isAutoManaged: Boolean) {
        val normalized = rect.normalized()
        if (!normalized.isValid()) return
        savedWebViewMarkedArea = normalized
        savedWebViewAutoArea = isAutoManaged
        val current = _uiState.value
        if (current.sourceMode != CaptureSourceMode.WEBVIEW || current.markedArea != null || current.isMarkingArea) {
            return
        }
        userClearedMarkedArea = false
        _uiState.update {
            it.copy(
                markedArea = normalized,
                autoChartAreaEnabled = isAutoManaged,
                countdownSec = FAST_TREND_HUNT_INTERVAL_SEC
            )
        }
        if (_uiState.value.autoScanEnabled) {
            startAutoScanTicker()
            triggerImmediateCapture(normalized)
        }
    }

    fun startMarkingArea() {
        val current = _uiState.value
        val initialDraft = current.markedArea ?: TrendLensUiState.DEFAULT_WEBVIEW_CHART_AREA
        _uiState.update {
            it.copy(
                isMarkingArea = true,
                draftSelection = initialDraft,
                isAnalysisExpanded = false
            )
        }
    }

    fun updateDraftSelection(rect: NormalizedRect) {
        _uiState.update { it.copy(draftSelection = rect.normalized()) }
    }

    fun cancelMarkingArea() {
        _uiState.update {
            it.copy(
                isMarkingArea = false,
                draftSelection = null
            )
        }
    }

    fun confirmMarkedArea() {
        val draft = _uiState.value.draftSelection?.normalized() ?: return
        if (!draft.isValid()) return

        clearReadingMemory()
        lastSpokenDirection = null
        lastSpokenAtMs = 0L
        lastAutoClickAtMs = 0L
        pendingManualTrigger = true
        userClearedMarkedArea = false
        if (_uiState.value.sourceMode == CaptureSourceMode.WEBVIEW) {
            savedWebViewMarkedArea = draft
            savedWebViewAutoArea = false
        }
        _uiState.update {
            it.copy(
                isMarkingArea = false,
                draftSelection = null,
                markedArea = draft,
                autoChartAreaEnabled = if (it.sourceMode == CaptureSourceMode.WEBVIEW) false else it.autoChartAreaEnabled,
                countdownSec = it.autoScanIntervalSec
            )
        }

        triggerImmediateCapture(draft)
        if (_uiState.value.autoScanEnabled) {
            startAutoScanTicker()
        }
    }

    /**
     * Automatically frames or smoothly re-frames the live chart area from the detected WebView
     * canvas bounds. If already framed in Auto mode and the broker layout shifted (resize, rotation,
     * Desktop/Mobile switch), updates the area seamlessly with zero user taps required.
     */
    fun applyAutoDetectedChartArea(detectedArea: NormalizedRect?) {
        val current = _uiState.value
        if (current.sourceMode != CaptureSourceMode.WEBVIEW ||
            current.isMarkingArea ||
            current.calibratingButton != null ||
            current.calibratingAmountField ||
            current.webError != null ||
            userClearedMarkedArea
        ) {
            return
        }
        val targetArea = detectedArea?.normalized()?.takeIf { it.isValid(0.15f) }
            ?: TrendLensUiState.DEFAULT_WEBVIEW_CHART_AREA

        val existing = current.markedArea
        if (existing == null) {
            autoMarkDefaultChartAndScan(targetArea)
            return
        }
        if (!current.autoChartAreaEnabled) {
            return
        }
        val shifted = kotlin.math.abs(existing.left - targetArea.left) >= 0.03f ||
            kotlin.math.abs(existing.top - targetArea.top) >= 0.03f ||
            kotlin.math.abs(existing.right - targetArea.right) >= 0.03f ||
            kotlin.math.abs(existing.bottom - targetArea.bottom) >= 0.03f
        if (shifted) {
            savedWebViewMarkedArea = targetArea
            savedWebViewAutoArea = true
            _uiState.update { it.copy(markedArea = targetArea, autoChartAreaEnabled = true) }
        }
    }

    fun enableAutoChartArea(detectedArea: NormalizedRect? = null) {
        autoMarkDefaultChartAndScan(detectedArea)
    }

    fun autoMarkDefaultChartAndScan(detectedArea: NormalizedRect? = null) {
        val current = _uiState.value
        if (current.sourceMode != CaptureSourceMode.WEBVIEW) return
        val targetArea = detectedArea?.normalized()?.takeIf { it.isValid(0.15f) }
            ?: TrendLensUiState.DEFAULT_WEBVIEW_CHART_AREA
        aiRepository.clearQuotaCooldowns()
        clearReadingMemory()
        lastSpokenDirection = null
        lastSpokenAtMs = 0L
        lastAutoClickAtMs = 0L
        pendingManualTrigger = true
        userClearedMarkedArea = false
        savedWebViewMarkedArea = targetArea
        savedWebViewAutoArea = true
        _uiState.update {
            it.copy(
                isMarkingArea = false,
                draftSelection = null,
                markedArea = targetArea,
                autoChartAreaEnabled = true,
                autoScanEnabled = true,
                countdownSec = it.autoScanIntervalSec,
                isAnalysisExpanded = false
            )
        }
        triggerImmediateCapture(targetArea)
        startAutoScanTicker()
    }

    fun tryAutoStartChartReadingIfUnmarked(detectedArea: NormalizedRect? = null) {
        val current = _uiState.value
        if (current.sourceMode != CaptureSourceMode.WEBVIEW ||
            current.markedArea != null ||
            current.isMarkingArea ||
            current.calibratingButton != null ||
            current.calibratingAmountField ||
            current.webError != null ||
            userClearedMarkedArea
        ) {
            return
        }
        autoMarkDefaultChartAndScan(detectedArea)
    }

    fun clearMarkedArea() {
        stopAutoScanTicker()
        analysisJob?.cancel()
        clearReadingMemory()
        lastSpokenDirection = null
        lastSpokenAtMs = 0L
        pendingManualTrigger = false
        userClearedMarkedArea = true
        if (_uiState.value.sourceMode == CaptureSourceMode.WEBVIEW) {
            savedWebViewMarkedArea = null
        }
        _uiState.update {
            it.copy(
                isMarkingArea = false,
                draftSelection = null,
                markedArea = null,
                analysisState = AnalysisUiState.Unavailable
            )
        }
    }

    fun requestManualScan() {
        val state = _uiState.value
        val area = state.markedArea ?: return
        consecutiveQuotaErrors = 0
        aiRepository.clearQuotaCooldowns()
        pendingManualTrigger = true
        _uiState.update {
            it.copy(
                countdownSec = it.autoScanIntervalSec,
                isAnalysisExpanded = if (it.sourceMode == CaptureSourceMode.WEBVIEW) false else it.isAnalysisExpanded
            )
        }
        if (state.autoScanEnabled && state.sourceMode == CaptureSourceMode.WEBVIEW) {
            startAutoScanTicker()
        }
        triggerImmediateCapture(area)
    }

    /**
     * Unpauses the AI immediately: clears any temporary quota cooldown or protection pause,
     * re-enables automatic scanning, ensures a chart region is framed in WebView mode,
     * and dispatches an immediate Gemini analysis.
     */
    fun resumeAiAndScanNow() {
        consecutiveQuotaErrors = 0
        aiRepository.clearQuotaCooldowns()
        val state = _uiState.value
        val wasAiPaused = state.aiRecoveryPlan?.mode == AiRecoveryMode.PAUSE_PROTECT
        if (wasAiPaused) {
            resetMartingaleCycle()
        }
        val shouldRestoreBot = restoreAutoClickOnResume
        restoreAutoClickOnResume = false
        val targetArea = state.markedArea
            ?: if (state.sourceMode == CaptureSourceMode.WEBVIEW) {
                NormalizedRect(0.03f, 0.16f, 0.97f, 0.68f)
            } else {
                null
            }
        pendingManualTrigger = true
        userClearedMarkedArea = false
        _uiState.update { current ->
            current.copy(
                isMarkingArea = false,
                draftSelection = null,
                markedArea = targetArea ?: current.markedArea,
                autoScanEnabled = true,
                autoClickEnabled = if (shouldRestoreBot) true else current.autoClickEnabled,
                countdownSec = current.autoScanIntervalSec,
                isAnalysisExpanded = if (current.sourceMode == CaptureSourceMode.WEBVIEW) false else current.isAnalysisExpanded,
                lastOutcomeMessage = "IA despausada e analisando o gráfico agora."
            )
        }
        if (targetArea != null) {
            if (_uiState.value.sourceMode == CaptureSourceMode.WEBVIEW) {
                startAutoScanTicker()
            }
            triggerImmediateCapture(targetArea)
        }
    }

    /**
     * Explicitly pauses both AI chart scanning and automatic robot entries until the user resumes.
     * Used by both the in-app Pause button and the persistent foreground notification action.
     */
    fun pauseAllMonitoring() {
        restoreAutoClickOnResume = _uiState.value.autoClickEnabled
        stopAutoScanTicker()
        analysisJob?.cancel()
        analysisJob = null
        pendingOutcomeCheckJob?.cancel()
        pendingOutcomeCheckJob = null
        recoveryPlanJob?.cancel()
        recoveryPlanJob = null
        _uiState.update { current ->
            current.copy(
                autoScanEnabled = false,
                autoClickEnabled = false,
                lastAutoClickSummary = "Pausado manualmente pelo usuário",
                lastOutcomeMessage = "IA e Robô pausados. Só voltam quando você tocar em Retomar."
            )
        }
    }

    fun toggleKeepRunningInBackground(enabled: Boolean) {
        _uiState.update { current ->
            current.copy(
                keepRunningInBackground = enabled,
                lastOutcomeMessage = if (enabled) {
                    "Modo segundo plano ativado: o app continua rodando ao sair e só para quando você pausar."
                } else {
                    "Modo segundo plano desativado."
                }
            )
        }
    }

    fun toggleAutoScan(enabled: Boolean) {
        if (enabled) {
            consecutiveQuotaErrors = 0
            aiRepository.clearQuotaCooldowns()
            val shouldRestoreBot = restoreAutoClickOnResume
            restoreAutoClickOnResume = false
            val state = _uiState.value
            val area = state.markedArea
            _uiState.update {
                it.copy(
                    autoScanEnabled = true,
                    autoClickEnabled = if (shouldRestoreBot) true else it.autoClickEnabled,
                    countdownSec = it.autoScanIntervalSec
                )
            }
            if (area != null && !state.isMarkingArea) {
                pendingManualTrigger = true
                startAutoScanTicker()
                triggerImmediateCapture(area)
            }
        } else {
            _uiState.update {
                it.copy(
                    autoScanEnabled = false,
                    countdownSec = it.autoScanIntervalSec
                )
            }
            stopAutoScanTicker()
        }
    }

    fun toggleAutoExpiry(enabled: Boolean) {
        clearReadingMemory()
        _uiState.update { state ->
            state.copy(
                boConfig = state.boConfig.copy(autoExpiryEnabled = enabled),
                lastOutcomeMessage = if (enabled) {
                    "Tempo Automático IA ativado: o robô alterna entre 30s, 1 min, 2 min e 5 min conforme a tendência."
                } else {
                    "Tempo fixo selecionado: ${state.boConfig.durationShortLabel}."
                }
            )
        }
        val state = _uiState.value
        if (enabled && state.markedArea != null && !state.isMarkingArea) {
            pendingManualTrigger = true
            triggerImmediateCapture(state.markedArea)
        }
    }

    fun setAutoScanInterval(seconds: Int, disableAutoExpiry: Boolean = false) {
        clearReadingMemory()
        _uiState.update {
            it.copy(
                autoScanIntervalSec = seconds,
                countdownSec = seconds,
                autoClickCooldownSec = seconds.coerceIn(15, 300),
                boConfig = it.boConfig.copy(
                    expirySeconds = seconds,
                    autoExpiryEnabled = if (disableAutoExpiry) false else it.boConfig.autoExpiryEnabled
                )
            )
        }
        val state = _uiState.value
        if (state.sourceMode == CaptureSourceMode.WEBVIEW && seconds in ADAPTIVE_EXPIRY_OPTIONS_SEC) {
            _applyExpiryTrigger.tryEmit(seconds)
        }
        if (state.autoScanEnabled && state.markedArea != null && state.sourceMode == CaptureSourceMode.WEBVIEW) {
            startAutoScanTicker()
        }
        state.markedArea?.let { area ->
            if (!state.isMarkingArea) {
                triggerImmediateCapture(area)
            }
        }
    }

    fun setBoSensitivity(sensitivity: BoSensitivity) {
        if (_uiState.value.boConfig.sensitivity == sensitivity) return
        _uiState.update {
            it.copy(boConfig = it.boConfig.copy(sensitivity = sensitivity))
        }
        val state = _uiState.value
        state.markedArea?.let { area ->
            if (!state.isMarkingArea) {
                triggerImmediateCapture(area)
            }
        }
    }

    fun toggleVoiceEnabled(enabled: Boolean) {
        _uiState.update { it.copy(voiceEnabled = enabled) }
    }

    fun toggleAutoClick(enabled: Boolean) {
        if (enabled) {
            lastAutoClickAtMs = 0L
        } else {
            cancelPendingCandleClose()
        }
        _uiState.update {
            it.copy(
                autoClickEnabled = enabled,
                lastAutoClickSummary = if (enabled) {
                    "Robô armado — aguardando 3 velas + RSI + fechamento do candle"
                } else {
                    "Robô pausado"
                }
            )
        }
        val current = _uiState.value
        if (enabled &&
            current.sourceMode == CaptureSourceMode.WEBVIEW &&
            current.markedArea == null &&
            !current.isMarkingArea &&
            current.calibratingButton == null &&
            !current.calibratingAmountField
        ) {
            autoMarkDefaultChartAndScan()
        }
    }

    fun setAutoClickCooldown(seconds: Int) {
        _uiState.update {
            it.copy(autoClickCooldownSec = seconds.coerceIn(15, 300))
        }
    }

    fun startButtonCalibration() {
        _uiState.update {
            it.copy(
                sourceMode = CaptureSourceMode.WEBVIEW,
                isMarkingArea = false,
                isAnalysisExpanded = false,
                calibratingAmountField = false,
                calibratingButton = SignalDirection.CALL_UP
            )
        }
    }

    fun startAmountCalibration() {
        _uiState.update {
            it.copy(
                sourceMode = CaptureSourceMode.WEBVIEW,
                isMarkingArea = false,
                isAnalysisExpanded = false,
                calibratingButton = null,
                calibratingAmountField = true
            )
        }
    }

    fun recordCalibratedTap(normX: Float, normY: Float) {
        val point = BotPoint(normX.coerceIn(0.02f, 0.98f), normY.coerceIn(0.05f, 0.98f))
        when (_uiState.value.calibratingButton) {
            SignalDirection.CALL_UP -> {
                _uiState.update {
                    it.copy(
                        calibratedCallPoint = point,
                        calibratingButton = SignalDirection.PUT_DOWN
                    )
                }
            }
            SignalDirection.PUT_DOWN -> {
                _uiState.update {
                    it.copy(
                        calibratedPutPoint = point,
                        calibratingButton = null,
                        lastAutoClickSummary = "Botões Acima e Abaixo calibrados!"
                    )
                }
            }
            else -> Unit
        }
    }

    fun recordCalibratedAmountTap(normX: Float, normY: Float) {
        val point = BotPoint(normX.coerceIn(0.02f, 0.98f), normY.coerceIn(0.05f, 0.98f))
        _uiState.update {
            it.copy(
                calibratedAmountPoint = point,
                autoDetectedAmountPoint = point,
                calibratingAmountField = false,
                lastAutoClickSummary = "Campo de Valor ($) calibrado!"
            )
        }
    }

    fun cacheAutoDetectedAmountPoint(point: BotPoint) {
        val safePoint = BotPoint(point.normX.coerceIn(0.02f, 0.98f), point.normY.coerceIn(0.45f, 0.98f))
        _uiState.update { it.copy(autoDetectedAmountPoint = safePoint) }
    }

    fun cacheAutoDetectedTradePoint(direction: SignalDirection, point: BotPoint) {
        val safePoint = BotPoint(point.normX.coerceIn(0.02f, 0.98f), point.normY.coerceIn(0.50f, 0.985f))
        _uiState.update {
            when (direction) {
                SignalDirection.CALL_UP -> it.copy(autoDetectedCallPoint = safePoint)
                SignalDirection.PUT_DOWN -> it.copy(autoDetectedPutPoint = safePoint)
                SignalDirection.NEUTRAL -> it
            }
        }
    }

    fun clearAutoDetectedTradePoints() {
        _uiState.update {
            it.copy(
                autoDetectedCallPoint = null,
                autoDetectedPutPoint = null,
                autoDetectedAmountPoint = null
            )
        }
    }

    fun cancelButtonCalibration() {
        _uiState.update { it.copy(calibratingButton = null, calibratingAmountField = false) }
    }

    fun clearCalibratedButtons() {
        _uiState.update {
            it.copy(
                calibratedCallPoint = null,
                calibratedPutPoint = null,
                autoDetectedCallPoint = null,
                autoDetectedPutPoint = null,
                calibratedAmountPoint = null,
                autoDetectedAmountPoint = null,
                lastAutoClickSummary = "Calibração removida (detecção automática ativa)"
            )
        }
    }

    suspend fun readBalanceFromHeaderBitmap(bitmap: Bitmap): Double? =
        aiRepository.readBrokerBalanceFromBitmap(bitmap)

    suspend fun locateStakeBoxFromBitmap(lowerPanelBitmap: Bitmap): VisualStakeBoxHit? =
        aiRepository.locateStakeBoxFromBitmap(lowerPanelBitmap)

    fun setRobotConfigOpen(open: Boolean) {
        _uiState.update { it.copy(isRobotConfigOpen = open) }
        if (open) {
            // Read DOM balance without spending Gemini Vision quota just for opening Settings
            _balanceCheckTrigger.tryEmit(false)
        }
    }

    fun requestBrokerBalanceRefresh() {
        _balanceCheckTrigger.tryEmit(true)
    }

    fun setAccountType(type: BrokerAccountType) {
        _uiState.update { it.copy(accountType = type) }
    }

    fun setTradingAsset(asset: TradingAsset) {
        _uiState.update {
            it.copy(boConfig = it.boConfig.copy(tradingAsset = asset))
        }
    }

    fun setTechnicalIndicator(indicator: TechnicalIndicator) {
        _uiState.update {
            it.copy(boConfig = it.boConfig.copy(technicalIndicator = indicator))
        }
    }

    fun setCurrencySymbol(symbol: String) {
        val safe = if (symbol == "R$") "R$" else "$"
        _uiState.update { state ->
            state.copy(martingaleConfig = state.martingaleConfig.copy(currencySymbol = safe))
        }
    }

    fun setSorosMaxLevels(levels: Int) {
        val clamped = levels.coerceIn(1, 4)
        _uiState.update { state ->
            state.copy(
                martingaleConfig = state.martingaleConfig.copy(sorosMaxLevels = clamped),
                currentMartingaleStep = state.currentMartingaleStep.coerceAtMost(clamped)
            )
        }
    }

    fun setRiskStrategy(strategy: RiskStrategy) {
        pendingOutcomeCheckJob?.cancel()
        recoveryPlanJob?.cancel()
        val previousStake = _uiState.value.currentBotStake
        _uiState.update { state ->
            val updatedConfig = state.martingaleConfig.copy(
                enabled = strategy != RiskStrategy.FIXED,
                riskStrategy = strategy
            )
            val stakeStr = "${updatedConfig.currencySymbol} ${formatStakeAmount(updatedConfig.initialStake)}"
            val msg = when (strategy) {
                RiskStrategy.AI_RECOVERY -> "Estratégia: Recuperação Inteligente IA ativa (inicial $stakeStr • calcula recuperação exata ao perder)"
                RiskStrategy.FIXED -> "Estratégia: Valor fixo ($stakeStr por entrada)"
                RiskStrategy.MARTINGALE -> "Estratégia: Dobrar na perda (inicial $stakeStr • volta ao acertar)"
                RiskStrategy.SOROS -> "Estratégia: Soros ativo (reinveste lucro ao acertar • inicial $stakeStr)"
            }
            state.copy(
                martingaleConfig = updatedConfig,
                currentMartingaleStep = 0,
                accumulatedLossDeficit = 0.0,
                aiRecoveryPlan = null,
                isPlanningRecovery = false,
                pendingTradeDirection = null,
                pendingTradeStake = null,
                pendingTradeBalanceBefore = null,
                pendingTradeOpenedAtMs = 0L,
                lastOutcomeMessage = msg
            )
        }
        maybeEmitRealTimeStakeSync(previousStake)
    }

    fun toggleMartingaleEnabled(enabled: Boolean) {
        if (enabled) {
            val current = _uiState.value.martingaleConfig.riskStrategy
            val target = if (current == RiskStrategy.FIXED) RiskStrategy.AI_RECOVERY else current
            setRiskStrategy(target)
        } else {
            setRiskStrategy(RiskStrategy.FIXED)
        }
    }

    fun setMartingaleInitialStake(amount: Double) {
        val clamped = (kotlin.math.round(amount.coerceIn(1.0, 50_000.0) * 100.0) / 100.0)
        val previousStake = _uiState.value.currentBotStake
        _uiState.update { state ->
            val updatedConfig = state.martingaleConfig.copy(initialStake = clamped)
            val updatedPlan = state.aiRecoveryPlan?.let { existing ->
                if (state.accumulatedLossDeficit > 0.005) {
                    val recomputed = updatedConfig.computeRecoveryStake(
                        accumulatedDeficit = state.accumulatedLossDeficit,
                        mode = existing.mode,
                        liveBalance = state.liveBrokerBalance
                    )
                    existing.copy(recommendedStake = recomputed)
                } else {
                    null
                }
            }
            val nextStake = if (updatedConfig.effectiveStrategy == RiskStrategy.AI_RECOVERY && updatedPlan != null) {
                updatedPlan.recommendedStake
            } else {
                updatedConfig.stakeForStep(state.currentMartingaleStep)
            }
            val sym = updatedConfig.currencySymbol
            state.copy(
                martingaleConfig = updatedConfig,
                aiRecoveryPlan = updatedPlan,
                lastOutcomeMessage = "Entrada inicial definida para $sym ${formatStakeAmount(clamped)} (próxima: $sym ${formatStakeAmount(nextStake)})"
            )
        }
        maybeEmitRealTimeStakeSync(previousStake)
    }

    fun setMartingaleMaxSteps(steps: Int) {
        // Hard finite cap — never allow infinite martingale
        val clamped = steps.coerceIn(1, 7)
        _uiState.update { state ->
            val updatedConfig = state.martingaleConfig.copy(maxSteps = clamped)
            val clampedStep = state.currentMartingaleStep.coerceAtMost(clamped)
            state.copy(
                martingaleConfig = updatedConfig,
                currentMartingaleStep = clampedStep
            )
        }
    }

    fun setSessionMaxLossLimit(amount: Double) {
        val clamped = (kotlin.math.round(amount.coerceIn(0.0, 50_000.0) * 100.0) / 100.0)
        _uiState.update { state ->
            val updatedConfig = state.martingaleConfig.copy(sessionMaxLossLimit = clamped)
            val stopHit = clamped > 0.0 && state.sessionNetLoss >= clamped - 0.005
            val sym = updatedConfig.currencySymbol
            val msg = if (clamped > 0.0) {
                "Limite máximo de perda da sessão definido: $sym ${formatStakeAmount(clamped)}"
            } else {
                "Limite máximo de perda por sessão desativado"
            }
            state.copy(
                martingaleConfig = updatedConfig,
                isSessionStopLossHit = stopHit,
                lastOutcomeMessage = msg
            )
        }
    }

    fun toggleStrictThreeCandleRsiStrategy(enabled: Boolean) {
        _uiState.update { state ->
            state.copy(
                boConfig = state.boConfig.copy(strictThreeCandleRsiStrategy = enabled),
                lastOutcomeMessage = if (enabled) {
                    "Estratégia ativa: 3 velas seguidas + virada do RSI + filtro anti-lateral"
                } else {
                    "Filtro de 3 velas + RSI flexibilizado (modo tendência geral)"
                }
            )
        }
    }

    fun toggleWaitForCandleClose(enabled: Boolean) {
        if (!enabled) {
            cancelPendingCandleClose()
        }
        _uiState.update { state ->
            state.copy(
                boConfig = state.boConfig.copy(waitForCandleClose = enabled),
                lastOutcomeMessage = if (enabled) {
                    "Confirmação de candle ativa: robô espera a vela fechar (nunca entra no meio)"
                } else {
                    "Entrada imediata sem aguardar fechamento do candle"
                }
            )
        }
    }

    fun cancelPendingCandleClose() {
        pendingCandleCloseJob?.cancel()
        pendingCandleCloseJob = null
        _uiState.update {
            if (it.pendingCandleCloseDirection != null || it.pendingCandleCloseRemainingSec != 0) {
                it.copy(
                    pendingCandleCloseDirection = null,
                    pendingCandleCloseRemainingSec = 0
                )
            } else {
                it
            }
        }
    }

    fun resetSessionRiskGuards() {
        pendingOutcomeCheckJob?.cancel()
        recoveryPlanJob?.cancel()
        cancelPendingCandleClose()
        lossPauseTickerJob?.cancel()
        lossPauseTickerJob = null
        val previousStake = _uiState.value.currentBotStake
        _uiState.update { state ->
            val sym = state.martingaleConfig.currencySymbol
            val stakeStr = "$sym ${formatStakeAmount(state.martingaleConfig.initialStake)}"
            state.copy(
                currentMartingaleStep = 0,
                consecutiveLosses = 0,
                sessionNetLoss = 0.0,
                isSessionStopLossHit = false,
                lossPauseUntilEpochMs = 0L,
                lossPauseRemainingSec = 0,
                accumulatedLossDeficit = 0.0,
                aiRecoveryPlan = null,
                isPlanningRecovery = false,
                pendingTradeDirection = null,
                pendingTradeStake = null,
                pendingTradeBalanceBefore = null,
                pendingTradeOpenedAtMs = 0L,
                lastOutcomeMessage = "Sessão zerada • Stop Loss e pausa limpos ($stakeStr)",
                lastAutoClickSummary = "Robô liberado para operar na nova sessão"
            )
        }
        maybeEmitRealTimeStakeSync(previousStake)
    }

    fun resetMartingaleCycle() {
        pendingOutcomeCheckJob?.cancel()
        recoveryPlanJob?.cancel()
        cancelPendingCandleClose()
        lossPauseTickerJob?.cancel()
        lossPauseTickerJob = null
        val previousStake = _uiState.value.currentBotStake
        _uiState.update { state ->
            val sym = state.martingaleConfig.currencySymbol
            val stakeStr = "$sym ${formatStakeAmount(state.martingaleConfig.initialStake)}"
            state.copy(
                currentMartingaleStep = 0,
                consecutiveLosses = 0,
                lossPauseUntilEpochMs = 0L,
                lossPauseRemainingSec = 0,
                accumulatedLossDeficit = 0.0,
                aiRecoveryPlan = null,
                isPlanningRecovery = false,
                pendingTradeDirection = null,
                pendingTradeStake = null,
                pendingTradeBalanceBefore = null,
                pendingTradeOpenedAtMs = 0L,
                lastOutcomeMessage = "Ciclo zerado • voltou para entrada inicial ($stakeStr)"
            )
        }
        maybeEmitRealTimeStakeSync(previousStake)
    }

    /**
     * Resets all in-app robot settings, calibrations, marked chart area, session stats, and reading memory
     * back to factory defaults while keeping the app ready for a fresh broker login.
     */
    fun resetAllAppState() {
        consecutiveQuotaErrors = 0
        aiRepository.clearQuotaCooldowns()
        stopAutoScanTicker()
        analysisJob?.cancel()
        analysisJob = null
        pendingOutcomeCheckJob?.cancel()
        pendingOutcomeCheckJob = null
        recoveryPlanJob?.cancel()
        recoveryPlanJob = null
        pendingCandleCloseJob?.cancel()
        pendingCandleCloseJob = null
        lossPauseTickerJob?.cancel()
        lossPauseTickerJob = null
        clearReadingMemory()
        lastSpokenDirection = null
        lastSpokenAtMs = 0L
        lastAutoClickAtMs = 0L
        pendingManualTrigger = false
        userClearedMarkedArea = false
        restoreAutoClickOnResume = false
        savedWebViewMarkedArea = null
        savedWebViewAutoArea = true
        _uiState.value = TrendLensUiState(
            lastOutcomeMessage = "Aplicativo resetado: cookies, cache, calibrações e configurações limpos."
        )
    }

    fun recordTradeOutcome(
        outcome: TradeOutcome,
        isAutomatic: Boolean = false,
        emitStakeSync: Boolean = true
    ) {
        pendingOutcomeCheckJob?.cancel()
        recoveryPlanJob?.cancel()
        val previousStake = _uiState.value.currentBotStake
        var aiPlanningRequest: AiRecoveryRequest? = null
        var shouldStartLossPauseTicker = false
        var shouldCancelPendingCandle = false

        _uiState.update { state ->
            val config = state.martingaleConfig
            val sym = config.currencySymbol
            val initialStr = "$sym ${formatStakeAmount(config.initialStake)}"
            val settledStake = state.pendingTradeStake ?: state.currentBotStake
            val payout = config.estimatedPayoutRate.coerceIn(0.65, 0.95)
            val nowEpochMs = clockEpochMs()

            val updatedState = when (config.effectiveStrategy) {
                RiskStrategy.AI_RECOVERY -> {
                    when (outcome) {
                        TradeOutcome.WIN -> {
                            val netProfit = kotlin.math.round(settledStake * payout * 100.0) / 100.0
                            val remainingDeficit = kotlin.math.round((state.accumulatedLossDeficit - netProfit).coerceAtLeast(0.0) * 100.0) / 100.0
                            if (remainingDeficit <= 0.05) {
                                val recoveredNote = if (state.accumulatedLossDeficit > 0.05) {
                                    "${if (isAutomatic) "Recuperação IA concluída!" else "Acerto!"} Prejuízo zerado (+$sym ${formatStakeAmount(netProfit)}) — voltou para $initialStr"
                                } else {
                                    "${if (isAutomatic) "Acerto detectado!" else "Acerto!"} Mantendo entrada inicial ($initialStr)"
                                }
                                state.copy(
                                    currentMartingaleStep = 0,
                                    sessionWins = state.sessionWins + 1,
                                    consecutiveLosses = 0,
                                    accumulatedLossDeficit = 0.0,
                                    aiRecoveryPlan = null,
                                    isPlanningRecovery = false,
                                    pendingTradeDirection = null,
                                    pendingTradeStake = null,
                                    pendingTradeBalanceBefore = null,
                                    pendingTradeOpenedAtMs = 0L,
                                    lastOutcomeMessage = recoveredNote
                                )
                            } else {
                                val nextPlan = buildDeterministicAiRecoveryPlan(
                                    config = config,
                                    accumulatedDeficit = remainingDeficit,
                                    consecutiveLosses = 1,
                                    liveBalance = state.liveBrokerBalance,
                                    baseMinConfidence = state.boConfig.sensitivity.minConfidence,
                                    isAtStepLimit = false
                                )
                                aiPlanningRequest = AiRecoveryRequest(
                                    accumulatedDeficit = remainingDeficit,
                                    initialStake = config.initialStake,
                                    payoutRate = payout,
                                    consecutiveLosses = 1,
                                    maxSteps = config.maxSteps,
                                    liveBalance = state.liveBrokerBalance,
                                    currencySymbol = sym,
                                    baseMinConfidence = state.boConfig.sensitivity.minConfidence,
                                    recentMarketContext = formatRecentMemoryHint()
                                )
                                state.copy(
                                    currentMartingaleStep = 1,
                                    sessionWins = state.sessionWins + 1,
                                    consecutiveLosses = 0,
                                    accumulatedLossDeficit = remainingDeficit,
                                    aiRecoveryPlan = nextPlan,
                                    isPlanningRecovery = true,
                                    pendingTradeDirection = null,
                                    pendingTradeStake = null,
                                    pendingTradeBalanceBefore = null,
                                    pendingTradeOpenedAtMs = 0L,
                                    lastOutcomeMessage = "Recuperação parcial (+$sym ${formatStakeAmount(netProfit)})! Falta $sym ${formatStakeAmount(remainingDeficit)} — próxima: $sym ${formatStakeAmount(nextPlan.recommendedStake)}"
                                )
                            }
                        }
                        TradeOutcome.LOSS -> {
                            val newDeficit = kotlin.math.round((state.accumulatedLossDeficit + settledStake) * 100.0) / 100.0
                            val newConsecutive = state.consecutiveLosses + 1
                            val nextStep = (state.currentMartingaleStep + 1).coerceAtMost(config.maxSteps)
                            val hitLimit = state.currentMartingaleStep >= config.maxSteps
                            val immediatePlan = buildDeterministicAiRecoveryPlan(
                                config = config,
                                accumulatedDeficit = newDeficit,
                                consecutiveLosses = newConsecutive,
                                liveBalance = state.liveBrokerBalance,
                                baseMinConfidence = state.boConfig.sensitivity.minConfidence,
                                isAtStepLimit = hitLimit
                            )
                            if (!hitLimit && immediatePlan.mode != AiRecoveryMode.PAUSE_PROTECT) {
                                aiPlanningRequest = AiRecoveryRequest(
                                    accumulatedDeficit = newDeficit,
                                    initialStake = config.initialStake,
                                    payoutRate = payout,
                                    consecutiveLosses = newConsecutive,
                                    maxSteps = config.maxSteps,
                                    liveBalance = state.liveBrokerBalance,
                                    currencySymbol = sym,
                                    baseMinConfidence = state.boConfig.sensitivity.minConfidence,
                                    recentMarketContext = formatRecentMemoryHint()
                                )
                            }
                            val msg = if (immediatePlan.mode == AiRecoveryMode.PAUSE_PROTECT) {
                                "Proteção IA ativada (perda acumulada $sym ${formatStakeAmount(newDeficit)}): ${immediatePlan.rationale}"
                            } else {
                                "Recuperação IA (${immediatePlan.mode.label}): recuperar $sym ${formatStakeAmount(newDeficit)} → próxima $sym ${formatStakeAmount(immediatePlan.recommendedStake)} (filtro ≥ ${immediatePlan.minRequiredConfidence}%)"
                            }
                            state.copy(
                                currentMartingaleStep = nextStep,
                                sessionLosses = state.sessionLosses + 1,
                                consecutiveLosses = newConsecutive,
                                accumulatedLossDeficit = newDeficit,
                                aiRecoveryPlan = immediatePlan,
                                isPlanningRecovery = aiPlanningRequest != null,
                                pendingTradeDirection = null,
                                pendingTradeStake = null,
                                pendingTradeBalanceBefore = null,
                                pendingTradeOpenedAtMs = 0L,
                                lastOutcomeMessage = msg
                            )
                        }
                    }
                }
                RiskStrategy.FIXED -> {
                    when (outcome) {
                        TradeOutcome.WIN -> state.copy(
                            currentMartingaleStep = 0,
                            sessionWins = state.sessionWins + 1,
                            consecutiveLosses = 0,
                            accumulatedLossDeficit = 0.0,
                            aiRecoveryPlan = null,
                            isPlanningRecovery = false,
                            pendingTradeDirection = null,
                            pendingTradeStake = null,
                            pendingTradeBalanceBefore = null,
                            pendingTradeOpenedAtMs = 0L,
                            lastOutcomeMessage = "Acerto registrado • mantendo valor fixo ($initialStr)"
                        )
                        TradeOutcome.LOSS -> state.copy(
                            currentMartingaleStep = 0,
                            sessionLosses = state.sessionLosses + 1,
                            consecutiveLosses = state.consecutiveLosses + 1,
                            pendingTradeDirection = null,
                            pendingTradeStake = null,
                            pendingTradeBalanceBefore = null,
                            pendingTradeOpenedAtMs = 0L,
                            lastOutcomeMessage = "Perda registrada • mantendo valor fixo ($initialStr)"
                        )
                    }
                }
                RiskStrategy.MARTINGALE -> {
                    when (outcome) {
                        TradeOutcome.WIN -> state.copy(
                            currentMartingaleStep = 0,
                            sessionWins = state.sessionWins + 1,
                            consecutiveLosses = 0,
                            accumulatedLossDeficit = 0.0,
                            aiRecoveryPlan = null,
                            isPlanningRecovery = false,
                            pendingTradeDirection = null,
                            pendingTradeStake = null,
                            pendingTradeBalanceBefore = null,
                            pendingTradeOpenedAtMs = 0L,
                            lastOutcomeMessage = "${if (isAutomatic) "Acerto detectado!" else "Acerto!"} Voltou para entrada inicial ($initialStr)"
                        )
                        TradeOutcome.LOSS -> {
                            val isAtMax = state.currentMartingaleStep >= config.maxSteps
                            val nextStep = if (isAtMax) 0 else state.currentMartingaleStep + 1
                            val nextStakeStr = "$sym ${formatStakeAmount(config.stakeForStep(nextStep))}"
                            val message = if (isAtMax) {
                                "${if (isAutomatic) "Perda no último Gale." else "Perda registrada."} Limite de ${config.maxSteps} dobras — voltou para $initialStr"
                            } else {
                                "${if (isAutomatic) "Perda detectada!" else "Perda registrada!"} Dobrou para $nextStakeStr (Gale $nextStep/${config.maxSteps})"
                            }
                            state.copy(
                                currentMartingaleStep = nextStep,
                                sessionLosses = state.sessionLosses + 1,
                                consecutiveLosses = state.consecutiveLosses + 1,
                                pendingTradeDirection = null,
                                pendingTradeStake = null,
                                pendingTradeBalanceBefore = null,
                                pendingTradeOpenedAtMs = 0L,
                                lastOutcomeMessage = message
                            )
                        }
                    }
                }
                RiskStrategy.SOROS -> {
                    when (outcome) {
                        TradeOutcome.WIN -> {
                            val isCycleDone = state.currentMartingaleStep >= config.sorosMaxLevels
                            val nextStep = if (isCycleDone) 0 else state.currentMartingaleStep + 1
                            val nextStakeStr = "$sym ${formatStakeAmount(config.stakeForStep(nextStep))}"
                            val message = if (isCycleDone) {
                                "Ciclo Soros concluído! Voltou para entrada inicial ($initialStr)"
                            } else {
                                "Soros Nível $nextStep/${config.sorosMaxLevels}: próxima entrada $nextStakeStr"
                            }
                            state.copy(
                                currentMartingaleStep = nextStep,
                                sessionWins = state.sessionWins + 1,
                                consecutiveLosses = 0,
                                pendingTradeDirection = null,
                                pendingTradeStake = null,
                                pendingTradeBalanceBefore = null,
                                pendingTradeOpenedAtMs = 0L,
                                lastOutcomeMessage = message
                            )
                        }
                        TradeOutcome.LOSS -> state.copy(
                            currentMartingaleStep = 0,
                            sessionLosses = state.sessionLosses + 1,
                            consecutiveLosses = state.consecutiveLosses + 1,
                            pendingTradeDirection = null,
                            pendingTradeStake = null,
                            pendingTradeBalanceBefore = null,
                            pendingTradeOpenedAtMs = 0L,
                            lastOutcomeMessage = "Perda no Soros — voltou para entrada inicial ($initialStr)"
                        )
                    }
                }
            }

            // Apply universal session risk-management guards:
            // 1) Session Max Loss Limit (Stop Loss de Sessão)
            // 2) 2 Consecutive Losses -> mandatory 5-minute (300s) trading pause
            when (outcome) {
                TradeOutcome.WIN -> {
                    val netProfit = kotlin.math.round(settledStake * payout * 100.0) / 100.0
                    val updatedNetLoss = kotlin.math.round((state.sessionNetLoss - netProfit).coerceAtLeast(0.0) * 100.0) / 100.0
                    val stopStillHit = config.sessionMaxLossLimit > 0.0 && updatedNetLoss >= config.sessionMaxLossLimit - 0.005
                    updatedState.copy(
                        sessionNetLoss = updatedNetLoss,
                        isSessionStopLossHit = stopStillHit,
                        lossPauseUntilEpochMs = 0L,
                        lossPauseRemainingSec = 0
                    )
                }
                TradeOutcome.LOSS -> {
                    val updatedNetLoss = kotlin.math.round((state.sessionNetLoss + settledStake) * 100.0) / 100.0
                    val stopLossHit = config.sessionMaxLossLimit > 0.0 && updatedNetLoss >= config.sessionMaxLossLimit - 0.005
                    val newConsecutive = updatedState.consecutiveLosses
                    val shouldPauseFiveMin = !stopLossHit &&
                        config.twoLossPauseSeconds > 0 &&
                        newConsecutive >= 2 &&
                        newConsecutive % 2 == 0
                    when {
                        stopLossHit -> {
                            shouldCancelPendingCandle = true
                            val limitStr = "$sym ${formatStakeAmount(config.sessionMaxLossLimit)}"
                            val lostStr = "$sym ${formatStakeAmount(updatedNetLoss)}"
                            updatedState.copy(
                                sessionNetLoss = updatedNetLoss,
                                isSessionStopLossHit = true,
                                lossPauseUntilEpochMs = 0L,
                                lossPauseRemainingSec = 0,
                                lastOutcomeMessage = "Stop Loss da sessão atingido ($lostStr / $limitStr) — robô parado para proteger sua banca",
                                lastAutoClickSummary = "Stop Loss da sessão atingido ($lostStr / $limitStr) — toque em Zerar sessão para retomar"
                            )
                        }
                        shouldPauseFiveMin -> {
                            shouldCancelPendingCandle = true
                            shouldStartLossPauseTicker = true
                            val pauseSec = config.twoLossPauseSeconds
                            val pauseMin = (pauseSec / 60).coerceAtLeast(1)
                            updatedState.copy(
                                sessionNetLoss = updatedNetLoss,
                                isSessionStopLossHit = false,
                                lossPauseUntilEpochMs = nowEpochMs + pauseSec * 1_000L,
                                lossPauseRemainingSec = pauseSec,
                                lastOutcomeMessage = "2 derrotas seguidas — robô pausado por $pauseMin min para proteger sua banca",
                                lastAutoClickSummary = "Pausa de segurança (2 derrotas seguidas): retomando em ${formatPauseCountdown(pauseSec)}"
                            )
                        }
                        else -> updatedState.copy(
                            sessionNetLoss = updatedNetLoss,
                            isSessionStopLossHit = false
                        )
                    }
                }
            }
        }
        if (shouldCancelPendingCandle) {
            cancelPendingCandleClose()
        }
        if (shouldStartLossPauseTicker) {
            startLossPauseTicker()
        } else if (outcome == TradeOutcome.WIN) {
            lossPauseTickerJob?.cancel()
            lossPauseTickerJob = null
        }
        if (emitStakeSync) {
            maybeEmitRealTimeStakeSync(previousStake)
        }
        aiPlanningRequest?.let { req ->
            if (!_uiState.value.isSessionStopLossHit) {
                launchAiRecoveryOptimization(req, emitStakeSync)
            }
        }
    }

    private fun startLossPauseTicker() {
        lossPauseTickerJob?.cancel()
        lossPauseTickerJob = viewModelScope.launch {
            while (true) {
                delay(1_000L)
                val state = _uiState.value
                val untilMs = state.lossPauseUntilEpochMs
                if (untilMs <= 0L) break
                val remainingMs = untilMs - clockEpochMs()
                if (remainingMs <= 0L) {
                    _uiState.update {
                        it.copy(
                            lossPauseUntilEpochMs = 0L,
                            lossPauseRemainingSec = 0,
                            lastAutoClickSummary = "Pausa de 5 min concluída — robô pronto para novas entradas"
                        )
                    }
                    break
                } else {
                    val remSec = ((remainingMs + 999L) / 1_000L).toInt()
                    _uiState.update {
                        it.copy(
                            lossPauseRemainingSec = remSec,
                            lastAutoClickSummary = "Pausa de segurança (2 derrotas seguidas): retomando em ${formatPauseCountdown(remSec)}"
                        )
                    }
                }
            }
        }
    }

    internal fun computeSecondsUntilCandleClose(nowEpochMs: Long, expirySeconds: Int): Int {
        val periodSec = expirySeconds.coerceIn(30, 300)
        val epochSec = nowEpochMs / 1_000L
        val elapsedSec = ((epochSec % periodSec) + periodSec) % periodSec
        val remainingSec = periodSec - elapsedSec.toInt()
        // Allow immediate entry right on candle close/open rollover boundary (<= 2s before or after close)
        if (elapsedSec <= 2L || remainingSec <= 2) return 0
        return remainingSec
    }

    private fun formatPauseCountdown(totalSeconds: Int): String {
        val safe = totalSeconds.coerceAtLeast(0)
        val mins = safe / 60
        val secs = safe % 60
        return String.format(java.util.Locale.US, "%02d:%02d", mins, secs)
    }

    private fun buildDeterministicAiRecoveryPlan(
        config: MartingaleConfig,
        accumulatedDeficit: Double,
        consecutiveLosses: Int,
        liveBalance: Double?,
        baseMinConfidence: Int,
        isAtStepLimit: Boolean
    ): AiRecoveryPlan {
        val sym = config.currencySymbol
        if (isAtStepLimit) {
            return AiRecoveryPlan(
                mode = AiRecoveryMode.PAUSE_PROTECT,
                recommendedStake = config.initialStake,
                minRequiredConfidence = 86,
                extraWaitSeconds = 60,
                rationale = "Limite de ${config.maxSteps} etapas atingido — robô pausado para proteger sua banca. Toque em Zerar ciclo quando quiser retomar.",
                isFromGemini = false
            )
        }
        val fullStake = config.computeRecoveryStake(
            accumulatedDeficit = accumulatedDeficit,
            mode = AiRecoveryMode.FULL_RECOVERY_NOW,
            liveBalance = liveBalance
        )
        val exceedsSafeSingleBet = liveBalance != null &&
            liveBalance > config.initialStake &&
            ((accumulatedDeficit + config.initialStake * config.estimatedPayoutRate) / config.estimatedPayoutRate) > liveBalance * 0.35

        val mode = when {
            exceedsSafeSingleBet && consecutiveLosses >= 3 -> AiRecoveryMode.PAUSE_PROTECT
            exceedsSafeSingleBet || consecutiveLosses >= 3 -> AiRecoveryMode.SPLIT_RECOVERY_2X
            else -> AiRecoveryMode.FULL_RECOVERY_NOW
        }
        val stake = config.computeRecoveryStake(
            accumulatedDeficit = accumulatedDeficit,
            mode = mode,
            liveBalance = liveBalance
        )
        val minConfidence = when {
            consecutiveLosses >= 3 -> maxOf(baseMinConfidence, 82)
            consecutiveLosses == 2 -> maxOf(baseMinConfidence, 78)
            else -> maxOf(baseMinConfidence, 75)
        }.coerceAtMost(88)
        val extraWait = if (consecutiveLosses >= 2) 15 else 0
        val rationale = when (mode) {
            AiRecoveryMode.FULL_RECOVERY_NOW ->
                "Cálculo exato (payout ${(config.estimatedPayoutRate * 100).toInt()}%): $sym ${formatStakeAmount(stake)} recupera $sym ${formatStakeAmount(accumulatedDeficit)} + lucro na próxima tendência ≥ $minConfidence%."
            AiRecoveryMode.SPLIT_RECOVERY_2X ->
                "Recuperação dividida em 2 etapas ($sym ${formatStakeAmount(stake)}) para proteger banca após $consecutiveLosses perdas (filtro ≥ $minConfidence%)."
            AiRecoveryMode.BREAKEVEN_SHIELD ->
                "Modo empate seguro ($sym ${formatStakeAmount(stake)}) para zerar perda de $sym ${formatStakeAmount(accumulatedDeficit)}."
            AiRecoveryMode.PAUSE_PROTECT ->
                "Déficit atual exige aposta acima do limite seguro de 35% da banca — robô pausado para proteção."
        }
        return AiRecoveryPlan(
            mode = mode,
            recommendedStake = stake,
            minRequiredConfidence = minConfidence,
            extraWaitSeconds = extraWait,
            rationale = rationale,
            isFromGemini = false
        )
    }

    private fun launchAiRecoveryOptimization(
        request: AiRecoveryRequest,
        emitStakeSync: Boolean
    ) {
        recoveryPlanJob?.cancel()
        recoveryPlanJob = viewModelScope.launch {
            try {
                val aiPlan = aiRepository.planLossRecoveryStrategy(request)
                val previousStake = _uiState.value.currentBotStake
                _uiState.update { state ->
                    if (state.martingaleConfig.effectiveStrategy != RiskStrategy.AI_RECOVERY ||
                        state.accumulatedLossDeficit <= 0.005
                    ) {
                        state.copy(isPlanningRecovery = false)
                    } else if (aiPlan != null) {
                        val sym = state.martingaleConfig.currencySymbol
                        val msg = if (aiPlan.mode == AiRecoveryMode.PAUSE_PROTECT) {
                            "Proteção IA Gemini: ${aiPlan.rationale}"
                        } else {
                            "IA Gemini (${aiPlan.mode.label}): $sym ${formatStakeAmount(aiPlan.recommendedStake)} • filtro ≥ ${aiPlan.minRequiredConfidence}% — ${aiPlan.rationale}"
                        }
                        state.copy(
                            aiRecoveryPlan = aiPlan,
                            isPlanningRecovery = false,
                            lastOutcomeMessage = msg
                        )
                    } else {
                        state.copy(isPlanningRecovery = false)
                    }
                }
                if (emitStakeSync && aiPlan != null) {
                    maybeEmitRealTimeStakeSync(previousStake)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(VM_TAG, "AI recovery optimization fell back to deterministic plan: ${e.message}")
                _uiState.update { it.copy(isPlanningRecovery = false) }
            }
        }
    }

    private fun maybeEmitRealTimeStakeSync(previousStake: Double) {
        val updated = _uiState.value
        val newStake = updated.currentBotStake
        if (updated.sourceMode == CaptureSourceMode.WEBVIEW &&
            kotlin.math.abs(newStake - previousStake) >= 0.005
        ) {
            _applyStakeTrigger.tryEmit(newStake)
        }
    }

    fun resolvePendingTradeFromBalance(balanceNow: Double?): Double? {
        if (balanceNow != null) {
            _uiState.update { it.copy(liveBrokerBalance = balanceNow) }
        }
        val state = _uiState.value
        val balanceBefore = state.pendingTradeBalanceBefore
        val stake = state.pendingTradeStake
        if (state.martingaleConfig.enabled && balanceBefore != null && stake != null) {
            val outcome = resolveTradeOutcomeFromBalance(
                balanceBeforeTrade = balanceBefore,
                balanceNow = balanceNow,
                stakeAmount = stake
            )
            if (outcome != null) {
                recordTradeOutcome(outcome, isAutomatic = true, emitStakeSync = false)
            }
        }
        val refreshed = _uiState.value
        return refreshed.currentBotStake
    }

    fun onBrokerBalanceObserved(balanceNow: Double?) {
        if (balanceNow != null) {
            _uiState.update { it.copy(liveBrokerBalance = balanceNow) }
        }
        val state = _uiState.value
        if (!state.martingaleConfig.enabled || state.pendingTradeDirection == null) return
        val before = state.pendingTradeBalanceBefore
        val stake = state.pendingTradeStake
        if (before == null || stake == null) {
            _uiState.update {
                it.copy(
                    pendingTradeDirection = null,
                    pendingTradeBalanceBefore = null,
                    pendingTradeStake = null,
                    lastOutcomeMessage = "Saldo não lido na tela — use 'Acertou' ou 'Perdeu' se o gráfico usar canvas."
                )
            }
            return
        }
        if (balanceNow == null) {
            // Keep pending trade tracking alive during transient null readings until expiry window + 45s elapses
            val elapsedMs = if (state.pendingTradeOpenedAtMs > 0L) {
                System.currentTimeMillis() - state.pendingTradeOpenedAtMs
            } else {
                0L
            }
            val timeoutMs = ((state.boConfig.expirySeconds.coerceAtLeast(30)) + 45) * 1_000L
            if (elapsedMs >= timeoutMs) {
                _uiState.update {
                    it.copy(
                        pendingTradeDirection = null,
                        pendingTradeBalanceBefore = null,
                        pendingTradeStake = null,
                        lastOutcomeMessage = "Saldo não lido na tela — use 'Acertou' ou 'Perdeu' se o gráfico usar canvas."
                    )
                }
            }
            return
        }
        val outcome = resolveTradeOutcomeFromBalance(before, balanceNow, stake)
        if (outcome != null) {
            recordTradeOutcome(outcome, isAutomatic = true, emitStakeSync = true)
        }
    }

    fun onAutoClickExecuted(direction: SignalDirection, result: BotExecutionResult) {
        if (!result.success) {
            lastAutoClickAtMs = 0L
        }
        val timeFormatted = com.example.data.BrazilLocaleConfig.formatBrasiliaTime()
        val dirWord = when (direction) {
            SignalDirection.CALL_UP -> "Acima"
            SignalDirection.PUT_DOWN -> "Abaixo"
            SignalDirection.NEUTRAL -> "Neutro"
        }

        val state = _uiState.value
        val summary = if (result.success) {
            if (state.martingaleConfig.enabled) {
                val stakeUsed = state.currentBotStake
                val stakeLabel = formatStakeAmount(stakeUsed)
                val stepPrefix = if (state.currentMartingaleStep > 0) {
                    "Gale ${state.currentMartingaleStep}/${state.martingaleConfig.maxSteps} ($stakeLabel)"
                } else {
                    "Entrada $stakeLabel"
                }
                val domNote = if (result.stakeAppliedInDom) "" else " • ajuste o valor na corretora se usar canvas"
                "$stepPrefix • ${result.detail} ($dirWord) às $timeFormatted$domNote"
            } else {
                "${result.detail} ($dirWord) às $timeFormatted"
            }
        } else {
            result.detail
        }

        _uiState.update { current ->
            val updatedBalance = result.balanceBeforeClick ?: current.liveBrokerBalance
            if (result.success && current.martingaleConfig.enabled) {
                current.copy(
                    liveBrokerBalance = updatedBalance,
                    lastAutoClickSummary = summary,
                    pendingTradeDirection = direction,
                    pendingTradeStake = current.currentBotStake,
                    pendingTradeBalanceBefore = result.balanceBeforeClick,
                    pendingTradeOpenedAtMs = System.currentTimeMillis()
                )
            } else {
                current.copy(
                    liveBrokerBalance = updatedBalance,
                    lastAutoClickSummary = summary
                )
            }
        }

        if (result.success && state.martingaleConfig.enabled) {
            schedulePostExpiryBalanceCheck(state.boConfig.expirySeconds)
        }
    }

    private fun schedulePostExpiryBalanceCheck(expirySeconds: Int) {
        pendingOutcomeCheckJob?.cancel()
        val waitSec = if (expirySeconds <= 0) 60 else expirySeconds
        pendingOutcomeCheckJob = viewModelScope.launch {
            delay((waitSec + 4) * 1_000L)
            for (attempt in 0 until 8) {
                val state = _uiState.value
                if (!state.martingaleConfig.enabled || state.pendingTradeDirection == null) {
                    break
                }
                _balanceCheckTrigger.tryEmit(true)
                delay(3_500L)
            }
        }
    }

    fun speakCurrentResult() {
        val current = _uiState.value.analysisState
        if (current is AnalysisUiState.Ready) {
            _speechTrigger.tryEmit(current.result)
        }
    }

    fun onGalleryBitmapSelected(
        bitmap: Bitmap,
        autoViewportArea: NormalizedRect? = null,
        autoCroppedRegion: Bitmap? = null
    ) {
        stopAutoScanTicker()
        analysisJob?.cancel()
        analysisJob = null
        clearReadingMemory()
        lastSpokenDirection = null
        lastSpokenAtMs = 0L
        val targetArea = autoViewportArea ?: NormalizedRect(0.06f, 0.20f, 0.94f, 0.68f)
        _uiState.update {
            it.copy(
                sourceMode = CaptureSourceMode.GALLERY,
                selectedGalleryBitmap = bitmap,
                markedArea = if (autoCroppedRegion != null) targetArea else null,
                isMarkingArea = autoCroppedRegion == null,
                draftSelection = if (autoCroppedRegion == null) targetArea else null,
                isAnalysisExpanded = false,
                analysisState = AnalysisUiState.Unavailable
            )
        }
        if (autoCroppedRegion != null) {
            pendingManualTrigger = true
            onRegionBitmapCaptured(autoCroppedRegion, force = true)
        }
    }

    fun onRegionBitmapCaptured(bitmap: Bitmap, force: Boolean = false) {
        if (!force && analysisJob?.isActive == true) return
        if (force) analysisJob?.cancel()
        val currentState = _uiState.value
        val sourceLabel = if (currentState.sourceMode == CaptureSourceMode.WEBVIEW) {
            currentState.displayHost
        } else {
            "Print da galeria"
        }
        val boConfigSnapshot = currentState.boConfig
        val previousHintSnapshot = formatRecentMemoryHint()
        val isManualScan = pendingManualTrigger
        pendingManualTrigger = false

        val frameSignature = computeChartFrameSignature(bitmap)
        val nowElapsedMs = android.os.SystemClock.elapsedRealtime()
        val previousSig = lastStaticFrameSignature
        val isVisuallyUnchanged = previousSig != null &&
            computeFrameChangeRatio(previousSig, frameSignature) < VISUAL_CHANGE_SKIP_THRESHOLD
        val previousWasNeutralOrNonChart = when (val prevState = currentState.analysisState) {
            is AnalysisUiState.NotAChart -> true
            is AnalysisUiState.Ready -> prevState.result.direction == SignalDirection.NEUTRAL
            else -> false
        }
        val isStaticUnchangedAutoScan = !isManualScan &&
            currentState.sourceMode == CaptureSourceMode.WEBVIEW &&
            isVisuallyUnchanged &&
            lastStaticFrameBoConfig == boConfigSnapshot &&
            (nowElapsedMs - lastStaticFrameCapturedAtMs) in 0L..STATIC_FRAME_SKIP_WINDOW_MS &&
            previousWasNeutralOrNonChart
        if (isStaticUnchangedAutoScan) {
            _uiState.update { state ->
                val nextCount = if (state.autoScanEnabled) {
                    minOf(FAST_TREND_HUNT_INTERVAL_SEC, state.autoScanIntervalSec.coerceAtLeast(FAST_TREND_HUNT_INTERVAL_SEC))
                } else {
                    state.countdownSec
                }
                state.copy(countdownSec = nextCount)
            }
            return
        }

        analysisJob = viewModelScope.launch {
            _uiState.update { it.copy(analysisState = AnalysisUiState.Analyzing) }
            try {
                when (
                    val outcome = aiRepository.analyzeChartRegion(
                        bitmap = bitmap,
                        sourceDescription = sourceLabel,
                        boConfig = boConfigSnapshot,
                        previousReadingHint = previousHintSnapshot
                    )
                ) {
                    is ChartAnalysisOutcome.ValidChart -> {
                        val result = outcome.result
                        val entryHint = "${result.direction.name}(${result.confidencePercent}%,F=${result.trendStrengthScore},P=${result.confluenceCount}/5,RSI=${result.rsiValue})"
                        if (recentReadingHints.size >= 3) {
                            recentReadingHints.removeFirst()
                        }
                        recentReadingHints.addLast(entryHint)

                        val previousExpirySec = _uiState.value.boConfig.expirySeconds
                        val adaptiveChosenSec: Int? = if (_uiState.value.boConfig.autoExpiryEnabled &&
                            result.direction != SignalDirection.NEUTRAL &&
                            result.recommendedExpirySeconds != null
                        ) {
                            normalizeAdaptiveExpirySeconds(result.recommendedExpirySeconds)
                        } else {
                            null
                        }

                        _uiState.update { state ->
                            val updatedBoConfig = if (adaptiveChosenSec != null) {
                                state.boConfig.copy(expirySeconds = adaptiveChosenSec)
                            } else {
                                state.boConfig
                            }
                            val effectiveIntervalSec = adaptiveChosenSec ?: state.autoScanIntervalSec
                            val effectiveCooldownSec = adaptiveChosenSec?.coerceIn(15, 300) ?: state.autoClickCooldownSec
                            val shouldFastHunt = state.sourceMode == CaptureSourceMode.WEBVIEW &&
                                state.autoScanEnabled &&
                                result.direction == SignalDirection.NEUTRAL &&
                                state.pendingTradeDirection == null
                            val nextCountdown = if (shouldFastHunt) {
                                minOf(FAST_TREND_HUNT_INTERVAL_SEC, effectiveIntervalSec.coerceAtLeast(FAST_TREND_HUNT_INTERVAL_SEC))
                            } else {
                                effectiveIntervalSec
                            }
                            val adaptiveMsg = if (adaptiveChosenSec != null && adaptiveChosenSec != previousExpirySec) {
                                val reasonPart = result.expirySelectionReason.takeIf { it.isNotBlank() }?.let { " • $it" }.orEmpty()
                                "IA ajustou tempo para ${formatExpiryShortLabel(adaptiveChosenSec)}$reasonPart"
                            } else {
                                state.lastOutcomeMessage
                            }
                            state.copy(
                                boConfig = updatedBoConfig,
                                autoScanIntervalSec = effectiveIntervalSec,
                                autoClickCooldownSec = effectiveCooldownSec,
                                analysisState = AnalysisUiState.Ready(
                                    result = result,
                                    capturedPreview = bitmap
                                ),
                                countdownSec = nextCountdown,
                                lastOutcomeMessage = adaptiveMsg,
                                history = (listOf(result) + state.history).take(8)
                            )
                        }
                        consecutiveQuotaErrors = 0
                        lastStaticFrameSignature = frameSignature
                        lastStaticFrameCapturedAtMs = android.os.SystemClock.elapsedRealtime()
                        lastStaticFrameBoConfig = _uiState.value.boConfig

                        if (adaptiveChosenSec != null &&
                            adaptiveChosenSec != previousExpirySec &&
                            _uiState.value.sourceMode == CaptureSourceMode.WEBVIEW
                        ) {
                            _applyExpiryTrigger.tryEmit(adaptiveChosenSec)
                        }
                        maybeEmitVoiceAlert(result = result, isManualScan = isManualScan)
                        maybeTriggerAutoClick(result = result)
                    }
                    is ChartAnalysisOutcome.InvalidChart -> {
                        consecutiveQuotaErrors = 0
                        clearReadingMemory()
                        lastStaticFrameSignature = frameSignature
                        lastStaticFrameCapturedAtMs = android.os.SystemClock.elapsedRealtime()
                        lastStaticFrameBoConfig = boConfigSnapshot
                        lastSpokenDirection = null
                        _uiState.update { state ->
                            val nextCountdown = if (state.sourceMode == CaptureSourceMode.WEBVIEW && state.autoScanEnabled) {
                                minOf(FAST_TREND_HUNT_INTERVAL_SEC, state.autoScanIntervalSec.coerceAtLeast(FAST_TREND_HUNT_INTERVAL_SEC))
                            } else {
                                state.countdownSec
                            }
                            state.copy(
                                isAnalysisExpanded = if (state.sourceMode == CaptureSourceMode.GALLERY || isManualScan) true else state.isAnalysisExpanded,
                                countdownSec = nextCountdown,
                                analysisState = AnalysisUiState.NotAChart(
                                    reason = outcome.reason,
                                    capturedPreview = bitmap
                                )
                            )
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(VM_TAG, "Chart analysis timed out", e)
                _uiState.update {
                    val nextCountdown = if (it.sourceMode == CaptureSourceMode.WEBVIEW && it.autoScanEnabled) {
                        minOf(FAST_TREND_HUNT_INTERVAL_SEC, it.autoScanIntervalSec.coerceAtLeast(FAST_TREND_HUNT_INTERVAL_SEC))
                    } else {
                        it.countdownSec
                    }
                    it.copy(
                        isAnalysisExpanded = if (isManualScan || it.sourceMode == CaptureSourceMode.GALLERY || it.history.isEmpty()) true else it.isAnalysisExpanded,
                        countdownSec = nextCountdown,
                        analysisState = AnalysisUiState.Error(
                            message = "A análise da IA demorou demais. Buscando novamente em ${nextCountdown}s.",
                            capturedPreview = bitmap
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: com.example.data.AiQuotaCooldownException) {
                Log.w(VM_TAG, "Gemini quota cooldown active (${e.remainingSeconds}s remaining)")
                val backoffSec = computeNextQuotaBackoffSec(e.remainingSeconds)
                val msg = e.localizedMessage
                    ?: "Proteção de cota da IA Gemini ativa — retomando automaticamente em ${backoffSec}s."
                _uiState.update {
                    val nextCountdown = if (it.sourceMode == CaptureSourceMode.WEBVIEW && it.autoScanEnabled) {
                        backoffSec
                    } else {
                        it.countdownSec
                    }
                    it.copy(
                        isAnalysisExpanded = if (isManualScan || it.sourceMode == CaptureSourceMode.GALLERY || it.history.isEmpty()) true else it.isAnalysisExpanded,
                        countdownSec = nextCountdown,
                        analysisState = AnalysisUiState.Error(
                            message = msg,
                            capturedPreview = bitmap
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(VM_TAG, "Firebase AI chart analysis failed", e)
                val msg = e.localizedMessage ?: "Falha ao consultar a IA Gemini."
                val isQuotaFailure = com.example.data.isQuotaExceededThrowable(e) ||
                    msg.contains("Cota", ignoreCase = true) ||
                    msg.contains("Limite momentâneo", ignoreCase = true) ||
                    msg.contains("Limite de requisições", ignoreCase = true) ||
                    msg.contains("Proteção de cota", ignoreCase = true) ||
                    msg.contains("quota", ignoreCase = true) ||
                    msg.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ||
                    msg.contains("429")
                val backoffSec = if (isQuotaFailure) computeNextQuotaBackoffSec() else 0
                _uiState.update {
                    val nextCountdown = when {
                        !(it.sourceMode == CaptureSourceMode.WEBVIEW && it.autoScanEnabled) -> it.countdownSec
                        isQuotaFailure -> backoffSec
                        else -> minOf(FAST_TREND_HUNT_INTERVAL_SEC, it.autoScanIntervalSec.coerceAtLeast(FAST_TREND_HUNT_INTERVAL_SEC))
                    }
                    it.copy(
                        isAnalysisExpanded = if (isManualScan || it.sourceMode == CaptureSourceMode.GALLERY || it.history.isEmpty()) true else it.isAnalysisExpanded,
                        countdownSec = nextCountdown,
                        analysisState = AnalysisUiState.Error(
                            message = msg,
                            capturedPreview = bitmap
                        )
                    )
                }
            }
        }
    }

    internal fun computeChartFrameSignature(bitmap: Bitmap): IntArray {
        val cols = 16
        val rows = 12
        val signature = IntArray(cols * rows)
        if (bitmap.isRecycled || bitmap.width < 8 || bitmap.height < 8) return signature
        var idx = 0
        for (yIdx in 0 until rows) {
            val py = (((yIdx + 0.5f) * bitmap.height) / rows).toInt().coerceIn(0, bitmap.height - 1)
            for (xIdx in 0 until cols) {
                val px = (((xIdx + 0.5f) * bitmap.width) / cols).toInt().coerceIn(0, bitmap.width - 1)
                val pixel = bitmap.getPixel(px, py)
                // Quantize RGB to 4-bit channels so minor clock-tick / anti-aliasing noise never triggers redundant Gemini scans
                val r = (android.graphics.Color.red(pixel) shr 4)
                val g = (android.graphics.Color.green(pixel) shr 4)
                val b = (android.graphics.Color.blue(pixel) shr 4)
                signature[idx++] = (r shl 8) or (g shl 4) or b
            }
        }
        return signature
    }

    internal fun computeFrameChangeRatio(previous: IntArray, current: IntArray): Float {
        if (previous.size != current.size || previous.isEmpty()) return 1f
        var changed = 0
        for (i in previous.indices) {
            if (previous[i] != current[i]) changed++
        }
        return changed.toFloat() / previous.size.toFloat()
    }

    fun onCaptureFailed(reason: String) {
        _uiState.update {
            it.copy(analysisState = AnalysisUiState.Error(reason))
        }
    }

    fun toggleAnalysisExpanded() {
        _uiState.update { it.copy(isAnalysisExpanded = !it.isAnalysisExpanded) }
    }

    fun setAnalysisExpanded(expanded: Boolean) {
        _uiState.update { it.copy(isAnalysisExpanded = expanded) }
    }

    fun setHelpDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(isHelpDialogVisible = visible) }
    }

    fun updateWebLoading(progress: Int, isLoading: Boolean) {
        _uiState.update {
            it.copy(
                webProgress = progress,
                isWebLoading = isLoading
            )
        }
    }

    fun updateWebLocation(url: String, canGoBack: Boolean) {
        val host = extractDisplayHost(url)
        _uiState.update {
            it.copy(
                currentUrl = url,
                displayHost = host,
                canGoBack = canGoBack,
                webError = null
            )
        }
    }

    fun updateWebError(errorDescription: String?) {
        _uiState.update {
            it.copy(
                webError = errorDescription,
                isWebLoading = false
            )
        }
    }

    private fun maybeEmitVoiceAlert(result: ChartAnalysisResult, isManualScan: Boolean) {
        if (!_uiState.value.voiceEnabled) return
        val nowMs = android.os.SystemClock.elapsedRealtime()
        val dir = result.direction
        if (dir == com.example.model.SignalDirection.NEUTRAL) {
            lastSpokenDirection = com.example.model.SignalDirection.NEUTRAL
            if (isManualScan) {
                _speechTrigger.tryEmit(result)
                lastSpokenAtMs = nowMs
            }
            return
        }

        val isNewOrChangedTrend = lastSpokenDirection == null ||
            lastSpokenDirection == com.example.model.SignalDirection.NEUTRAL ||
            lastSpokenDirection != dir
        val reminderElapsed = (nowMs - lastSpokenAtMs) >= 35_000L

        if (isManualScan || isNewOrChangedTrend || reminderElapsed) {
            _speechTrigger.tryEmit(result)
            lastSpokenDirection = dir
            lastSpokenAtMs = nowMs
        }
    }

    private fun maybeTriggerAutoClick(result: ChartAnalysisResult) {
        val state = _uiState.value
        if (!state.autoClickEnabled) return
        if (state.sourceMode != CaptureSourceMode.WEBVIEW) return
        if (state.calibratingButton != null || state.calibratingAmountField || state.isMarkingArea) return

        // Hard Gate 1: Session Max Loss Limit (Stop Loss de Sessão)
        if (state.isSessionStopLossHit) {
            val sym = state.martingaleConfig.currencySymbol
            val limitStr = "$sym ${formatStakeAmount(state.martingaleConfig.sessionMaxLossLimit)}"
            val lostStr = "$sym ${formatStakeAmount(state.sessionNetLoss)}"
            _uiState.update {
                it.copy(
                    lastAutoClickSummary = "Stop Loss da sessão atingido ($lostStr / $limitStr) — toque em Zerar sessão para retomar"
                )
            }
            return
        }

        // Hard Gate 2: Mandatory 5-minute trading pause after 2 consecutive losses
        val nowEpochMs = clockEpochMs()
        if (state.lossPauseUntilEpochMs > nowEpochMs) {
            val remSec = (((state.lossPauseUntilEpochMs - nowEpochMs) + 999L) / 1_000L).toInt().coerceAtLeast(1)
            _uiState.update {
                it.copy(
                    lossPauseRemainingSec = remSec,
                    lastAutoClickSummary = "Pausa de segurança (2 derrotas seguidas): retomando em ${formatPauseCountdown(remSec)}"
                )
            }
            return
        } else if (state.lossPauseUntilEpochMs > 0L) {
            _uiState.update { it.copy(lossPauseUntilEpochMs = 0L, lossPauseRemainingSec = 0) }
        }

        val activeAiPlan = if (state.martingaleConfig.effectiveStrategy == RiskStrategy.AI_RECOVERY &&
            state.accumulatedLossDeficit > 0.005
        ) {
            state.aiRecoveryPlan
        } else {
            null
        }
        if (activeAiPlan?.mode == AiRecoveryMode.PAUSE_PROTECT) {
            _uiState.update {
                it.copy(
                    lastAutoClickSummary = "Proteção IA ativa: ${activeAiPlan.rationale}"
                )
            }
            return
        }

        val baseRequiredConfidence = if (state.consecutiveLosses >= 2) {
            maxOf(state.boConfig.sensitivity.minConfidence, 80)
        } else {
            state.boConfig.sensitivity.minConfidence
        }
        val requiredConfidence = if (activeAiPlan != null) {
            maxOf(baseRequiredConfidence, activeAiPlan.minRequiredConfidence)
        } else {
            baseRequiredConfidence
        }

        if (result.direction != SignalDirection.CALL_UP && result.direction != SignalDirection.PUT_DOWN) {
            cancelPendingCandleClose()
            _uiState.update {
                it.copy(
                    lastAutoClickSummary = "Aguardando 3 velas + RSI (${state.boConfig.sensitivity.label} ≥ $requiredConfidence%): ${result.patternLabel}"
                )
            }
            return
        }

        if (result.confidencePercent < requiredConfidence) {
            val shieldNote = when {
                activeAiPlan != null ->
                    "Recuperação IA exige tendência forte ≥ $requiredConfidence% antes de recuperar (atual ${result.confidencePercent}%)"
                state.consecutiveLosses >= 2 ->
                    "Escudo pós-${state.consecutiveLosses} perdas ativo: exige ≥ $requiredConfidence% (atual ${result.confidencePercent}%)"
                else ->
                    "Filtro ${state.boConfig.sensitivity.label} bloqueou entrada fraca (${result.confidencePercent}% < $requiredConfidence%)"
            }
            _uiState.update { it.copy(lastAutoClickSummary = shieldNote) }
            return
        }

        val nowMs = android.os.SystemClock.elapsedRealtime()
        // Anti-whipsaw lock: block sudden direction flip (CALL <-> PUT) within 25s unless trend is solid (>= 72%)
        val prevDir = lastDirectionalSignal
        val flippedAbruptly = prevDir != null &&
            prevDir != SignalDirection.NEUTRAL &&
            prevDir != result.direction &&
            (nowMs - lastDirectionalSignalAtMs) in 0L..25_000L
        if (flippedAbruptly && result.confidencePercent < 72) {
            lastDirectionalSignal = result.direction
            lastDirectionalSignalAtMs = nowMs
            _uiState.update {
                it.copy(
                    lastAutoClickSummary = "Bloqueado: oscilação rápida (${result.confidencePercent}% < 72%) — aguardando confirmar direção"
                )
            }
            return
        }
        lastDirectionalSignal = result.direction
        lastDirectionalSignalAtMs = nowMs

        // Prevent overlapping double trades while the current expiry candle is still open
        val expirySec = state.boConfig.expirySeconds.coerceAtLeast(30)
        if (state.pendingTradeDirection != null && state.pendingTradeOpenedAtMs > 0L) {
            val openElapsedMs = nowEpochMs - state.pendingTradeOpenedAtMs
            if (openElapsedMs in 0 until ((expirySec + 3) * 1000L)) {
                _uiState.update {
                    it.copy(lastAutoClickSummary = "Operação aberta em andamento — aguardando encerramento da vela")
                }
                return
            }
        }

        val baseCooldownSec = maxOf(
            minOf(state.autoClickCooldownSec, state.autoScanIntervalSec.coerceAtLeast(15)),
            expirySec
        )
        // Respect AI recovery stabilization pause or post-loss shield
        val shieldExtraSec = when {
            activeAiPlan != null && activeAiPlan.extraWaitSeconds > 0 -> activeAiPlan.extraWaitSeconds
            state.consecutiveLosses >= 2 -> expirySec
            else -> 0
        }
        val effectiveCooldownMs = (baseCooldownSec + shieldExtraSec).coerceAtLeast(25) * 1000L
        if (lastAutoClickAtMs != 0L && (nowMs - lastAutoClickAtMs) < effectiveCooldownMs) {
            if (state.consecutiveLosses >= 2) {
                _uiState.update {
                    it.copy(lastAutoClickSummary = "Escudo anti-sequência: aguardando 1 vela extra para estabilizar mercado")
                }
            }
            return
        }

        // Rule 3: Candle-Close Confirmation — NEVER enter mid-candle; wait for candle close!
        val secondsToClose = computeSecondsUntilCandleClose(nowEpochMs, expirySec)
        val mustWaitForCandleClose = state.boConfig.waitForCandleClose &&
            (secondsToClose > 0 || !result.candleCloseConfirmed)

        if (mustWaitForCandleClose) {
            val waitSeconds = if (secondsToClose > 0) secondsToClose else 2
            val dirWord = if (result.direction == SignalDirection.CALL_UP) "COMPRA (Acima)" else "VENDA (Abaixo)"
            pendingCandleCloseJob?.cancel()
            _uiState.update {
                it.copy(
                    pendingCandleCloseDirection = result.direction,
                    pendingCandleCloseRemainingSec = waitSeconds,
                    lastAutoClickSummary = "Aguardando fechamento do candle (${waitSeconds}s) para entrar em $dirWord — nunca entra no meio"
                )
            }
            pendingCandleCloseJob = viewModelScope.launch {
                var remaining = waitSeconds
                while (remaining > 0) {
                    delay(1_000L)
                    remaining--
                    val live = _uiState.value
                    val liveEpochMs = clockEpochMs()
                    if (!live.autoClickEnabled ||
                        live.sourceMode != CaptureSourceMode.WEBVIEW ||
                        live.isSessionStopLossHit ||
                        live.lossPauseUntilEpochMs > liveEpochMs ||
                        live.calibratingButton != null ||
                        live.calibratingAmountField ||
                        live.isMarkingArea
                    ) {
                        _uiState.update {
                            it.copy(
                                pendingCandleCloseDirection = null,
                                pendingCandleCloseRemainingSec = 0
                            )
                        }
                        return@launch
                    }
                    if (remaining > 0) {
                        _uiState.update {
                            it.copy(
                                pendingCandleCloseRemainingSec = remaining,
                                lastAutoClickSummary = "Aguardando fechamento do candle (${remaining}s) para entrar em $dirWord"
                            )
                        }
                    }
                }
                _uiState.update {
                    it.copy(
                        pendingCandleCloseDirection = null,
                        pendingCandleCloseRemainingSec = 0
                    )
                }
                dispatchAutoClickCommand(result)
            }
            return
        }

        dispatchAutoClickCommand(result)
    }

    private fun dispatchAutoClickCommand(result: ChartAnalysisResult) {
        val state = _uiState.value
        val nowEpochMs = clockEpochMs()
        if (!state.autoClickEnabled ||
            state.sourceMode != CaptureSourceMode.WEBVIEW ||
            state.isSessionStopLossHit ||
            state.lossPauseUntilEpochMs > nowEpochMs
        ) {
            return
        }
        lastAutoClickAtMs = android.os.SystemClock.elapsedRealtime()
        val calibratedPoint = if (result.direction == SignalDirection.CALL_UP) {
            state.calibratedCallPoint ?: state.autoDetectedCallPoint
        } else {
            state.calibratedPutPoint ?: state.autoDetectedPutPoint
        }
        val hasPending = state.martingaleConfig.enabled &&
            state.pendingTradeDirection != null &&
            state.pendingTradeBalanceBefore != null
        val targetExpiryToSync = (result.recommendedExpirySeconds ?: state.boConfig.expirySeconds)
            .takeIf { it in ADAPTIVE_EXPIRY_OPTIONS_SEC }
        _autoClickTrigger.tryEmit(
            AutoClickCommand(
                direction = result.direction,
                calibratedPoint = calibratedPoint,
                calibratedAmountPoint = state.calibratedAmountPoint ?: state.autoDetectedAmountPoint,
                checkPendingBalanceFirst = hasPending,
                stakeAmount = if (state.martingaleConfig.enabled) state.currentBotStake else null,
                martingaleStep = state.currentMartingaleStep,
                expirySecondsToApply = targetExpiryToSync
            )
        )
    }

    private fun triggerImmediateCapture(area: NormalizedRect) {
        _captureTrigger.tryEmit(area)
    }

    private fun startAutoScanTicker() {
        autoScanJob?.cancel()
        autoScanJob = viewModelScope.launch {
            while (true) {
                delay(1_000L)
                val state = _uiState.value
                val area = state.markedArea
                if (!state.autoScanEnabled || area == null || state.isMarkingArea ||
                    state.isRobotConfigOpen || state.calibratingButton != null || state.calibratingAmountField ||
                    state.sourceMode == CaptureSourceMode.GALLERY || state.isWebLoading || state.webError != null
                ) {
                    continue
                }
                // Conserve quota while Session Stop Loss, 5-min loss pause, or candle-close countdown is active
                val nowEpochMs = clockEpochMs()
                if (state.isSessionStopLossHit ||
                    state.lossPauseUntilEpochMs > nowEpochMs ||
                    state.pendingCandleCloseDirection != null
                ) {
                    continue
                }
                // Conserve Gemini quota while a trade is actively open and awaiting expiry
                if (state.pendingTradeDirection != null && state.pendingTradeOpenedAtMs > 0L) {
                    val expiryWindowMs = (state.boConfig.expirySeconds.coerceAtLeast(30) + 3) * 1_000L
                    val openElapsedMs = nowEpochMs - state.pendingTradeOpenedAtMs
                    if (openElapsedMs in 0L until expiryWindowMs) {
                        continue
                    }
                }
                if (analysisJob?.isActive == true) {
                    continue
                }
                val nextCount = state.countdownSec - 1
                if (nextCount <= 0) {
                    _uiState.update { it.copy(countdownSec = it.autoScanIntervalSec.coerceAtLeast(FAST_TREND_HUNT_INTERVAL_SEC)) }
                    triggerImmediateCapture(area)
                } else {
                    _uiState.update { it.copy(countdownSec = nextCount) }
                }
            }
        }
    }

    private fun stopAutoScanTicker() {
        autoScanJob?.cancel()
        autoScanJob = null
    }

    private fun extractDisplayHost(url: String): String {
        return try {
            val uri = android.net.Uri.parse(url)
            val host = uri.host?.removePrefix("www.")
            if (!host.isNullOrBlank()) host else "iqoption.com"
        } catch (_: Exception) {
            "iqoption.com"
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopAutoScanTicker()
        pendingOutcomeCheckJob?.cancel()
        recoveryPlanJob?.cancel()
        analysisJob?.cancel()
    }
}
