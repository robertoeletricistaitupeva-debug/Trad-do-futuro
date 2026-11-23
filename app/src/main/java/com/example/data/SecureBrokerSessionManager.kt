package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.webkit.CookieManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.concurrent.ConcurrentHashMap

/**
 * Non-sensitive metadata describing the persisted encrypted broker session.
 * Raw authentication tokens are intentionally omitted so UI layers never handle secrets directly.
 */
data class BrokerSessionStatus(
    val hasSavedSession: Boolean,
    val primaryTokenName: String? = null,
    val savedAtEpochMs: Long = 0L,
    val expiresAtEpochMs: Long = 0L,
    val isEncryptedStorage: Boolean = true
)

/**
 * Non-sensitive metadata describing whether encrypted auto-login credentials are saved on device.
 * Raw passwords are NEVER exposed in UI state objects.
 */
data class BrokerAutoLoginStatus(
    val hasCredentials: Boolean = false,
    val enabled: Boolean = false,
    val maskedEmail: String = "",
    val isEncryptedStorage: Boolean = true
)

internal data class BrokerAutoLoginCredentials(
    val email: String,
    val password: String
)

/**
 * Persistent encrypted session manager for the IQ Option broker authentication token and session
 * cookies, backed by Android Keystore AES-256-GCM + [EncryptedSharedPreferences]
 * (`AES256_SIV` key encryption and `AES256_GCM` value encryption).
 *
 * Security guarantees:
 * - Auth tokens and cookies are never stored in plaintext SharedPreferences on disk.
 * - Legacy plaintext session cookies (if any) are automatically migrated into encrypted storage
 *   and immediately scrubbed from unencrypted preferences.
 * - Ephemeral Cloudflare / WAF / FingerprintJS cookies are strictly stripped and never persisted.
 * - All cookie/token strings are sanitized against CR/LF header injection.
 * - Tokens are only re-injected onto allowlisted HTTPS IQ Option broker domains (`Secure`).
 * - Raw tokens and cookie headers are never logged.
 */
object SecureBrokerSessionManager {
    private const val TAG = "SecureBrokerSession"

    internal const val SECURE_PREFS_NAME = "trendlens_secure_broker_session"
    private const val LEGACY_PLAIN_PREFS_NAME = "trendlens_broker_session"

    private const val KEY_PRIMARY_TOKEN_NAME = "broker_primary_token_name"
    private const val KEY_PRIMARY_AUTH_TOKEN = "broker_primary_auth_token"
    private const val KEY_REFRESH_TOKEN = "broker_refresh_token"
    private const val KEY_COOKIE_ROOT = "enc_cookies_iq_root"
    private const val KEY_COOKIE_APP = "enc_cookies_iq_app"
    private const val KEY_COOKIE_AUTH = "enc_cookies_iq_auth"
    private const val KEY_COOKIE_LOGIN = "enc_cookies_iq_login"
    private const val KEY_COOKIE_API = "enc_cookies_iq_api"
    private const val KEY_COOKIE_EU = "enc_cookies_iq_eu"
    private const val KEY_WEB_LOCAL_STORAGE_JSON = "enc_web_local_storage_json"
    private const val KEY_SAVED_AT_MS = "session_saved_at_ms"
    private const val KEY_EXPIRES_AT_MS = "session_expires_at_ms"
    private const val KEY_AUTOLOGIN_EMAIL = "broker_autologin_email"
    private const val KEY_AUTOLOGIN_PASSWORD = "broker_autologin_password"
    private const val KEY_AUTOLOGIN_ENABLED = "broker_autologin_enabled"

    private const val MAX_LOCAL_STORAGE_SNAPSHOT_CHARS = 131_072

    private const val LEGACY_KEY_COOKIE_ROOT = "saved_cookies_iq_root"
    private const val LEGACY_KEY_COOKIE_APP = "saved_cookies_iq_app"
    private const val LEGACY_KEY_COOKIE_AUTH = "saved_cookies_iq_auth"

    const val DEFAULT_SESSION_TTL_SECONDS: Long = 2_592_000L // 30 days
    const val DEFAULT_SESSION_TTL_MS: Long = DEFAULT_SESSION_TTL_SECONDS * 1_000L

    private const val DOMAIN_ROOT = "https://iqoption.com"
    private const val DOMAIN_APP = "https://app.iqoption.com"
    private const val DOMAIN_AUTH = "https://auth.iqoption.com"
    private const val DOMAIN_LOGIN = "https://login.iqoption.com"
    private const val DOMAIN_API = "https://api.iqoption.com"
    private const val DOMAIN_EU = "https://eu.iqoption.com"

    private val ALLOWLISTED_AUTOLOGIN_HOSTS = setOf(
        "iqoption.com",
        "www.iqoption.com",
        "login.iqoption.com",
        "auth.iqoption.com",
        "app.iqoption.com",
        "eu.iqoption.com"
    )

    private val AUTH_TOKEN_PRIORITY = listOf(
        "ssid",
        "ws_ssid",
        "auth_token",
        "iq_auth",
        "iq_session",
        "session_id",
        "access_token",
        "jwt",
        "token",
        "remember",
        "remember_me"
    )

    private val defaultCookieReader: (String) -> String? = { url ->
        runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
    }

    private val defaultCookieWriter: (String, String) -> Unit = { url, header ->
        runCatching { CookieManager.getInstance().setCookie(url, header) }
    }

    private val defaultCookieFlusher: () -> Unit = {
        runCatching { CookieManager.getInstance().flush() }
    }

    @Volatile
    private var cachedEncryptedPrefs: SharedPreferences? = null

    @Volatile
    private var usingHardwareEncryptedStore: Boolean = true

    @Volatile
    internal var clockProvider: () -> Long = { System.currentTimeMillis() }

    /**
     * Optional test hook allowing JVM unit tests to supply a deterministic [SharedPreferences]
     * backing store without requiring an Android hardware Keystore daemon.
     */
    @Volatile
    internal var testPrefsFactory: ((Context) -> SharedPreferences)? = null

    internal fun resetInternalCacheForTesting() {
        synchronized(this) {
            cachedEncryptedPrefs = null
            usingHardwareEncryptedStore = true
            clockProvider = { System.currentTimeMillis() }
            testPrefsFactory = null
        }
    }

    @Suppress("DEPRECATION")
    private fun createEncryptedPrefs(appContext: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            appContext,
            SECURE_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun getSecurePrefs(context: Context): SharedPreferences {
        val existing = cachedEncryptedPrefs
        if (existing != null) return existing

        return synchronized(this) {
            cachedEncryptedPrefs?.let { return it }

            val appContext = context.applicationContext ?: context
            val testFactory = testPrefsFactory
            if (testFactory != null) {
                val created = testFactory(appContext)
                usingHardwareEncryptedStore = true
                cachedEncryptedPrefs = created
                return created
            }

            val resolved = try {
                usingHardwareEncryptedStore = true
                createEncryptedPrefs(appContext)
            } catch (e: GeneralSecurityException) {
                Log.w(TAG, "Encrypted store key mismatch; resetting encrypted vault safely.")
                recoverCorruptedEncryptedPrefs(appContext)
            } catch (e: IOException) {
                Log.w(TAG, "Encrypted store I/O error; resetting encrypted vault safely.")
                recoverCorruptedEncryptedPrefs(appContext)
            } catch (e: Throwable) {
                // Covers JVM unit-test runtimes (e.g. Robolectric) where AndroidKeyStore is absent.
                // Never fall back to plaintext disk files for authentication tokens.
                Log.w(TAG, "Hardware Keystore unavailable in this runtime; using ephemeral in-memory vault.")
                usingHardwareEncryptedStore = false
                InMemorySharedPreferences()
            }

            cachedEncryptedPrefs = resolved
            resolved
        }
    }

    private fun recoverCorruptedEncryptedPrefs(appContext: Context): SharedPreferences {
        runCatching {
            appContext.deleteSharedPreferences(SECURE_PREFS_NAME)
        }
        runCatching {
            val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            if (keyStore.containsAlias(MasterKey.DEFAULT_MASTER_KEY_ALIAS)) {
                keyStore.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            }
        }
        return try {
            usingHardwareEncryptedStore = true
            createEncryptedPrefs(appContext)
        } catch (retryError: Throwable) {
            Log.w(TAG, "Encrypted vault recovery unavailable in this runtime; using ephemeral in-memory vault.")
            usingHardwareEncryptedStore = false
            InMemorySharedPreferences()
        }
    }

    /**
     * Migrates any legacy unencrypted broker cookies stored in `trendlens_broker_session`
     * into [EncryptedSharedPreferences] and immediately erases the plaintext copies.
     */
    fun migrateLegacyPlaintextSessionIfNeeded(context: Context): Boolean {
        val appContext = context.applicationContext ?: context
        val legacyPrefs = appContext.getSharedPreferences(LEGACY_PLAIN_PREFS_NAME, Context.MODE_PRIVATE)
        val rawRoot = legacyPrefs.getString(LEGACY_KEY_COOKIE_ROOT, null)
        val rawApp = legacyPrefs.getString(LEGACY_KEY_COOKIE_APP, null)
        val rawAuth = legacyPrefs.getString(LEGACY_KEY_COOKIE_AUTH, null)

        if (rawRoot.isNullOrBlank() && rawApp.isNullOrBlank() && rawAuth.isNullOrBlank()) {
            return false
        }

        val cleanRoot = sanitizePersistableCookieHeader(rawRoot)
        val cleanApp = sanitizePersistableCookieHeader(rawApp)
        val cleanAuth = sanitizePersistableCookieHeader(rawAuth)

        var migrated = false
        val extracted = extractPrimaryAndRefreshTokens(cleanRoot, cleanApp, cleanAuth, "")
        if (extracted != null) {
            val now = clockProvider()
            val prefs = getSecurePrefs(appContext)
            // Only write if encrypted store does not already hold a newer session
            if (prefs.getString(KEY_PRIMARY_AUTH_TOKEN, null).isNullOrBlank()) {
                val editor = prefs.edit()
                    .putString(KEY_PRIMARY_TOKEN_NAME, extracted.first)
                    .putString(KEY_PRIMARY_AUTH_TOKEN, extracted.second)
                    .putLong(KEY_SAVED_AT_MS, now)
                    .putLong(KEY_EXPIRES_AT_MS, now + DEFAULT_SESSION_TTL_MS)
                if (!extracted.third.isNullOrBlank()) {
                    editor.putString(KEY_REFRESH_TOKEN, extracted.third)
                }
                putOrRemove(editor, KEY_COOKIE_ROOT, cleanRoot)
                putOrRemove(editor, KEY_COOKIE_APP, cleanApp)
                putOrRemove(editor, KEY_COOKIE_AUTH, cleanAuth)
                editor.commit()
                migrated = true
            }
        }

        // Always scrub legacy plaintext cookie keys immediately so secrets never remain unencrypted
        legacyPrefs.edit()
            .remove(LEGACY_KEY_COOKIE_ROOT)
            .remove(LEGACY_KEY_COOKIE_APP)
            .remove(LEGACY_KEY_COOKIE_AUTH)
            .commit()

        return migrated
    }

    /**
     * Persists an explicit broker authentication token (and optional refresh token) inside
     * [EncryptedSharedPreferences] with a bounded expiration timestamp.
     */
    fun saveAuthToken(
        context: Context,
        token: String,
        tokenName: String = "ssid",
        refreshToken: String? = null,
        ttlMillis: Long = DEFAULT_SESSION_TTL_MS,
        synchronous: Boolean = false
    ): Boolean {
        val cleanName = sanitizeCookieName(tokenName) ?: return false
        val cleanToken = sanitizeCookieValue(token) ?: return false
        val cleanRefresh = refreshToken?.let { sanitizeCookieValue(it) }
        val boundedTtl = ttlMillis.coerceAtLeast(60_000L)
        val now = clockProvider()

        val prefs = getSecurePrefs(context)
        val existingRootPairs = parseSanitizedCookiePairs(prefs.getString(KEY_COOKIE_ROOT, null))
        existingRootPairs[cleanName] = cleanToken
        if (!cleanRefresh.isNullOrBlank()) {
            existingRootPairs["refresh_token"] = cleanRefresh
        }
        val mergedRoot = formatCookieHeader(existingRootPairs)

        val editor = prefs.edit()
            .putString(KEY_PRIMARY_TOKEN_NAME, cleanName)
            .putString(KEY_PRIMARY_AUTH_TOKEN, cleanToken)
            .putString(KEY_COOKIE_ROOT, mergedRoot)
            .putLong(KEY_SAVED_AT_MS, now)
            .putLong(KEY_EXPIRES_AT_MS, now + boundedTtl)

        if (!cleanRefresh.isNullOrBlank()) {
            editor.putString(KEY_REFRESH_TOKEN, cleanRefresh)
        }

        if (synchronous) {
            editor.commit()
        } else {
            editor.apply()
        }
        return true
    }

    /**
     * Returns the decrypted primary broker authentication token if valid and non-expired.
     */
    fun getAuthToken(context: Context): String? {
        migrateLegacyPlaintextSessionIfNeeded(context)
        val prefs = getSecurePrefs(context)
        if (isSessionExpired(prefs)) {
            clearSession(context, clearWebViewCookies = false)
            return null
        }
        return prefs.getString(KEY_PRIMARY_AUTH_TOKEN, null)?.takeIf { isValidAuthValue(it) }
    }

    /**
     * Returns the decrypted refresh token if available and non-expired.
     */
    fun getRefreshToken(context: Context): String? {
        migrateLegacyPlaintextSessionIfNeeded(context)
        val prefs = getSecurePrefs(context)
        if (isSessionExpired(prefs)) {
            clearSession(context, clearWebViewCookies = false)
            return null
        }
        return prefs.getString(KEY_REFRESH_TOKEN, null)?.takeIf { isValidAuthValue(it) }
    }

    /**
     * Returns non-sensitive status metadata about the currently saved encrypted broker session.
     */
    fun getSessionStatus(context: Context): BrokerSessionStatus {
        migrateLegacyPlaintextSessionIfNeeded(context)
        val prefs = getSecurePrefs(context)
        if (isSessionExpired(prefs)) {
            clearSession(context, clearWebViewCookies = false)
            return BrokerSessionStatus(
                hasSavedSession = false,
                isEncryptedStorage = usingHardwareEncryptedStore
            )
        }
        val token = prefs.getString(KEY_PRIMARY_AUTH_TOKEN, null)
        val hasToken = isValidAuthValue(token)
        if (!hasToken) {
            return BrokerSessionStatus(
                hasSavedSession = false,
                isEncryptedStorage = usingHardwareEncryptedStore
            )
        }
        return BrokerSessionStatus(
            hasSavedSession = true,
            primaryTokenName = prefs.getString(KEY_PRIMARY_TOKEN_NAME, "ssid"),
            savedAtEpochMs = prefs.getLong(KEY_SAVED_AT_MS, 0L),
            expiresAtEpochMs = prefs.getLong(KEY_EXPIRES_AT_MS, 0L),
            isEncryptedStorage = usingHardwareEncryptedStore
        )
    }

    fun hasValidSavedSession(context: Context): Boolean = getSessionStatus(context).hasSavedSession

    /**
     * Captures live IQ Option authentication cookies from [CookieManager], strips ephemeral
     * Cloudflare/WAF cookies, extracts the primary auth & refresh tokens, and persists them
     * inside [EncryptedSharedPreferences].
     */
    fun captureAndPersistFromWebView(
        context: Context,
        synchronous: Boolean = false,
        cookieReader: (String) -> String? = defaultCookieReader
    ): Boolean {
        val root = sanitizePersistableCookieHeader(cookieReader(DOMAIN_ROOT))
        val app = sanitizePersistableCookieHeader(cookieReader(DOMAIN_APP))
        val auth = sanitizePersistableCookieHeader(cookieReader(DOMAIN_AUTH))
        val login = sanitizePersistableCookieHeader(cookieReader(DOMAIN_LOGIN))
        val api = sanitizePersistableCookieHeader(cookieReader(DOMAIN_API))
        val eu = sanitizePersistableCookieHeader(cookieReader(DOMAIN_EU))

        val extracted = extractPrimaryAndRefreshTokens(root, app, auth, login, api, eu) ?: return false
        val (primaryName, primaryToken, refreshToken) = extracted
        val now = clockProvider()

        val prefs = getSecurePrefs(context)
        val editor = prefs.edit()
            .putString(KEY_PRIMARY_TOKEN_NAME, primaryName)
            .putString(KEY_PRIMARY_AUTH_TOKEN, primaryToken)
            .putLong(KEY_SAVED_AT_MS, now)
            .putLong(KEY_EXPIRES_AT_MS, now + DEFAULT_SESSION_TTL_MS)

        if (!refreshToken.isNullOrBlank()) {
            editor.putString(KEY_REFRESH_TOKEN, refreshToken)
        } else {
            editor.remove(KEY_REFRESH_TOKEN)
        }
        putOrRemove(editor, KEY_COOKIE_ROOT, root)
        putOrRemove(editor, KEY_COOKIE_APP, app)
        putOrRemove(editor, KEY_COOKIE_AUTH, auth)
        putOrRemove(editor, KEY_COOKIE_LOGIN, login)
        putOrRemove(editor, KEY_COOKIE_API, api)
        putOrRemove(editor, KEY_COOKIE_EU, eu)

        if (synchronous) {
            editor.commit()
        } else {
            editor.apply()
        }
        return true
    }

    /**
     * Restores previously saved encrypted IQ Option auth tokens & cookies into [CookieManager] when
     * the WebView has lost its session cookies (e.g., cold start or process kill). Never overwrites
     * an already-active live session and never replays ephemeral WAF cookies.
     */
    fun restoreToWebViewIfMissing(
        context: Context,
        cookieReader: (String) -> String? = defaultCookieReader,
        cookieWriter: (String, String) -> Unit = defaultCookieWriter,
        flusher: () -> Unit = defaultCookieFlusher
    ): Boolean {
        migrateLegacyPlaintextSessionIfNeeded(context)

        if (cookieWriter === defaultCookieWriter) {
            runCatching { CookieManager.getInstance().setAcceptCookie(true) }
        }

        if (BrokerSessionPrefs.hasIqOptionSessionCookies(cookieReader)) {
            return false
        }

        val prefs = getSecurePrefs(context)
        if (isSessionExpired(prefs)) {
            clearSession(context, clearWebViewCookies = false)
            return false
        }

        val now = clockProvider()
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT_MS, now + DEFAULT_SESSION_TTL_MS)
        val remainingSeconds = ((expiresAt - now) / 1_000L)
            .coerceIn(60L, DEFAULT_SESSION_TTL_SECONDS)

        val rootPairs = parseSanitizedCookiePairs(prefs.getString(KEY_COOKIE_ROOT, null))
        val appPairs = parseSanitizedCookiePairs(prefs.getString(KEY_COOKIE_APP, null))
        val authPairs = parseSanitizedCookiePairs(prefs.getString(KEY_COOKIE_AUTH, null))
        val loginPairs = parseSanitizedCookiePairs(prefs.getString(KEY_COOKIE_LOGIN, null))
        val apiPairs = parseSanitizedCookiePairs(prefs.getString(KEY_COOKIE_API, null))
        val euPairs = parseSanitizedCookiePairs(prefs.getString(KEY_COOKIE_EU, null))

        // Guarantee primary auth token & refresh token are included even if saved via saveAuthToken()
        val primaryName = sanitizeCookieName(prefs.getString(KEY_PRIMARY_TOKEN_NAME, null))
        val primaryToken = sanitizeCookieValue(prefs.getString(KEY_PRIMARY_AUTH_TOKEN, null))
        if (primaryName != null && primaryToken != null && !rootPairs.containsKey(primaryName)) {
            rootPairs[primaryName] = primaryToken
        }
        val refreshToken = sanitizeCookieValue(prefs.getString(KEY_REFRESH_TOKEN, null))
        if (refreshToken != null && !rootPairs.containsKey("refresh_token")) {
            rootPairs["refresh_token"] = refreshToken
        }

        if (rootPairs.isEmpty() &&
            appPairs.isEmpty() &&
            authPairs.isEmpty() &&
            loginPairs.isEmpty() &&
            apiPairs.isEmpty() &&
            euPairs.isEmpty()
        ) {
            return false
        }

        var restoredCount = 0
        rootPairs.forEach { (k, v) ->
            cookieWriter(
                DOMAIN_ROOT,
                "$k=$v; Domain=.iqoption.com; Path=/; Secure; Max-Age=$remainingSeconds"
            )
            restoredCount++
        }
        appPairs.forEach { (k, v) ->
            if (rootPairs[k] != v) {
                cookieWriter(
                    DOMAIN_APP,
                    "$k=$v; Path=/; Secure; Max-Age=$remainingSeconds"
                )
                restoredCount++
            }
        }
        authPairs.forEach { (k, v) ->
            if (rootPairs[k] != v) {
                cookieWriter(
                    DOMAIN_AUTH,
                    "$k=$v; Path=/; Secure; Max-Age=$remainingSeconds"
                )
                restoredCount++
            }
        }
        loginPairs.forEach { (k, v) ->
            if (rootPairs[k] != v) {
                cookieWriter(
                    DOMAIN_LOGIN,
                    "$k=$v; Path=/; Secure; Max-Age=$remainingSeconds"
                )
                restoredCount++
            }
        }
        apiPairs.forEach { (k, v) ->
            if (rootPairs[k] != v) {
                cookieWriter(
                    DOMAIN_API,
                    "$k=$v; Path=/; Secure; Max-Age=$remainingSeconds"
                )
                restoredCount++
            }
        }
        euPairs.forEach { (k, v) ->
            if (rootPairs[k] != v) {
                cookieWriter(
                    DOMAIN_EU,
                    "$k=$v; Path=/; Secure; Max-Age=$remainingSeconds"
                )
                restoredCount++
            }
        }

        if (restoredCount > 0) {
            flusher()
            return true
        }
        return false
    }

    /**
     * Encrypts and stores a bounded, sanitized IQ Option PWA `localStorage` snapshot so SPA
     * authentication state survives cold WebView restarts. Ephemeral Cloudflare/WAF/FPJS keys
     * are strictly stripped.
     */
    fun saveWebLocalStorageSnapshot(
        context: Context,
        rawJson: String?,
        synchronous: Boolean = false
    ): Boolean {
        if (rawJson.isNullOrBlank() || rawJson.length > MAX_LOCAL_STORAGE_SNAPSHOT_CHARS) {
            return false
        }
        val parsed = runCatching { org.json.JSONObject(rawJson) }.getOrNull() ?: return false
        val filtered = org.json.JSONObject()
        var keptCount = 0
        val keys = parsed.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (k.isNullOrBlank() || k.length > 200 || BrokerSessionPrefs.isEphemeralWafCookieName(k)) {
                continue
            }
            val lower = k.lowercase()
            if (lower.contains("fpjs") || lower.contains("cloudflare") || lower.contains("turnstile")) {
                continue
            }
            val v = parsed.optString(k, null) ?: continue
            if (v.length <= 16_384) {
                filtered.put(k, v)
                keptCount++
            }
        }
        if (keptCount == 0) return false
        val serialized = filtered.toString()
        if (serialized.length > MAX_LOCAL_STORAGE_SNAPSHOT_CHARS) return false

        val editor = getSecurePrefs(context).edit()
            .putString(KEY_WEB_LOCAL_STORAGE_JSON, serialized)
        if (synchronous) editor.commit() else editor.apply()
        return true
    }

    fun getWebLocalStorageSnapshot(context: Context): String? {
        val prefs = getSecurePrefs(context)
        if (isSessionExpired(prefs)) return null
        return prefs.getString(KEY_WEB_LOCAL_STORAGE_JSON, null)?.takeIf { it.startsWith("{") }
    }

    /**
     * Validates that a target URL is strictly HTTPS on an official allowlisted IQ Option broker
     * domain before any credential autofill or auto-login attempt is permitted.
     */
    fun isAllowlistedAutoLoginUrl(rawUrl: String?): Boolean {
        if (rawUrl.isNullOrBlank()) return false
        val uri = runCatching { android.net.Uri.parse(rawUrl.trim()) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "https") return false
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return false
        return host in ALLOWLISTED_AUTOLOGIN_HOSTS
    }

    /**
     * Encrypts and stores the user's broker email and password inside [EncryptedSharedPreferences]
     * (Android Keystore AES-256-GCM) strictly on-device for automatic re-authentication when logged
     * out. Raw credentials are never logged or written to plaintext files.
     */
    fun saveAutoLoginCredentials(
        context: Context,
        email: String,
        password: String,
        enabled: Boolean = true,
        synchronous: Boolean = false
    ): Boolean {
        if (email.any { it == '\r' || it == '\n' || it.code < 0x20 || it.code == 0x7F }) {
            return false
        }
        val cleanEmail = email.trim()
        if (cleanEmail.length !in 5..254 || !cleanEmail.contains('@') || !cleanEmail.contains('.')) {
            return false
        }
        val prefs = getSecurePrefs(context)
        val effectivePassword = if (password.isBlank()) {
            // Allow updating email or enabled toggle while preserving existing encrypted password
            prefs.getString(KEY_AUTOLOGIN_PASSWORD, null).orEmpty()
        } else {
            password
        }
        if (effectivePassword.isBlank() || effectivePassword.length > 256) {
            return false
        }
        if (effectivePassword.any { it == '\r' || it == '\n' || it == '\u0000' }) {
            return false
        }

        val editor = prefs.edit()
            .putString(KEY_AUTOLOGIN_EMAIL, cleanEmail)
            .putString(KEY_AUTOLOGIN_PASSWORD, effectivePassword)
            .putBoolean(KEY_AUTOLOGIN_ENABLED, enabled)
        if (synchronous) {
            editor.commit()
        } else {
            editor.apply()
        }
        return true
    }

    fun getSavedAutoLoginEmail(context: Context): String {
        return getSecurePrefs(context).getString(KEY_AUTOLOGIN_EMAIL, null)?.trim().orEmpty()
    }

    fun setAutoLoginEnabled(context: Context, enabled: Boolean, synchronous: Boolean = false) {
        val prefs = getSecurePrefs(context)
        val hasCreds = !prefs.getString(KEY_AUTOLOGIN_EMAIL, null).isNullOrBlank() &&
            !prefs.getString(KEY_AUTOLOGIN_PASSWORD, null).isNullOrBlank()
        val editor = prefs.edit().putBoolean(KEY_AUTOLOGIN_ENABLED, enabled && hasCreds)
        if (synchronous) editor.commit() else editor.apply()
    }

    fun getAutoLoginStatus(context: Context): BrokerAutoLoginStatus {
        val prefs = getSecurePrefs(context)
        val email = prefs.getString(KEY_AUTOLOGIN_EMAIL, null)?.trim().orEmpty()
        val hasPass = !prefs.getString(KEY_AUTOLOGIN_PASSWORD, null).isNullOrBlank()
        val hasCreds = email.isNotBlank() && hasPass
        val enabled = hasCreds && prefs.getBoolean(KEY_AUTOLOGIN_ENABLED, false)
        return BrokerAutoLoginStatus(
            hasCredentials = hasCreds,
            enabled = enabled,
            maskedEmail = if (hasCreds) maskEmailForDisplay(email) else "",
            isEncryptedStorage = usingHardwareEncryptedStore
        )
    }

    internal fun getAutoLoginCredentials(context: Context): BrokerAutoLoginCredentials? {
        val prefs = getSecurePrefs(context)
        if (!prefs.getBoolean(KEY_AUTOLOGIN_ENABLED, false)) return null
        val email = prefs.getString(KEY_AUTOLOGIN_EMAIL, null)?.trim().orEmpty()
        val password = prefs.getString(KEY_AUTOLOGIN_PASSWORD, null).orEmpty()
        if (email.isBlank() || password.isBlank()) return null
        return BrokerAutoLoginCredentials(email = email, password = password)
    }

    fun clearAutoLoginCredentials(context: Context, synchronous: Boolean = true) {
        val editor = getSecurePrefs(context).edit()
            .remove(KEY_AUTOLOGIN_EMAIL)
            .remove(KEY_AUTOLOGIN_PASSWORD)
            .remove(KEY_AUTOLOGIN_ENABLED)
        if (synchronous) editor.commit() else editor.apply()
    }

    internal fun maskEmailForDisplay(email: String): String {
        val trimmed = email.trim()
        val atIdx = trimmed.indexOf('@')
        if (atIdx <= 1 || atIdx >= trimmed.length - 1) return "***"
        val local = trimmed.substring(0, atIdx)
        val domain = trimmed.substring(atIdx + 1)
        val visiblePrefix = local.take(if (local.length >= 4) 2 else 1)
        return "$visiblePrefix***@$domain"
    }

    /**
     * Erases all encrypted session tokens, session cookies, and any leftover legacy plaintext keys.
     * Optionally clears live WebView cookies and/or the saved encrypted auto-login vault.
     */
    fun clearSession(
        context: Context,
        clearWebViewCookies: Boolean = false,
        clearAutoLoginVault: Boolean = false,
        onComplete: (() -> Unit)? = null
    ) {
        val appContext = context.applicationContext ?: context
        runCatching {
            val editor = getSecurePrefs(appContext)
                .edit()
                .remove(KEY_PRIMARY_TOKEN_NAME)
                .remove(KEY_PRIMARY_AUTH_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_COOKIE_ROOT)
                .remove(KEY_COOKIE_APP)
                .remove(KEY_COOKIE_AUTH)
                .remove(KEY_COOKIE_LOGIN)
                .remove(KEY_COOKIE_API)
                .remove(KEY_COOKIE_EU)
                .remove(KEY_WEB_LOCAL_STORAGE_JSON)
                .remove(KEY_SAVED_AT_MS)
                .remove(KEY_EXPIRES_AT_MS)
            if (clearAutoLoginVault) {
                editor
                    .remove(KEY_AUTOLOGIN_EMAIL)
                    .remove(KEY_AUTOLOGIN_PASSWORD)
                    .remove(KEY_AUTOLOGIN_ENABLED)
            }
            editor.commit()
        }
        runCatching {
            appContext.getSharedPreferences(LEGACY_PLAIN_PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(LEGACY_KEY_COOKIE_ROOT)
                .remove(LEGACY_KEY_COOKIE_APP)
                .remove(LEGACY_KEY_COOKIE_AUTH)
                .commit()
        }

        if (!clearWebViewCookies) {
            onComplete?.invoke()
            return
        }

        val cookieManager = runCatching { CookieManager.getInstance() }.getOrNull()
        if (cookieManager != null) {
            runCatching {
                cookieManager.removeSessionCookies(null)
                cookieManager.removeAllCookies {
                    runCatching { cookieManager.flush() }
                    onComplete?.invoke()
                }
            }.onFailure {
                onComplete?.invoke()
            }
        } else {
            onComplete?.invoke()
        }
    }

    private fun isSessionExpired(prefs: SharedPreferences): Boolean {
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT_MS, 0L)
        if (expiresAt <= 0L) return false
        return clockProvider() >= expiresAt
    }

    private fun putOrRemove(editor: SharedPreferences.Editor, key: String, value: String) {
        if (value.isNotBlank()) {
            editor.putString(key, value)
        } else {
            editor.remove(key)
        }
    }

    private fun extractPrimaryAndRefreshTokens(
        root: String,
        app: String,
        auth: String,
        login: String,
        api: String = "",
        eu: String = ""
    ): Triple<String, String, String?>? {
        val merged = LinkedHashMap<String, String>()
        // Root takes priority, followed by api, auth, eu, app, login
        listOf(login, app, eu, auth, api, root).forEach { header ->
            parseSanitizedCookiePairs(header).forEach { (k, v) ->
                merged[k] = v
            }
        }
        if (merged.isEmpty()) return null

        val byLower = merged.entries.associateBy { it.key.lowercase() }
        var primaryName: String? = null
        var primaryValue: String? = null
        for (candidate in AUTH_TOKEN_PRIORITY) {
            val match = byLower[candidate]
            if (match != null && isValidAuthValue(match.value)) {
                primaryName = match.key
                primaryValue = match.value
                break
            }
        }
        val refreshValue = byLower["refresh_token"]?.value?.takeIf { isValidAuthValue(it) }
        if (primaryName == null || primaryValue == null) {
            if (refreshValue != null) {
                primaryName = "refresh_token"
                primaryValue = refreshValue
            } else {
                return null
            }
        }
        return Triple(primaryName, primaryValue, refreshValue)
    }

    internal fun sanitizePersistableCookieHeader(rawCookieHeader: String?): String {
        val pairs = parseSanitizedCookiePairs(rawCookieHeader)
        return formatCookieHeader(pairs)
    }

    internal fun parseSanitizedCookiePairs(rawCookieHeader: String?): LinkedHashMap<String, String> {
        val result = LinkedHashMap<String, String>()
        if (rawCookieHeader.isNullOrBlank()) return result
        rawCookieHeader.split(';').forEach { part ->
            val trimmed = part.trim()
            val eqIdx = trimmed.indexOf('=')
            if (eqIdx > 0) {
                val cleanName = sanitizeCookieName(trimmed.substring(0, eqIdx))
                val cleanValue = sanitizeCookieValue(trimmed.substring(eqIdx + 1))
                if (cleanName != null &&
                    cleanValue != null &&
                    !BrokerSessionPrefs.isEphemeralWafCookieName(cleanName)
                ) {
                    result[cleanName] = cleanValue
                }
            }
        }
        return result
    }

    private fun formatCookieHeader(pairs: Map<String, String>): String {
        if (pairs.isEmpty()) return ""
        return pairs.entries.joinToString("; ") { (k, v) -> "$k=$v" }
    }

    private fun sanitizeCookieName(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed.length > 128) return null
        if (trimmed.any { it == '\r' || it == '\n' || it == ';' || it == '=' || it.code < 0x21 || it.code > 0x7E }) {
            return null
        }
        return trimmed
    }

    private fun sanitizeCookieValue(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (!isValidAuthValue(trimmed) || trimmed.length > 4096) return null
        if (trimmed.any { it == '\r' || it == '\n' || it == ';' || it.code < 0x20 || it.code == 0x7F }) {
            return null
        }
        return trimmed
    }

    private fun isValidAuthValue(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        return !value.equals("deleted", ignoreCase = true) &&
            !value.equals("null", ignoreCase = true) &&
            !value.equals("undefined", ignoreCase = true)
    }

    /**
     * Ephemeral RAM-only [SharedPreferences] used strictly when hardware AndroidKeyStore is
     * unavailable (such as headless JVM Robolectric tests), ensuring sensitive broker tokens
     * are never written unencrypted to disk.
     */
    private class InMemorySharedPreferences : SharedPreferences {
        private val store = ConcurrentHashMap<String, Any>()

        override fun getAll(): MutableMap<String, *> = HashMap(store)

        override fun getString(key: String?, defValue: String?): String? =
            (key?.let { store[it] } as? String) ?: defValue

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (key?.let { store[it] } as? Set<String>)?.toMutableSet() ?: defValues

        override fun getInt(key: String?, defValue: Int): Int =
            (key?.let { store[it] } as? Int) ?: defValue

        override fun getLong(key: String?, defValue: Long): Long =
            (key?.let { store[it] } as? Long) ?: defValue

        override fun getFloat(key: String?, defValue: Float): Float =
            (key?.let { store[it] } as? Float) ?: defValue

        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            (key?.let { store[it] } as? Boolean) ?: defValue

        override fun contains(key: String?): Boolean = key != null && store.containsKey(key)

        override fun edit(): SharedPreferences.Editor = InMemoryEditor(store)

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit

        private class InMemoryEditor(
            private val backing: ConcurrentHashMap<String, Any>
        ) : SharedPreferences.Editor {
            private val updates = LinkedHashMap<String, Any?>()
            private var clearRequested = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
                if (key != null) updates[key] = value
            }

            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply {
                if (key != null) updates[key] = values?.toSet()
            }

            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
                if (key != null) updates[key] = value
            }

            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
                if (key != null) updates[key] = value
            }

            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
                if (key != null) updates[key] = value
            }

            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
                if (key != null) updates[key] = value
            }

            override fun remove(key: String?): SharedPreferences.Editor = apply {
                if (key != null) updates[key] = null
            }

            override fun clear(): SharedPreferences.Editor = apply {
                clearRequested = true
            }

            override fun commit(): Boolean {
                if (clearRequested) backing.clear()
                updates.forEach { (k, v) ->
                    if (v == null) backing.remove(k) else backing[k] = v
                }
                return true
            }

            override fun apply() {
                commit()
            }
        }
    }
}
