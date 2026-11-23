package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.BrokerAutoLoginStatus
import com.example.model.AiRecoveryMode
import com.example.model.AiRecoveryPlan
import com.example.model.BoConfig
import com.example.model.BrokerAccountType
import com.example.model.MartingaleConfig
import com.example.model.RiskStrategy
import com.example.model.TechnicalIndicator
import com.example.model.TradeOutcome
import com.example.model.TradingAsset
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

private enum class ActiveConfigPicker {
    ACCOUNT,
    AUTO_LOGIN_VAULT,
    INITIAL_STAKE,
    SESSION_MAX_LOSS,
    DURATION,
    ASSET,
    INDICATOR,
    STRATEGY,
    HELP_MAIN,
    HELP_RISK,
    CONFIRM_RESET_APP
}

@Composable
fun RobotConfigOverlay(
    accountType: BrokerAccountType,
    liveBrokerBalance: Double?,
    autoLoginStatus: BrokerAutoLoginStatus = BrokerAutoLoginStatus(),
    savedAutoLoginEmail: String = "",
    openAutoLoginVaultInitially: Boolean = false,
    boConfig: BoConfig,
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
    lastOutcomeMessage: String?,
    onSelectAccountType: (BrokerAccountType) -> Unit,
    onRefreshBalance: () -> Unit,
    onSaveAutoLoginCredentials: (email: String, password: String, enabled: Boolean) -> Boolean = { _, _, _ -> false },
    onToggleAutoLoginEnabled: (Boolean) -> Unit = {},
    onClearAutoLoginCredentials: () -> Unit = {},
    onTriggerAutoLoginNow: () -> Unit = {},
    onSelectInitialStake: (Double) -> Unit,
    onSelectCurrencySymbol: (String) -> Unit,
    onSelectDurationSeconds: (Int) -> Unit,
    onToggleAutoExpiry: (Boolean) -> Unit = {},
    onToggleStrictThreeCandleStrategy: (Boolean) -> Unit = {},
    onToggleWaitForCandleClose: (Boolean) -> Unit = {},
    onSelectSessionMaxLossLimit: (Double) -> Unit = {},
    onResetSessionRiskGuards: () -> Unit = {},
    onSelectTradingAsset: (TradingAsset) -> Unit,
    onSelectTechnicalIndicator: (TechnicalIndicator) -> Unit,
    onSelectRiskStrategy: (RiskStrategy) -> Unit,
    onSelectMaxMartingaleSteps: (Int) -> Unit,
    onSelectSorosMaxLevels: (Int) -> Unit,
    onRecordTradeOutcome: (TradeOutcome) -> Unit,
    onResetCycle: () -> Unit,
    onOpenIqOptionApp: () -> Unit = {},
    onResetApp: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var activePicker by remember(openAutoLoginVaultInitially) {
        mutableStateOf<ActiveConfigPicker?>(
            if (openAutoLoginVaultInitially) ActiveConfigPicker.AUTO_LOGIN_VAULT else null
        )
    }

    BackHandler(enabled = true) {
        if (activePicker != null) {
            activePicker = null
        } else {
            onDismiss()
        }
    }

    val sym = martingaleConfig.currencySymbol
    val strategy = martingaleConfig.effectiveStrategy

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = ObsidianBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TerminalSurface)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Configurações do Robô",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("close_robot_config_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Fechar configurações",
                        tint = TextSecondary
                    )
                }
            }
            HorizontalDivider(color = TerminalBorder)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // 1. Seleção da conta
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Seleção da conta",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(TerminalSurfaceElevated)
                            .border(1.dp, TerminalBorder, RoundedCornerShape(14.dp))
                            .clickable { activePicker = ActiveConfigPicker.ACCOUNT }
                            .testTag("config_account_card")
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.AccountCircle,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .align(Alignment.BottomEnd)
                                    .background(
                                        if (accountType == BrokerAccountType.DEMO) AmberWarning else EmeraldBright,
                                        CircleShape
                                    )
                                    .border(1.5.dp, TerminalSurfaceElevated, CircleShape)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = accountType.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Text(
                                text = if (liveBrokerBalance != null) {
                                    "$sym ${formatStakeAmount(liveBrokerBalance)}"
                                } else {
                                    "Saldo lido ao abrir gráfico"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(
                            onClick = onRefreshBalance,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Atualizar saldo da corretora",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = TextPrimary
                        )
                    }

                    // Auto-login encrypted vault card
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (autoLoginStatus.enabled) EmeraldContainer.copy(alpha = 0.35f)
                                else TerminalSurfaceElevated
                            )
                            .border(
                                1.dp,
                                if (autoLoginStatus.enabled) EmeraldBright.copy(alpha = 0.6f)
                                else TerminalBorder,
                                RoundedCornerShape(14.dp)
                            )
                            .clickable { activePicker = ActiveConfigPicker.AUTO_LOGIN_VAULT }
                            .testTag("config_auto_login_vault_card")
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    if (autoLoginStatus.enabled) EmeraldBright.copy(alpha = 0.2f)
                                    else TerminalSurface
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = if (autoLoginStatus.enabled) EmeraldBright else TextSecondary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.auto_login_card_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            val subtitle = when {
                                autoLoginStatus.hasCredentials && autoLoginStatus.enabled ->
                                    "Ativo • ${autoLoginStatus.maskedEmail} (relogin automático)"
                                autoLoginStatus.hasCredentials ->
                                    "Pausado • ${autoLoginStatus.maskedEmail} (salvo no cofre)"
                                else -> stringResource(R.string.auto_login_card_inactive)
                            }
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (autoLoginStatus.enabled) EmeraldMint else TextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (autoLoginStatus.hasCredentials) {
                            Switch(
                                checked = autoLoginStatus.enabled,
                                onCheckedChange = onToggleAutoLoginEnabled,
                                modifier = Modifier.testTag("config_auto_login_quick_switch"),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = ObsidianBg,
                                    checkedTrackColor = EmeraldBright
                                )
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                tint = TextPrimary
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onOpenIqOptionApp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("config_open_iq_app_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                            tint = IqOptionOrange,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Abrir aplicativo da IQ Option", color = IqOptionOrange)
                    }
                }

                // 2. Principais opções
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Principais opções",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = { activePicker = ActiveConfigPicker.HELP_MAIN },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                                contentDescription = "Ajuda sobre principais opções",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Row: Valor inicial + Duração
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1.15f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Valor inicial",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(TerminalSurfaceElevated)
                                    .border(1.dp, TerminalBorder, RoundedCornerShape(14.dp))
                                    .clickable { activePicker = ActiveConfigPicker.INITIAL_STAKE }
                                    .testTag("config_initial_stake_card")
                                    .padding(horizontal = 16.dp, vertical = 16.dp)
                            ) {
                                Text(
                                    text = "$sym ${formatStakeAmount(martingaleConfig.initialStake)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Duração",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(TerminalSurfaceElevated)
                                    .border(1.dp, TerminalBorder, RoundedCornerShape(14.dp))
                                    .clickable { activePicker = ActiveConfigPicker.DURATION }
                                    .testTag("config_duration_card")
                                    .padding(horizontal = 16.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (boConfig.autoExpiryEnabled) {
                                        "Auto • ${boConfig.durationShortLabel}"
                                    } else {
                                        boConfig.durationShortLabel
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (boConfig.autoExpiryEnabled) EmeraldMint else TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = TextPrimary
                                )
                            }
                        }
                    }

                    // Ativo de negociação
                    Text(
                        text = "Ativo de negociação",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(TerminalSurfaceElevated)
                            .border(1.dp, TerminalBorder, RoundedCornerShape(14.dp))
                            .clickable { activePicker = ActiveConfigPicker.ASSET }
                            .testTag("config_asset_card")
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DualAssetBadge(asset = boConfig.tradingAsset)
                        Spacer(modifier = Modifier.width(12.dp))
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = boConfig.tradingAsset.label,
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = boConfig.tradingAsset.badgeText,
                                style = MaterialTheme.typography.labelLarge,
                                color = AmberWarning,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = TextPrimary
                        )
                    }

                    // Indicador técnico
                    Text(
                        text = "Indicador técnico",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(TerminalSurfaceElevated)
                            .border(1.dp, TerminalBorder, RoundedCornerShape(14.dp))
                            .clickable { activePicker = ActiveConfigPicker.INDICATOR }
                            .testTag("config_indicator_card")
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IndicatorWaveformBadge()
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = boConfig.technicalIndicator.label,
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Priorizado na análise da IA Gemini",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = TextPrimary
                        )
                    }
                }

                // 2.5 Estratégia de Entrada (3 Condições Simultâneas + Filtro Lateral)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, EmeraldBright.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                        .testTag("config_strategy_rules_card"),
                    shape = RoundedCornerShape(16.dp),
                    color = EmeraldContainer.copy(alpha = 0.35f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = EmeraldBright,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.strategy_card_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = EmeraldMint,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(R.string.strategy_card_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(TerminalSurface.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                                .border(1.dp, TerminalBorder, RoundedCornerShape(12.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.strategy_rule_1),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary
                            )
                            Text(
                                text = stringResource(R.string.strategy_rule_2),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary
                            )
                            Text(
                                text = stringResource(R.string.strategy_rule_3),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary
                            )
                            HorizontalDivider(color = TerminalBorder)
                            Text(
                                text = stringResource(R.string.strategy_rule_filter),
                                style = MaterialTheme.typography.labelMedium,
                                color = AmberWarning,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.strategy_toggle_strict),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = boConfig.strictThreeCandleRsiStrategy,
                                onCheckedChange = onToggleStrictThreeCandleStrategy,
                                modifier = Modifier.testTag("toggle_strict_three_candle_rsi"),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = ObsidianBg,
                                    checkedTrackColor = EmeraldBright
                                )
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.strategy_toggle_candle_close),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = boConfig.waitForCandleClose,
                                onCheckedChange = onToggleWaitForCandleClose,
                                modifier = Modifier.testTag("toggle_wait_candle_close"),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = ObsidianBg,
                                    checkedTrackColor = EmeraldBright
                                )
                            )
                        }
                    }
                }

                // 3. Gerenciamento de Riscos
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Gerenciamento de Riscos",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = { activePicker = ActiveConfigPicker.HELP_RISK },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                                contentDescription = "Ajuda sobre gerenciamento de riscos",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Text(
                        text = "Estratégia",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )

                    val strategyBadgeBg = when (strategy) {
                        RiskStrategy.AI_RECOVERY -> EmeraldBright
                        RiskStrategy.FIXED -> Color(0xFF53A8F5)
                        RiskStrategy.MARTINGALE -> AmberWarning
                        RiskStrategy.SOROS -> Color(0xFF9E7BFF)
                    }
                    val strategyRiskColor = when (strategy) {
                        RiskStrategy.AI_RECOVERY -> EmeraldMint
                        RiskStrategy.FIXED -> Color(0xFF7CBDF8)
                        RiskStrategy.MARTINGALE -> AmberWarning
                        RiskStrategy.SOROS -> Color(0xFFBBA3FF)
                    }
                    val strategyIcon = when (strategy) {
                        RiskStrategy.AI_RECOVERY -> Icons.Rounded.AutoAwesome
                        RiskStrategy.FIXED -> Icons.Rounded.Shield
                        RiskStrategy.MARTINGALE -> Icons.Rounded.TrendingUp
                        RiskStrategy.SOROS -> Icons.Rounded.Autorenew
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(TerminalSurfaceElevated)
                            .border(1.dp, TerminalBorder, RoundedCornerShape(16.dp))
                            .clickable { activePicker = ActiveConfigPicker.STRATEGY }
                            .testTag("config_strategy_card")
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 56.dp, height = 66.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(strategyBadgeBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = strategyIcon,
                                contentDescription = null,
                                tint = ObsidianBg,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = strategy.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Speed,
                                    contentDescription = null,
                                    tint = strategyRiskColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = strategy.riskLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = strategyRiskColor,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = "Saldo recomendado: pelo menos ${formatStakeAmount(martingaleConfig.recommendedBankroll())} $sym",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = TextPrimary
                        )
                    }

                    // Active AI Recovery, Martingale or Soros management card
                    if (strategy != RiskStrategy.FIXED) {
                        val isStepped = currentMartingaleStep > 0 || accumulatedLossDeficit > 0.005
                        val isPausedByAi = strategy == RiskStrategy.AI_RECOVERY &&
                            aiRecoveryPlan?.mode == AiRecoveryMode.PAUSE_PROTECT
                        val cardBg = when {
                            isPausedByAi -> BearishContainer.copy(alpha = 0.65f)
                            isStepped -> AmberContainer.copy(alpha = 0.65f)
                            else -> EmeraldContainer.copy(alpha = 0.45f)
                        }
                        val cardBorder = when {
                            isPausedByAi -> BearishRed.copy(alpha = 0.55f)
                            isStepped -> AmberWarning.copy(alpha = 0.5f)
                            else -> EmeraldBright.copy(alpha = 0.35f)
                        }
                        val accent = when {
                            isPausedByAi -> BearishRed
                            isStepped -> AmberWarning
                            else -> EmeraldMint
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(cardBg)
                                .border(1.dp, cardBorder, RoundedCornerShape(14.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    val cycleText = when (strategy) {
                                        RiskStrategy.AI_RECOVERY -> if (accumulatedLossDeficit > 0.005) {
                                            aiRecoveryPlan?.mode?.label ?: "Recuperação IA Ativa"
                                        } else {
                                            "Recuperação IA Pronta (Sem perdas)"
                                        }
                                        RiskStrategy.MARTINGALE -> if (isStepped) {
                                            "Gale $currentMartingaleStep de ${martingaleConfig.maxSteps} (Dobrada)"
                                        } else {
                                            "Entrada Inicial"
                                        }
                                        RiskStrategy.SOROS -> if (isStepped) {
                                            "Soros Nível $currentMartingaleStep de ${martingaleConfig.sorosMaxLevels}"
                                        } else {
                                            "Soros Inicial"
                                        }
                                        RiskStrategy.FIXED -> "Valor Fixo"
                                    }
                                    Text(
                                        text = cycleText,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = accent,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Próxima entrada: $sym ${formatStakeAmount(currentBotStake)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (strategy == RiskStrategy.AI_RECOVERY && accumulatedLossDeficit > 0.005) {
                                        val filterNote = aiRecoveryPlan?.let { " • Filtro ≥ ${it.minRequiredConfidence}%" }.orEmpty()
                                        Text(
                                            text = "A recuperar: $sym ${formatStakeAmount(accumulatedLossDeficit)}$filterNote",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AmberWarning,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (isPlanningRecovery) {
                                            Text(
                                                text = "IA Gemini refinando estratégia de recuperação...",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = EmeraldMint
                                            )
                                        } else if (aiRecoveryPlan != null) {
                                            Text(
                                                text = aiRecoveryPlan.rationale,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextSecondary
                                            )
                                        }
                                    } else if (strategy == RiskStrategy.AI_RECOVERY) {
                                        Text(
                                            text = "Sequência base (payout 85%): ${martingaleConfig.martingaleSequencePreview()}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
                                        )
                                    } else if (strategy == RiskStrategy.MARTINGALE) {
                                        Text(
                                            text = "Sequência: ${martingaleConfig.martingaleSequencePreview()}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
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

                            if (strategy == RiskStrategy.MARTINGALE || strategy == RiskStrategy.AI_RECOVERY) {
                                Text(
                                    text = if (strategy == RiskStrategy.AI_RECOVERY) {
                                        "Limite máximo de etapas de recuperação IA"
                                    } else {
                                        "Máximo de dobras ao perder (Gales)"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(1, 2, 3, 5, 7).forEach { steps ->
                                        FilterChip(
                                            selected = martingaleConfig.maxSteps == steps,
                                            onClick = { onSelectMaxMartingaleSteps(steps) },
                                            label = { Text("${steps}x") },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = EmeraldContainer,
                                                selectedLabelColor = EmeraldMint
                                            )
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = "Níveis de Soros ao acertar",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(1, 2, 3, 4).forEach { levels ->
                                        FilterChip(
                                            selected = martingaleConfig.sorosMaxLevels == levels,
                                            onClick = { onSelectSorosMaxLevels(levels) },
                                            label = { Text("${levels}x") },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = EmeraldContainer,
                                                selectedLabelColor = EmeraldMint
                                            )
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onRecordTradeOutcome(TradeOutcome.WIN) },
                                    modifier = Modifier.weight(1f),
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
                                        text = when (strategy) {
                                            RiskStrategy.AI_RECOVERY -> "Acertou (Zerar)"
                                            RiskStrategy.MARTINGALE -> "Acertou (Voltar)"
                                            else -> "Acertou"
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Button(
                                    onClick = { onRecordTradeOutcome(TradeOutcome.LOSS) },
                                    modifier = Modifier.weight(1f),
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
                                        text = when (strategy) {
                                            RiskStrategy.AI_RECOVERY -> "Perdeu (Plano IA)"
                                            RiskStrategy.MARTINGALE -> "Perdeu (Dobrar)"
                                            else -> "Perdeu"
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (isStepped) {
                                TextButton(
                                    onClick = onResetCycle,
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("Zerar ciclo para $sym ${formatStakeAmount(martingaleConfig.initialStake)}", color = TextSecondary)
                                }
                            }

                            if (!lastOutcomeMessage.isNullOrBlank()) {
                                Text(
                                    text = lastOutcomeMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = accent
                                )
                            }
                        }
                    }

                    // Session Stop Loss + 2-Loss 5-Minute Pause + No Infinite Martingale card
                    val stopLimitStr = if (martingaleConfig.sessionMaxLossLimit > 0.0) {
                        "$sym ${formatStakeAmount(martingaleConfig.sessionMaxLossLimit)}"
                    } else {
                        "Desativado"
                    }
                    val sessionLossStr = "$sym ${formatStakeAmount(sessionNetLoss)}"
                    val guardBorder = when {
                        isSessionStopLossHit -> BearishRed.copy(alpha = 0.7f)
                        lossPauseRemainingSec > 0 -> AmberWarning.copy(alpha = 0.7f)
                        else -> TerminalBorder
                    }
                    val guardBg = when {
                        isSessionStopLossHit -> BearishContainer.copy(alpha = 0.65f)
                        lossPauseRemainingSec > 0 -> AmberContainer.copy(alpha = 0.55f)
                        else -> TerminalSurfaceElevated
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(guardBg)
                            .border(1.dp, guardBorder, RoundedCornerShape(14.dp))
                            .clickable { activePicker = ActiveConfigPicker.SESSION_MAX_LOSS }
                            .testTag("config_session_stop_loss_card")
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Shield,
                                    contentDescription = null,
                                    tint = if (isSessionStopLossHit) BearishRed else EmeraldBright,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = stringResource(R.string.session_stop_loss_title),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Perda na sessão: $sessionLossStr / $stopLimitStr",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSessionStopLossHit) BearishRed else TextSecondary,
                                        fontWeight = if (isSessionStopLossHit) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                tint = TextPrimary
                            )
                        }

                        Text(
                            text = stringResource(R.string.session_stop_loss_no_martingale_note),
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldMint
                        )

                        if (lossPauseRemainingSec > 0) {
                            val pauseMins = lossPauseRemainingSec / 60
                            val pauseSecs = lossPauseRemainingSec % 60
                            val formattedPause = String.format(java.util.Locale.US, "%02d:%02d", pauseMins, pauseSecs)
                            Text(
                                text = "Pausa ativa (2 derrotas seguidas): retomando em $formattedPause",
                                style = MaterialTheme.typography.labelMedium,
                                color = AmberWarning,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (isSessionStopLossHit || lossPauseRemainingSec > 0 || sessionNetLoss > 0.005) {
                            OutlinedButton(
                                onClick = onResetSessionRiskGuards,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("config_reset_session_button"),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSessionStopLossHit) BearishRed else EmeraldBright
                                )
                            ) {
                                Text(
                                    text = stringResource(R.string.action_reset_session),
                                    color = if (isSessionStopLossHit) BearishRed else EmeraldMint,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                OwnerContactCard()

                // Maintenance / Full App Reset card
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BearishRed.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    color = BearishContainer.copy(alpha = 0.45f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = null,
                                tint = BearishRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(R.string.reset_app_section_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = stringResource(R.string.reset_app_section_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        OutlinedButton(
                            onClick = { activePicker = ActiveConfigPicker.CONFIRM_RESET_APP },
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("reset_app_button"),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BearishRed.copy(alpha = 0.7f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = BearishContainer.copy(alpha = 0.65f),
                                contentColor = BearishRed
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.action_reset_app),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Bottom apply button
            HorizontalDivider(color = TerminalBorder)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TerminalSurface)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("apply_robot_config_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldBright,
                        contentColor = ObsidianBg
                    )
                ) {
                    Text("Salvar e voltar ao gráfico", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Sub-pickers
    when (activePicker) {
        ActiveConfigPicker.ACCOUNT -> {
            AlertDialog(
                onDismissRequest = { activePicker = null },
                containerColor = TerminalSurface,
                title = { Text("Seleção da conta", color = TextPrimary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BrokerAccountType.entries.forEach { type ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        onSelectAccountType(type)
                                        onRefreshBalance()
                                        activePicker = null
                                    }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = accountType == type,
                                    onClick = {
                                        onSelectAccountType(type)
                                        onRefreshBalance()
                                        activePicker = null
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = EmeraldBright)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(type.label, color = TextPrimary)
                            }
                        }
                        Text(
                            text = "Troque também entre Demo/Real na própria tela da IQ Option; o saldo acima acompanha a página aberta.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { activePicker = null }) {
                        Text("Fechar", color = EmeraldBright)
                    }
                }
            )
        }

        ActiveConfigPicker.INITIAL_STAKE -> {
            var inputText by remember(martingaleConfig.initialStake) {
                mutableStateOf(formatStakeAmount(martingaleConfig.initialStake))
            }
            AlertDialog(
                onDismissRequest = { activePicker = null },
                containerColor = TerminalSurface,
                title = { Text("Valor inicial da entrada", color = TextPrimary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("R$", "$").forEach { symbolOption ->
                                FilterChip(
                                    selected = martingaleConfig.currencySymbol == symbolOption,
                                    onClick = { onSelectCurrencySymbol(symbolOption) },
                                    label = { Text(symbolOption) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = EmeraldContainer,
                                        selectedLabelColor = EmeraldMint
                                    )
                                )
                            }
                        }
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { raw ->
                                inputText = raw.filter { it.isDigit() || it == '.' || it == ',' }
                            },
                            label = { Text("Valor (${martingaleConfig.currencySymbol})") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldBright,
                                unfocusedBorderColor = TerminalBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0).forEach { preset ->
                                FilterChip(
                                    selected = kotlin.math.abs(martingaleConfig.initialStake - preset) < 0.01,
                                    onClick = {
                                        onSelectInitialStake(preset)
                                        inputText = formatStakeAmount(preset)
                                    },
                                    label = { Text("$sym ${formatStakeAmount(preset)}") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = EmeraldContainer,
                                        selectedLabelColor = EmeraldMint
                                    )
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val parsed = inputText.replace(',', '.').toDoubleOrNull()
                            if (parsed != null && parsed >= 1.0) {
                                onSelectInitialStake(parsed)
                            }
                            activePicker = null
                        }
                    ) {
                        Text("Confirmar", color = EmeraldBright)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { activePicker = null }) {
                        Text("Cancelar", color = TextSecondary)
                    }
                }
            )
        }

        ActiveConfigPicker.SESSION_MAX_LOSS -> {
            var stopInput by remember(martingaleConfig.sessionMaxLossLimit) {
                mutableStateOf(formatStakeAmount(martingaleConfig.sessionMaxLossLimit))
            }
            AlertDialog(
                onDismissRequest = { activePicker = null },
                containerColor = TerminalSurface,
                title = { Text("Limite máximo de perda por sessão", color = TextPrimary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Quando a perda acumulada na sessão atingir este valor, o robô para automaticamente para proteger sua banca (sem martingale infinito).",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        OutlinedTextField(
                            value = stopInput,
                            onValueChange = { raw ->
                                stopInput = raw.filter { it.isDigit() || it == '.' || it == ',' }
                            },
                            label = { Text("Stop Loss da sessão ($sym)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldBright,
                                unfocusedBorderColor = TerminalBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("session_stop_loss_input")
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(10.0, 20.0, 30.0, 50.0, 100.0, 200.0).forEach { preset ->
                                FilterChip(
                                    selected = kotlin.math.abs(martingaleConfig.sessionMaxLossLimit - preset) < 0.01,
                                    onClick = {
                                        onSelectSessionMaxLossLimit(preset)
                                        stopInput = formatStakeAmount(preset)
                                    },
                                    label = { Text("$sym ${formatStakeAmount(preset)}") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = EmeraldContainer,
                                        selectedLabelColor = EmeraldMint
                                    )
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val parsed = stopInput.replace(',', '.').toDoubleOrNull()
                            if (parsed != null && parsed >= 0.0) {
                                onSelectSessionMaxLossLimit(parsed)
                            }
                            activePicker = null
                        }
                    ) {
                        Text("Confirmar", color = EmeraldBright)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { activePicker = null }) {
                        Text("Cancelar", color = TextSecondary)
                    }
                }
            )
        }

        ActiveConfigPicker.DURATION -> {
            AlertDialog(
                onDismissRequest = { activePicker = null },
                containerColor = TerminalSurface,
                title = { Text("Duração da leitura / entrada", color = TextPrimary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (boConfig.autoExpiryEnabled) EmeraldContainer.copy(alpha = 0.45f) else Color.Transparent
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (boConfig.autoExpiryEnabled) EmeraldBright.copy(alpha = 0.6f) else TerminalBorder,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    onToggleAutoExpiry(true)
                                    activePicker = null
                                }
                                .testTag("config_duration_auto_option")
                                .padding(vertical = 8.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = boConfig.autoExpiryEnabled,
                                onClick = {
                                    onToggleAutoExpiry(true)
                                    activePicker = null
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = EmeraldBright)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.auto_expiry_option_title),
                                    color = EmeraldMint,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(R.string.auto_expiry_option_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                        HorizontalDivider(color = TerminalBorder, modifier = Modifier.padding(vertical = 4.dp))
                        listOf(
                            30 to "30s (Fixo)",
                            60 to "1 min • M1 (Fixo)",
                            120 to "2 min • M2 (Fixo)",
                            300 to "5 min • M5 (Fixo)",
                            900 to "15 min • M15 (Fixo)"
                        ).forEach { (sec, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        onSelectDurationSeconds(sec)
                                        activePicker = null
                                    }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = !boConfig.autoExpiryEnabled && boConfig.expirySeconds == sec,
                                    onClick = {
                                        onSelectDurationSeconds(sec)
                                        activePicker = null
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = EmeraldBright)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(label, color = TextPrimary)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { activePicker = null }) {
                        Text("Fechar", color = EmeraldBright)
                    }
                }
            )
        }

        ActiveConfigPicker.ASSET -> {
            AlertDialog(
                onDismissRequest = { activePicker = null },
                containerColor = TerminalSurface,
                title = { Text("Ativo de negociação", color = TextPrimary) },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TradingAsset.entries.forEach { asset ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        onSelectTradingAsset(asset)
                                        activePicker = null
                                    }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = boConfig.tradingAsset == asset,
                                    onClick = {
                                        onSelectTradingAsset(asset)
                                        activePicker = null
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = EmeraldBright)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(asset.label, color = TextPrimary)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { activePicker = null }) {
                        Text("Fechar", color = EmeraldBright)
                    }
                }
            )
        }

        ActiveConfigPicker.INDICATOR -> {
            AlertDialog(
                onDismissRequest = { activePicker = null },
                containerColor = TerminalSurface,
                title = { Text("Indicador técnico", color = TextPrimary) },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TechnicalIndicator.entries.forEach { indicator ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        onSelectTechnicalIndicator(indicator)
                                        activePicker = null
                                    }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = boConfig.technicalIndicator == indicator,
                                    onClick = {
                                        onSelectTechnicalIndicator(indicator)
                                        activePicker = null
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = EmeraldBright)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(indicator.label, color = TextPrimary)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { activePicker = null }) {
                        Text("Fechar", color = EmeraldBright)
                    }
                }
            )
        }

        ActiveConfigPicker.STRATEGY -> {
            AlertDialog(
                onDismissRequest = { activePicker = null },
                containerColor = TerminalSurface,
                title = { Text("Estratégia de Gerenciamento", color = TextPrimary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        RiskStrategy.entries.forEach { item ->
                            val selected = strategy == item
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) EmeraldContainer.copy(alpha = 0.55f) else TerminalSurfaceElevated)
                                    .border(
                                        1.dp,
                                        if (selected) EmeraldBright else TerminalBorder,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        onSelectRiskStrategy(item)
                                        activePicker = null
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                RadioButton(
                                    selected = selected,
                                    onClick = {
                                        onSelectRiskStrategy(item)
                                        activePicker = null
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = EmeraldBright)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = item.riskLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (item == RiskStrategy.MARTINGALE) AmberWarning else EmeraldMint
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = item.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { activePicker = null }) {
                        Text("Fechar", color = EmeraldBright)
                    }
                }
            )
        }

        ActiveConfigPicker.HELP_MAIN -> {
            AlertDialog(
                onDismissRequest = { activePicker = null },
                containerColor = TerminalSurface,
                title = { Text("Principais opções", color = TextPrimary) },
                text = {
                    Text(
                        text = "Defina o valor inicial de cada operação, a duração (tempo entre análises e expiração), o ativo em foco e qual indicador técnico (como RSI ou EMA) a IA Gemini deve priorizar na leitura do gráfico.",
                        color = TextSecondary
                    )
                },
                confirmButton = {
                    TextButton(onClick = { activePicker = null }) {
                        Text("Entendi", color = EmeraldBright)
                    }
                }
            )
        }

        ActiveConfigPicker.HELP_RISK -> {
            AlertDialog(
                onDismissRequest = { activePicker = null },
                containerColor = TerminalSurface,
                title = { Text("Gerenciamento de Riscos", color = TextPrimary) },
                text = {
                    Text(
                        text = "• Recuperação Inteligente IA: a própria IA calcula o valor exato considerando o payout (~85%) para recuperar perdas acumuladas, aumenta o filtro de tendência na entrada de recuperação e pausa se a banca estiver em risco.\n• Valor fixo: entra sempre com o valor inicial.\n• Dobrar na perda (Martingale): dobra o valor ao perder e volta ao valor inicial quando acerta.\n• Soros: reinveste o lucro ao acertar até o nível escolhido e volta ao valor inicial ao perder.",
                        color = TextSecondary
                    )
                },
                confirmButton = {
                    TextButton(onClick = { activePicker = null }) {
                        Text("Entendi", color = EmeraldBright)
                    }
                }
            )
        }

        ActiveConfigPicker.CONFIRM_RESET_APP -> {
            AlertDialog(
                onDismissRequest = { activePicker = null },
                containerColor = TerminalSurface,
                modifier = Modifier.testTag("confirm_reset_app_dialog"),
                title = {
                    Text(
                        text = stringResource(R.string.reset_app_confirm_title),
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.reset_app_confirm_body),
                        color = TextSecondary
                    )
                },
                dismissButton = {
                    TextButton(
                        onClick = { activePicker = null },
                        modifier = Modifier.testTag("reset_app_cancel_button")
                    ) {
                        Text(stringResource(R.string.action_cancel), color = TextSecondary)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            activePicker = null
                            onResetApp()
                        },
                        modifier = Modifier.testTag("reset_app_confirm_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BearishRed,
                            contentColor = TextPrimary
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.reset_app_confirm_button),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            )
        }

        ActiveConfigPicker.AUTO_LOGIN_VAULT -> {
            var emailDraft by remember(savedAutoLoginEmail) { mutableStateOf(savedAutoLoginEmail) }
            var passwordDraft by remember { mutableStateOf("") }
            var passwordVisible by remember { mutableStateOf(false) }
            var enableDraft by remember(autoLoginStatus.enabled, autoLoginStatus.hasCredentials) {
                mutableStateOf(if (autoLoginStatus.hasCredentials) autoLoginStatus.enabled else true)
            }
            var validationError by remember { mutableStateOf<String?>(null) }
            val invalidMsg = stringResource(R.string.auto_login_invalid_toast)

            AlertDialog(
                onDismissRequest = { activePicker = null },
                containerColor = TerminalSurface,
                modifier = Modifier.testTag("auto_login_vault_dialog"),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = EmeraldBright,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.auto_login_dialog_title),
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = stringResource(R.string.auto_login_dialog_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        if (autoLoginStatus.hasCredentials) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(EmeraldContainer.copy(alpha = 0.45f))
                                    .border(1.dp, EmeraldBright.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = EmeraldBright,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Conta salva e criptografada: ${autoLoginStatus.maskedEmail}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = EmeraldMint,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        OutlinedTextField(
                            value = emailDraft,
                            onValueChange = {
                                emailDraft = it
                                validationError = null
                            },
                            label = { Text(stringResource(R.string.auto_login_email_label)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldBright,
                                unfocusedBorderColor = TerminalBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedLabelColor = EmeraldMint,
                                unfocusedLabelColor = TextSecondary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auto_login_email_input")
                        )
                        OutlinedTextField(
                            value = passwordDraft,
                            onValueChange = {
                                passwordDraft = it
                                validationError = null
                            },
                            label = { Text(stringResource(R.string.auto_login_password_label)) },
                            placeholder = if (autoLoginStatus.hasCredentials) {
                                {
                                    Text(
                                        text = stringResource(R.string.auto_login_saved_password_hint),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextMuted
                                    )
                                }
                            } else {
                                null
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            visualTransformation = if (passwordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { passwordVisible = !passwordVisible },
                                    modifier = Modifier.testTag("auto_login_toggle_password_visibility")
                                ) {
                                    Icon(
                                        imageVector = if (passwordVisible) {
                                            Icons.Rounded.VisibilityOff
                                        } else {
                                            Icons.Rounded.Visibility
                                        },
                                        contentDescription = if (passwordVisible) {
                                            "Ocultar senha"
                                        } else {
                                            "Mostrar senha"
                                        },
                                        tint = TextSecondary
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldBright,
                                unfocusedBorderColor = TerminalBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedLabelColor = EmeraldMint,
                                unfocusedLabelColor = TextSecondary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auto_login_password_input")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.auto_login_enable_toggle),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = enableDraft,
                                onCheckedChange = { enableDraft = it },
                                modifier = Modifier.testTag("auto_login_enable_switch"),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = ObsidianBg,
                                    checkedTrackColor = EmeraldBright
                                )
                            )
                        }
                        if (validationError != null) {
                            Text(
                                text = validationError.orEmpty(),
                                style = MaterialTheme.typography.labelMedium,
                                color = BearishRed
                            )
                        }
                        if (autoLoginStatus.hasCredentials) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        activePicker = null
                                        onTriggerAutoLoginNow()
                                    },
                                    modifier = Modifier.testTag("auto_login_trigger_now_button"),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldBright)
                                ) {
                                    Text(
                                        text = stringResource(R.string.auto_login_trigger_now_button),
                                        color = EmeraldMint,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        onClearAutoLoginCredentials()
                                        activePicker = null
                                    },
                                    modifier = Modifier.testTag("auto_login_clear_button")
                                ) {
                                    Text(
                                        text = stringResource(R.string.auto_login_clear_button),
                                        color = BearishRed,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { activePicker = null },
                        modifier = Modifier.testTag("auto_login_cancel_button")
                    ) {
                        Text(stringResource(R.string.action_cancel), color = TextSecondary)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val saved = onSaveAutoLoginCredentials(
                                emailDraft.trim(),
                                passwordDraft,
                                enableDraft
                            )
                            if (saved) {
                                activePicker = null
                                if (enableDraft) {
                                    onTriggerAutoLoginNow()
                                }
                            } else {
                                validationError = invalidMsg
                            }
                        },
                        modifier = Modifier.testTag("auto_login_save_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldBright,
                            contentColor = ObsidianBg
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.auto_login_save_button),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            )
        }

        null -> Unit
    }
}

@Composable
private fun DualAssetBadge(asset: TradingAsset) {
    val primaryColor = when (asset) {
        TradingAsset.GOLD_SILVER, TradingAsset.BTC_USD -> AmberWarning
        TradingAsset.AUTO -> EmeraldBright
        else -> Color(0xFF4C8DFF)
    }
    val secondaryColor = when (asset) {
        TradingAsset.GOLD_SILVER -> Color(0xFFB7C0CE)
        else -> EmeraldMint
    }
    Box(modifier = Modifier.size(width = 36.dp, height = 28.dp)) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .align(Alignment.TopStart)
                .background(primaryColor, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(20.dp)
                .align(Alignment.BottomEnd)
                .background(secondaryColor, CircleShape)
                .border(1.5.dp, TerminalSurfaceElevated, CircleShape)
        )
    }
}

@Composable
private fun IndicatorWaveformBadge() {
    Box(
        modifier = Modifier
            .size(width = 58.dp, height = 42.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(ObsidianBg)
            .border(1.dp, TerminalBorder, RoundedCornerShape(8.dp))
            .padding(6.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val dash = PathEffect.dashPathEffect(floatArrayOf(5f, 4f), 0f)
            drawLine(
                color = Color(0xFF475266),
                start = Offset(0f, h * 0.25f),
                end = Offset(w, h * 0.25f),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dash
            )
            drawLine(
                color = Color(0xFF475266),
                start = Offset(0f, h * 0.75f),
                end = Offset(w, h * 0.75f),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dash
            )

            val wave = Path().apply {
                moveTo(0f, h * 0.68f)
                cubicTo(w * 0.14f, h * 0.18f, w * 0.25f, h * 0.92f, w * 0.42f, h * 0.45f)
                cubicTo(w * 0.56f, h * 0.08f, w * 0.68f, h * 0.88f, w * 0.82f, h * 0.36f)
                lineTo(w, h * 0.56f)
            }
            drawPath(
                path = wave,
                color = Color(0xFF9E7BFF),
                style = Stroke(
                    width = 2.2.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}
