package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.TrendLensUiState
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.IqOptionOrange
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TerminalBorder
import com.example.ui.theme.TerminalSurface
import com.example.ui.theme.TerminalSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun HelpDialog(
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenIqOptionApp: () -> Unit = {},
    onOpenQrCode: () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TerminalSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = {
            Text(
                text = "Como usar o Trad do futuro Roberto Silva",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OwnerContactCard()

                HelpStepRow(
                    step = "1",
                    title = "Acesse a IQ Option",
                    description = "Entre na sua conta demo ou real pelo navegador integrado acima ou envie um print do gráfico pela galeria."
                )
                HelpStepRow(
                    step = "2",
                    title = "Marque a área do gráfico",
                    description = "Toque em \"Marcar área\" e ajuste o retângulo sobre os candles e indicadores (como RSI)."
                )
                HelpStepRow(
                    step = "3",
                    title = "Leitura a cada 1 min e Robô Auto-Clique",
                    description = "Ao confirmar a área, a IA Gemini analisa o gráfico a cada 1 minuto, fala quando detectar alta ou baixa e clica automaticamente em Acima ou Abaixo com o Robô ativo."
                )

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Atalhos rápidos da IQ Option",
                    style = MaterialTheme.typography.labelMedium,
                    color = EmeraldMint
                )

                QuickUrlChip(
                    label = "QR Code do aplicativo",
                    icon = Icons.Rounded.QrCode2,
                    iconTint = EmeraldMint,
                    onClick = {
                        onDismiss()
                        onOpenQrCode()
                    }
                )
                QuickUrlChip(
                    label = "Abrir aplicativo da IQ Option",
                    icon = Icons.AutoMirrored.Rounded.OpenInNew,
                    iconTint = IqOptionOrange,
                    onClick = {
                        onOpenIqOptionApp()
                        onDismiss()
                    }
                )
                QuickUrlChip(
                    label = "IQ Option Traderoom",
                    onClick = {
                        onOpenUrl(TrendLensUiState.TRADEROOM_URL)
                        onDismiss()
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Entendi", color = EmeraldBright)
            }
        }
    )
}

@Composable
private fun HelpStepRow(
    step: String,
    title: String,
    description: String
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(EmeraldContainer, CircleShape)
                .border(1.dp, EmeraldBright.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = step,
                style = MaterialTheme.typography.labelSmall,
                color = EmeraldMint
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun QuickUrlChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Rounded.Language,
    iconTint: androidx.compose.ui.graphics.Color = EmeraldBright,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(TerminalSurfaceElevated)
            .border(1.dp, TerminalBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextPrimary
        )
    }
}
