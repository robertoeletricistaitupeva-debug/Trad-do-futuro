package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

val IQ_OPTION_PACKAGE_CANDIDATES = listOf(
    "com.iqoption",
    "com.iqoption.x"
)

const val IQ_OPTION_PLAY_STORE_MARKET_URI = "market://details?id=com.iqoption"
const val IQ_OPTION_PLAY_STORE_WEB_URI = "https://play.google.com/store/apps/details?id=com.iqoption"

enum class BrokerLaunchOutcome {
    OPENED_INSTALLED_APP,
    OPENED_DEEP_LINK,
    OPENED_PLAY_STORE,
    UNAVAILABLE
}

fun findInstalledIqOptionPackage(context: Context): String? {
    val pm = context.packageManager
    return IQ_OPTION_PACKAGE_CANDIDATES.firstOrNull { pkg ->
        runCatching { pm.getLaunchIntentForPackage(pkg) != null }.getOrDefault(false)
    }
}

fun openIqOptionApp(
    context: Context,
    onFallbackToTraderoom: (() -> Unit)? = null
): BrokerLaunchOutcome {
    val pm = context.packageManager

    for (pkg in IQ_OPTION_PACKAGE_CANDIDATES) {
        val launchIntent = runCatching { pm.getLaunchIntentForPackage(pkg) }.getOrNull()
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            val started = runCatching {
                context.startActivity(launchIntent)
                true
            }.getOrDefault(false)
            if (started) {
                return BrokerLaunchOutcome.OPENED_INSTALLED_APP
            }
        }
    }

    val deepLinkIntent = Intent(Intent.ACTION_VIEW, Uri.parse("iqoption://")).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (deepLinkIntent.resolveActivity(pm) != null) {
        val started = runCatching {
            context.startActivity(deepLinkIntent)
            true
        }.getOrDefault(false)
        if (started) {
            return BrokerLaunchOutcome.OPENED_DEEP_LINK
        }
    }

    val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse(IQ_OPTION_PLAY_STORE_MARKET_URI)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (marketIntent.resolveActivity(pm) != null && runCatching { context.startActivity(marketIntent); true }.getOrDefault(false)) {
        Toast.makeText(
            context,
            "App IQ Option não encontrado. Abrindo na Play Store...",
            Toast.LENGTH_SHORT
        ).show()
        return BrokerLaunchOutcome.OPENED_PLAY_STORE
    }

    val playStoreWebIntent = Intent(Intent.ACTION_VIEW, Uri.parse(IQ_OPTION_PLAY_STORE_WEB_URI)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (playStoreWebIntent.resolveActivity(pm) != null && runCatching { context.startActivity(playStoreWebIntent); true }.getOrDefault(false)) {
        Toast.makeText(
            context,
            "App IQ Option não encontrado. Abrindo página de instalação...",
            Toast.LENGTH_SHORT
        ).show()
        return BrokerLaunchOutcome.OPENED_PLAY_STORE
    }

    Toast.makeText(
        context,
        "App IQ Option não instalado. Abrindo corretora no navegador interno.",
        Toast.LENGTH_SHORT
    ).show()
    onFallbackToTraderoom?.invoke()
    return BrokerLaunchOutcome.UNAVAILABLE
}

fun handleExternalBrokerIntentUrl(
    context: Context,
    rawUrl: String,
    webViewFallback: (String) -> Unit
): Boolean {
    // Keep the user inside the internal Traderoom WebView unless they explicitly tap "Abrir aplicativo IQ Option".
    if (rawUrl.startsWith("iqoption://")) {
        return true
    }

    if (rawUrl.startsWith("intent://")) {
        val parsedIntent = runCatching {
            Intent.parseUri(rawUrl, Intent.URI_INTENT_SCHEME)
        }.getOrNull()

        if (parsedIntent != null) {
            val fallbackUrl = parsedIntent.getStringExtra("browser_fallback_url")
                ?: parsedIntent.dataString?.takeIf {
                    it.startsWith("http://") || it.startsWith("https://")
                }
            if (!fallbackUrl.isNullOrBlank() &&
                (fallbackUrl.startsWith("http://") || fallbackUrl.startsWith("https://"))
            ) {
                webViewFallback(fallbackUrl)
            }
        }
        return true
    }

    return rawUrl.startsWith("market://")
}

fun openUrlInExternalChrome(
    context: Context,
    url: String
): Boolean {
    val parsedUri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
    val chromeIntent = Intent(Intent.ACTION_VIEW, parsedUri).apply {
        setPackage("com.android.chrome")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (runCatching { context.startActivity(chromeIntent); true }.getOrDefault(false)) {
        return true
    }
    val genericIntent = Intent(Intent.ACTION_VIEW, parsedUri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val opened = runCatching { context.startActivity(genericIntent); true }.getOrDefault(false)
    if (!opened) {
        Toast.makeText(
            context,
            "Não foi possível abrir o Google Chrome neste aparelho.",
            Toast.LENGTH_SHORT
        ).show()
    }
    return opened
}

