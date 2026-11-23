package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.TerminalBorder
import com.example.ui.theme.TerminalSurface
import com.example.ui.theme.TerminalSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

const val OWNER_PHONE_E164 = "+5511930197088"
const val OWNER_WHATSAPP_DIGITS = "5511930197088"

fun openOwnerPhoneDialer(context: Context) {
    runCatching {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$OWNER_PHONE_E164")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }.onFailure {
        copyOwnerPhoneToClipboard(context)
    }
}

fun openOwnerWhatsApp(context: Context) {
    runCatching {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://wa.me/$OWNER_WHATSAPP_DIGITS")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }.onFailure {
        openOwnerPhoneDialer(context)
    }
}

fun copyOwnerPhoneToClipboard(context: Context) {
    runCatching {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("Telefone Roberto Silva", OWNER_PHONE_E164))
        Toast.makeText(context, "Telefone copiado: $OWNER_PHONE_E164", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun OwnerContactCard(
    modifier: Modifier = Modifier,
    showCopyAction: Boolean = true
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(TerminalSurfaceElevated)
            .border(1.dp, EmeraldBright.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .padding(12.dp)
            .testTag("owner_contact_card"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(EmeraldContainer, CircleShape)
                    .border(1.dp, EmeraldBright.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Phone,
                    contentDescription = stringResource(R.string.cd_owner_phone),
                    tint = EmeraldMint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.owner_contact_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = EmeraldMint,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.owner_phone_display),
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("owner_phone_number_text")
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ContactActionPill(
                label = stringResource(R.string.action_call_phone),
                icon = Icons.Rounded.Phone,
                containerColor = EmeraldContainer,
                borderColor = EmeraldBright.copy(alpha = 0.65f),
                contentColor = EmeraldMint,
                testTag = "owner_call_button",
                modifier = Modifier.weight(1f),
                onClick = { openOwnerPhoneDialer(context) }
            )
            ContactActionPill(
                label = stringResource(R.string.action_whatsapp),
                icon = Icons.AutoMirrored.Rounded.Chat,
                containerColor = EmeraldContainer,
                borderColor = EmeraldBright.copy(alpha = 0.65f),
                contentColor = EmeraldMint,
                testTag = "owner_whatsapp_button",
                modifier = Modifier.weight(1f),
                onClick = { openOwnerWhatsApp(context) }
            )
            if (showCopyAction) {
                ContactActionPill(
                    label = stringResource(R.string.action_copy_phone),
                    icon = Icons.Rounded.ContentCopy,
                    containerColor = TerminalSurface,
                    borderColor = TerminalBorder,
                    contentColor = TextSecondary,
                    testTag = "owner_copy_phone_button",
                    modifier = Modifier.weight(0.9f),
                    onClick = { copyOwnerPhoneToClipboard(context) }
                )
            }
        }
    }
}

@Composable
private fun ContactActionPill(
    label: String,
    icon: ImageVector,
    containerColor: Color,
    borderColor: Color,
    contentColor: Color,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
