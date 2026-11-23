package com.example.ui

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.audio.TrendVoiceSpeaker
import com.example.data.BrokerSessionPrefs
import com.example.data.SecureBrokerSessionManager
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.model.AnalysisUiState
import com.example.model.CaptureSourceMode
import com.example.model.NormalizedRect
import com.example.model.SignalDirection
import com.example.model.TradeOutcome
import com.example.model.formatStakeAmount
import com.example.service.RobotBackgroundService
import com.example.ui.components.AnalysisBottomPanel
import com.example.ui.components.AppQrCodeDialog
import com.example.ui.components.AreaSelectionOverlay
import com.example.ui.components.BotPoint
import com.example.ui.components.HelpDialog
import com.example.ui.components.IqOptionWebViewContainer
import com.example.ui.components.RobotConfigOverlay
import com.example.ui.components.WebViewLifecycleObserver
import com.example.ui.components.applyWebViewExpirySeconds
import com.example.ui.components.applyWebViewStakeAmount
import com.example.ui.components.ChartBoundsDetection
import com.example.ui.components.captureWebViewRegion
import com.example.ui.components.cropBitmapRegion
import com.example.ui.components.cropFittedBitmapRegion
import com.example.ui.components.decodeSampledBitmapFromUri
import com.example.ui.components.defaultChartRegionOnBitmap
import com.example.ui.components.detectTradeButtonFromBottomBitmap
import com.example.ui.components.detectWebViewChartBounds
import com.example.ui.components.executeWebViewTradeButtonClick
import com.example.ui.components.findActivity
import com.example.ui.components.mapBitmapRectToViewport
import com.example.ui.components.openIqOptionApp
import com.example.ui.components.openOwnerPhoneDialer
import com.example.ui.components.openUrlInExternalChrome
import com.example.ui.components.readWebViewBrokerBalance
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BearishRed
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.IqOptionOrange
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TerminalBorder
import com.example.ui.theme.TerminalSurface
import com.example.ui.theme.TerminalSurfaceElevated
import com.example.ui.theme.TerminalToolbar
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

@Composable
fun TrendLensScreen(
    sharedImageUri: Uri? = null,
    onSharedImageConsumed: () -> Unit = {},
    isInPipMode: Boolean = false,
    onKeepBackgroundRunningChanged: (Boolean) -> Unit = {},
    onMinimizeToBackground: () -> Unit = {},
    viewModel: TrendLensViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var requestedNotifPermission by remember { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Service runs smoothly regardless; granting enables the persistent status notification */ }

    LaunchedEffect(uiState.keepRunningInBackground, uiState.autoScanEnabled) {
        val activeInBg = uiState.keepRunningInBackground && uiState.autoScanEnabled
        onKeepBackgroundRunningChanged(activeInBg)
        if (activeInBg && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !requestedNotifPermission) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestedNotifPermission = true
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val notifCountdownBucket = uiState.countdownSec / 10
    LaunchedEffect(
        uiState.keepRunningInBackground,
        uiState.autoScanEnabled,
        uiState.autoClickEnabled,
        uiState.analysisState,
        uiState.boConfig.expiryLabel,
        notifCountdownBucket
    ) {
        if (uiState.keepRunningInBackground && uiState.autoScanEnabled) {
            val botStateLabel = if (uiState.autoClickEnabled) "Robô ON" else "Somente IA"
            val title = "Trad do futuro • $botStateLabel (${uiState.boConfig.expiryLabel})"
            val subtitle = when (val st = uiState.analysisState) {
                is AnalysisUiState.Ready -> {
                    val dir = when (st.result.direction) {
                        SignalDirection.CALL_UP -> "ACIMA"
                        SignalDirection.PUT_DOWN -> "ABAIXO"
                        SignalDirection.NEUTRAL -> "AGUARDANDO"
                    }
                    "Último sinal: $dir ${st.result.confidencePercent}% • Próxima leitura em ~${formatCountdownMmSs(uiState.countdownSec)}"
                }
                is AnalysisUiState.Analyzing -> "IA analisando gráfico em segundo plano agora..."
                is AnalysisUiState.Error -> "Retomada automática em ~${formatCountdownMmSs(uiState.countdownSec)} • Só para quando você pausar"
                else -> "Monitorando gráfico em segundo plano • Só para quando você pausar"
            }
            RobotBackgroundService.startOrUpdate(
                context = context,
                title = title,
                subtitle = subtitle
            )
        } else {
            RobotBackgroundService.stop(context)
        }
    }

    val initialBrokerUrl = remember(context) {
        BrokerSessionPrefs.getInitialBrokerUrl(context)
    }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var showUrlPresetMenu by remember { mutableStateOf(false) }
    var showExitConfirmDialog by remember { mutableStateOf(false) }
    var showQrCodeDialog by remember { mutableStateOf(false) }
    var galleryViewportSize by remember { mutableStateOf(IntSize.Zero) }
    var isDesktopMode by remember(context) { mutableStateOf(BrokerSessionPrefs.isDesktopModeEnabled(context)) }
    var isBrokerBlockedOrChallenging by remember { mutableStateOf(false) }
    var hasEncryptedBrokerSession by remember(context) {
        mutableStateOf(SecureBrokerSessionManager.hasValidSavedSession(context))
    }
    var autoLoginStatus by remember(context) {
        mutableStateOf(SecureBrokerSessionManager.getAutoLoginStatus(context))
    }
    var openAutoLoginVaultInConfig by remember { mutableStateOf(false) }
    var autoLoginRetryNonce by remember { mutableIntStateOf(0) }

    val latestWebView by rememberUpdatedState(webViewRef)
    val latestSourceMode by rememberUpdatedState(uiState.sourceMode)
    val latestGalleryBitmap by rememberUpdatedState(uiState.selectedGalleryBitmap)
    val latestViewportSize by rememberUpdatedState(galleryViewportSize)
    val latestBrokerBlocked by rememberUpdatedState(isBrokerBlockedOrChallenging)
    val brokerSyncMutex = remember { Mutex() }
    var lastVisualBalanceOcrAtMs by remember { mutableLongStateOf(0L) }
    var lastSyncedExpirySec by remember { mutableStateOf<Int?>(null) }
    var lastSyncedExpiryAtMs by remember { mutableLongStateOf(0L) }
    var lastSyncedStakeAmount by remember { mutableStateOf<Double?>(null) }

    fun loadBrokerUrl(url: String) {
        viewModel.setSourceMode(CaptureSourceMode.WEBVIEW)
        viewModel.updateWebError(null)
        webViewRef?.let { wv ->
            wv.stopLoading()
            wv.loadUrl(url, BrokerSessionPrefs.BRAZIL_PT_HEADERS)
        }
    }

    fun openTraderoomFast() {
        loadBrokerUrl(TrendLensUiState.TRADEROOM_URL)
    }

    fun toggleDesktopBrowserMode() {
        val next = !isDesktopMode
        isDesktopMode = next
        BrokerSessionPrefs.setDesktopModeEnabled(context, next)
        viewModel.clearAutoDetectedTradePoints()
        android.widget.Toast.makeText(
            context,
            context.getString(if (next) R.string.broker_desktop_enabled_toast else R.string.broker_mobile_enabled_toast),
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    fun clearWafChallengeAndReload() {
        // Restore authentic Android Mobile Chrome UA aligned with Sec-CH-UA Client Hints
        if (isDesktopMode) {
            isDesktopMode = false
            BrokerSessionPrefs.setDesktopModeEnabled(context, false)
        }
        BrokerSessionPrefs.clearAntiBlockChallengeCookies(webView = webViewRef)
        val target = webViewRef?.url?.takeIf {
            it.startsWith("http") && !BrokerSessionPrefs.isSecurityChallengeOrAuthUrl(it)
        } ?: TrendLensUiState.TRADEROOM_URL
        loadBrokerUrl(target)
        android.widget.Toast.makeText(
            context,
            context.getString(R.string.broker_waf_cleared_toast),
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    fun launchIqOptionBrokerApp() {
        openIqOptionApp(context) {
            openTraderoomFast()
        }
    }

    LaunchedEffect(initialBrokerUrl) {
        if (initialBrokerUrl != uiState.currentUrl) {
            viewModel.updateWebLocation(initialBrokerUrl, canGoBack = false)
        }
    }

    // Restore persisted chart area on app startup so the chart is already framed automatically
    var hasLoadedSavedChartArea by remember { mutableStateOf(false) }
    LaunchedEffect(context) {
        val saved = BrokerSessionPrefs.loadSavedChartArea(context)
        if (saved != null) {
            viewModel.restoreSavedChartArea(saved.rect, saved.isAutoManaged)
        }
        hasLoadedSavedChartArea = true
    }

    // Persist active WebView chart area automatically whenever it is framed or redefined
    LaunchedEffect(hasLoadedSavedChartArea, uiState.sourceMode, uiState.markedArea, uiState.autoChartAreaEnabled) {
        if (hasLoadedSavedChartArea && uiState.sourceMode == CaptureSourceMode.WEBVIEW) {
            BrokerSessionPrefs.saveChartArea(context, uiState.markedArea, uiState.autoChartAreaEnabled)
        }
    }

    val voiceSpeaker = remember(context) { TrendVoiceSpeaker(context) }
    DisposableEffect(voiceSpeaker) {
        onDispose {
            voiceSpeaker.shutdown()
        }
    }

    LaunchedEffect(viewModel, voiceSpeaker) {
        viewModel.speechTrigger.collect { result ->
            val spokenHorizon = result.recommendedExpirySeconds ?: viewModel.uiState.value.boConfig.expirySeconds
            voiceSpeaker.speakAnalysis(result, spokenHorizon)
        }
    }

    LaunchedEffect(viewModel, voiceSpeaker) {
        RobotBackgroundService.pauseRequests.collect {
            viewModel.pauseAllMonitoring()
            voiceSpeaker.stop()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.applyExpiryTrigger.collect { targetExpirySec ->
            val webView = latestWebView
            if (webView != null && latestSourceMode == CaptureSourceMode.WEBVIEW && !latestBrokerBlocked) {
                brokerSyncMutex.withLock {
                    val now = SystemClock.elapsedRealtime()
                    val recentlySynced = lastSyncedExpirySec == targetExpirySec && (now - lastSyncedExpiryAtMs) in 0L..8_000L
                    if (!recentlySynced) {
                        val applied = applyExpiryToBroker(webView = webView, expirySeconds = targetExpirySec)
                        if (applied) {
                            lastSyncedExpirySec = targetExpirySec
                            lastSyncedExpiryAtMs = SystemClock.elapsedRealtime()
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.applyStakeTrigger.collect { targetStake ->
            val webView = latestWebView
            if (webView != null && latestSourceMode == CaptureSourceMode.WEBVIEW && !latestBrokerBlocked) {
                brokerSyncMutex.withLock {
                    val stateSnapshot = viewModel.uiState.value
                    val point = stateSnapshot.calibratedAmountPoint ?: stateSnapshot.autoDetectedAmountPoint
                    val applied = applyStakeToBrokerWithVisualFallback(
                        webView = webView,
                        viewModel = viewModel,
                        targetStake = targetStake,
                        calibratedOrCachedAmountPoint = point
                    )
                    if (applied) {
                        lastSyncedStakeAmount = targetStake
                    }
                }
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.autoClickTrigger.collect { command ->
            val webView = latestWebView
            if (webView != null && latestSourceMode == CaptureSourceMode.WEBVIEW && !latestBrokerBlocked) {
                brokerSyncMutex.withLock {
                    // Deduplicate expiry sync so we never re-open the expiry dropdown right before tapping CALL/PUT
                    if (command.expirySecondsToApply != null) {
                        val now = SystemClock.elapsedRealtime()
                        val alreadySynced = lastSyncedExpirySec == command.expirySecondsToApply &&
                            (now - lastSyncedExpiryAtMs) in 0L..8_000L
                        if (!alreadySynced) {
                            val applied = applyExpiryToBroker(
                                webView = webView,
                                expirySeconds = command.expirySecondsToApply
                            )
                            if (applied) {
                                lastSyncedExpirySec = command.expirySecondsToApply
                                lastSyncedExpiryAtMs = SystemClock.elapsedRealtime()
                            }
                        }
                    }

                    // Fast DOM-only pre-trade balance check (never stall 4.5s on visual OCR while candle is moving)
                    val liveBalance = if (command.checkPendingBalanceFirst || command.stakeAmount != null) {
                        readLiveBrokerBalance(
                            webView = webView,
                            viewModel = viewModel,
                            ocrTimeoutMs = 900L,
                            allowVisualOcr = false
                        )
                    } else {
                        null
                    }
                    val resolvedStake = if (command.checkPendingBalanceFirst) {
                        viewModel.resolvePendingTradeFromBalance(liveBalance)
                    } else {
                        command.stakeAmount
                    }
                    val amountPoint = command.calibratedAmountPoint
                        ?: viewModel.uiState.value.calibratedAmountPoint
                        ?: viewModel.uiState.value.autoDetectedAmountPoint

                    // Only mutate stake on the broker when stake actually changed (e.g. recovery/gale step),
                    // preventing IQ Option's numeric keypad modal from popping up over Acima/Abaixo on normal entries
                    val stakeNeedsChange = resolvedStake != null && resolvedStake > 0.0 && (
                        command.martingaleStep > 0 ||
                            (lastSyncedStakeAmount != null && kotlin.math.abs(resolvedStake - (lastSyncedStakeAmount ?: resolvedStake)) >= 0.01)
                        )
                    val stakeApplied = if (stakeNeedsChange && resolvedStake != null) {
                        val applied = applyStakeToBrokerWithVisualFallback(
                            webView = webView,
                            viewModel = viewModel,
                            targetStake = resolvedStake,
                            calibratedOrCachedAmountPoint = amountPoint
                        )
                        if (applied) {
                            lastSyncedStakeAmount = resolvedStake
                            kotlinx.coroutines.delay(180L)
                        }
                        applied
                    } else {
                        if (resolvedStake != null && lastSyncedStakeAmount == null) {
                            lastSyncedStakeAmount = resolvedStake
                        }
                        false
                    }

                    val initialResult = withTimeoutOrNull(4_500L) {
                        suspendCancellableCoroutine<com.example.ui.components.BotExecutionResult> { cont ->
                            executeWebViewTradeButtonClick(
                                webView = webView,
                                direction = command.direction,
                                calibratedPoint = command.calibratedPoint,
                                calibratedAmountPoint = amountPoint,
                                knownBalanceBefore = liveBalance,
                                stakeAmountToApply = if (stakeNeedsChange) resolvedStake else null,
                                stakePreApplied = stakeApplied || !stakeNeedsChange
                            ) { res ->
                                if (cont.isActive) cont.resume(res)
                            }
                        }
                    }

                    // Canvas fallback: when IQ Option PWA renders Acima/Abaixo on WebGL <canvas> without DOM buttons,
                    // automatically locate the solid green/red button visually (<5ms CPU scan) and tap it immediately
                    val finalResult = if (initialResult != null && !initialResult.success && command.calibratedPoint == null) {
                        val bottomBitmap = withTimeoutOrNull(1_800L) {
                            suspendCancellableCoroutine<android.graphics.Bitmap?> { cont ->
                                captureWebViewRegion(webView, NormalizedRect(0f, 0.65f, 1f, 1f)) { bmp ->
                                    if (cont.isActive) cont.resume(bmp)
                                }
                            }
                        }
                        val visualBtnPoint = if (bottomBitmap != null) {
                            try {
                                detectTradeButtonFromBottomBitmap(
                                    bottomBitmap = bottomBitmap,
                                    direction = command.direction,
                                    regionTopNorm = 0.65f
                                )
                            } finally {
                                if (!bottomBitmap.isRecycled) bottomBitmap.recycle()
                            }
                        } else {
                            null
                        }

                        if (visualBtnPoint != null) {
                            viewModel.cacheAutoDetectedTradePoint(command.direction, visualBtnPoint)
                            withTimeoutOrNull(3_000L) {
                                suspendCancellableCoroutine<com.example.ui.components.BotExecutionResult> { cont ->
                                    executeWebViewTradeButtonClick(
                                        webView = webView,
                                        direction = command.direction,
                                        calibratedPoint = visualBtnPoint,
                                        calibratedAmountPoint = amountPoint,
                                        knownBalanceBefore = liveBalance,
                                        stakeAmountToApply = null,
                                        stakePreApplied = true
                                    ) { res ->
                                        val autoDetail = if (res.success) {
                                            val btnWord = if (command.direction == SignalDirection.CALL_UP) "Acima (CALL)" else "Abaixo (PUT)"
                                            "Robô clicou em $btnWord (auto-detectado)"
                                        } else {
                                            res.detail
                                        }
                                        if (cont.isActive) {
                                            cont.resume(res.copy(detail = autoDetail, stakeAppliedInDom = stakeApplied || res.stakeAppliedInDom))
                                        }
                                    }
                                }
                            } ?: initialResult
                        } else {
                            initialResult
                        }
                    } else {
                        initialResult
                    }

                    if (finalResult != null) {
                        viewModel.onAutoClickExecuted(command.direction, finalResult)
                    }
                }
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.balanceCheckTrigger.collect { allowOcr ->
            val webView = latestWebView
            if (webView != null && latestSourceMode == CaptureSourceMode.WEBVIEW) {
                val now = SystemClock.elapsedRealtime()
                val allowVisualOcr = allowOcr && (now - lastVisualBalanceOcrAtMs) >= 45_000L
                val liveBalance = readLiveBrokerBalance(
                    webView = webView,
                    viewModel = viewModel,
                    ocrTimeoutMs = 4_500L,
                    allowVisualOcr = allowVisualOcr,
                    onVisualOcrAttempted = { lastVisualBalanceOcrAtMs = SystemClock.elapsedRealtime() }
                )
                viewModel.onBrokerBalanceObserved(liveBalance)
            }
        }
    }

    WebViewLifecycleObserver(webViewRef)

    fun handleSelectedImageUri(uri: Uri?) {
        if (uri == null) return
        coroutineScope.launch {
            val decodedResult = withContext(Dispatchers.IO) {
                val bitmap = decodeSampledBitmapFromUri(context, uri) ?: return@withContext null
                val bitmapRegion = defaultChartRegionOnBitmap(bitmap)
                val cropped = cropBitmapRegion(bitmap, bitmapRegion)
                Triple(bitmap, bitmapRegion, cropped)
            }
            if (decodedResult != null) {
                val (bitmap, bitmapRegion, cropped) = decodedResult
                val measuredViewport = if (latestViewportSize.width > 0 && latestViewportSize.height > 0) {
                    latestViewportSize
                } else {
                    withTimeoutOrNull(800L) {
                        snapshotFlow { latestViewportSize }.first { it.width > 0 && it.height > 0 }
                    } ?: latestViewportSize
                }
                val viewportArea = mapBitmapRectToViewport(
                    source = bitmap,
                    viewportWidth = measuredViewport.width,
                    viewportHeight = measuredViewport.height,
                    bitmapArea = bitmapRegion
                )
                viewModel.onGalleryBitmapSelected(
                    bitmap = bitmap,
                    autoViewportArea = viewportArea,
                    autoCroppedRegion = cropped
                )
            } else {
                viewModel.setSourceMode(CaptureSourceMode.GALLERY)
                viewModel.onCaptureFailed("Não foi possível abrir a imagem selecionada.")
            }
        }
    }

    LaunchedEffect(sharedImageUri) {
        if (sharedImageUri != null) {
            handleSelectedImageUri(sharedImageUri)
            onSharedImageConsumed()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> handleSelectedImageUri(uri) }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> handleSelectedImageUri(uri) }

    val fallbackGetContentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> handleSelectedImageUri(uri) }

    fun launchDocumentImagePicker() {
        val opened = runCatching {
            openDocumentLauncher.launch(arrayOf("image/*"))
            true
        }.getOrDefault(false)

        if (!opened) {
            runCatching {
                fallbackGetContentLauncher.launch("image/*")
            }.onFailure {
                viewModel.onCaptureFailed("Nenhum seletor de imagens disponível neste dispositivo.")
            }
        }
    }

    fun launchGalleryPicker() {
        val launched = runCatching {
            if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)) {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
                true
            } else {
                false
            }
        }.getOrDefault(false)

        if (!launched) {
            launchDocumentImagePicker()
        }
    }

    val isChartAreaUnmarked = uiState.markedArea == null
    LaunchedEffect(
        uiState.sourceMode,
        uiState.isWebLoading,
        uiState.webError,
        isChartAreaUnmarked,
        uiState.autoChartAreaEnabled,
        uiState.isMarkingArea,
        uiState.currentUrl,
        isDesktopMode,
        galleryViewportSize,
        isBrokerBlockedOrChallenging,
        webViewRef
    ) {
        val webView = webViewRef ?: return@LaunchedEffect
        val needsInitialFrame = uiState.markedArea == null
        val canAutoReframe = uiState.autoChartAreaEnabled
        if (uiState.sourceMode != CaptureSourceMode.WEBVIEW ||
            uiState.isWebLoading ||
            uiState.webError != null ||
            uiState.isMarkingArea ||
            isBrokerBlockedOrChallenging ||
            (!needsInitialFrame && !canAutoReframe) ||
            BrokerSessionPrefs.isSecurityChallengeOrAuthUrl(uiState.currentUrl) ||
            !BrokerSessionPrefs.isTradingWorkspaceUrl(uiState.currentUrl)
        ) {
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(if (needsInitialFrame) 1_200L else 1_000L)
        val attempts = if (needsInitialFrame) 30 else 2
        repeat(attempts) {
            val currentState = viewModel.uiState.value
            if (currentState.sourceMode != CaptureSourceMode.WEBVIEW ||
                currentState.isMarkingArea ||
                latestBrokerBlocked ||
                !BrokerSessionPrefs.isTradingWorkspaceUrl(currentState.currentUrl)
            ) {
                return@LaunchedEffect
            }
            val detection = withTimeoutOrNull(1_200L) {
                suspendCancellableCoroutine<ChartBoundsDetection> { cont ->
                    detectWebViewChartBounds(webView) { result ->
                        if (cont.isActive) cont.resume(result)
                    }
                }
            } ?: ChartBoundsDetection(ready = false)
            if (detection.ready) {
                viewModel.applyAutoDetectedChartArea(detection.bounds)
                if (currentState.markedArea == null || detection.bounds != null) {
                    return@LaunchedEffect
                }
            }
            kotlinx.coroutines.delay(if (needsInitialFrame) 3_500L else 2_000L)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.captureTrigger.collect { area ->
            if (latestSourceMode == CaptureSourceMode.WEBVIEW) {
                val webView = latestWebView
                val currentWebUrl = webView?.url ?: viewModel.uiState.value.currentUrl
                if (webView != null &&
                    !latestBrokerBlocked &&
                    !BrokerSessionPrefs.isSecurityChallengeOrAuthUrl(currentWebUrl)
                ) {
                    // Ensure expanded analysis drawer & scrim finish animating out before PixelCopy captures the WebView chart
                    if (viewModel.uiState.value.isAnalysisExpanded) {
                        viewModel.setAnalysisExpanded(false)
                        kotlinx.coroutines.delay(320L)
                    } else {
                        kotlinx.coroutines.delay(80L)
                    }
                    captureWebViewRegion(webView, area) { captured ->
                        if (captured != null) {
                            viewModel.onRegionBitmapCaptured(captured, force = true)
                        } else {
                            viewModel.onCaptureFailed("O gráfico ainda está renderizando na tela. Aguarde um instante e toque em Analisar.")
                        }
                    }
                } else {
                    viewModel.onCaptureFailed("Navegador ainda carregando o gráfico. Tente novamente em instantes.")
                }
            } else {
                val galleryBmp = latestGalleryBitmap
                if (galleryBmp != null) {
                    val cropped = cropFittedBitmapRegion(
                        source = galleryBmp,
                        viewportWidth = latestViewportSize.width,
                        viewportHeight = latestViewportSize.height,
                        area = area
                    )
                    if (cropped != null) {
                        viewModel.onRegionBitmapCaptured(cropped, force = true)
                    } else {
                        viewModel.onCaptureFailed("Marque uma região dentro da imagem carregada.")
                    }
                } else {
                    viewModel.onCaptureFailed("Selecione um print do gráfico na galeria primeiro.")
                }
            }
        }
    }

    BackHandler(enabled = true) {
        when {
            showExitConfirmDialog -> showExitConfirmDialog = false
            showQrCodeDialog -> showQrCodeDialog = false
            uiState.isRobotConfigOpen -> viewModel.setRobotConfigOpen(false)
            uiState.isHelpDialogVisible -> viewModel.setHelpDialogVisible(false)
            uiState.isAnalysisExpanded -> viewModel.setAnalysisExpanded(false)
            uiState.calibratingButton != null || uiState.calibratingAmountField -> viewModel.cancelButtonCalibration()
            uiState.isMarkingArea -> viewModel.cancelMarkingArea()
            uiState.sourceMode == CaptureSourceMode.WEBVIEW && webViewRef?.canGoBack() == true -> {
                webViewRef?.goBack()
            }
            else -> showExitConfirmDialog = true
        }
    }

    Scaffold(
        containerColor = ObsidianBg,
        contentWindowInsets = WindowInsets.displayCutout
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = if (isInPipMode) 0.dp else 8.dp, vertical = if (isInPipMode) 0.dp else 6.dp),
                verticalArrangement = Arrangement.spacedBy(if (isInPipMode) 0.dp else 6.dp)
            ) {
                if (!isInPipMode) {
                    // 1. Top Brand & Mode Header
                    TrendLensTopHeader(
                        sourceMode = uiState.sourceMode,
                        voiceEnabled = uiState.voiceEnabled,
                        onToggleVoice = {
                            val next = !uiState.voiceEnabled
                            viewModel.toggleVoiceEnabled(next)
                            if (!next) voiceSpeaker.stop()
                        },
                        onOpenRobotConfig = { viewModel.setRobotConfigOpen(true) },
                        onHelpClick = { viewModel.setHelpDialogVisible(true) },
                        onPhoneClick = { openOwnerPhoneDialer(context) },
                        onSelectMode = { mode -> viewModel.setSourceMode(mode) }
                    )

                    // 2. Section Label + Area Status Indicator + Quick Robot Pill
                    SectionStatusRow(
                        isMarkingArea = uiState.isMarkingArea,
                        hasMarkedArea = uiState.markedArea != null,
                        autoScanEnabled = uiState.autoScanEnabled,
                        countdownSec = uiState.countdownSec,
                        autoClickEnabled = uiState.autoClickEnabled,
                        martingaleEnabled = uiState.martingaleConfig.enabled,
                        martingaleStep = uiState.currentMartingaleStep,
                        currentBotStake = uiState.currentBotStake,
                        sourceMode = uiState.sourceMode,
                        onToggleAutoClick = { viewModel.toggleAutoClick(!uiState.autoClickEnabled) },
                        onStartChartReading = { viewModel.resumeAiAndScanNow() },
                        onPauseMonitoring = {
                            viewModel.pauseAllMonitoring()
                            voiceSpeaker.stop()
                        },
                        onOpenQrCode = { showQrCodeDialog = true }
                    )
                }

                // 3. Main Viewport Card (WebView IQ Option or Gallery Image + Crop Overlay)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(if (isInPipMode) 0.dp else 20.dp))
                        .background(TerminalSurface)
                        .then(
                            if (isInPipMode) Modifier
                            else Modifier.border(1.dp, TerminalBorder, RoundedCornerShape(20.dp))
                        )
                ) {
                    if (!isInPipMode) {
                        // Viewport Toolbar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(TerminalToolbar)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                        if (uiState.sourceMode == CaptureSourceMode.WEBVIEW && uiState.canGoBack) {
                            IconButton(
                                onClick = { webViewRef?.goBack() },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("webview_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = stringResource(R.string.cd_back),
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Host / Source Pill with quick preset menu in WebView mode
                        Box(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        if (uiState.sourceMode == CaptureSourceMode.WEBVIEW) {
                                            showUrlPresetMenu = true
                                        } else {
                                            launchGalleryPicker()
                                        }
                                    }
                                    .padding(horizontal = 6.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (uiState.sourceMode == CaptureSourceMode.WEBVIEW) {
                                        Icons.Rounded.Language
                                    } else {
                                        Icons.Outlined.Image
                                    },
                                    contentDescription = null,
                                    tint = if (uiState.sourceMode == CaptureSourceMode.WEBVIEW) {
                                        IqOptionOrange
                                    } else {
                                        EmeraldBright
                                    },
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                if (uiState.sourceMode == CaptureSourceMode.WEBVIEW) {
                                    Box(
                                        modifier = Modifier
                                            .background(EmeraldContainer, RoundedCornerShape(5.dp))
                                            .border(0.75.dp, EmeraldBright.copy(alpha = 0.6f), RoundedCornerShape(5.dp))
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                            .testTag("brazil_mode_badge")
                                    ) {
                                        Text(
                                            text = "BR",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = EmeraldMint,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = if (uiState.sourceMode == CaptureSourceMode.WEBVIEW) {
                                        uiState.displayHost
                                    } else {
                                        "Print da galeria"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            DropdownMenu(
                                expanded = showUrlPresetMenu,
                                onDismissRequest = { showUrlPresetMenu = false },
                                containerColor = TerminalSurfaceElevated
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(R.string.broker_menu_traderoom_pwa),
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Language,
                                            contentDescription = null,
                                            tint = IqOptionOrange,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = {
                                        showUrlPresetMenu = false
                                        openTraderoomFast()
                                    },
                                    modifier = Modifier.testTag("iq_traderoom_menu_item")
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(R.string.broker_menu_traderoom_web),
                                            color = TextPrimary
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Language,
                                            contentDescription = null,
                                            tint = EmeraldBright,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = {
                                        showUrlPresetMenu = false
                                        loadBrokerUrl(BrokerSessionPrefs.TRADEROOM_WEB_URL)
                                    },
                                    modifier = Modifier.testTag("iq_traderoom_web_menu_item")
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(R.string.broker_menu_login_br),
                                            color = TextPrimary
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Login,
                                            contentDescription = null,
                                            tint = EmeraldMint,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = {
                                        showUrlPresetMenu = false
                                        loadBrokerUrl(BrokerSessionPrefs.LOGIN_BR_URL)
                                    },
                                    modifier = Modifier.testTag("iq_login_br_menu_item")
                                )
                                HorizontalDivider(color = TerminalBorder)
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(
                                                if (isDesktopMode) R.string.broker_menu_mobile_mode
                                                else R.string.broker_menu_desktop_mode
                                            ),
                                            color = EmeraldBright,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.DesktopWindows,
                                            contentDescription = null,
                                            tint = EmeraldBright,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = {
                                        showUrlPresetMenu = false
                                        toggleDesktopBrowserMode()
                                    },
                                    modifier = Modifier.testTag("broker_desktop_mode_menu_item")
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(
                                                if (hasEncryptedBrokerSession) R.string.broker_session_encrypted_active
                                                else R.string.broker_session_not_saved
                                            ),
                                            color = if (hasEncryptedBrokerSession) EmeraldBright else TextSecondary,
                                            fontWeight = if (hasEncryptedBrokerSession) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (hasEncryptedBrokerSession) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                                            contentDescription = null,
                                            tint = if (hasEncryptedBrokerSession) EmeraldBright else TextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    enabled = false,
                                    onClick = {},
                                    modifier = Modifier.testTag("broker_encrypted_session_status_item")
                                )
                                DropdownMenuItem(
                                    text = {
                                        val suffix = when {
                                            autoLoginStatus.enabled -> " (ON)"
                                            autoLoginStatus.hasCredentials -> " (Pausado)"
                                            else -> ""
                                        }
                                        Text(
                                            text = stringResource(R.string.broker_menu_auto_login) + suffix,
                                            color = if (autoLoginStatus.enabled) EmeraldBright else TextPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Lock,
                                            contentDescription = null,
                                            tint = if (autoLoginStatus.enabled) EmeraldBright else EmeraldMint,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = {
                                        showUrlPresetMenu = false
                                        openAutoLoginVaultInConfig = true
                                        viewModel.setRobotConfigOpen(true)
                                    },
                                    modifier = Modifier.testTag("broker_auto_login_vault_menu_item")
                                )
                                if (hasEncryptedBrokerSession) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = stringResource(R.string.broker_session_clear_action),
                                                color = AmberWarning,
                                                fontWeight = FontWeight.Medium
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.Logout,
                                                contentDescription = null,
                                                tint = AmberWarning,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
                                        onClick = {
                                            showUrlPresetMenu = false
                                            hasEncryptedBrokerSession = false
                                            SecureBrokerSessionManager.setAutoLoginEnabled(context, false)
                                            autoLoginStatus = SecureBrokerSessionManager.getAutoLoginStatus(context)
                                            val targetWebView = webViewRef
                                            SecureBrokerSessionManager.clearSession(
                                                context = context,
                                                clearWebViewCookies = true
                                            ) {
                                                targetWebView?.post {
                                                    loadBrokerUrl(BrokerSessionPrefs.LOGIN_BR_URL)
                                                }
                                            }
                                            android.widget.Toast.makeText(
                                                context,
                                                context.getString(R.string.broker_session_cleared_toast),
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        modifier = Modifier.testTag("broker_clear_saved_session_menu_item")
                                    )
                                }
                                HorizontalDivider(color = TerminalBorder)
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(R.string.broker_menu_clear_waf),
                                            color = AmberWarning,
                                            fontWeight = FontWeight.Medium
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Security,
                                            contentDescription = null,
                                            tint = AmberWarning,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = {
                                        showUrlPresetMenu = false
                                        clearWafChallengeAndReload()
                                    },
                                    modifier = Modifier.testTag("broker_clear_waf_menu_item")
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(R.string.broker_menu_open_chrome),
                                            color = TextPrimary
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                            contentDescription = null,
                                            tint = IqOptionOrange,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = {
                                        showUrlPresetMenu = false
                                        openUrlInExternalChrome(
                                            context,
                                            webViewRef?.url?.takeIf { it.startsWith("http") } ?: TrendLensUiState.TRADEROOM_URL
                                        )
                                    },
                                    modifier = Modifier.testTag("broker_open_chrome_menu_item")
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(R.string.action_open_iq_app),
                                            color = TextSecondary
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                            contentDescription = null,
                                            tint = TextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = {
                                        showUrlPresetMenu = false
                                        launchIqOptionBrokerApp()
                                    },
                                    modifier = Modifier.testTag("broker_open_iq_app_menu_item")
                                )
                            }
                        }

                        // Clear area button if marked
                        if (uiState.markedArea != null && !uiState.isMarkingArea) {
                            IconButton(
                                onClick = { viewModel.clearMarkedArea() },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("clear_area_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.action_clear_area),
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // "Marcar área" action chip
                        val canMark = uiState.sourceMode == CaptureSourceMode.WEBVIEW ||
                            uiState.selectedGalleryBitmap != null
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(enabled = canMark) {
                                    if (uiState.isMarkingArea) {
                                        viewModel.cancelMarkingArea()
                                    } else {
                                        viewModel.startMarkingArea()
                                    }
                                }
                                .testTag("mark_area_button")
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (uiState.markedArea != null && uiState.autoChartAreaEnabled && !uiState.isMarkingArea) {
                                    Icons.Rounded.AutoFixHigh
                                } else {
                                    Icons.Rounded.Crop
                                },
                                contentDescription = null,
                                tint = if (canMark) EmeraldBright else TextMuted,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when {
                                    uiState.isMarkingArea -> "Marcando..."
                                    uiState.markedArea != null && uiState.autoChartAreaEnabled ->
                                        stringResource(R.string.action_area_auto_badge)
                                    uiState.markedArea != null -> stringResource(R.string.action_redefine_area)
                                    else -> stringResource(R.string.action_mark_area)
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = if (canMark) EmeraldBright else TextMuted
                            )
                        }

                        // Fast shortcut to open https://iqoption.com/pwa/traderoom immediately
                        IconButton(
                            onClick = { openTraderoomFast() },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("open_iq_app_toolbar_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = stringResource(R.string.cd_open_traderoom_fast),
                                tint = IqOptionOrange,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        // Refresh WebView / Pick Image button
                        IconButton(
                            onClick = {
                                if (uiState.sourceMode == CaptureSourceMode.WEBVIEW) {
                                    viewModel.updateWebError(null)
                                    val currentWebUrl = webViewRef?.url.orEmpty()
                                    if (currentWebUrl.isBlank() ||
                                        !BrokerSessionPrefs.isTradingWorkspaceUrl(currentWebUrl)
                                    ) {
                                        openTraderoomFast()
                                    } else {
                                        webViewRef?.reload()
                                    }
                                } else {
                                    launchGalleryPicker()
                                }
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("refresh_button")
                        ) {
                            Icon(
                                imageVector = if (uiState.sourceMode == CaptureSourceMode.WEBVIEW) {
                                    Icons.Rounded.Refresh
                                } else {
                                    Icons.Rounded.AddPhotoAlternate
                                },
                                contentDescription = stringResource(R.string.cd_refresh),
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = TerminalBorder)
                    }

                    // Viewport Content Area + Selection Overlay
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .onSizeChanged { galleryViewportSize = it }
                    ) {
                        // Keep WebView mounted across mode switches so the broker session stays logged in
                        IqOptionWebViewContainer(
                            initialUrl = initialBrokerUrl,
                            isVisible = uiState.sourceMode == CaptureSourceMode.WEBVIEW,
                            isLoading = uiState.isWebLoading,
                            progress = uiState.webProgress,
                            errorMessage = uiState.webError,
                            isDesktopMode = isDesktopMode,
                            autoLoginStatus = autoLoginStatus,
                            autoLoginRetryNonce = autoLoginRetryNonce,
                            onWebViewReady = { webView -> webViewRef = webView },
                            onProgressChanged = { progress, loading ->
                                viewModel.updateWebLoading(progress, loading)
                            },
                            onLocationChanged = { url, canGoBack ->
                                viewModel.updateWebLocation(url, canGoBack)
                            },
                            onError = { err -> viewModel.updateWebError(err) },
                            onRetry = {
                                viewModel.updateWebError(null)
                                val wv = webViewRef
                                val currentUrl = wv?.url.orEmpty()
                                if (wv != null) {
                                    if (currentUrl.isBlank() ||
                                        !BrokerSessionPrefs.isTradingWorkspaceUrl(currentUrl)
                                    ) {
                                        openTraderoomFast()
                                    } else {
                                        wv.reload()
                                    }
                                }
                            },
                            onOpenBrokerApp = { openTraderoomFast() },
                            onToggleDesktopMode = { toggleDesktopBrowserMode() },
                            onClearWafCookies = { clearWafChallengeAndReload() },
                            onOpenExternalChrome = {
                                openUrlInExternalChrome(
                                    context,
                                    webViewRef?.url?.takeIf { it.startsWith("http") } ?: TrendLensUiState.TRADEROOM_URL
                                )
                            },
                            onSwitchBrokerUrl = { url -> loadBrokerUrl(url) },
                            onBrokerBlockStateChanged = { blocked ->
                                isBrokerBlockedOrChallenging = blocked
                            },
                            onSessionStateChanged = { saved ->
                                hasEncryptedBrokerSession = saved
                            },
                            onOpenAutoLoginConfig = {
                                openAutoLoginVaultInConfig = true
                                viewModel.setRobotConfigOpen(true)
                            },
                            onAutoLoginVaultUpdated = {
                                autoLoginStatus = SecureBrokerSessionManager.getAutoLoginStatus(context)
                                android.widget.Toast.makeText(
                                    context,
                                    context.getString(R.string.auto_login_captured_toast),
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        if (uiState.sourceMode == CaptureSourceMode.GALLERY) {
                            val bmp = uiState.selectedGalleryBitmap
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Gráfico carregado da galeria",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(ObsidianBg)
                                )
                            } else {
                                GalleryEmptyState(
                                    onPickImage = { launchGalleryPicker() },
                                    onPickFromFiles = { launchDocumentImagePicker() }
                                )
                            }
                        }

                        AreaSelectionOverlay(
                            isMarkingArea = uiState.isMarkingArea,
                            draftSelection = uiState.draftSelection,
                            markedArea = uiState.markedArea,
                            onDraftChange = { viewModel.updateDraftSelection(it) },
                            onConfirm = { viewModel.confirmMarkedArea() },
                            onCancel = { viewModel.cancelMarkingArea() },
                            onAutoDetect = if (uiState.sourceMode == CaptureSourceMode.WEBVIEW) {
                                {
                                    val wv = webViewRef
                                    if (wv != null) {
                                        detectWebViewChartBounds(wv) { detection ->
                                            viewModel.enableAutoChartArea(detection.bounds)
                                        }
                                    } else {
                                        viewModel.enableAutoChartArea()
                                    }
                                }
                            } else {
                                null
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        uiState.calibratingButton?.let { targetButton ->
                            val targetLabel = if (targetButton == SignalDirection.CALL_UP) {
                                "Toque no botão ACIMA (verde)"
                            } else {
                                "Toque no botão ABAIXO (vermelho)"
                            }
                            ButtonCalibrationOverlay(
                                targetLabel = targetLabel,
                                onTapPoint = { nx, ny -> viewModel.recordCalibratedTap(nx, ny) },
                                onCancel = { viewModel.cancelButtonCalibration() },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        if (uiState.calibratingAmountField) {
                            ButtonCalibrationOverlay(
                                targetLabel = "Toque no campo de VALOR ($ / R$) na corretora",
                                onTapPoint = { nx, ny -> viewModel.recordCalibratedAmountTap(nx, ny) },
                                onCancel = { viewModel.cancelButtonCalibration() },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        if (isInPipMode) {
                            val pipLabel = when (val st = uiState.analysisState) {
                                is AnalysisUiState.Ready -> {
                                    val dir = when (st.result.direction) {
                                        SignalDirection.CALL_UP -> "ACIMA"
                                        SignalDirection.PUT_DOWN -> "ABAIXO"
                                        SignalDirection.NEUTRAL -> "IA"
                                    }
                                    "$dir ${st.result.confidencePercent}% • ${formatCountdownMmSs(uiState.countdownSec)}"
                                }
                                is AnalysisUiState.Analyzing -> "Analisando..."
                                else -> "IA Ativa • ${formatCountdownMmSs(uiState.countdownSec)}"
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ObsidianBg.copy(alpha = 0.82f))
                                    .border(1.dp, EmeraldBright, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = pipLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldMint,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                if (!isInPipMode) {
                    // 4. Helper Hint Line below viewport
                    Text(
                        text = when {
                            isBrokerBlockedOrChallenging && uiState.sourceMode == CaptureSourceMode.WEBVIEW ->
                                stringResource(R.string.broker_block_paused_hint)
                            uiState.calibratingButton == SignalDirection.CALL_UP ->
                                "Toque exatamente no botão ACIMA (verde) na corretora."
                            uiState.calibratingButton == SignalDirection.PUT_DOWN ->
                                "Agora toque exatamente no botão ABAIXO (vermelho) na corretora."
                            uiState.calibratingAmountField ->
                                "Toque no campo onde aparece o valor da entrada (ex: $ 2) na corretora."
                            uiState.markedArea == null ->
                                stringResource(R.string.hint_open_and_mark)
                            uiState.sourceMode == CaptureSourceMode.WEBVIEW && uiState.autoScanEnabled -> {
                                val intervalLabel = uiState.boConfig.durationShortLabel
                                val modeTag = if (uiState.boConfig.autoExpiryEnabled) "Auto $intervalLabel" else intervalLabel
                                val bgSuffix = if (uiState.keepRunningInBackground) " • 2º plano ON" else ""
                                val prefix = if (uiState.autoClickEnabled) "Robô ($modeTag)" else "Tendência ($modeTag)"
                                "$prefix$bgSuffix • próxima leitura em ${formatCountdownMmSs(uiState.countdownSec)}."
                            }
                            else ->
                                "IA pausada • toque em \"Retomar\" no topo ou expanda o painel."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isBrokerBlockedOrChallenging) AmberWarning else TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    // Reserved slot for collapsed AnalysisBottomPanel so expanding never squishes WebView into landscape
                    Spacer(modifier = Modifier.height(74.dp))
                }
            }

            if (!isInPipMode && uiState.isAnalysisExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x8804080A))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { viewModel.setAnalysisExpanded(false) }
                        )
                )
            }

            // 5. Bottom Expandable Analysis Card (overlays cleanly when expanded)
            if (!isInPipMode) {
            AnalysisBottomPanel(
                analysisState = uiState.analysisState,
                isExpanded = uiState.isAnalysisExpanded,
                sourceMode = uiState.sourceMode,
                hasMarkedArea = uiState.markedArea != null,
                autoScanEnabled = uiState.autoScanEnabled,
                autoScanIntervalSec = uiState.autoScanIntervalSec,
                countdownSec = uiState.countdownSec,
                boConfig = uiState.boConfig,
                voiceEnabled = uiState.voiceEnabled,
                keepRunningInBackground = uiState.keepRunningInBackground,
                autoClickEnabled = uiState.autoClickEnabled,
                autoClickCooldownSec = uiState.autoClickCooldownSec,
                lastAutoClickSummary = uiState.lastAutoClickSummary,
                hasCalibratedButtons = uiState.calibratedCallPoint != null && uiState.calibratedPutPoint != null,
                hasCalibratedAmount = uiState.calibratedAmountPoint != null,
                martingaleConfig = uiState.martingaleConfig,
                currentMartingaleStep = uiState.currentMartingaleStep,
                currentBotStake = uiState.currentBotStake,
                accumulatedLossDeficit = uiState.accumulatedLossDeficit,
                aiRecoveryPlan = uiState.aiRecoveryPlan,
                isPlanningRecovery = uiState.isPlanningRecovery,
                sessionWins = uiState.sessionWins,
                sessionLosses = uiState.sessionLosses,
                sessionNetLoss = uiState.sessionNetLoss,
                isSessionStopLossHit = uiState.isSessionStopLossHit,
                lossPauseRemainingSec = uiState.lossPauseRemainingSec,
                pendingCandleCloseDirection = uiState.pendingCandleCloseDirection,
                pendingCandleCloseRemainingSec = uiState.pendingCandleCloseRemainingSec,
                lastOutcomeMessage = uiState.lastOutcomeMessage,
                history = uiState.history,
                onToggleExpand = { viewModel.toggleAnalysisExpanded() },
                onStartMarkArea = {
                    viewModel.setAnalysisExpanded(false)
                    viewModel.startMarkingArea()
                },
                onAutoDetectChart = {
                    val wv = webViewRef
                    if (wv != null && uiState.sourceMode == CaptureSourceMode.WEBVIEW) {
                        detectWebViewChartBounds(wv) { detection ->
                            viewModel.enableAutoChartArea(detection.bounds)
                        }
                    } else {
                        viewModel.enableAutoChartArea()
                    }
                },
                onAnalyzeNow = { viewModel.resumeAiAndScanNow() },
                onToggleAutoScan = { viewModel.toggleAutoScan(it) },
                onSelectInterval = { viewModel.setAutoScanInterval(it, disableAutoExpiry = true) },
                onToggleAutoExpiry = { viewModel.toggleAutoExpiry(it) },
                onSelectBoSensitivity = { viewModel.setBoSensitivity(it) },
                onToggleVoice = { enabled ->
                    viewModel.toggleVoiceEnabled(enabled)
                    if (!enabled) voiceSpeaker.stop()
                },
                onToggleKeepRunningInBackground = { viewModel.toggleKeepRunningInBackground(it) },
                onPauseAll = {
                    viewModel.pauseAllMonitoring()
                    voiceSpeaker.stop()
                },
                onToggleAutoClick = { viewModel.toggleAutoClick(it) },
                onSelectAutoClickCooldown = { viewModel.setAutoClickCooldown(it) },
                onStartButtonCalibration = { viewModel.startButtonCalibration() },
                onStartAmountCalibration = { viewModel.startAmountCalibration() },
                onClearCalibratedButtons = { viewModel.clearCalibratedButtons() },
                onToggleMartingale = { viewModel.toggleMartingaleEnabled(it) },
                onSelectRiskStrategy = { viewModel.setRiskStrategy(it) },
                onSelectInitialStake = { viewModel.setMartingaleInitialStake(it) },
                onSelectMaxMartingaleSteps = { viewModel.setMartingaleMaxSteps(it) },
                onRecordTradeOutcome = { viewModel.recordTradeOutcome(it, isAutomatic = false) },
                onResetMartingaleCycle = { viewModel.resetMartingaleCycle() },
                onResetSessionRiskGuards = { viewModel.resetSessionRiskGuards() },
                onOpenRobotConfig = {
                    viewModel.setAnalysisExpanded(false)
                    viewModel.setRobotConfigOpen(true)
                },
                onOpenIqOptionApp = { launchIqOptionBrokerApp() },
                onOpenGalleryPicker = {
                    viewModel.setAnalysisExpanded(false)
                    launchGalleryPicker()
                },
                onSpeakNow = { viewModel.speakCurrentResult() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
            }
        }
    }

    if (uiState.isRobotConfigOpen) {
        RobotConfigOverlay(
            accountType = uiState.accountType,
            liveBrokerBalance = uiState.liveBrokerBalance,
            autoLoginStatus = autoLoginStatus,
            savedAutoLoginEmail = SecureBrokerSessionManager.getSavedAutoLoginEmail(context),
            openAutoLoginVaultInitially = openAutoLoginVaultInConfig,
            boConfig = uiState.boConfig,
            martingaleConfig = uiState.martingaleConfig,
            currentMartingaleStep = uiState.currentMartingaleStep,
            currentBotStake = uiState.currentBotStake,
            accumulatedLossDeficit = uiState.accumulatedLossDeficit,
            aiRecoveryPlan = uiState.aiRecoveryPlan,
            isPlanningRecovery = uiState.isPlanningRecovery,
            sessionWins = uiState.sessionWins,
            sessionLosses = uiState.sessionLosses,
            sessionNetLoss = uiState.sessionNetLoss,
            isSessionStopLossHit = uiState.isSessionStopLossHit,
            lossPauseRemainingSec = uiState.lossPauseRemainingSec,
            lastOutcomeMessage = uiState.lastOutcomeMessage,
            onSelectAccountType = { viewModel.setAccountType(it) },
            onRefreshBalance = { viewModel.requestBrokerBalanceRefresh() },
            onSaveAutoLoginCredentials = { email, password, enabled ->
                val ok = SecureBrokerSessionManager.saveAutoLoginCredentials(
                    context = context,
                    email = email,
                    password = password,
                    enabled = enabled,
                    synchronous = true
                )
                if (ok) {
                    autoLoginStatus = SecureBrokerSessionManager.getAutoLoginStatus(context)
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.auto_login_saved_toast),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
                ok
            },
            onToggleAutoLoginEnabled = { enabled ->
                SecureBrokerSessionManager.setAutoLoginEnabled(
                    context = context,
                    enabled = enabled,
                    synchronous = true
                )
                autoLoginStatus = SecureBrokerSessionManager.getAutoLoginStatus(context)
            },
            onClearAutoLoginCredentials = {
                SecureBrokerSessionManager.clearAutoLoginCredentials(context, synchronous = true)
                autoLoginStatus = SecureBrokerSessionManager.getAutoLoginStatus(context)
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.auto_login_cleared_toast),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            },
            onTriggerAutoLoginNow = {
                openAutoLoginVaultInConfig = false
                viewModel.setRobotConfigOpen(false)
                autoLoginRetryNonce++
            },
            onSelectInitialStake = { viewModel.setMartingaleInitialStake(it) },
            onSelectCurrencySymbol = { viewModel.setCurrencySymbol(it) },
            onSelectDurationSeconds = { viewModel.setAutoScanInterval(it, disableAutoExpiry = true) },
            onToggleAutoExpiry = { viewModel.toggleAutoExpiry(it) },
            onToggleStrictThreeCandleStrategy = { viewModel.toggleStrictThreeCandleRsiStrategy(it) },
            onToggleWaitForCandleClose = { viewModel.toggleWaitForCandleClose(it) },
            onSelectSessionMaxLossLimit = { viewModel.setSessionMaxLossLimit(it) },
            onResetSessionRiskGuards = { viewModel.resetSessionRiskGuards() },
            onSelectTradingAsset = { viewModel.setTradingAsset(it) },
            onSelectTechnicalIndicator = { viewModel.setTechnicalIndicator(it) },
            onSelectRiskStrategy = { viewModel.setRiskStrategy(it) },
            onSelectMaxMartingaleSteps = { viewModel.setMartingaleMaxSteps(it) },
            onSelectSorosMaxLevels = { viewModel.setSorosMaxLevels(it) },
            onRecordTradeOutcome = { viewModel.recordTradeOutcome(it, isAutomatic = false) },
            onResetCycle = { viewModel.resetMartingaleCycle() },
            onOpenIqOptionApp = { launchIqOptionBrokerApp() },
            onResetApp = {
                lastVisualBalanceOcrAtMs = 0L
                hasEncryptedBrokerSession = false
                openAutoLoginVaultInConfig = false
                viewModel.resetAllAppState()
                val targetWebView = webViewRef
                BrokerSessionPrefs.resetAllAppData(
                    context = context,
                    webView = targetWebView
                ) {
                    autoLoginStatus = SecureBrokerSessionManager.getAutoLoginStatus(context)
                    targetWebView?.post {
                        targetWebView.loadUrl(
                            TrendLensUiState.TRADEROOM_URL,
                            BrokerSessionPrefs.BRAZIL_PT_HEADERS
                        )
                    }
                }
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.reset_app_done_toast),
                    android.widget.Toast.LENGTH_LONG
                ).show()
            },
            onDismiss = {
                openAutoLoginVaultInConfig = false
                viewModel.setRobotConfigOpen(false)
            }
        )
    }

    if (uiState.isHelpDialogVisible) {
        HelpDialog(
            onDismiss = { viewModel.setHelpDialogVisible(false) },
            onOpenUrl = { url ->
                viewModel.setSourceMode(CaptureSourceMode.WEBVIEW)
                webViewRef?.loadUrl(url, BrokerSessionPrefs.BRAZIL_PT_HEADERS)
            },
            onOpenIqOptionApp = { launchIqOptionBrokerApp() },
            onOpenQrCode = { showQrCodeDialog = true }
        )
    }

    if (showQrCodeDialog) {
        AppQrCodeDialog(
            onDismiss = { showQrCodeDialog = false }
        )
    }

    if (showExitConfirmDialog) {
        val bgRunning = uiState.keepRunningInBackground && uiState.autoScanEnabled
        AlertDialog(
            onDismissRequest = { showExitConfirmDialog = false },
            containerColor = TerminalSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            modifier = Modifier.testTag("exit_confirm_dialog"),
            title = {
                Text(
                    text = if (bgRunning) "Continuar rodando em segundo plano?" else "Deseja sair do aplicativo?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (bgRunning) {
                        "O Trad do futuro Roberto Silva pode continuar analisando o gráfico e operando em segundo plano (mini janela + notificação) até você tocar em Pausar."
                    } else {
                        "Para não desconectar da corretora nem interromper o robô, continue no Trad do futuro Roberto Silva."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showExitConfirmDialog = false
                        if (bgRunning) {
                            onMinimizeToBackground()
                        }
                    },
                    modifier = Modifier.testTag("exit_cancel_button")
                ) {
                    Text(
                        text = if (bgRunning) {
                            stringResource(R.string.action_minimize_background)
                        } else {
                            "Continuar no app"
                        },
                        color = EmeraldBright,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitConfirmDialog = false
                        viewModel.pauseAllMonitoring()
                        voiceSpeaker.stop()
                        RobotBackgroundService.stop(context)
                        context.findActivity()?.finish()
                    },
                    modifier = Modifier.testTag("exit_confirm_button")
                ) {
                    Text(
                        text = if (bgRunning) {
                            stringResource(R.string.action_pause_and_exit)
                        } else {
                            "Sair mesmo assim"
                        },
                        color = BearishRed
                    )
                }
            }
        )
    }
}

@Composable
private fun TrendLensTopHeader(
    sourceMode: CaptureSourceMode,
    voiceEnabled: Boolean,
    onToggleVoice: () -> Unit,
    onOpenRobotConfig: () -> Unit,
    onHelpClick: () -> Unit,
    onPhoneClick: () -> Unit,
    onSelectMode: (CaptureSourceMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Brand Badge + Wordmark + Owner Phone
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(end = 4.dp)
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onPhoneClick)
                .padding(vertical = 2.dp, horizontal = 2.dp)
                .testTag("owner_phone_header_button"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(EmeraldMint, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(19.dp)) {
                    val w = size.width
                    val h = size.height
                    val path = Path().apply {
                        moveTo(0f, h * 0.55f)
                        lineTo(w * 0.24f, h * 0.55f)
                        lineTo(w * 0.38f, h * 0.22f)
                        lineTo(w * 0.54f, h * 0.82f)
                        lineTo(w * 0.70f, h * 0.38f)
                        lineTo(w * 0.80f, h * 0.55f)
                        lineTo(w, h * 0.55f)
                    }
                    drawPath(
                        path = path,
                        color = ObsidianBg,
                        style = Stroke(
                            width = 2.4.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = stringResource(R.string.brand_trend),
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.brand_lens),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = EmeraldMint,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Phone,
                        contentDescription = stringResource(R.string.cd_owner_phone),
                        tint = EmeraldBright,
                        modifier = Modifier.size(10.dp)
                    )
                    Text(
                        text = stringResource(R.string.owner_phone_display),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Voice Button + Help Button + Mode Switcher
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = onToggleVoice,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("voice_toggle_button")
            ) {
                Icon(
                    imageVector = if (voiceEnabled) {
                        Icons.Rounded.VolumeUp
                    } else {
                        Icons.Rounded.VolumeOff
                    },
                    contentDescription = stringResource(R.string.cd_voice_toggle),
                    tint = if (voiceEnabled) EmeraldBright else TextMuted,
                    modifier = Modifier.size(22.dp)
                )
            }

            IconButton(
                onClick = onOpenRobotConfig,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("open_robot_config_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = "Configurar robô, ativo, indicador e gestão de risco",
                    tint = EmeraldMint,
                    modifier = Modifier.size(22.dp)
                )
            }

            IconButton(
                onClick = onHelpClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("help_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.HelpOutline,
                    contentDescription = stringResource(R.string.cd_help),
                    tint = TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(TerminalSurface)
                    .border(1.dp, TerminalBorder, RoundedCornerShape(14.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ModeTogglePill(
                    selected = sourceMode == CaptureSourceMode.GALLERY,
                    icon = Icons.Outlined.Image,
                    contentDescription = stringResource(R.string.cd_mode_gallery),
                    testTag = "mode_gallery_button",
                    onClick = { onSelectMode(CaptureSourceMode.GALLERY) }
                )
                ModeTogglePill(
                    selected = sourceMode == CaptureSourceMode.WEBVIEW,
                    icon = Icons.Rounded.DesktopWindows,
                    contentDescription = stringResource(R.string.cd_mode_webview),
                    testTag = "mode_webview_button",
                    onClick = { onSelectMode(CaptureSourceMode.WEBVIEW) }
                )
            }
        }
    }
}

@Composable
private fun ModeTogglePill(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    testTag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) EmeraldContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (selected) EmeraldBright else TextSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SectionStatusRow(
    isMarkingArea: Boolean,
    hasMarkedArea: Boolean,
    autoScanEnabled: Boolean,
    countdownSec: Int,
    autoClickEnabled: Boolean,
    martingaleEnabled: Boolean,
    martingaleStep: Int,
    currentBotStake: Double,
    sourceMode: CaptureSourceMode,
    onToggleAutoClick: () -> Unit,
    onStartChartReading: () -> Unit,
    onPauseMonitoring: () -> Unit,
    onOpenQrCode: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(TerminalSurfaceElevated)
                .border(1.dp, TerminalBorder, RoundedCornerShape(10.dp))
                .clickable(onClick = onOpenQrCode)
                .testTag("open_qr_code_button")
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.QrCode2,
                contentDescription = stringResource(R.string.cd_qr_code),
                tint = EmeraldMint,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = stringResource(R.string.action_qr_code),
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary,
                fontWeight = FontWeight.Medium
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (sourceMode == CaptureSourceMode.WEBVIEW) {
                val isGaleActive = autoClickEnabled && martingaleEnabled && martingaleStep > 0
                val pillBorder = when {
                    isGaleActive -> AmberWarning
                    autoClickEnabled -> EmeraldBright
                    else -> TerminalBorder
                }
                val pillBg = when {
                    isGaleActive -> com.example.ui.theme.AmberContainer
                    autoClickEnabled -> EmeraldContainer
                    else -> TerminalSurfaceElevated
                }
                val pillText = when {
                    !autoClickEnabled -> "Robô OFF"
                    martingaleEnabled && martingaleStep > 0 -> "Robô G$martingaleStep (${formatStakeAmount(currentBotStake)})"
                    martingaleEnabled -> "Robô (${formatStakeAmount(currentBotStake)})"
                    else -> "Robô ON"
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(pillBg)
                        .border(1.dp, pillBorder, RoundedCornerShape(10.dp))
                        .clickable(onClick = onToggleAutoClick)
                        .testTag("quick_robot_toggle")
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                when {
                                    isGaleActive -> AmberWarning
                                    autoClickEnabled -> EmeraldBright
                                    else -> TextMuted
                                },
                                CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = pillText,
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            isGaleActive -> AmberWarning
                            autoClickEnabled -> EmeraldMint
                            else -> TextSecondary
                        }
                    )
                }
            }

            if (!hasMarkedArea && !isMarkingArea && sourceMode == CaptureSourceMode.WEBVIEW) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(EmeraldContainer)
                        .border(1.dp, EmeraldBright, RoundedCornerShape(10.dp))
                        .clickable(onClick = onStartChartReading)
                        .testTag("quick_start_read_button")
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(EmeraldBright, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Ler gráfico",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldMint
                    )
                }
            } else if (hasMarkedArea && sourceMode == CaptureSourceMode.WEBVIEW && !autoScanEnabled) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(com.example.ui.theme.AmberContainer)
                        .border(1.dp, AmberWarning, RoundedCornerShape(10.dp))
                        .clickable(onClick = onStartChartReading)
                        .testTag("resume_ai_status_pill")
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(AmberWarning, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "IA Pausada • Retomar",
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberWarning,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                val (dotColor, statusText) = when {
                    isMarkingArea -> AmberWarning to stringResource(R.string.status_selecting_area)
                    hasMarkedArea && sourceMode == CaptureSourceMode.WEBVIEW && autoScanEnabled ->
                        EmeraldBright to "Ativa (${formatCountdownMmSs(countdownSec)})"
                    hasMarkedArea -> EmeraldBright to stringResource(R.string.status_reading_active)
                    else -> TextMuted to stringResource(R.string.status_mark_area)
                }

                    Row(
                        modifier = if (hasMarkedArea && sourceMode == CaptureSourceMode.WEBVIEW) {
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(onClick = onStartChartReading)
                                .testTag("active_ai_status_pill")
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        } else {
                            Modifier
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(dotColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hasMarkedArea) EmeraldMint else TextSecondary
                        )
                    }
                    if (hasMarkedArea && sourceMode == CaptureSourceMode.WEBVIEW && autoScanEnabled) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(com.example.ui.theme.AmberContainer)
                                .border(1.dp, AmberWarning.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                                .clickable(onClick = onPauseMonitoring)
                                .testTag("pause_ai_status_pill")
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.action_pause_ai),
                                style = MaterialTheme.typography.labelSmall,
                                color = AmberWarning,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }

private fun formatCountdownMmSs(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val mins = safe / 60
    val secs = safe % 60
    return if (mins > 0) String.format("%d:%02d", mins, secs) else "${secs}s"
}

private suspend fun applyStakeToBrokerWithVisualFallback(
    webView: WebView,
    viewModel: TrendLensViewModel,
    targetStake: Double,
    calibratedOrCachedAmountPoint: BotPoint?
): Boolean {
    suspend fun attemptDomOrPoint(point: BotPoint?): Boolean {
        val result = withTimeoutOrNull(4_500L) {
            suspendCancellableCoroutine<Pair<Boolean, Boolean>> { cont ->
                applyWebViewStakeAmount(webView, targetStake, point) { applied, needsDelay ->
                    if (cont.isActive) cont.resume(applied to needsDelay)
                }
            }
        } ?: return false
        if (result.first && result.second) {
            kotlinx.coroutines.delay(160L)
        }
        return result.first
    }

    if (attemptDomOrPoint(calibratedOrCachedAmountPoint)) {
        return true
    }

    // Visual Gemini fallback for canvas-based traderooms when user has not manually calibrated the amount box yet
    val lowerBitmap = withTimeoutOrNull(2_000L) {
        suspendCancellableCoroutine<android.graphics.Bitmap?> { cont ->
            captureWebViewRegion(webView, NormalizedRect(0f, 0.50f, 1f, 1f)) { bmp ->
                if (cont.isActive) cont.resume(bmp)
            }
        }
    } ?: return false

    val visualHit = try {
        withTimeoutOrNull(6_000L) {
            viewModel.locateStakeBoxFromBitmap(lowerBitmap)
        }
    } finally {
        if (!lowerBitmap.isRecycled) {
            lowerBitmap.recycle()
        }
    } ?: return false

    val fullPoint = BotPoint(
        normX = visualHit.normX.coerceIn(0.02f, 0.98f),
        normY = (0.50f + visualHit.normY * 0.50f).coerceIn(0.50f, 0.98f)
    )
    viewModel.cacheAutoDetectedAmountPoint(fullPoint)

    if (visualHit.currentAmount != null && kotlin.math.abs(visualHit.currentAmount - targetStake) < 0.01) {
        return true
    }
    return attemptDomOrPoint(fullPoint)
}

private suspend fun applyExpiryToBroker(
    webView: WebView,
    expirySeconds: Int
): Boolean {
    return withTimeoutOrNull(2_400L) {
        suspendCancellableCoroutine<Boolean> { cont ->
            applyWebViewExpirySeconds(
                webView = webView,
                expirySeconds = expirySeconds
            ) { applied, _ ->
                if (cont.isActive) cont.resume(applied)
            }
        }
    } ?: false
}

private suspend fun readLiveBrokerBalance(
    webView: WebView,
    viewModel: TrendLensViewModel,
    ocrTimeoutMs: Long = 4_500L,
    allowVisualOcr: Boolean = true,
    onVisualOcrAttempted: () -> Unit = {}
): Double? {
    val domBalance = withTimeoutOrNull(1_500L) {
        suspendCancellableCoroutine<Double?> { cont ->
            readWebViewBrokerBalance(webView) { bal ->
                if (cont.isActive) cont.resume(bal)
            }
        }
    }
    if (domBalance != null) return domBalance
    if (!allowVisualOcr) return null

    onVisualOcrAttempted()
    val headerBitmap = withTimeoutOrNull(2_000L) {
        suspendCancellableCoroutine<android.graphics.Bitmap?> { cont ->
            captureWebViewRegion(webView, NormalizedRect(0f, 0f, 1f, 0.20f)) { bmp ->
                if (cont.isActive) cont.resume(bmp)
            }
        }
    } ?: return null

    return try {
        withTimeoutOrNull(ocrTimeoutMs) {
            viewModel.readBalanceFromHeaderBitmap(headerBitmap)
        }
    } finally {
        if (!headerBitmap.isRecycled) {
            headerBitmap.recycle()
        }
    }
}

@Composable
private fun ButtonCalibrationOverlay(
    targetLabel: String,
    onTapPoint: (Float, Float) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .background(Color(0x6604080A))
            .onSizeChanged { boxSize = it }
            .pointerInput(targetLabel) {
                detectTapGestures { offset ->
                    val w = boxSize.width.toFloat().coerceAtLeast(1f)
                    val h = boxSize.height.toFloat().coerceAtLeast(1f)
                    onTapPoint(offset.x / w, offset.y / h)
                }
            }
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(TerminalSurfaceElevated)
                .border(1.dp, EmeraldBright, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = targetLabel,
                style = MaterialTheme.typography.titleSmall,
                color = EmeraldMint,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedButton(onClick = onCancel) {
                Text("Cancelar calibração", color = TextPrimary)
            }
        }
    }
}

@Composable
private fun GalleryEmptyState(
    onPickImage: () -> Unit,
    onPickFromFiles: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(EmeraldContainer, CircleShape)
                .border(1.dp, EmeraldBright.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.AddPhotoAlternate,
                contentDescription = null,
                tint = EmeraldMint,
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Carregar print da IQ Option",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Escolha um print do gráfico (ou compartilhe direto para o Trad do futuro Roberto Silva) para ler a tendência na hora.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(18.dp))
        Button(
            onClick = onPickImage,
            modifier = Modifier.testTag("select_image_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = EmeraldBright,
                contentColor = ObsidianBg
            )
        ) {
            Icon(
                imageVector = Icons.Outlined.Image,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Selecionar da Galeria")
        }
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
            onClick = onPickFromFiles,
            modifier = Modifier.testTag("select_file_image_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.FolderOpen,
                contentDescription = null,
                tint = EmeraldMint,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Abrir Arquivos / Capturas", color = TextPrimary)
        }
    }
}
