package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Rocket
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AiRecoveryMode
import com.example.model.AiRecoveryPlan
import com.example.model.AnalysisUiState
import com.example.model.BoConfig
import com.example.model.BoSensitivity
import com.example.model.CaptureSourceMode
import com.example.model.ChartAnalysisResult
import com.example.model.MartingaleConfig
import com.example.model.RiskStrategy
import com.example.model.SignalDirection
import com.example.model.TradeOutcome
import com.example.model.formatStakeAmount
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BearishContainer
import com.example.ui.theme.BearishRed
import com.example.ui.theme.BullishContainer
import com.example.ui.theme.BullishGreen
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.IqOptionOrange
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TerminalBorder
import com.example.ui.theme.TerminalSurface
import com.example.ui.theme.TerminalSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun AnalysisBottomPanel(
    analysisState: AnalysisUiState,
    isExpanded: Boolean,
    sourceMode: CaptureSourceMode,
    hasMarkedArea: Boolean,
    autoScanEnabled: Boolean,
    autoScanIntervalSec: Int,
    countdownSec: Int,
    boConfig: BoConfig,
    voiceEnabled: Boolean,
    keepRunningInBackground: Boolean = true,
    autoClickEnabled: Boolean,
    autoClickCooldownSec: Int,
    lastAutoClickSummary: String?,
    hasCalibratedButtons: Boolean,
    hasCalibratedAmount: Boolean,
    martingaleConfig: MartingaleConfig,
    currentMartingaleStep: Int,
    currentBotStake: Double,
    accumulatedLossDeficit: Double = 0.0,
    aiRecoveryPlan: AiRecoveryPlan? = null,
    isPlanningRecovery: Boolean = false,
    sessionWins: Int,
    sessionLosses: Int,
    sessionNetLoss: Double = 0.0,
    isSessionStopLossHit: Boolean = false,
    lossPauseRemainingSec: Int = 0,
    pendingCandleCloseDirection: SignalDirection? = null,
    pendingCandleCloseRemainingSec: Int = 0,
    lastOutcomeMessage: String?,
    history: List<ChartAnalysisResult>,
    onToggleExpand: () -> Unit,
    onStartMarkArea: () -> Unit,
    onAutoDetectChart: () -> Unit = {},
    onAnalyzeNow: () -> Unit,
    onToggleAutoScan: (Boolean) -> Unit,
    onSelectInterval: (Int) -> Unit,
    onToggleAutoExpiry: (Boolean) -> Unit = {},
    onSelectBoSensitivity: (BoSensitivity) -> Unit,
    onToggleVoice: (Boolean) -> Unit,
    onToggleKeepRunningInBackground: (Boolean) -> Unit = {},
    onPauseAll: () -> Unit = {},
    onToggleAutoClick: (Boolean) -> Unit,
    onSelectAutoClickCooldown: (Int) -> Unit,
    onStartButtonCalibration: () -> Unit,
    onStartAmountCalibration: () -> Unit,
    onClearCalibratedButtons: () -> Unit,
    onToggleMartingale: (Boolean) -> Unit,
    onSelectRiskStrategy: (RiskStrategy) -> Unit = {},
    onSelectInitialStake: (Double) -> Unit,
    onSelectMaxMartingaleSteps: (Int) -> Unit,
    onRecordTradeOutcome: (TradeOutcome) -> Unit,
    onResetMartingaleCycle: () -> Unit,
    onResetSessionRiskGuards: () -> Unit = {},
    onOpenRobotConfig: () -> Unit,
    onOpenIqOptionApp: () -> Unit = {},
    onOpenGalleryPicker: () -> Unit = {},
    onSpeakNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, TerminalBorder, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = TerminalSurface,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Collapsed Header Row (always visible)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .clickable(onClick = onToggleExpand)
                    .testTag("analysis_panel_header")
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeaderLeadingIcon(analysisState = analysisState)
                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    when (analysisState) {
                        is AnalysisUiState.Unavailable -> {
                            Text(
                                text = stringResource(R.string.analysis_unavailable),
                                style = MaterialTheme.typography.titleSmall,
                                color = AmberWarning,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "BO ${if (boConfig.autoExpiryEnabled) "Auto IA (${boConfig.durationShortLabel})" else boConfig.expiryLabel} • ${boConfig.sensitivity.label} • ${stringResource(R.string.analysis_expand)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        is AnalysisUiState.Analyzing -> {
                            Text(
                                text = stringResource(R.string.analysis_analyzing),
                                style = MaterialTheme.typography.titleSmall,
                                color = EmeraldMint
                            )
                            Text(
                                text = "Consultando IA Gemini na área marcada",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        is AnalysisUiState.NotAChart -> {
                            Text(
                                text = stringResource(R.string.analysis_not_a_chart),
                                style = MaterialTheme.typography.titleSmall,
                                color = BearishRed,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = analysisState.reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        is AnalysisUiState.Ready -> {
                            val res = analysisState.result
                            val (label, accent) = signalTitleAndColor(res.direction)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "$label • ${res.confidencePercent}%",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = accent,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (res.timeframeLabel != "Tempo real") {
                                    Box(
                                        modifier = Modifier
                                            .background(AmberContainer, RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = res.timeframeLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AmberWarning
                                        )
                                    }
                                }
                            }
                            Text(
                                text = when {
                                    isSessionStopLossHit ->
                                        "Stop Loss da sessão atingido — robô parado"
                                    lossPauseRemainingSec > 0 -> {
                                        val pMin = lossPauseRemainingSec / 60
                                        val pSec = lossPauseRemainingSec % 60
                                        "Pausa 5 min (2 derrotas): ${String.format(java.util.Locale.US, "%02d:%02d", pMin, pSec)}"
                                    }
                                    pendingCandleCloseDirection != null && pendingCandleCloseRemainingSec > 0 ->
                                        "Aguardando fechamento do candle (${pendingCandleCloseRemainingSec}s)..."
                                    isExpanded ->
                                        stringResource(R.string.analysis_collapse)
                                    res.direction == SignalDirection.NEUTRAL && autoScanEnabled && sourceMode == CaptureSourceMode.WEBVIEW ->
                                        "Busca rápida em ${countdownSec}s • ${res.trendLabel} • ${stringResource(R.string.analysis_expand)}"
                                    else -> {
                                        val confPrefix = if (res.confluenceCount > 0) "${res.confluenceCount}/5 • " else ""
                                        "${confPrefix}RSI ${res.rsiValue} • ${res.trendLabel} • ${stringResource(R.string.analysis_expand)}"
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = when {
                                    isSessionStopLossHit -> BearishRed
                                    lossPauseRemainingSec > 0 -> AmberWarning
                                    pendingCandleCloseDirection != null -> EmeraldMint
                                    else -> TextSecondary
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        is AnalysisUiState.Error -> {
                            Text(
                                text = "Erro na leitura",
                                style = MaterialTheme.typography.titleSmall,
                                color = BearishRed
                            )
                            Text(
                                text = analysisState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Icon(
                    imageVector = if (isExpanded) {
                        Icons.Rounded.KeyboardArrowDown
                    } else {
                        Icons.Rounded.KeyboardArrowUp
                    },
                    contentDescription = stringResource(R.string.cd_expand_analysis),
                    tint = TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Expandable Body
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HorizontalDivider(color = TerminalBorder)

                    // AI Engine status notice
                    val showDemoNotice = analysisState is AnalysisUiState.Ready && analysisState.result.isDemoMode
                    val noticeBg = if (showDemoNotice) AmberContainer.copy(alpha = 0.75f) else EmeraldContainer.copy(alpha = 0.6f)
                    val noticeBorder = if (showDemoNotice) AmberWarning.copy(alpha = 0.35f) else EmeraldBright.copy(alpha = 0.35f)
                    val noticeTint = if (showDemoNotice) AmberWarning else EmeraldBright
                    val noticeText = if (showDemoNotice) {
                        stringResource(R.string.demo_firebase_notice)
                    } else {
                        stringResource(R.string.ai_gemini_active_notice)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(noticeBg, RoundedCornerShape(12.dp))
                            .border(1.dp, noticeBorder, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = noticeTint,
                            modifier = Modifier
                                .size(18.dp)
                                .padding(top = 1.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = noticeText,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary
                        )
                    }

                    // Active Strategy & Risk Protection Live Status Banners
                    if (isSessionStopLossHit) {
                        val sym = martingaleConfig.currencySymbol
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BearishContainer.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                                .border(1.dp, BearishRed.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                                .testTag("session_stop_loss_banner"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                tint = BearishRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Stop Loss da sessão atingido",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = BearishRed,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Perda: $sym ${formatStakeAmount(sessionNetLoss)} / Limite: $sym ${formatStakeAmount(martingaleConfig.sessionMaxLossLimit)}. Robô parado.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary
                                )
                            }
                            TextButton(
                                onClick = onResetSessionRiskGuards,
                                modifier = Modifier.testTag("banner_reset_session_button")
                            ) {
                                Text(
                                    text = stringResource(R.string.action_reset_session),
                                    color = BearishRed,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else if (lossPauseRemainingSec > 0) {
                        val pMin = lossPauseRemainingSec / 60
                        val pSec = lossPauseRemainingSec % 60
                        val formatted = String.format(java.util.Locale.US, "%02d:%02d", pMin, pSec)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(AmberContainer.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                                .border(1.dp, AmberWarning.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                                .testTag("two_loss_pause_banner"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                tint = AmberWarning,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Pausa de 5 min (2 derrotas seguidas)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = AmberWarning,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Robô pausado automaticamente • retomando em $formatted",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary
                                )
                            }
                            TextButton(onClick = onResetSessionRiskGuards) {
                                Text("Zerar", color = AmberWarning, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else if (pendingCandleCloseDirection != null && pendingCandleCloseRemainingSec > 0) {
                        val dirWord = if (pendingCandleCloseDirection == SignalDirection.CALL_UP) "COMPRA" else "VENDA"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(EmeraldContainer.copy(alpha = 0.75f), RoundedCornerShape(12.dp))
                                .border(1.dp, EmeraldBright.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                                .testTag("candle_close_wait_banner"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = EmeraldBright,
                                strokeWidth = 2.dp
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Aguardando fechamento do candle (${pendingCandleCloseRemainingSec}s)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = EmeraldMint,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Sinal de $dirWord pronto — robô nunca entra no meio da vela.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary
                                )
                            }
                        }
                    }

                    when (analysisState) {
                        is AnalysisUiState.Unavailable -> {
                            Text(
                                text = if (sourceMode == CaptureSourceMode.WEBVIEW) {
                                    "Toque em \"Detectar gráfico\" para enquadrar as velas automaticamente ou marque manualmente a área."
                                } else {
                                    "Selecione um print de gráfico de velas e toque em \"Marcar área\" para validar e analisar."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            if (sourceMode == CaptureSourceMode.WEBVIEW) {
                                Button(
                                    onClick = onAutoDetectChart,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("auto_detect_chart_button"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = EmeraldBright,
                                        contentColor = ObsidianBg
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Detectar gráfico e iniciar robô")
                                }
                                OutlinedButton(
                                    onClick = onStartMarkArea,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("expanded_mark_area_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Crop,
                                        contentDescription = null,
                                        tint = EmeraldMint,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.action_mark_area), color = EmeraldMint)
                                }
                                OutlinedButton(
                                    onClick = onOpenGalleryPicker,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("expanded_gallery_test_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AddPhotoAlternate,
                                        contentDescription = null,
                                        tint = TextPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Analisar print da galeria com IA", color = TextPrimary)
                                }
                            } else {
                                Button(
                                    onClick = onStartMarkArea,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("expanded_mark_area_button"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = EmeraldBright,
                                        contentColor = ObsidianBg
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Crop,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.action_mark_area))
                                }
                            }
                        }

                        is AnalysisUiState.Analyzing -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(TerminalSurfaceElevated, RoundedCornerShape(14.dp))
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = EmeraldBright,
                                    strokeWidth = 2.5.dp
                                )
                                Text(
                                    text = "IA Gemini analisando velas, estrutura e indicadores para BO...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary
                                )
                            }
                        }

                        is AnalysisUiState.NotAChart -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(BearishContainer.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                                    .border(1.dp, BearishRed.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (analysisState.capturedPreview != null) {
                                    Image(
                                        bitmap = analysisState.capturedPreview.asImageBitmap(),
                                        contentDescription = "Prévia rejeitada",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .border(1.dp, BearishRed.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Sinal bloqueado: não é um gráfico",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = BearishRed,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = analysisState.reason,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextPrimary
                                    )
                                }
                            }
                            Text(
                                text = "Dica: marque apenas o quadro com as velas verdes e vermelhas, evitando fotos, menus ou os botões Acima/Abaixo.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onStartMarkArea,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("redefine_invalid_area_button"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = EmeraldBright,
                                        contentColor = ObsidianBg
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Crop,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Redefinir área")
                                }
                                if (hasMarkedArea) {
                                    OutlinedButton(
                                        onClick = onAnalyzeNow,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Revalidar", color = TextPrimary)
                                    }
                                }
                            }
                            if (sourceMode == CaptureSourceMode.WEBVIEW) {
                                OutlinedButton(
                                    onClick = onOpenGalleryPicker,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AddPhotoAlternate,
                                        contentDescription = null,
                                        tint = EmeraldMint,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Testar IA com print da galeria", color = EmeraldMint)
                                }
                            }
                        }

                        is AnalysisUiState.Error -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(BearishContainer.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                                    .border(1.dp, BearishRed.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (analysisState.capturedPreview != null) {
                                    Image(
                                        bitmap = analysisState.capturedPreview.asImageBitmap(),
                                        contentDescription = "Prévia capturada",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .border(1.dp, BearishRed.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Não foi possível concluir a análise da IA",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = BearishRed,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = analysisState.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextPrimary
                                    )
                                    if (autoScanEnabled && sourceMode == CaptureSourceMode.WEBVIEW) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Próxima tentativa automática em ${countdownSec}s",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AmberWarning
                                        )
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onStartMarkArea,
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = EmeraldContainer,
                                        contentColor = EmeraldMint
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Crop,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Redefinir área")
                                }
                                Button(
                                    onClick = onAnalyzeNow,
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("resume_ai_now_button"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = EmeraldBright,
                                        contentColor = ObsidianBg
                                    )
                                ) {
                                    Text(
                                        text = if (!autoScanEnabled || analysisState.message.contains("pausa", ignoreCase = true) || analysisState.message.contains("Limite", ignoreCase = true) || analysisState.message.contains("Cota", ignoreCase = true)) {
                                            "Despausar IA agora"
                                        } else {
                                            "Tentar novamente"
                                        },
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            if (sourceMode == CaptureSourceMode.WEBVIEW) {
                                OutlinedButton(
                                    onClick = onOpenGalleryPicker,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AddPhotoAlternate,
                                        contentDescription = null,
                                        tint = EmeraldMint,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Testar IA com print da galeria", color = EmeraldMint)
                                }
                            }
                        }

                        is AnalysisUiState.Ready -> {
                            ReadyAnalysisContent(
                                result = analysisState.result,
                                capturedPreview = analysisState.capturedPreview,
                                history = history
                            )
                        }
                    }

                    // BO Configuration Card (always accessible when expanded)
                    BoConfigurationCard(
                        sourceMode = sourceMode,
                        hasMarkedArea = hasMarkedArea,
                        isReady = analysisState is AnalysisUiState.Ready,
                        autoScanEnabled = autoScanEnabled,
                        autoScanIntervalSec = autoScanIntervalSec,
                        countdownSec = countdownSec,
                        boConfig = boConfig,
                        voiceEnabled = voiceEnabled,
                        keepRunningInBackground = keepRunningInBackground,
                        autoClickEnabled = autoClickEnabled,
                        autoClickCooldownSec = autoClickCooldownSec,
                        lastAutoClickSummary = lastAutoClickSummary,
                        hasCalibratedButtons = hasCalibratedButtons,
                        hasCalibratedAmount = hasCalibratedAmount,
                        martingaleConfig = martingaleConfig,
                        currentMartingaleStep = currentMartingaleStep,
                        currentBotStake = currentBotStake,
                        accumulatedLossDeficit = accumulatedLossDeficit,
                        aiRecoveryPlan = aiRecoveryPlan,
                        isPlanningRecovery = isPlanningRecovery,
                        sessionWins = sessionWins,
                        sessionLosses = sessionLosses,
                        lastOutcomeMessage = lastOutcomeMessage,
                        onToggleAutoScan = onToggleAutoScan,
                        onSelectInterval = onSelectInterval,
                        onToggleAutoExpiry = onToggleAutoExpiry,
                        onSelectBoSensitivity = onSelectBoSensitivity,
                        onToggleVoice = onToggleVoice,
                        onToggleKeepRunningInBackground = onToggleKeepRunningInBackground,
                        onPauseAll = onPauseAll,
                        onToggleAutoClick = onToggleAutoClick,
                        onSelectAutoClickCooldown = onSelectAutoClickCooldown,
                        onStartButtonCalibration = onStartButtonCalibration,
                        onStartAmountCalibration = onStartAmountCalibration,
                        onClearCalibratedButtons = onClearCalibratedButtons,
                        onToggleMartingale = onToggleMartingale,
                        onSelectRiskStrategy = onSelectRiskStrategy,
                        onSelectInitialStake = onSelectInitialStake,
                        onSelectMaxMartingaleSteps = onSelectMaxMartingaleSteps,
                        onRecordTradeOutcome = onRecordTradeOutcome,
                        onResetMartingaleCycle = onResetMartingaleCycle,
                        onOpenRobotConfig = onOpenRobotConfig,
                        onOpenIqOptionApp = onOpenIqOptionApp,
                        onSpeakNow = onSpeakNow,
                        onAnalyzeNow = onAnalyzeNow,
                        onAutoDetectChart = onAutoDetectChart
                    )

                    Text(
                        text = stringResource(R.string.disclaimer_text),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadyAnalysisContent(
    result: ChartAnalysisResult,
    capturedPreview: android.graphics.Bitmap?,
    history: List<ChartAnalysisResult>
) {
    val (signalTitle, signalAccent) = signalTitleAndColor(result.direction)
    val signalBg = when (result.direction) {
        SignalDirection.CALL_UP -> BullishContainer
        SignalDirection.PUT_DOWN -> BearishContainer
        SignalDirection.NEUTRAL -> AmberContainer
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TerminalSurfaceElevated, RoundedCornerShape(14.dp))
            .border(1.dp, TerminalBorder, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (capturedPreview != null) {
            Image(
                bitmap = capturedPreview.asImageBitmap(),
                contentDescription = "Prévia da área marcada",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, EmeraldBright.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(signalBg, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (result.direction != SignalDirection.NEUTRAL) {
                            TrendRocketIcon(
                                direction = result.direction,
                                iconSize = 15.dp
                            )
                        }
                        Text(
                            text = signalTitle,
                            style = MaterialTheme.typography.labelLarge,
                            color = signalAccent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = result.timestampLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Confluência BO: ${result.confidencePercent}% • ${result.assetLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { result.confidencePercent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = signalAccent,
                trackColor = TerminalBorder
            )
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MetricTile(
            label = "Tendência",
            value = result.trendLabel,
            modifier = Modifier.weight(1f)
        )
        MetricTile(
            label = "RSI Estimado",
            value = "${result.rsiValue} pts",
            modifier = Modifier.weight(1f)
        )
    }
    if (result.trendStrengthScore > 0 || result.confluenceCount > 0) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricTile(
                label = "Força da Tendência",
                value = "${result.trendStrengthScore}/100",
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "Confluência IA",
                value = "${result.confluenceCount}/5 pilares",
                modifier = Modifier.weight(1f)
            )
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MetricTile(
            label = "Leitura de Candles",
            value = result.patternLabel,
            modifier = Modifier.weight(1f)
        )
        MetricTile(
            label = "Zona S/R",
            value = result.supportResistanceLabel,
            modifier = Modifier.weight(1f)
        )
    }

    if (result.consecutiveSameDirectionCandles > 0 || result.rsiTurnState.isNotBlank()) {
        val rsiStatusLabel = when (result.rsiTurnState) {
            "LEAVING_OVERSOLD_UP" -> "Saindo sobrevenda ↑"
            "LEAVING_OVERBOUGHT_DOWN" -> "Saindo sobrecompra ↓"
            "AGAINST_SIGNAL" -> "Contra o sinal"
            "FLAT_OR_MIDDLE" -> "Neutro / sem virada"
            else -> "RSI ${result.rsiValue}"
        }
        val candleCloseLabel = if (result.candleCloseConfirmed) "Candle fechado" else "Aguardando fechar"
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricTile(
                label = "Velas seguidas",
                value = "${result.consecutiveSameDirectionCandles}/3 velas",
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "Regra RSI / Vela",
                value = "$rsiStatusLabel • $candleCloseLabel",
                modifier = Modifier.weight(1.4f)
            )
        }
    }

    if (result.recommendedExpirySeconds != null && result.expirySelectionReason.isNotBlank()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(EmeraldContainer.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                .border(1.dp, EmeraldBright.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .testTag("ai_expiry_rationale_badge"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = EmeraldMint,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Tempo IA: ${result.timeframeLabel} — ${result.expirySelectionReason}",
                style = MaterialTheme.typography.labelMedium,
                color = EmeraldMint
            )
        }
    }

    Text(
        text = result.summary,
        style = MaterialTheme.typography.bodyMedium,
        color = TextPrimary
    )

    if (history.size > 1) {
        Text(
            text = "Leituras recentes (${history.size})",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            history.take(3).forEach { item ->
                val (shortDir, dirColor) = when (item.direction) {
                    SignalDirection.CALL_UP -> "ACIMA" to BullishGreen
                    SignalDirection.PUT_DOWN -> "ABAIXO" to BearishRed
                    SignalDirection.NEUTRAL -> "NEUTRO" to AmberWarning
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(TerminalSurfaceElevated, RoundedCornerShape(10.dp))
                        .border(1.dp, TerminalBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (item.direction != SignalDirection.NEUTRAL) {
                                TrendRocketIcon(
                                    direction = item.direction,
                                    iconSize = 13.dp
                                )
                            }
                            Text(
                                text = "$shortDir ${item.confidencePercent}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = dirColor
                            )
                        }
                        Text(
                            text = "${item.timeframeLabel} • ${item.timestampLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BoConfigurationCard(
    sourceMode: CaptureSourceMode,
    hasMarkedArea: Boolean,
    isReady: Boolean,
    autoScanEnabled: Boolean,
    autoScanIntervalSec: Int,
    countdownSec: Int,
    boConfig: BoConfig,
    voiceEnabled: Boolean,
    keepRunningInBackground: Boolean,
    autoClickEnabled: Boolean,
    autoClickCooldownSec: Int,
    lastAutoClickSummary: String?,
    hasCalibratedButtons: Boolean,
    hasCalibratedAmount: Boolean,
    martingaleConfig: MartingaleConfig,
    currentMartingaleStep: Int,
    currentBotStake: Double,
    accumulatedLossDeficit: Double,
    aiRecoveryPlan: AiRecoveryPlan?,
    isPlanningRecovery: Boolean,
    sessionWins: Int,
    sessionLosses: Int,
    lastOutcomeMessage: String?,
    onToggleAutoScan: (Boolean) -> Unit,
    onSelectInterval: (Int) -> Unit,
    onToggleAutoExpiry: (Boolean) -> Unit,
    onSelectBoSensitivity: (BoSensitivity) -> Unit,
    onToggleVoice: (Boolean) -> Unit,
    onToggleKeepRunningInBackground: (Boolean) -> Unit,
    onPauseAll: () -> Unit,
    onToggleAutoClick: (Boolean) -> Unit,
    onSelectAutoClickCooldown: (Int) -> Unit,
    onStartButtonCalibration: () -> Unit,
    onStartAmountCalibration: () -> Unit,
    onClearCalibratedButtons: () -> Unit,
    onToggleMartingale: (Boolean) -> Unit,
    onSelectRiskStrategy: (RiskStrategy) -> Unit,
    onSelectInitialStake: (Double) -> Unit,
    onSelectMaxMartingaleSteps: (Int) -> Unit,
    onRecordTradeOutcome: (TradeOutcome) -> Unit,
    onResetMartingaleCycle: () -> Unit,
    onOpenRobotConfig: () -> Unit,
    onOpenIqOptionApp: () -> Unit,
    onSpeakNow: () -> Unit,
    onAnalyzeNow: () -> Unit,
    onAutoDetectChart: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TerminalSurfaceElevated, RoundedCornerShape(14.dp))
            .border(1.dp, TerminalBorder, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = if (boConfig.autoExpiryEnabled) {
                "Configuração da Leitura (Auto IA • ${boConfig.durationShortLabel})"
            } else {
                "Configuração da Leitura (${boConfig.durationShortLabel})"
            },
            style = MaterialTheme.typography.titleSmall,
            color = EmeraldMint
        )

        Text(
            text = if (boConfig.autoExpiryEnabled) {
                "Tempo / Expiração (IA escolhe 30s, 1m, 2m ou 5m • atual: ${boConfig.durationShortLabel})"
            } else {
                "Tempo / Expiração (fixo em ${boConfig.durationShortLabel})"
            },
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = boConfig.autoExpiryEnabled,
                onClick = { onToggleAutoExpiry(true) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = { Text(stringResource(R.string.auto_expiry_chip_label)) },
                modifier = Modifier.testTag("bo_expiry_auto"),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = EmeraldContainer,
                    selectedLabelColor = EmeraldMint,
                    selectedLeadingIconColor = EmeraldMint
                )
            )
            listOf(
                30 to "30s",
                60 to "1 min",
                120 to "2 min",
                300 to "5 min"
            ).forEach { (sec, label) ->
                val isCurrentActive = autoScanIntervalSec == sec
                FilterChip(
                    selected = !boConfig.autoExpiryEnabled && isCurrentActive,
                    onClick = { onSelectInterval(sec) },
                    label = {
                        Text(
                            text = if (boConfig.autoExpiryEnabled && isCurrentActive) "$label • IA" else label
                        )
                    },
                    modifier = Modifier.testTag("bo_expiry_$sec"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EmeraldContainer,
                        selectedLabelColor = EmeraldMint,
                        containerColor = if (boConfig.autoExpiryEnabled && isCurrentActive) {
                            EmeraldContainer.copy(alpha = 0.35f)
                        } else {
                            Color.Transparent
                        },
                        labelColor = if (boConfig.autoExpiryEnabled && isCurrentActive) {
                            EmeraldMint
                        } else {
                            TextSecondary
                        }
                    )
                )
            }
        }

        Text(
            text = "Filtro de tendência (${boConfig.sensitivity.description})",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BoSensitivity.entries.forEach { sensitivity ->
                FilterChip(
                    selected = boConfig.sensitivity == sensitivity,
                    onClick = { onSelectBoSensitivity(sensitivity) },
                    label = { Text(sensitivity.label) },
                    modifier = Modifier.testTag("bo_sensitivity_${sensitivity.name.lowercase()}"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EmeraldContainer,
                        selectedLabelColor = EmeraldMint
                    )
                )
            }
        }

        if (sourceMode == CaptureSourceMode.WEBVIEW) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (boConfig.autoExpiryEnabled) {
                            "Leitura automática (Auto IA • ${boConfig.durationShortLabel})"
                        } else {
                            "Leitura automática a cada ${boConfig.durationShortLabel}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = if (autoScanEnabled && hasMarkedArea) {
                            "Próxima leitura em ${formatIntervalMmSs(countdownSec)}"
                        } else if (autoScanEnabled) {
                            "Aguardando marcação da área"
                        } else {
                            "Pausada — disparo manual"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Switch(
                    checked = autoScanEnabled,
                    onCheckedChange = onToggleAutoScan,
                    modifier = Modifier.testTag("auto_scan_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ObsidianBg,
                        checkedTrackColor = EmeraldBright
                    )
                )
            }
            if (!autoScanEnabled) {
                Button(
                    onClick = { onToggleAutoScan(true) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("unpause_auto_scan_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldBright,
                        contentColor = ObsidianBg
                    )
                ) {
                    Text("Despausar IA agora", fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedButton(
                    onClick = onPauseAll,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("pause_all_monitoring_button")
                ) {
                    Text("Pausar IA e Robô", color = AmberWarning, fontWeight = FontWeight.SemiBold)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = stringResource(R.string.bg_mode_title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = if (keepRunningInBackground) {
                            stringResource(R.string.bg_mode_active_desc)
                        } else {
                            stringResource(R.string.bg_mode_inactive_desc)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (keepRunningInBackground) EmeraldMint else TextSecondary
                    )
                }
                Switch(
                    checked = keepRunningInBackground,
                    onCheckedChange = onToggleKeepRunningInBackground,
                    modifier = Modifier.testTag("background_mode_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ObsidianBg,
                        checkedTrackColor = EmeraldBright
                    )
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (voiceEnabled) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff,
                    contentDescription = null,
                    tint = if (voiceEnabled) EmeraldBright else TextMuted,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Falar tendência em voz alta",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary
                )
            }
            Switch(
                checked = voiceEnabled,
                onCheckedChange = onToggleVoice,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ObsidianBg,
                    checkedTrackColor = EmeraldBright
                )
            )
        }

        if (sourceMode == CaptureSourceMode.WEBVIEW) {
            HorizontalDivider(color = TerminalBorder)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Robô Auto-Clique (Acima / Abaixo)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = if (autoClickEnabled) {
                            "Clica automaticamente no botão verde ou vermelho ao achar tendência"
                        } else {
                            "Desativado — apenas avisa sem clicar"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Switch(
                    checked = autoClickEnabled,
                    onCheckedChange = onToggleAutoClick,
                    modifier = Modifier.testTag("auto_click_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ObsidianBg,
                        checkedTrackColor = EmeraldBright
                    )
                )
            }

            if (autoClickEnabled) {
                Text(
                    text = "Intervalo mínimo entre cliques automáticos",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(30 to "30s", 60 to "1 min", 120 to "2 min").forEach { (sec, label) ->
                        FilterChip(
                            selected = autoClickCooldownSec == sec,
                            onClick = { onSelectAutoClickCooldown(sec) },
                            label = { Text(label) },
                            modifier = Modifier.testTag("bot_cooldown_$sec"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldContainer,
                                selectedLabelColor = EmeraldMint
                            )
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onStartButtonCalibration,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("calibrate_bot_buttons")
                    ) {
                        Text(
                            text = if (hasCalibratedButtons) "Botões ✓" else "Calibrar Acima/Abaixo",
                            color = EmeraldMint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    OutlinedButton(
                        onClick = onStartAmountCalibration,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("calibrate_amount_button")
                    ) {
                        Text(
                            text = if (hasCalibratedAmount) "Valor ($) ✓" else "Calibrar Valor ($)",
                            color = if (hasCalibratedAmount) EmeraldBright else AmberWarning,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (hasCalibratedButtons || hasCalibratedAmount) {
                    OutlinedButton(
                        onClick = onClearCalibratedButtons,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Voltar para Auto DOM", color = TextSecondary)
                    }
                }

                if (!lastAutoClickSummary.isNullOrBlank()) {
                    Text(
                        text = lastAutoClickSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = EmeraldMint
                    )
                }

                HorizontalDivider(color = TerminalBorder)

                // Gestão de Recuperação Automática (IA Adaptativa / Martingale / Soros)
                val activeStrategy = martingaleConfig.effectiveStrategy
                val sym = martingaleConfig.currencySymbol
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (activeStrategy == RiskStrategy.AI_RECOVERY) {
                                "Recuperação Inteligente IA"
                            } else {
                                activeStrategy.title
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = when (activeStrategy) {
                                RiskStrategy.AI_RECOVERY ->
                                    "IA calcula valor exato (payout 85%) para recuperar perdas e eleva o filtro na entrada"
                                RiskStrategy.MARTINGALE ->
                                    "Perdeu: dobra (2x) • Acertou: volta para $sym ${formatStakeAmount(martingaleConfig.initialStake)}"
                                RiskStrategy.SOROS ->
                                    "Reinveste lucro ao acertar • volta para $sym ${formatStakeAmount(martingaleConfig.initialStake)} ao perder"
                                RiskStrategy.FIXED ->
                                    "Desativado — mantém valor fixo na corretora"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = martingaleConfig.enabled,
                        onCheckedChange = onToggleMartingale,
                        modifier = Modifier.testTag("martingale_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ObsidianBg,
                            checkedTrackColor = EmeraldBright
                        )
                    )
                }

                if (martingaleConfig.enabled) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            RiskStrategy.AI_RECOVERY to "Recuperação IA",
                            RiskStrategy.MARTINGALE to "Martingale 2x",
                            RiskStrategy.SOROS to "Soros"
                        ).forEach { (item, chipLabel) ->
                            FilterChip(
                                selected = activeStrategy == item,
                                onClick = { onSelectRiskStrategy(item) },
                                label = { Text(chipLabel) },
                                modifier = Modifier.testTag("risk_strategy_${item.name.lowercase()}"),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldContainer,
                                    selectedLabelColor = EmeraldMint
                                )
                            )
                        }
                    }

                    val isGale = currentMartingaleStep > 0 || accumulatedLossDeficit > 0.005
                    val isAiPaused = activeStrategy == RiskStrategy.AI_RECOVERY &&
                        aiRecoveryPlan?.mode == AiRecoveryMode.PAUSE_PROTECT
                    val statusBg = when {
                        isAiPaused -> BearishContainer.copy(alpha = 0.65f)
                        isGale -> AmberContainer.copy(alpha = 0.65f)
                        else -> EmeraldContainer.copy(alpha = 0.55f)
                    }
                    val statusBorder = when {
                        isAiPaused -> BearishRed.copy(alpha = 0.55f)
                        isGale -> AmberWarning.copy(alpha = 0.5f)
                        else -> EmeraldBright.copy(alpha = 0.4f)
                    }
                    val statusAccent = when {
                        isAiPaused -> BearishRed
                        isGale -> AmberWarning
                        else -> EmeraldMint
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(statusBg, RoundedCornerShape(12.dp))
                            .border(1.dp, statusBorder, RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = when (activeStrategy) {
                                        RiskStrategy.AI_RECOVERY -> if (accumulatedLossDeficit > 0.005) {
                                            aiRecoveryPlan?.mode?.label ?: "Recuperação IA Ativa"
                                        } else {
                                            "Recuperação IA Pronta"
                                        }
                                        RiskStrategy.MARTINGALE -> if (isGale) {
                                            "Gale $currentMartingaleStep de ${martingaleConfig.maxSteps} (Dobrada)"
                                        } else {
                                            "Entrada Inicial"
                                        }
                                        RiskStrategy.SOROS -> if (isGale) {
                                            "Soros Nível $currentMartingaleStep de ${martingaleConfig.sorosMaxLevels}"
                                        } else {
                                            "Soros Inicial"
                                        }
                                        RiskStrategy.FIXED -> "Valor Fixo"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = statusAccent,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Próxima entrada: $sym ${formatStakeAmount(currentBotStake)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (activeStrategy == RiskStrategy.AI_RECOVERY && accumulatedLossDeficit > 0.005) {
                                    val filterSuffix = aiRecoveryPlan?.let { " • Filtro ≥ ${it.minRequiredConfidence}%" }.orEmpty()
                                    Text(
                                        text = "Déficit a recuperar: $sym ${formatStakeAmount(accumulatedLossDeficit)}$filterSuffix",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AmberWarning,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (isPlanningRecovery) {
                                        Text(
                                            text = "IA Gemini calculando estratégia ótima...",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = EmeraldMint
                                        )
                                    } else if (aiRecoveryPlan != null) {
                                        Text(
                                            text = aiRecoveryPlan.rationale,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "Sequência: ${martingaleConfig.martingaleSequencePreview()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .background(TerminalSurface, RoundedCornerShape(8.dp))
                                    .border(1.dp, TerminalBorder, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${sessionWins}V / ${sessionLosses}D",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary
                                )
                            }
                        }

                        // Entrada inicial selector
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Valor inicial",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        onSelectInitialStake((martingaleConfig.initialStake - 1.0).coerceAtLeast(1.0))
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("martingale_stake_minus")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Remove,
                                        contentDescription = "Diminuir entrada inicial",
                                        tint = TextPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Text(
                                    text = formatStakeAmount(martingaleConfig.initialStake),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = EmeraldMint,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(
                                    onClick = {
                                        onSelectInitialStake(martingaleConfig.initialStake + 1.0)
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("martingale_stake_plus")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = "Aumentar entrada inicial",
                                        tint = TextPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(1.0, 2.0, 5.0, 10.0, 20.0, 50.0).forEach { preset ->
                                FilterChip(
                                    selected = kotlin.math.abs(martingaleConfig.initialStake - preset) < 0.01,
                                    onClick = { onSelectInitialStake(preset) },
                                    label = { Text(formatStakeAmount(preset)) },
                                    modifier = Modifier.testTag("martingale_stake_${preset.toInt()}"),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = EmeraldContainer,
                                        selectedLabelColor = EmeraldMint
                                    )
                                )
                            }
                        }

                        Text(
                            text = "Limite de dobras (Gales)",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(1, 2, 3, 5, 7, 8).forEach { steps ->
                                FilterChip(
                                    selected = martingaleConfig.maxSteps == steps,
                                    onClick = { onSelectMaxMartingaleSteps(steps) },
                                    label = { Text("${steps}x") },
                                    modifier = Modifier.testTag("martingale_max_gale_$steps"),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = EmeraldContainer,
                                        selectedLabelColor = EmeraldMint
                                    )
                                )
                            }
                        }

                        // Outcome action buttons (Acertou -> voltar | Perdeu -> dobrar)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onRecordTradeOutcome(TradeOutcome.WIN) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("martingale_win_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BullishContainer,
                                    contentColor = BullishGreen
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (activeStrategy == RiskStrategy.AI_RECOVERY) "Acertou (Zerar)" else "Acertou (Voltar)",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Button(
                                onClick = { onRecordTradeOutcome(TradeOutcome.LOSS) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("martingale_loss_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BearishContainer,
                                    contentColor = BearishRed
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.TrendingDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (activeStrategy == RiskStrategy.AI_RECOVERY) "Perdeu (Plano IA)" else "Perdeu (Dobrar)",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (isGale) {
                            TextButton(
                                onClick = onResetMartingaleCycle,
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .testTag("martingale_reset_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Zerar para entrada inicial", color = TextSecondary)
                            }
                        }

                        if (!lastOutcomeMessage.isNullOrBlank()) {
                            Text(
                                text = lastOutcomeMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = statusAccent
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = onOpenRobotConfig,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("panel_open_robot_config_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        tint = EmeraldBright,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Conta, Ativo, Indicador e Gestão de Risco",
                        color = EmeraldMint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onOpenIqOptionApp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("panel_open_iq_app_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = null,
                        tint = IqOptionOrange,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Abrir aplicativo da IQ Option",
                        color = IqOptionOrange,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (hasMarkedArea) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isReady) {
                    Button(
                        onClick = onSpeakNow,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("speak_now_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldContainer,
                            contentColor = EmeraldMint
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VolumeUp,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_listen_now))
                    }
                }

                Button(
                    onClick = onAnalyzeNow,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("analyze_now_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldBright,
                        contentColor = ObsidianBg
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Analisar")
                }
            }
        } else if (sourceMode == CaptureSourceMode.WEBVIEW) {
            Button(
                onClick = onAutoDetectChart,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bo_config_auto_detect_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldBright,
                    contentColor = ObsidianBg
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Detectar gráfico e analisar")
            }
        }
    }
}

@Composable
private fun HeaderLeadingIcon(analysisState: AnalysisUiState) {
    when (analysisState) {
        is AnalysisUiState.Unavailable -> {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = AmberWarning,
                modifier = Modifier.size(22.dp)
            )
        }
        is AnalysisUiState.Analyzing -> {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = EmeraldBright,
                strokeWidth = 2.dp
            )
        }
        is AnalysisUiState.NotAChart -> {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(BearishContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = BearishRed,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        is AnalysisUiState.Ready -> {
            val direction = analysisState.result.direction
            val bg = when (direction) {
                SignalDirection.CALL_UP -> BullishContainer
                SignalDirection.PUT_DOWN -> BearishContainer
                SignalDirection.NEUTRAL -> AmberContainer
            }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(bg, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (direction == SignalDirection.NEUTRAL) {
                    Icon(
                        imageVector = Icons.Rounded.Remove,
                        contentDescription = "Mercado lateral",
                        tint = AmberWarning,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    TrendRocketIcon(
                        direction = direction,
                        iconSize = 21.dp
                    )
                }
            }
        }
        is AnalysisUiState.Error -> {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = BearishRed,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun TrendRocketIcon(
    direction: SignalDirection,
    iconSize: Dp = 20.dp,
    modifier: Modifier = Modifier
) {
    val isUp = direction == SignalDirection.CALL_UP
    val rocketColor = if (isUp) BullishGreen else BearishRed
    val infiniteTransition = rememberInfiniteTransition(label = "rocket_motion")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rocket_phase"
    )
    // Smooth directional launch/dive travel that resets gently
    val offsetPx = if (isUp) {
        (0.5f - phase) * 8f
    } else {
        (phase - 0.5f) * 8f
    }

    Icon(
        imageVector = Icons.Rounded.Rocket,
        contentDescription = if (isUp) {
            "Foguete verde subindo - tendência de alta"
        } else {
            "Foguete vermelho descendo - tendência de baixa"
        },
        tint = rocketColor,
        modifier = modifier
            .size(iconSize)
            .graphicsLayer {
                rotationZ = if (isUp) 0f else 180f
                translationY = offsetPx
            }
    )
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(TerminalSurfaceElevated, RoundedCornerShape(12.dp))
            .border(1.dp, TerminalBorder, RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatIntervalMmSs(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val mins = safe / 60
    val secs = safe % 60
    return if (mins > 0) String.format("%d:%02d", mins, secs) else "${secs}s"
}

private fun signalTitleAndColor(direction: SignalDirection): Pair<String, Color> {
    return when (direction) {
        SignalDirection.CALL_UP -> "ACIMA (CALL)" to BullishGreen
        SignalDirection.PUT_DOWN -> "ABAIXO (PUT)" to BearishRed
        SignalDirection.NEUTRAL -> "AGUARDAR" to AmberWarning
    }
}
