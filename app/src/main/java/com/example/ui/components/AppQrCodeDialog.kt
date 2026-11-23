package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.BrokerSessionPrefs
import com.example.ui.theme.BearishRed
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TerminalBorder
import com.example.ui.theme.TerminalSurface
import com.example.ui.theme.TerminalSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

fun generateQrCodeBitmap(content: String, sizePx: Int = 512): Bitmap? {
    if (content.isBlank() || sizePx < 64) return null
    return runCatching {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 2
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val width = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE
            }
        }
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }.getOrNull()
}

@Composable
fun AppQrCodeDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var savedAppUrl by remember {
        mutableStateOf(BrokerSessionPrefs.getCustomAppShareUrl(context))
    }
    var urlInput by remember {
        mutableStateOf(savedAppUrl.orEmpty())
    }
    var showInvalidError by remember { mutableStateOf(false) }
    var isExportingApk by remember { mutableStateOf(false) }

    val qrBitmap = remember(savedAppUrl) {
        savedAppUrl?.let { generateQrCodeBitmap(it, sizePx = 560) }
    }

    fun applyAppUrlInput(raw: String) {
        urlInput = raw
        if (raw.isBlank()) {
            showInvalidError = false
            savedAppUrl = BrokerSessionPrefs.saveCustomAppShareUrl(context, null)
            return
        }
        val normalized = BrokerSessionPrefs.normalizeShareUrl(raw)
        if (normalized != null) {
            showInvalidError = false
            savedAppUrl = BrokerSessionPrefs.saveCustomAppShareUrl(context, normalized)
        } else {
            showInvalidError = true
            savedAppUrl = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(TerminalSurface)
                .border(1.dp, TerminalBorder, RoundedCornerShape(24.dp))
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .testTag("qr_code_dialog"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(EmeraldContainer, CircleShape)
                            .border(1.dp, EmeraldBright.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.QrCode2,
                            contentDescription = null,
                            tint = EmeraldMint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.qr_dialog_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldMint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.owner_phone_display),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("qr_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.action_cancel),
                        tint = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (qrBitmap != null && savedAppUrl != null) {
                // Crisp white QR frame for instant camera scanning of the App + Robot APK link
                Box(
                    modifier = Modifier
                        .size(232.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White)
                        .border(2.dp, EmeraldBright.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.cd_qr_code),
                        filterQuality = FilterQuality.None,
                        modifier = Modifier
                            .size(208.dp)
                            .testTag("qr_code_image")
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(ObsidianBg)
                        .border(1.dp, EmeraldBright.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                        .padding(16.dp)
                        .testTag("qr_setup_guide"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Link,
                            contentDescription = null,
                            tint = EmeraldMint,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "QR Code do App + Robô",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "1. Toque em \"Enviar APK (App + Robô)\" abaixo e salve no Google Drive (ou envie direto para outro celular).\n" +
                            "2. No Google Drive, marque \"Qualquer pessoa com o link\" e copie o link.\n" +
                            "3. Cole o link abaixo para gerar e salvar o QR Code oficial do aplicativo completo com robô!",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (savedAppUrl != null) {
                    stringResource(R.string.qr_hint_app)
                } else {
                    stringResource(R.string.qr_hint_app_empty)
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = urlInput,
                onValueChange = { applyAppUrlInput(it) },
                label = {
                    Text(
                        text = stringResource(R.string.qr_input_label),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                placeholder = {
                    Text(
                        text = stringResource(R.string.qr_input_placeholder),
                        color = TextMuted
                    )
                },
                singleLine = true,
                isError = showInvalidError,
                supportingText = if (showInvalidError) {
                    {
                        Text(
                            text = stringResource(R.string.qr_error_invalid_link),
                            color = BearishRed
                        )
                    }
                } else null,
                trailingIcon = {
                    IconButton(
                        onClick = {
                            val pasted = readClipboardText(context)
                            if (!pasted.isNullOrBlank()) {
                                applyAppUrlInput(pasted)
                            } else {
                                Toast.makeText(
                                    context,
                                    "Área de transferência vazia.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("qr_paste_link_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentPaste,
                            contentDescription = stringResource(R.string.action_paste),
                            tint = EmeraldMint
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EmeraldBright,
                    unfocusedBorderColor = TerminalBorder,
                    focusedLabelColor = EmeraldMint,
                    unfocusedLabelColor = TextSecondary,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = EmeraldBright
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("qr_app_link_input")
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (savedAppUrl != null && qrBitmap != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { copyTextToClipboard(context, savedAppUrl!!) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("qr_copy_link_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = null,
                            tint = EmeraldMint,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.action_copy_link),
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Button(
                        onClick = {
                            shareQrCodeImageAndUrl(
                                context = context,
                                appName = context.getString(R.string.app_name),
                                url = savedAppUrl!!,
                                qrBitmap = qrBitmap
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldBright,
                            contentColor = ObsidianBg
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("qr_share_link_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.action_share_qr_image),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            // Direct 1-tap APK share (includes the full App + Robot) to Google Drive, Quick Share, Bluetooth, etc.
            Button(
                onClick = {
                    if (isExportingApk) return@Button
                    isExportingApk = true
                    coroutineScope.launch {
                        val success = shareInstalledAppApk(
                            context = context,
                            appName = context.getString(R.string.app_name)
                        )
                        isExportingApk = false
                        if (!success) {
                            Toast.makeText(
                                context,
                                "Não foi possível preparar o arquivo APK neste aparelho.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                enabled = !isExportingApk,
                colors = if (savedAppUrl == null) {
                    ButtonDefaults.buttonColors(
                        containerColor = EmeraldBright,
                        contentColor = ObsidianBg,
                        disabledContainerColor = TerminalSurfaceElevated,
                        disabledContentColor = TextMuted
                    )
                } else {
                    ButtonDefaults.buttonColors(
                        containerColor = EmeraldContainer,
                        contentColor = EmeraldMint,
                        disabledContainerColor = TerminalSurfaceElevated,
                        disabledContentColor = TextMuted
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("qr_share_apk_button")
            ) {
                if (isExportingApk) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = if (savedAppUrl == null) ObsidianBg else EmeraldMint
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Preparando APK...",
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Android,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.action_share_apk),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun readClipboardText(context: Context): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = clipboard?.primaryClip ?: return null
    if (clip.itemCount <= 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()?.trim()
}

private fun copyTextToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("App URL", text))
    Toast.makeText(context, "Link copiado!", Toast.LENGTH_SHORT).show()
}

private fun shareQrCodeImageAndUrl(
    context: Context,
    appName: String,
    url: String,
    qrBitmap: Bitmap
) {
    val sharedImage = runCatching {
        val qrDir = File(context.cacheDir, "qr_codes").apply { mkdirs() }
        val file = File(qrDir, "trad_do_futuro_qr.png")
        FileOutputStream(file).use { out ->
            qrBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val imageIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, appName)
            putExtra(Intent.EXTRA_TEXT, "$appName\n$url")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(imageIntent, "Compartilhar QR Code"))
        true
    }.getOrDefault(false)

    if (!sharedImage) {
        val textIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, appName)
            putExtra(Intent.EXTRA_TEXT, "$appName\n$url")
        }
        runCatching {
            context.startActivity(Intent.createChooser(textIntent, "Compartilhar $appName"))
        }
    }
}

private suspend fun shareInstalledAppApk(
    context: Context,
    appName: String
): Boolean {
    val exportedFile = withContext(Dispatchers.IO) {
        runCatching {
            val sourcePath = context.applicationInfo?.sourceDir ?: return@runCatching null
            val sourceApk = File(sourcePath)
            if (!sourceApk.exists() || !sourceApk.canRead()) return@runCatching null
            val exportDir = File(context.cacheDir, "qr_codes").apply { mkdirs() }
            val outApk = File(exportDir, "Trad_do_futuro_Roberto_Silva.apk")
            sourceApk.inputStream().use { input ->
                FileOutputStream(outApk).use { output ->
                    input.copyTo(output)
                }
            }
            outApk
        }.getOrNull()
    } ?: return false

    return runCatching {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            exportedFile
        )
        val apkIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "$appName (APK)")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(apkIntent, "Enviar APK — $appName"))
        true
    }.getOrDefault(false)
}
