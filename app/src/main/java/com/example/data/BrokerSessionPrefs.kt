package com.example.data

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import com.example.model.NormalizedRect
import com.example.ui.TrendLensUiState

data class SavedChartArea(
    val rect: NormalizedRect,
    val isAutoManaged: Boolean
)

object BrokerSessionPrefs {
    private const val PREFS_NAME = "trendlens_broker_session"
    private const val KEY_LAST_BROKER_URL = "last_broker_url"
    private const val KEY_URL_VERSION = "broker_url_version"
    private const val KEY_COOKIE_ROOT = "saved_cookies_iq_root"
    private const val KEY_COOKIE_APP = "saved_cookies_iq_app"
    private const val KEY_COOKIE_AUTH = "saved_cookies_iq_auth"
    private const val KEY_DESKTOP_UA_MODE = "broker_desktop_ua_mode"
    private const val KEY_CHART_AREA_LEFT = "saved_chart_area_left"
    private const val KEY_CHART_AREA_TOP = "saved_chart_area_top"
    private const val KEY_CHART_AREA_RIGHT = "saved_chart_area_right"
    private const val KEY_CHART_AREA_BOTTOM = "saved_chart_area_bottom"
    private const val KEY_CHART_AREA_AUTO = "saved_chart_area_auto"
    private const val CURRENT_URL_VERSION = 9
    private const val PERSIST_MAX_AGE_SECONDS = 2_592_000 // 30 days

    const val TRADEROOM_WEB_URL = "https://iqoption.com/traderoom"
    const val LOGIN_BR_URL = "https://login.iqoption.com/pt/login"

    val BRAZIL_PT_HEADERS: Map<String, String> = mapOf(
        "Accept-Language" to "pt-BR,pt;q=0.9"
    )

    private const val DOMAIN_ROOT = "https://iqoption.com"
    private const val DOMAIN_APP = "https://app.iqoption.com"
    private const val DOMAIN_AUTH = "https://auth.iqoption.com"
    private const val DOMAIN_LOGIN = "https://login.iqoption.com"
    private const val DOMAIN_API = "https://api.iqoption.com"
    private const val DOMAIN_EU = "https://eu.iqoption.com"

    private val defaultCookieReader: (String) -> String? = { url ->
        runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
    }

    private val defaultCookieWriter: (String, String) -> Unit = { url, header ->
        runCatching { CookieManager.getInstance().setCookie(url, header) }
    }

    private val defaultCookieFlusher: () -> Unit = {
        runCatching { CookieManager.getInstance().flush() }
    }

    fun getInitialBrokerUrl(
        context: Context,
        hasActiveSessionCookies: Boolean = hasIqOptionSessionCookies()
    ): String {
        SecureBrokerSessionManager.migrateLegacyPlaintextSessionIfNeeded(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedVersion = prefs.getInt(KEY_URL_VERSION, 1)
        if (savedVersion < CURRENT_URL_VERSION) {
            prefs.edit()
                .remove(KEY_LAST_BROKER_URL)
                .remove(KEY_COOKIE_ROOT)
                .remove(KEY_COOKIE_APP)
                .remove(KEY_COOKIE_AUTH)
                .putBoolean(KEY_DESKTOP_UA_MODE, false)
                .putInt(KEY_URL_VERSION, CURRENT_URL_VERSION)
                .apply()
        }
        // Always open directly into the IQ Option PWA Traderoom as soon as the app starts;
        // encrypted session cookies keep the user authenticated automatically.
        return TrendLensUiState.TRADEROOM_URL
    }

    fun hasIqOptionSessionCookies(
        cookieReader: (String) -> String? = defaultCookieReader
    ): Boolean {
        return runCatching {
            containsAuthCookie(cookieReader(DOMAIN_ROOT)) ||
                containsAuthCookie(cookieReader(DOMAIN_APP)) ||
                containsAuthCookie(cookieReader(DOMAIN_AUTH)) ||
                containsAuthCookie(cookieReader(DOMAIN_LOGIN)) ||
                containsAuthCookie(cookieReader(DOMAIN_API)) ||
                containsAuthCookie(cookieReader(DOMAIN_EU))
        }.getOrDefault(false)
    }

    internal fun containsAuthCookie(rawCookieHeader: String?): Boolean {
        if (rawCookieHeader.isNullOrBlank()) return false
        val pairs = parseCookiePairs(rawCookieHeader)
        return pairs.any { (name, value) ->
            val lower = name.lowercase()
            val isAuthKey = lower == "ssid" ||
                lower == "ws_ssid" ||
                lower == "auth_token" ||
                lower == "iq_auth" ||
                lower == "refresh_token" ||
                lower == "iq_session" ||
                lower == "session_id" ||
                lower == "access_token" ||
                lower == "jwt" ||
                lower == "remember" ||
                lower == "remember_me"
            isAuthKey && value.isNotBlank() &&
                !value.equals("deleted", ignoreCase = true) &&
                !value.equals("null", ignoreCase = true)
        }
    }

    internal fun parseCookiePairs(rawCookieHeader: String?): LinkedHashMap<String, String> {
        val map = LinkedHashMap<String, String>()
        if (rawCookieHeader.isNullOrBlank()) return map
        rawCookieHeader.split(';').forEach { part ->
            val trimmed = part.trim()
            val eqIdx = trimmed.indexOf('=')
            if (eqIdx > 0) {
                val name = trimmed.substring(0, eqIdx).trim()
                val value = trimmed.substring(eqIdx + 1).trim()
                if (name.isNotBlank() && value.isNotBlank() && !value.equals("deleted", ignoreCase = true)) {
                    map[name] = value
                }
            }
        }
        return map
    }

    /**
     * Ephemeral anti-bot / Cloudflare / WAF cookies must NEVER be persisted or replayed across
     * sessions; replaying expired `__cf_bm` or `cf_clearance` cookies causes immediate 403 blocks.
     */
    internal fun isEphemeralWafCookieName(name: String): Boolean {
        val lower = name.trim().lowercase()
        if (lower.isEmpty()) return true
        return lower == "__cf_bm" ||
            lower == "cf_clearance" ||
            lower == "_cfuvid" ||
            lower == "__cflb" ||
            lower == "__cfruid" ||
            lower == "__cfwaitingroom" ||
            lower == "cf_ob_info" ||
            lower == "cf_use_ob" ||
            lower.startsWith("cf_chl_") ||
            lower.startsWith("cf_turnstile") ||
            lower.startsWith("_fpjs") ||
            lower.startsWith("fp_")
    }

    internal fun filterPersistableCookies(rawCookieHeader: String?): String {
        val pairs = parseCookiePairs(rawCookieHeader)
        if (pairs.isEmpty()) return ""
        return pairs.entries
            .filterNot { (name, _) -> isEphemeralWafCookieName(name) }
            .joinToString("; ") { (k, v) -> "$k=$v" }
    }

    /**
     * Snapshots live IQ Option session cookies (including session-only `ssid` cookies that Chromium
     * would otherwise discard on cold app restart) into [SecureBrokerSessionManager]'s
     * EncryptedSharedPreferences vault, strictly stripping ephemeral Cloudflare / WAF cookies.
     */
    fun persistSessionCookiesSnapshot(
        context: Context,
        synchronous: Boolean = false,
        cookieReader: (String) -> String? = defaultCookieReader
    ): Boolean {
        return SecureBrokerSessionManager.captureAndPersistFromWebView(
            context = context,
            synchronous = synchronous,
            cookieReader = cookieReader
        )
    }

    /**
     * Restores previously saved encrypted IQ Option auth tokens and cookies with persistent Max-Age
     * if Chromium discarded session-only cookies when the process closed. Never overwrites active
     * live session cookies and never replays ephemeral Cloudflare / WAF cookies.
     */
    fun restoreSessionCookiesIfMissing(
        context: Context,
        cookieReader: (String) -> String? = defaultCookieReader,
        cookieWriter: (String, String) -> Unit = defaultCookieWriter,
        flusher: () -> Unit = defaultCookieFlusher
    ): Boolean {
        return SecureBrokerSessionManager.restoreToWebViewIfMissing(
            context = context,
            cookieReader = cookieReader,
            cookieWriter = cookieWriter,
            flusher = flusher
        )
    }

    /**
     * Ensures IQ Option receives Portuguese (Brazil) language cookies before loading the Traderoom
     * so it opens in Portuguese instead of defaulting to English.
     */
    fun ensureBrazilLocaleCookies(
        cookieReader: (String) -> String? = defaultCookieReader,
        cookieWriter: (String, String) -> Unit = defaultCookieWriter,
        flusher: () -> Unit = defaultCookieFlusher
    ): Boolean {
        val currentRoot = parseCookiePairs(cookieReader(DOMAIN_ROOT))
        val existingLang = currentRoot["lang"]?.lowercase().orEmpty()
        if (existingLang.startsWith("pt")) {
            return false
        }
        cookieWriter(
            DOMAIN_ROOT,
            "lang=pt_PT; Domain=.iqoption.com; Path=/; Secure; Max-Age=$PERSIST_MAX_AGE_SECONDS"
        )
        cookieWriter(
            DOMAIN_APP,
            "lang=pt_PT; Path=/; Secure; Max-Age=$PERSIST_MAX_AGE_SECONDS"
        )
        flusher()
        return true
    }

    /**
     * Blocks app-store, ad-tracker, and social-media escape URLs so the WebView stays locked on
     * the IQ Option Brazil Traderoom instead of pulling the user out of the app.
     */
    fun isBlockedExternalEscapeUrl(url: String): Boolean {
        val lower = url.lowercase()
        if (lower.contains("play.google.com/store/apps") ||
            lower.contains("apps.apple.com") ||
            lower.contains("app.appsflyer.com") ||
            lower.contains("onelink.me") ||
            lower.contains("app.adjust.com") ||
            lower.contains("branch.io")
        ) {
            return true
        }
        val host = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull() ?: return false
        return host == "t.me" ||
            host.endsWith(".t.me") ||
            host == "wa.me" ||
            host.endsWith("whatsapp.com") ||
            host.endsWith("instagram.com") ||
            host.endsWith("youtube.com") ||
            host == "youtu.be" ||
            host.endsWith("tiktok.com") ||
            host.endsWith("twitter.com") ||
            host == "x.com"
    }

    /**
     * Returns true when the URL belongs to a Cloudflare/WAF security challenge, captcha, or login
     * flow that must NEVER be interrupted by automatic redirects or bot DOM polling.
     */
    fun isSecurityChallengeOrAuthUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase()
        if (lower.contains("/cdn-cgi/") ||
            lower.contains("challenges.cloudflare.com") ||
            lower.contains("turnstile") ||
            lower.contains("recaptcha") ||
            lower.contains("hcaptcha") ||
            lower.contains("arkoselabs") ||
            lower.contains("funcaptcha") ||
            lower.contains("captcha") ||
            lower.contains("challenge") ||
            lower.contains("accounts.google.com") ||
            lower.contains("appleid.apple.com") ||
            lower.contains("facebook.com/v") ||
            lower.contains("facebook.com/login")
        ) {
            return true
        }
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val host = uri.host?.lowercase().orEmpty()
        val path = uri.path?.lowercase().orEmpty()
        return isAuthOrExitPath(host, path)
    }

    /**
     * Builds an authentic modern Chrome User-Agent using the WebView's real Chromium version so
     * User-Agent and Sec-CH-UA Client Hints remain aligned (avoiding Cloudflare / WAF blocks).
     */
    fun buildCleanChromeUserAgent(rawUa: String?, desktopMode: Boolean): String {
        val chromeVersion = Regex("""Chrome/([0-9.]+)""")
            .find(rawUa.orEmpty())
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
            ?: "131.0.0.0"

        if (desktopMode) {
            return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chromeVersion Safari/537.36"
        }

        if (!rawUa.isNullOrBlank()) {
            val cleaned = rawUa
                .replace("; wv)", ")")
                .replace("; wv", "")
                .replace("Version/4.0 ", "")
                .replace(Regex("""\s{2,}"""), " ")
                .trim()
            if (cleaned.contains("Chrome/")) {
                return cleaned
            }
        }

        return "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chromeVersion Mobile Safari/537.36"
    }

    fun isDesktopModeEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DESKTOP_UA_MODE, false)
    }

    fun setDesktopModeEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DESKTOP_UA_MODE, enabled)
            .apply()
    }

    /**
     * Persists the active WebView chart area (whether auto-detected or manually adjusted)
     * so the user never has to redefine the chart box after closing or reopening the app.
     */
    fun saveChartArea(context: Context, rect: NormalizedRect?, isAutoManaged: Boolean) {
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        val normalized = rect?.normalized()
        if (normalized == null || !normalized.isValid()) {
            editor
                .remove(KEY_CHART_AREA_LEFT)
                .remove(KEY_CHART_AREA_TOP)
                .remove(KEY_CHART_AREA_RIGHT)
                .remove(KEY_CHART_AREA_BOTTOM)
                .putBoolean(KEY_CHART_AREA_AUTO, isAutoManaged)
                .apply()
            return
        }
        editor
            .putFloat(KEY_CHART_AREA_LEFT, normalized.left)
            .putFloat(KEY_CHART_AREA_TOP, normalized.top)
            .putFloat(KEY_CHART_AREA_RIGHT, normalized.right)
            .putFloat(KEY_CHART_AREA_BOTTOM, normalized.bottom)
            .putBoolean(KEY_CHART_AREA_AUTO, isAutoManaged)
            .apply()
    }

    fun loadSavedChartArea(context: Context): SavedChartArea? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_CHART_AREA_LEFT) ||
            !prefs.contains(KEY_CHART_AREA_TOP) ||
            !prefs.contains(KEY_CHART_AREA_RIGHT) ||
            !prefs.contains(KEY_CHART_AREA_BOTTOM)
        ) {
            return null
        }
        val rect = NormalizedRect(
            left = prefs.getFloat(KEY_CHART_AREA_LEFT, 0.03f),
            top = prefs.getFloat(KEY_CHART_AREA_TOP, 0.15f),
            right = prefs.getFloat(KEY_CHART_AREA_RIGHT, 0.97f),
            bottom = prefs.getFloat(KEY_CHART_AREA_BOTTOM, 0.68f)
        ).normalized()
        if (!rect.isValid()) return null
        return SavedChartArea(
            rect = rect,
            isAutoManaged = prefs.getBoolean(KEY_CHART_AREA_AUTO, true)
        )
    }

    /**
     * Clears only Cloudflare / WAF challenge cookies and stale HTTP cache so anti-bot challenges
     * reset cleanly without wiping the user's robot settings or calibrations.
     */
    fun clearAntiBlockChallengeCookies(
        webView: android.webkit.WebView? = null,
        cookieWriter: (String, String) -> Unit = defaultCookieWriter,
        flusher: () -> Unit = defaultCookieFlusher
    ) {
        val wafCookieNames = listOf(
            "__cf_bm",
            "cf_clearance",
            "_cfuvid",
            "cf_chl_2",
            "cf_chl_prog",
            "__cflb",
            "__cfruid"
        )
        val domains = listOf(DOMAIN_ROOT, DOMAIN_APP, DOMAIN_AUTH, DOMAIN_LOGIN)
        domains.forEach { domain ->
            wafCookieNames.forEach { name ->
                cookieWriter(domain, "$name=; Domain=.iqoption.com; Path=/; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT")
                cookieWriter(domain, "$name=; Path=/; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT")
            }
        }
        flusher()
        runCatching {
            webView?.clearCache(true)
        }
    }

    /**
     * Returns true when IQ Option redirected away from the Traderoom to a generic marketing/root
     * landing page (such as /en, /pt, or /) rather than an active trading, auth, or account page.
     */
    fun isEnglishOrMarketingLandingUrl(url: String): Boolean {
        if (isSecurityChallengeOrAuthUrl(url)) return false
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        if (!host.contains("iqoption")) return false
        if (host.startsWith("app.") || host.startsWith("trade.")) return false
        val path = uri.path?.lowercase().orEmpty().trimEnd('/')
        if (isAuthOrExitPath(host, path)) return false
        if (isTradingWorkspaceUrl(url)) return false
        return path.isEmpty() ||
            path == "/en" ||
            path == "/pt" ||
            path == "/pt-br" ||
            path == "/es" ||
            path.startsWith("/lp/")
    }

    fun isTradingWorkspaceUrl(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        if (!host.contains("iqoption")) return false
        val path = uri.path?.lowercase().orEmpty()
        if (isAuthOrExitPath(host, path)) return false
        return host.startsWith("app.") ||
            host.startsWith("trade.") ||
            path.contains("/traderoom") ||
            path.contains("/pwa") ||
            path.contains("/trade") ||
            path.contains("/wallet") ||
            path.contains("/deposit") ||
            path.contains("/profile")
    }

    fun isEligibleNonAuthBrokerUrl(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        val host = uri.host?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return false
        if (!host.contains("iqoption")) return false
        val path = uri.path?.lowercase().orEmpty()
        return !isAuthOrExitPath(host, path)
    }

    private fun isExplicitLogoutPath(path: String): Boolean {
        val normalized = path.trimEnd('/')
        return normalized.endsWith("/logout") ||
            normalized.endsWith("/signout") ||
            normalized.contains("/logout/") ||
            normalized.contains("/signout/")
    }

    private fun isAuthOrExitPath(host: String, path: String): Boolean {
        if (host.startsWith("auth.") || host.startsWith("login.") || host.startsWith("oauth.")) {
            return true
        }
        return isExplicitLogoutPath(path) ||
            path.contains("login") ||
            path.contains("signin") ||
            path.contains("register") ||
            path.contains("signup") ||
            path.contains("forgot") ||
            path.contains("reset-password") ||
            path.contains("oauth") ||
            path.contains("sso") ||
            path.contains("2fa") ||
            path.contains("verify")
    }

    fun saveBrokerUrlIfEligible(context: Context, url: String) {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return
        val scheme = uri.scheme?.lowercase() ?: return
        val host = uri.host?.lowercase() ?: return
        if (scheme != "http" && scheme != "https") return
        if (host.contains("share.google") || host.contains("accounts.google.")) return

        val path = uri.path?.lowercase().orEmpty()
        if (host.contains("iqoption") && isExplicitLogoutPath(path)) {
            clearSavedBrokerUrl(context)
            return
        }

        if (host.contains("iqoption") && !isAuthOrExitPath(host, path)) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val existing = prefs.getString(KEY_LAST_BROKER_URL, null)
            // Never downgrade an already-saved trading workspace URL (/traderoom, /pwa, app.iqoption.com)
            // back to a marketing landing page (/pt, /en, /).
            if (!existing.isNullOrBlank() && isTradingWorkspaceUrl(existing) && !isTradingWorkspaceUrl(url)) {
                return
            }
            prefs.edit()
                .putInt(KEY_URL_VERSION, CURRENT_URL_VERSION)
                .putString(KEY_LAST_BROKER_URL, url)
                .apply()
        }
    }

    private const val KEY_APP_SHARE_URL = "custom_app_share_url"

    fun normalizeShareUrl(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isBlank()) return null
        val withScheme = if (
            trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "https://$trimmed"
        }
        val uri = runCatching { Uri.parse(withScheme) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (!host.contains('.') || host.length < 4) return null
        // Reject internal AI Studio emulator preview URLs that do not open an Android APK publicly
        if (host.startsWith("ais-pre-") || host.startsWith("ais-dev-")) return null
        return withScheme
    }

    fun getCustomAppShareUrl(context: Context): String? {
        val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_APP_SHARE_URL, null)
        return normalizeShareUrl(saved)
    }

    fun saveCustomAppShareUrl(context: Context, rawUrl: String?): String? {
        val normalized = normalizeShareUrl(rawUrl)
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        if (normalized == null) {
            editor.remove(KEY_APP_SHARE_URL)
        } else {
            editor.putString(KEY_APP_SHARE_URL, normalized)
        }
        editor.apply()
        return normalized
    }

    fun clearSavedBrokerUrl(context: Context) {
        SecureBrokerSessionManager.clearSession(context, clearWebViewCookies = false)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LAST_BROKER_URL)
            .remove(KEY_COOKIE_ROOT)
            .remove(KEY_COOKIE_APP)
            .remove(KEY_COOKIE_AUTH)
            .putInt(KEY_URL_VERSION, CURRENT_URL_VERSION)
            .apply()
    }

    /**
     * Full app-level reset: clears encrypted broker auth tokens, persisted session preferences,
     * WebView cache/history/storage, and all broker cookies, then invokes [onComplete].
     */
    fun resetAllAppData(
        context: Context,
        webView: android.webkit.WebView? = null,
        onComplete: (() -> Unit)? = null
    ) {
        SecureBrokerSessionManager.clearSession(
            context = context,
            clearWebViewCookies = false,
            clearAutoLoginVault = true
        )
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putInt(KEY_URL_VERSION, CURRENT_URL_VERSION)
            .commit()

        runCatching {
            webView?.stopLoading()
            webView?.clearCache(true)
            webView?.clearHistory()
            webView?.clearFormData()
        }
        runCatching {
            android.webkit.WebStorage.getInstance().deleteAllData()
        }

        val cookieManager = runCatching { CookieManager.getInstance() }.getOrNull()
        if (cookieManager != null) {
            runCatching {
                cookieManager.removeSessionCookies(null)
                cookieManager.removeAllCookies {
                    runCatching {
                        cookieManager.flush()
                    }
                    onComplete?.invoke()
                }
            }.onFailure {
                onComplete?.invoke()
            }
        } else {
            onComplete?.invoke()
        }
    }
}

