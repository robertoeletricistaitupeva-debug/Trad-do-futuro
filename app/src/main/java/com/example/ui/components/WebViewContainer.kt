package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.ServiceWorkerController
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.BrazilLocaleConfig
import com.example.data.BrokerAutoLoginStatus
import com.example.data.BrokerSessionPrefs
import com.example.data.SecureBrokerSessionManager
import com.example.ui.TrendLensUiState
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.IqOptionOrange
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TerminalBorder
import com.example.ui.theme.TerminalSurface
import com.example.ui.theme.TerminalSurfaceElevated
import com.example.ui.theme.TerminalToolbar
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay


internal fun isBlockedTurnRelayHost(host: String?): Boolean {
    val h = host?.lowercase()?.trimEnd('.') ?: return false
    return h == "use1-turn.fpjs.io" ||
        h == "turn.fpjs.io" ||
        (h.endsWith(".fpjs.io") && h.contains("turn"))
}

/**
 * Document-start script that surgically strips unreachable `*.fpjs.io` TURN ICE servers
 * (such as `use1-turn.fpjs.io`) from RTCPeerConnection configurations before Chromium's
 * native WebRTC P2P socket manager attempts failing DNS lookups (`ERR_NAME_NOT_RESOLVED` / -105).
 * Leaves all FingerprintJS HTTP scripts, browser signals, and all other ICE servers untouched.
 */
internal const val TURN_RELAY_ICE_SANITIZER_JS = """
(function() {
  try {
    if (window.__tlTurnIceSanitizerInstalled) return;
    window.__tlTurnIceSanitizerInstalled = true;
    var blockedTurnRe = /(?:^|[.:\/@])[a-z0-9-]*turn[a-z0-9-]*\.fpjs\.io(?::|\/|\?|$)/i;

    function isBlockedIceUrl(u) {
      return typeof u === 'string' && blockedTurnRe.test(u);
    }

    function sanitizeIceConfig(cfg) {
      if (!cfg || typeof cfg !== 'object' || !Array.isArray(cfg.iceServers)) {
        return cfg;
      }
      var changed = false;
      var filteredServers = [];
      for (var i = 0; i < cfg.iceServers.length; i++) {
        var srv = cfg.iceServers[i];
        if (!srv || typeof srv !== 'object') {
          filteredServers.push(srv);
          continue;
        }
        var rawUrls = srv.urls !== undefined ? srv.urls : srv.url;
        if (Array.isArray(rawUrls)) {
          var kept = [];
          for (var j = 0; j < rawUrls.length; j++) {
            if (isBlockedIceUrl(rawUrls[j])) {
              changed = true;
            } else {
              kept.push(rawUrls[j]);
            }
          }
          if (kept.length > 0) {
            var copySrv = Object.assign({}, srv);
            if (srv.urls !== undefined) copySrv.urls = kept;
            else copySrv.url = kept;
            filteredServers.push(copySrv);
          }
        } else if (isBlockedIceUrl(rawUrls)) {
          changed = true;
        } else {
          filteredServers.push(srv);
        }
      }
      if (!changed) return cfg;
      return Object.assign({}, cfg, { iceServers: filteredServers });
    }

    function wrapRtcCtor(name, existingWrapped, origRef) {
      var Orig = window[name];
      if (typeof Orig !== 'function') return null;
      if (existingWrapped && Orig === origRef) {
        try {
          Object.defineProperty(window, name, {
            value: existingWrapped,
            writable: true,
            configurable: true
          });
        } catch (e) {
          window[name] = existingWrapped;
        }
        return existingWrapped;
      }
      var Wrapped = function RTCPeerConnection(configuration, constraints) {
        var cleanCfg = sanitizeIceConfig(configuration);
        if (arguments.length >= 2) {
          return new Orig(cleanCfg, constraints);
        }
        if (arguments.length === 1) {
          return new Orig(cleanCfg);
        }
        return new Orig();
      };
      Wrapped.prototype = Orig.prototype;
      Object.setPrototypeOf(Wrapped, Orig);
      if (Orig.prototype && typeof Orig.prototype.setConfiguration === 'function' && !Orig.prototype.__tlSetConfigSanitized) {
        var origSetConfig = Orig.prototype.setConfiguration;
        Orig.prototype.__tlSetConfigSanitized = true;
        Orig.prototype.setConfiguration = function(cfg) {
          return origSetConfig.call(this, sanitizeIceConfig(cfg));
        };
      }
      try {
        Object.defineProperty(window, name, {
          value: Wrapped,
          writable: true,
          configurable: true
        });
      } catch (e) {
        window[name] = Wrapped;
      }
      return Wrapped;
    }

    var origStd = window.RTCPeerConnection;
    var wrappedStd = wrapRtcCtor('RTCPeerConnection', null, null);
    wrapRtcCtor('webkitRTCPeerConnection', wrappedStd, origStd);
  } catch (ignore) {}
})();
"""

private fun installTurnRelayIceSanitizer(webView: WebView) {
    runCatching {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(
                webView,
                TURN_RELAY_ICE_SANITIZER_JS,
                setOf("*")
            )
        }
    }
}

enum class BrokerBlockKind {
    NONE,
    GEO_BLOCK,
    WAF_OR_CLOUDFLARE,
    BROWSER_OR_LOGIN_BLOCK
}

internal fun detectBrokerBlockKindFromText(rawText: String?, pageUrl: String? = null): BrokerBlockKind {
    val urlLower = pageUrl?.lowercase().orEmpty()
    val text = rawText?.lowercase().orEmpty()

    if (urlLower.contains("/cdn-cgi/challenge") ||
        urlLower.contains("challenges.cloudflare.com") ||
        text.contains("attention required") ||
        text.contains("sorry, you have been blocked") ||
        text.contains("you have been blocked") ||
        text.contains("just a moment") ||
        text.contains("checking your browser") ||
        text.contains("verify you are human") ||
        text.contains("enable javascript and cookies to continue") ||
        text.contains("error 1020") ||
        (text.contains("cloudflare") && text.contains("ray id")) ||
        text.contains("access denied")
    ) {
        return BrokerBlockKind.WAF_OR_CLOUDFLARE
    }

    if (text.contains("unavailable in your country") ||
        text.contains("not available in your country") ||
        text.contains("restricted country") ||
        text.contains("country is not supported") ||
        text.contains("não está disponível em seu país") ||
        text.contains("nao esta disponivel em seu pais") ||
        text.contains("indisponível no seu país") ||
        text.contains("indisponivel no seu pais")
    ) {
        return BrokerBlockKind.GEO_BLOCK
    }

    if (text.contains("disallowed_useragent") ||
        text.contains("browser not supported") ||
        text.contains("navegador não suportado") ||
        text.contains("navegador nao suportado") ||
        text.contains("este navegador ou app pode não ser seguro") ||
        text.contains("este navegador ou app pode nao ser seguro") ||
        text.contains("this browser or app may not be secure") ||
        text.contains("tente usar outro navegador") ||
        text.contains("try using a different browser") ||
        text.contains("403 forbidden")
    ) {
        return BrokerBlockKind.BROWSER_OR_LOGIN_BLOCK
    }

    return BrokerBlockKind.NONE
}

private const val BROKER_BLOCK_DETECT_JS = """
(function() {
  try {
    var title = (document.title || '');
    var body = (document.body && document.body.innerText) ? document.body.innerText.slice(0, 1400) : '';
    var hasFullPageCfInterstitial = !!document.querySelector('#challenge-running, #cf-wrapper, #challenge-stage, .cf-error-details');
    return (hasFullPageCfInterstitial ? 'cloudflare ray id ' : '') + title + ' ' + body;
  } catch (e) {
    return '';
  }
})();
"""

internal enum class AutoLoginStepStatus {
    SUBMITTED,
    OPENED_LOGIN_FORM,
    ALREADY_LOGGED_IN,
    PAUSED_CHALLENGE,
    PAUSED_ERROR,
    NO_FORM
}

internal data class AutoLoginStepOutcome(
    val status: AutoLoginStepStatus,
    val detail: String = ""
)

private val ALLOWED_IQ_ORIGINS = setOf(
    "https://iqoption.com",
    "https://www.iqoption.com",
    "https://login.iqoption.com",
    "https://auth.iqoption.com",
    "https://app.iqoption.com",
    "https://eu.iqoption.com"
)

private fun quoteForJsLiteral(raw: String): String {
    return org.json.JSONObject.quote(raw)
        .replace("\u2028", "\\u2028")
        .replace("\u2029", "\\u2029")
}

/**
 * Restores a previously encrypted IQ Option PWA `localStorage` snapshot strictly on allowlisted
 * official HTTPS IQ Option origins without overwriting already-present live keys.
 */
internal fun buildLocalStorageRestoreScript(snapshotJson: String): String {
    val safeJsonJs = quoteForJsLiteral(snapshotJson)
    return """
(function() {
  try {
    if (window.location.protocol !== 'https:' || !/^(?:www\.|login\.|auth\.|app\.|eu\.)?iqoption\.com$/i.test(window.location.hostname || '')) {
      return;
    }
    if (!window.localStorage) return;
    var data = JSON.parse($safeJsonJs);
    if (!data || typeof data !== 'object') return;
    var keys = Object.keys(data);
    for (var i = 0; i < keys.length; i++) {
      var k = keys[i];
      if (!k || k.length > 200) continue;
      if (window.localStorage.getItem(k) === null && typeof data[k] === 'string') {
        try { window.localStorage.setItem(k, data[k]); } catch (e) {}
      }
    }
  } catch (ignore) {}
})();
""".trimIndent()
}

internal const val CAPTURE_LOCAL_STORAGE_JS = """
(function() {
  try {
    if (window.location.protocol !== 'https:' || !/^(?:www\.|login\.|auth\.|app\.|eu\.)?iqoption\.com$/i.test(window.location.hostname || '')) {
      return '';
    }
    if (!window.localStorage) return '';
    var out = {};
    var totalLen = 2;
    for (var i = 0; i < window.localStorage.length; i++) {
      var k = window.localStorage.key(i);
      if (!k || k.length > 200) continue;
      var kl = k.toLowerCase();
      if (kl.indexOf('fpjs') !== -1 || kl.indexOf('cloudflare') !== -1 || kl.indexOf('turnstile') !== -1 || kl.indexOf('cf_clearance') !== -1) {
        continue;
      }
      var v = window.localStorage.getItem(k);
      if (typeof v !== 'string' || v.length > 16384) continue;
      if (totalLen + k.length + v.length + 12 > 120000) break;
      out[k] = v;
      totalLen += k.length + v.length + 12;
    }
    var serialized = JSON.stringify(out);
    if (serialized && serialized.length > 2 && window.TrendLensVaultBridge && typeof window.TrendLensVaultBridge.onLocalStorageSnapshot === 'function') {
      window.TrendLensVaultBridge.onLocalStorageSnapshot(serialized);
    }
    return serialized;
  } catch (ignore) {
    return '';
  }
})();
"""

/**
 * Automatically checks IQ Option's "Lembrar-me / Remember me" checkbox when present and captures
 * manual logins submitted directly on official HTTPS IQ Option pages into the on-device encrypted vault.
 */
internal const val BROKER_LOGIN_CAPTURE_AND_REMEMBER_JS = """
(function() {
  try {
    if (window.location.protocol !== 'https:' || !/^(?:www\.|login\.|auth\.|app\.|eu\.)?iqoption\.com$/i.test(window.location.hostname || '')) {
      return;
    }
    function queryAllDeep(root, selector) {
      var out = [];
      if (!root || !root.querySelectorAll) return out;
      try {
        var found = root.querySelectorAll(selector);
        for (var i = 0; i < found.length; i++) out.push(found[i]);
        var all = root.querySelectorAll('*');
        for (var j = 0; j < all.length; j++) {
          if (all[j].shadowRoot) {
            var nested = queryAllDeep(all[j].shadowRoot, selector);
            for (var k = 0; k < nested.length; k++) out.push(nested[k]);
          }
        }
      } catch (e) {}
      return out;
    }
    function isVisible(el) {
      if (!el) return false;
      var style = window.getComputedStyle(el);
      if (!style || style.display === 'none' || style.visibility === 'hidden' || parseFloat(style.opacity || '1') < 0.05) return false;
      var rect = el.getBoundingClientRect();
      return rect.width >= 6 && rect.height >= 6;
    }
    function tickRememberMeCheckboxes() {
      var boxes = queryAllDeep(document, 'input[type="checkbox"]');
      for (var i = 0; i < boxes.length; i++) {
        var box = boxes[i];
        if (box.__tlRememberChecked || box.disabled) continue;
        var labelText = '';
        if (box.id) {
          var lbl = document.querySelector('label[for="' + box.id.replace(/"/g, '\\"') + '"]');
          if (lbl) labelText += ' ' + (lbl.innerText || '');
        }
        var parentLbl = box.closest ? (box.closest('label') || box.parentElement) : box.parentElement;
        if (parentLbl) labelText += ' ' + (parentLbl.innerText || '');
        var meta = ((box.name || '') + ' ' + (box.id || '') + ' ' + (box.getAttribute('aria-label') || '') + ' ' + labelText).toLowerCase();
        if (meta.indexOf('remember') !== -1 || meta.indexOf('lembrar') !== -1 || meta.indexOf('manter conectado') !== -1 || meta.indexOf('keep me') !== -1 || meta.indexOf('permanecer') !== -1) {
          box.__tlRememberChecked = true;
          if (!box.checked) {
            try { box.click(); } catch (e) { box.checked = true; }
          }
        }
      }
    }
    tickRememberMeCheckboxes();

    if (window.__tlLoginCaptureInstalled) return;
    window.__tlLoginCaptureInstalled = true;

    function captureCredentialsIfPresent() {
      try {
        if (!window.TrendLensVaultBridge || typeof window.TrendLensVaultBridge.onBrokerLoginSubmitted !== 'function') return;
        var passInputs = queryAllDeep(document, 'input[type="password"]').filter(function(el) {
          return !el.disabled && el.value && el.value.length >= 4;
        });
        if (passInputs.length === 0) return;
        var passEl = passInputs[0];
        var scope = (passEl.closest && passEl.closest('form')) || document;
        var emailInputs = queryAllDeep(scope, 'input[type="email"], input[name*="email" i], input[name*="identifier" i], input[name*="login" i], input[autocomplete="username"], input[autocomplete="email"], input[type="text"]').filter(function(el) {
          return el !== passEl && !el.disabled && el.type !== 'hidden' && el.value && el.value.indexOf('@') !== -1;
        });
        if (emailInputs.length === 0 && scope !== document) {
          emailInputs = queryAllDeep(document, 'input[type="email"], input[name*="email" i], input[name*="identifier" i], input[name*="login" i], input[autocomplete="username"], input[autocomplete="email"]').filter(function(el) {
            return el !== passEl && !el.disabled && el.value && el.value.indexOf('@') !== -1;
          });
        }
        if (emailInputs.length > 0) {
          var em = (emailInputs[0].value || '').trim();
          var pw = passEl.value || '';
          if (em.length >= 5 && pw.length >= 4) {
            window.TrendLensVaultBridge.onBrokerLoginSubmitted(em, pw);
          }
        }
      } catch (ignore) {}
    }

    document.addEventListener('submit', function() {
      tickRememberMeCheckboxes();
      captureCredentialsIfPresent();
    }, true);

    document.addEventListener('click', function(ev) {
      var t = ev && ev.target ? (ev.target.closest ? ev.target.closest('button, input[type="submit"], [role="button"]') : ev.target) : null;
      if (!t || !isVisible(t)) return;
      var txt = ((t.innerText || t.value || t.getAttribute('aria-label') || '')).trim().toLowerCase();
      if (t.type === 'submit' || txt === 'entrar' || txt === 'log in' || txt === 'login' || txt === 'sign in' || txt === 'acessar' || txt === 'continuar' || txt.indexOf('iniciar sess') !== -1) {
        tickRememberMeCheckboxes();
        captureCredentialsIfPresent();
      }
    }, true);

    document.addEventListener('keydown', function(ev) {
      if (ev && ev.key === 'Enter' && ev.target && ev.target.type === 'password') {
        tickRememberMeCheckboxes();
        captureCredentialsIfPresent();
      }
    }, true);
  } catch (ignore) {}
})();
"""

/**
 * Builds a defense-in-depth auto-login script that:
 * 1. Verifies strictly HTTPS on an allowlisted official IQ Option host inside the page context.
 * 2. Safely serializes credentials via JSON quoting (no raw string interpolation).
 * 3. Pauses immediately on CAPTCHA, 2FA/OTP prompts, or visible invalid-password errors.
 * 4. Walks light DOM and open Shadow DOM roots, ticks "Lembrar-me / Remember me", fills React/SPA
 *    controlled inputs via the native HTMLInputElement setter, and submits once.
 */
internal fun buildAutoLoginScript(email: String, password: String): String {
    val safeEmailJs = quoteForJsLiteral(email)
    val safePasswordJs = quoteForJsLiteral(password)
    return """
(function() {
  try {
    if (window.location.protocol !== 'https:' || !/^(?:www\.|login\.|auth\.|app\.|eu\.)?iqoption\.com$/i.test(window.location.hostname || '')) {
      return JSON.stringify({ status: 'NO_FORM', detail: 'host_guard' });
    }

    function queryAllDeep(root, selector) {
      var out = [];
      if (!root || !root.querySelectorAll) return out;
      try {
        var found = root.querySelectorAll(selector);
        for (var i = 0; i < found.length; i++) out.push(found[i]);
        var all = root.querySelectorAll('*');
        for (var j = 0; j < all.length; j++) {
          if (all[j].shadowRoot) {
            var nested = queryAllDeep(all[j].shadowRoot, selector);
            for (var k = 0; k < nested.length; k++) out.push(nested[k]);
          }
        }
      } catch (e) {}
      return out;
    }

    function isVisible(el) {
      if (!el) return false;
      var style = window.getComputedStyle(el);
      if (!style || style.display === 'none' || style.visibility === 'hidden' || parseFloat(style.opacity || '1') < 0.05) {
        return false;
      }
      var rect = el.getBoundingClientRect();
      return rect.width >= 8 && rect.height >= 8;
    }

    var challengeEls = queryAllDeep(
      document,
      '#challenge-running, #cf-wrapper, #challenge-stage, .cf-turnstile, .g-recaptcha, .h-captcha, ' +
      'iframe[src*="recaptcha"], iframe[src*="hcaptcha"], iframe[src*="challenges.cloudflare"]'
    );
    for (var c = 0; c < challengeEls.length; c++) {
      if (isVisible(challengeEls[c])) {
        return JSON.stringify({ status: 'PAUSED_CHALLENGE', detail: 'captcha' });
      }
    }

    var bodyText = ((document.body && document.body.innerText) ? document.body.innerText : '').toLowerCase();
    var errorHints = [
      'senha incorreta',
      'invalid password',
      'wrong password',
      'e-mail ou senha',
      'email ou senha',
      'incorrect email',
      'incorrect password',
      'invalid credentials',
      'credenciais inválidas',
      'credenciais invalidas',
      'too many attempts',
      'muitas tentativas',
      'conta bloqueada',
      'account blocked'
    ];
    for (var e = 0; e < errorHints.length; e++) {
      if (bodyText.indexOf(errorHints[e]) !== -1) {
        return JSON.stringify({ status: 'PAUSED_ERROR', detail: 'invalid_credentials' });
      }
    }

    var passwordInputs = queryAllDeep(document, 'input[type="password"]').filter(function(el) {
      return isVisible(el) && !el.disabled;
    });

    var otpInputs = queryAllDeep(
      document,
      'input[autocomplete="one-time-code"], input[name*="2fa" i], input[name*="otp" i], input[name*="sms" i], input[placeholder*="código" i], input[placeholder*="codigo" i]'
    ).filter(function(el) { return isVisible(el) && !el.disabled; });
    if (otpInputs.length > 0 && passwordInputs.length === 0) {
      return JSON.stringify({ status: 'PAUSED_CHALLENGE', detail: '2fa' });
    }

    if (passwordInputs.length > 0) {
      var passInput = passwordInputs[0];
      var scope = (passInput.closest && passInput.closest('form')) || document;
      var emailCandidates = queryAllDeep(
        scope,
        'input[type="email"], input[name*="email" i], input[name*="identifier" i], input[name*="login" i], input[autocomplete="username"], input[autocomplete="email"], input[type="text"]'
      ).filter(function(el) {
        return el !== passInput && isVisible(el) && !el.disabled && el.type !== 'hidden';
      });
      if (emailCandidates.length === 0 && scope !== document) {
        emailCandidates = queryAllDeep(
          document,
          'input[type="email"], input[name*="email" i], input[name*="identifier" i], input[name*="login" i], input[autocomplete="username"], input[autocomplete="email"]'
        ).filter(function(el) { return el !== passInput && isVisible(el) && !el.disabled; });
      }

      if (emailCandidates.length > 0) {
        var emailInput = emailCandidates[0];
        var nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value');
        function setNativeValue(input, val) {
          input.focus();
          if (nativeSetter && nativeSetter.set) {
            nativeSetter.set.call(input, val);
          } else {
            input.value = val;
          }
          try {
            input.dispatchEvent(new InputEvent('input', { bubbles: true, data: val, inputType: 'insertText' }));
          } catch (e) {
            input.dispatchEvent(new Event('input', { bubbles: true }));
          }
          input.dispatchEvent(new Event('change', { bubbles: true }));
          input.dispatchEvent(new Event('blur', { bubbles: true }));
        }

        setNativeValue(emailInput, $safeEmailJs);
        setNativeValue(passInput, $safePasswordJs);

        var rememberBoxes = queryAllDeep(scope, 'input[type="checkbox"]');
        for (var r = 0; r < rememberBoxes.length; r++) {
          var rb = rememberBoxes[r];
          if (rb.disabled || rb.checked) continue;
          var pText = (rb.parentElement && rb.parentElement.innerText) ? rb.parentElement.innerText : '';
          var rMeta = ((rb.name || '') + ' ' + (rb.id || '') + ' ' + (rb.getAttribute('aria-label') || '') + ' ' + pText).toLowerCase();
          if (rMeta.indexOf('remember') !== -1 || rMeta.indexOf('lembrar') !== -1 || rMeta.indexOf('manter conectado') !== -1 || rMeta.indexOf('keep me') !== -1) {
            try { rb.click(); } catch (e) { rb.checked = true; }
          }
        }

        var submitBtn = null;
        var btns = queryAllDeep(scope, 'button, input[type="submit"], [role="button"]').filter(function(el) {
          return isVisible(el) && !el.disabled;
        });
        for (var b = 0; b < btns.length; b++) {
          var el = btns[b];
          var txt = ((el.innerText || el.value || el.getAttribute('aria-label') || '')).trim().toLowerCase();
          if (el.type === 'submit' || txt === 'entrar' || txt === 'log in' || txt === 'login' || txt === 'sign in' || txt === 'acessar' || txt === 'continuar' || txt.indexOf('iniciar sess') !== -1) {
            submitBtn = el;
            break;
          }
        }
        var formEl = passInput.closest ? passInput.closest('form') : null;
        setTimeout(function() {
          try {
            if (submitBtn) {
              submitBtn.click();
            } else if (formEl && typeof formEl.requestSubmit === 'function') {
              formEl.requestSubmit();
            } else if (formEl) {
              formEl.submit();
            }
          } catch (ignore) {}
        }, 160);
        return JSON.stringify({ status: 'SUBMITTED', detail: 'login_submitted' });
      }
    }

    var isWorkspacePath = /\/(?:pwa\/)?traderoom/i.test(window.location.pathname || '');
    var canvases = queryAllDeep(document, 'canvas').filter(function(el) {
      var r = el.getBoundingClientRect();
      return r.width >= 180 && r.height >= 120;
    });
    var hasExpiredPrompt =
      bodyText.indexOf('sessão expirada') !== -1 ||
      bodyText.indexOf('sessao expirada') !== -1 ||
      bodyText.indexOf('session expired') !== -1 ||
      bodyText.indexOf('você foi desconectado') !== -1 ||
      bodyText.indexOf('voce foi desconectado') !== -1 ||
      bodyText.indexOf('logged out') !== -1;

    if (!isWorkspacePath || canvases.length === 0 || hasExpiredPrompt) {
      var triggers = queryAllDeep(document, 'button, a, [role="button"]').filter(function(el) {
        return isVisible(el) && !el.disabled;
      });
      for (var t = 0; t < triggers.length; t++) {
        var label = ((triggers[t].innerText || triggers[t].getAttribute('aria-label') || '')).trim().toLowerCase();
        if (label === 'entrar' || label === 'log in' || label === 'login' || label === 'sign in' || label === 'fazer login' || label === 'iniciar sessão' || label === 'iniciar sessao') {
          triggers[t].click();
          return JSON.stringify({ status: 'OPENED_LOGIN_FORM', detail: 'opened_login_trigger' });
        }
      }
    }

    if (isWorkspacePath && canvases.length > 0 && !hasExpiredPrompt) {
      return JSON.stringify({ status: 'ALREADY_LOGGED_IN', detail: 'workspace_active' });
    }

    return JSON.stringify({ status: 'NO_FORM', detail: 'idle' });
  } catch (err) {
    return JSON.stringify({ status: 'NO_FORM', detail: 'script_error' });
  }
})();
""".trimIndent()
}

internal fun parseAutoLoginOutcome(rawJsResult: String?): AutoLoginStepOutcome {
    if (rawJsResult.isNullOrBlank() || rawJsResult == "null") {
        return AutoLoginStepOutcome(AutoLoginStepStatus.NO_FORM)
    }
    val jsonText = runCatching {
        val trimmed = rawJsResult.trim()
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            org.json.JSONTokener(trimmed).nextValue().toString()
        } else {
            trimmed
        }
    }.getOrDefault(rawJsResult.trim())

    return runCatching {
        val obj = org.json.JSONObject(jsonText)
        val statusStr = obj.optString("status", "NO_FORM")
        val detail = obj.optString("detail", "")
        val status = AutoLoginStepStatus.entries.firstOrNull { it.name == statusStr }
            ?: AutoLoginStepStatus.NO_FORM
        AutoLoginStepOutcome(status = status, detail = detail)
    }.getOrDefault(AutoLoginStepOutcome(AutoLoginStepStatus.NO_FORM))
}

private class TrendLensVaultBridge(
    private val appContext: android.content.Context,
    private val webViewProvider: () -> WebView?,
    private val onAutoLoginVaultUpdated: () -> Unit
) {
    @JavascriptInterface
    fun onBrokerLoginSubmitted(email: String?, password: String?) {
        val wv = webViewProvider() ?: return
        val em = email ?: return
        val pw = password ?: return
        wv.post {
            if (!SecureBrokerSessionManager.isAllowlistedAutoLoginUrl(wv.url)) return@post
            val saved = SecureBrokerSessionManager.saveAutoLoginCredentials(
                context = appContext,
                email = em,
                password = pw,
                enabled = true,
                synchronous = true
            )
            if (saved) {
                onAutoLoginVaultUpdated()
            }
        }
    }

    @JavascriptInterface
    fun onLocalStorageSnapshot(rawJson: String?) {
        val wv = webViewProvider() ?: return
        val json = rawJson ?: return
        wv.post {
            if (!SecureBrokerSessionManager.isAllowlistedAutoLoginUrl(wv.url)) return@post
            SecureBrokerSessionManager.saveWebLocalStorageSnapshot(
                context = appContext,
                rawJson = json,
                synchronous = false
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun IqOptionWebViewContainer(
    initialUrl: String,
    isVisible: Boolean,
    isLoading: Boolean,
    progress: Int,
    errorMessage: String?,
    isDesktopMode: Boolean = false,
    autoLoginStatus: BrokerAutoLoginStatus = BrokerAutoLoginStatus(),
    autoLoginRetryNonce: Int = 0,
    onWebViewReady: (WebView) -> Unit,
    onProgressChanged: (Int, Boolean) -> Unit,
    onLocationChanged: (String, Boolean) -> Unit,
    onError: (String?) -> Unit,
    onRetry: () -> Unit,
    onOpenBrokerApp: () -> Unit = {},
    onToggleDesktopMode: () -> Unit = {},
    onClearWafCookies: () -> Unit = {},
    onOpenExternalChrome: () -> Unit = {},
    onSwitchBrokerUrl: (String) -> Unit = {},
    onBrokerBlockStateChanged: (Boolean) -> Unit = {},
    onSessionStateChanged: (Boolean) -> Unit = {},
    onOpenAutoLoginConfig: () -> Unit = {},
    onAutoLoginVaultUpdated: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val composeContext = LocalContext.current.applicationContext
    var popupWebView by remember { mutableStateOf<WebView?>(null) }
    var popupCurrentUrl by remember { mutableStateOf("") }
    var mainWebView by remember { mutableStateOf<WebView?>(null) }
    var defaultSystemUa by remember { mutableStateOf<String?>(null) }
    var webViewGeneration by remember { mutableIntStateOf(0) }
    var lastStableUrl by remember(initialUrl) { mutableStateOf(initialUrl) }
    var blockKind by remember { mutableStateOf(BrokerBlockKind.NONE) }
    var blockBannerDismissed by remember { mutableStateOf(false) }
    var hasCompletedFirstLoad by remember { mutableStateOf(false) }
    var coldStartRetried by remember { mutableStateOf(false) }

    val latestAutoLoginStatus by rememberUpdatedState(autoLoginStatus)
    val latestBlockKind by rememberUpdatedState(blockKind)
    val latestOnAutoLoginVaultUpdated by rememberUpdatedState(onAutoLoginVaultUpdated)
    var autoLoginBannerMessage by remember { mutableStateOf<String?>(null) }
    var autoLoginPausedForUser by remember { mutableStateOf(false) }
    var autoLoginLastSubmitAtMs by remember { mutableLongStateOf(0L) }
    var autoLoginWindowStartMs by remember { mutableLongStateOf(0L) }
    var autoLoginAttemptsInWindow by remember { mutableIntStateOf(0) }
    var autoLoginInFlight by remember { mutableStateOf(false) }
    var vaultSetupBannerDismissed by remember { mutableStateOf(false) }
    var hasLiveBrokerSession by remember {
        mutableStateOf(BrokerSessionPrefs.hasIqOptionSessionCookies())
    }

    LaunchedEffect(
        autoLoginStatus.hasCredentials,
        autoLoginStatus.enabled,
        autoLoginStatus.maskedEmail
    ) {
        autoLoginPausedForUser = false
        autoLoginAttemptsInWindow = 0
        autoLoginWindowStartMs = 0L
        autoLoginLastSubmitAtMs = 0L
        if (!autoLoginStatus.enabled || !autoLoginStatus.hasCredentials) {
            autoLoginBannerMessage = null
        }
    }

    LaunchedEffect(blockKind) {
        onBrokerBlockStateChanged(blockKind != BrokerBlockKind.NONE)
    }

    LaunchedEffect(isDesktopMode, mainWebView) {
        val wv = mainWebView ?: return@LaunchedEffect
        val targetUa = BrokerSessionPrefs.buildCleanChromeUserAgent(
            rawUa = defaultSystemUa ?: wv.settings.userAgentString,
            desktopMode = isDesktopMode
        )
        if (wv.settings.userAgentString != targetUa) {
            wv.settings.userAgentString = targetUa
            wv.settings.useWideViewPort = true
            wv.settings.loadWithOverviewMode = true
            blockKind = BrokerBlockKind.NONE
            blockBannerDismissed = false
            val targetUrl = wv.url?.takeIf { it.startsWith("http") } ?: TrendLensUiState.TRADEROOM_URL
            wv.loadUrl(targetUrl, BrokerSessionPrefs.BRAZIL_PT_HEADERS)
        }
    }

    fun updateStableBrokerUrl(candidateUrl: String) {
        if (!BrokerSessionPrefs.isEligibleNonAuthBrokerUrl(candidateUrl)) return
        if (BrokerSessionPrefs.isTradingWorkspaceUrl(lastStableUrl) &&
            !BrokerSessionPrefs.isTradingWorkspaceUrl(candidateUrl)
        ) {
            return
        }
        lastStableUrl = candidateUrl
    }

    fun captureWebLocalStorageSnapshot(targetView: WebView?) {
        val wv = targetView ?: return
        if (!SecureBrokerSessionManager.isAllowlistedAutoLoginUrl(wv.url)) return
        runCatching { wv.evaluateJavascript(CAPTURE_LOCAL_STORAGE_JS, null) }
    }

    fun installBrokerLoginCapture(targetView: WebView?) {
        val wv = targetView ?: return
        if (!SecureBrokerSessionManager.isAllowlistedAutoLoginUrl(wv.url)) return
        runCatching { wv.evaluateJavascript(BROKER_LOGIN_CAPTURE_AND_REMEMBER_JS, null) }
    }

    fun persistEncryptedSession(synchronous: Boolean = false) {
        runCatching { CookieManager.getInstance().flush() }
        mainWebView?.let { wv ->
            wv.post { captureWebLocalStorageSnapshot(wv) }
        }
        val persisted = SecureBrokerSessionManager.captureAndPersistFromWebView(
            context = composeContext,
            synchronous = synchronous
        )
        if (persisted) {
            hasLiveBrokerSession = true
            onSessionStateChanged(true)
            autoLoginAttemptsInWindow = 0
            autoLoginPausedForUser = false
            if (autoLoginBannerMessage != null) {
                autoLoginBannerMessage = null
            }
        } else {
            hasLiveBrokerSession = BrokerSessionPrefs.hasIqOptionSessionCookies()
            onSessionStateChanged(SecureBrokerSessionManager.hasValidSavedSession(composeContext))
        }
    }

    fun maybeAttemptAutoLogin(targetView: WebView?) {
        val wv = targetView ?: return
        if (autoLoginInFlight || autoLoginPausedForUser) return
        if (latestBlockKind != BrokerBlockKind.NONE) return
        val status = latestAutoLoginStatus
        if (!status.hasCredentials || !status.enabled) return
        val currentUrl = wv.url
        if (!SecureBrokerSessionManager.isAllowlistedAutoLoginUrl(currentUrl)) return

        val now = System.currentTimeMillis()
        if (autoLoginWindowStartMs == 0L || now - autoLoginWindowStartMs > 90_000L) {
            autoLoginWindowStartMs = now
            autoLoginAttemptsInWindow = 0
        }
        if (autoLoginAttemptsInWindow >= 2) {
            autoLoginPausedForUser = true
            autoLoginBannerMessage =
                "Login automático pausado após 2 tentativas. Confira sua conta ou toque em Configurar."
            return
        }
        if (autoLoginLastSubmitAtMs > 0L && now - autoLoginLastSubmitAtMs < 12_000L) {
            return
        }

        val credentials = SecureBrokerSessionManager.getAutoLoginCredentials(composeContext) ?: return
        autoLoginInFlight = true
        val script = buildAutoLoginScript(credentials.email, credentials.password)
        runCatching {
            wv.evaluateJavascript(script) { raw ->
                autoLoginInFlight = false
                val outcome = parseAutoLoginOutcome(raw)
                when (outcome.status) {
                    AutoLoginStepStatus.SUBMITTED -> {
                        autoLoginLastSubmitAtMs = System.currentTimeMillis()
                        autoLoginAttemptsInWindow++
                        autoLoginBannerMessage = "Login automático: entrando na IQ Option..."
                        wv.postDelayed({ persistEncryptedSession(synchronous = false) }, 3_500L)
                    }
                    AutoLoginStepStatus.OPENED_LOGIN_FORM -> {
                        autoLoginBannerMessage = "Login automático: abrindo formulário de login..."
                        wv.postDelayed({ maybeAttemptAutoLogin(wv) }, 1_300L)
                    }
                    AutoLoginStepStatus.ALREADY_LOGGED_IN -> {
                        autoLoginAttemptsInWindow = 0
                        autoLoginPausedForUser = false
                        autoLoginBannerMessage = null
                    }
                    AutoLoginStepStatus.PAUSED_CHALLENGE -> {
                        autoLoginPausedForUser = true
                        autoLoginBannerMessage = if (outcome.detail == "2fa") {
                            "Login automático pausado: digite o código 2FA na corretora."
                        } else {
                            "Login automático pausado: conclua a verificação de segurança (CAPTCHA)."
                        }
                    }
                    AutoLoginStepStatus.PAUSED_ERROR -> {
                        autoLoginPausedForUser = true
                        autoLoginBannerMessage =
                            "Login automático pausado: e-mail ou senha recusados. Atualize no cofre."
                    }
                    AutoLoginStepStatus.NO_FORM -> {
                        if (!autoLoginPausedForUser &&
                            System.currentTimeMillis() - autoLoginLastSubmitAtMs > 8_000L
                        ) {
                            autoLoginBannerMessage = null
                        }
                    }
                }
            }
        }.onFailure {
            autoLoginInFlight = false
        }
    }

    fun closePopup() {
        persistEncryptedSession(synchronous = true)
        popupWebView?.let { child ->
            (child.parent as? ViewGroup)?.removeView(child)
            child.stopLoading()
            child.destroy()
        }
        popupWebView = null
        popupCurrentUrl = ""
    }

    LaunchedEffect(composeContext) {
        while (true) {
            delay(15_000L)
            persistEncryptedSession(synchronous = false)
        }
    }

    LaunchedEffect(autoLoginRetryNonce) {
        if (autoLoginRetryNonce <= 0) return@LaunchedEffect
        autoLoginPausedForUser = false
        autoLoginAttemptsInWindow = 0
        autoLoginWindowStartMs = 0L
        autoLoginLastSubmitAtMs = 0L
        mainWebView?.let { wv ->
            wv.post { maybeAttemptAutoLogin(wv) }
        }
    }

    LaunchedEffect(
        isVisible,
        autoLoginStatus.enabled,
        autoLoginStatus.hasCredentials,
        blockKind,
        mainWebView
    ) {
        val wv = mainWebView ?: return@LaunchedEffect
        if (!isVisible || !autoLoginStatus.enabled || !autoLoginStatus.hasCredentials || blockKind != BrokerBlockKind.NONE) {
            return@LaunchedEffect
        }
        delay(800L)
        while (true) {
            wv.post { maybeAttemptAutoLogin(wv) }
            delay(5_000L)
        }
    }

    DisposableEffect(Unit) {
        onDispose { closePopup() }
    }

    Box(modifier = modifier.background(ObsidianBg)) {
        key(webViewGeneration) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                val appContext = context.applicationContext

                fun configureMobileChromeSettings(wv: WebView) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        wv.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
                    }
                    wv.settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        @Suppress("DEPRECATION")
                        databaseEnabled = true
                        allowContentAccess = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                        mediaPlaybackRequiresUserGesture = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        javaScriptCanOpenWindowsAutomatically = true
                        setSupportMultipleWindows(true)

                        val currentUa = userAgentString
                        if (defaultSystemUa == null && !currentUa.isNullOrBlank()) {
                            defaultSystemUa = currentUa
                        }
                        userAgentString = BrokerSessionPrefs.buildCleanChromeUserAgent(
                            rawUa = defaultSystemUa ?: currentUa,
                            desktopMode = isDesktopMode
                        )
                    }
                    runCatching {
                        val cookieMgr = CookieManager.getInstance()
                        cookieMgr.setAcceptCookie(true)
                        cookieMgr.setAcceptThirdPartyCookies(wv, true)
                    }
                    installTurnRelayIceSanitizer(wv)
                }

                BrazilLocaleConfig.applyBrazilProcessDefaults()
                runCatching { CookieManager.getInstance().setAcceptCookie(true) }
                SecureBrokerSessionManager.restoreToWebViewIfMissing(appContext)
                hasLiveBrokerSession = BrokerSessionPrefs.hasIqOptionSessionCookies()
                onSessionStateChanged(SecureBrokerSessionManager.hasValidSavedSession(appContext))
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    isFocusable = true
                    isFocusableInTouchMode = true
                    setBackgroundColor(android.graphics.Color.parseColor("#081012"))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
                    }
                    configureMobileChromeSettings(this)
                    addJavascriptInterface(
                        TrendLensVaultBridge(
                            appContext = appContext,
                            webViewProvider = { this },
                            onAutoLoginVaultUpdated = { latestOnAutoLoginVaultUpdated() }
                        ),
                        "TrendLensVaultBridge"
                    )
                    SecureBrokerSessionManager.getWebLocalStorageSnapshot(appContext)?.let { snapshot ->
                        runCatching {
                            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                                WebViewCompat.addDocumentStartJavaScript(
                                    this,
                                    buildLocalStorageRestoreScript(snapshot),
                                    ALLOWED_IQ_ORIGINS
                                )
                            }
                        }
                    }

                    runCatching {
                        val swSettings = ServiceWorkerController.getInstance().serviceWorkerWebSettings
                        swSettings.allowContentAccess = true
                        swSettings.cacheMode = WebSettings.LOAD_DEFAULT
                        swSettings.blockNetworkLoads = false
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            onProgressChanged(newProgress, newProgress in 1..99)
                        }

                        override fun onCreateWindow(
                            view: WebView?,
                            isDialog: Boolean,
                            isUserGesture: Boolean,
                            resultMsg: Message?
                        ): Boolean {
                            val parentContext = view?.context ?: return false
                            val child = WebView(parentContext).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                isFocusable = true
                                isFocusableInTouchMode = true
                                configureMobileChromeSettings(this)
                                addJavascriptInterface(
                                    TrendLensVaultBridge(
                                        appContext = appContext,
                                        webViewProvider = { this },
                                        onAutoLoginVaultUpdated = { latestOnAutoLoginVaultUpdated() }
                                    ),
                                    "TrendLensVaultBridge"
                                )
                                webChromeClient = object : WebChromeClient() {
                                    override fun onCloseWindow(window: WebView?) {
                                        closePopup()
                                    }
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun shouldInterceptRequest(
                                        childView: WebView?,
                                        request: WebResourceRequest?
                                    ): android.webkit.WebResourceResponse? {
                                        val host = request?.url?.host
                                        if (isBlockedTurnRelayHost(host)) {
                                            return android.webkit.WebResourceResponse(
                                                "text/plain",
                                                "UTF-8",
                                                204,
                                                "No Content",
                                                emptyMap(),
                                                java.io.ByteArrayInputStream(ByteArray(0))
                                            )
                                        }
                                        return super.shouldInterceptRequest(childView, request)
                                    }

                                    override fun onPageStarted(
                                        childView: WebView?,
                                        url: String?,
                                        favicon: Bitmap?
                                    ) {
                                        if (!url.isNullOrBlank()) {
                                            popupCurrentUrl = url
                                        }
                                        childView?.evaluateJavascript(TURN_RELAY_ICE_SANITIZER_JS, null)
                                        runCatching { CookieManager.getInstance().flush() }
                                    }

                                    override fun onPageFinished(childView: WebView?, url: String?) {
                                        if (!url.isNullOrBlank()) {
                                            popupCurrentUrl = url
                                        }
                                        installBrokerLoginCapture(childView)
                                        captureWebLocalStorageSnapshot(childView)
                                        persistEncryptedSession(synchronous = false)
                                        if (!url.isNullOrBlank()) {
                                            BrokerSessionPrefs.saveBrokerUrlIfEligible(appContext, url)
                                            if (BrokerSessionPrefs.isTradingWorkspaceUrl(url)) {
                                                updateStableBrokerUrl(url)
                                                val currentMainUrl = mainWebView?.url.orEmpty()
                                                if (!BrokerSessionPrefs.isTradingWorkspaceUrl(currentMainUrl)) {
                                                    mainWebView?.loadUrl(url, BrokerSessionPrefs.BRAZIL_PT_HEADERS)
                                                }
                                                closePopup()
                                            }
                                        }
                                    }

                                    override fun doUpdateVisitedHistory(
                                        childView: WebView?,
                                        url: String?,
                                        isReload: Boolean
                                    ) {
                                        super.doUpdateVisitedHistory(childView, url, isReload)
                                        persistEncryptedSession(synchronous = false)
                                        if (!url.isNullOrBlank()) {
                                            BrokerSessionPrefs.saveBrokerUrlIfEligible(appContext, url)
                                        }
                                    }

                                    override fun shouldOverrideUrlLoading(
                                        childView: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val reqUrl = request?.url?.toString() ?: return false
                                        if (BrokerSessionPrefs.isBlockedExternalEscapeUrl(reqUrl)) {
                                            return true
                                        }
                                        if (reqUrl.startsWith("http://") || reqUrl.startsWith("https://")) {
                                            return false
                                        }
                                        val handled = handleExternalBrokerIntentUrl(
                                            context = appContext,
                                            rawUrl = reqUrl,
                                            webViewFallback = { fallbackUrl ->
                                                childView?.loadUrl(fallbackUrl, BrokerSessionPrefs.BRAZIL_PT_HEADERS)
                                            }
                                        )
                                        return handled || (!reqUrl.startsWith("about:") && !reqUrl.startsWith("data:") && !reqUrl.startsWith("blob:"))
                                    }
                                }
                            }
                            val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                            transport.webView = child
                            resultMsg.sendToTarget()
                            popupWebView = child
                            return true
                        }

                        override fun onCloseWindow(window: WebView?) {
                            closePopup()
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): android.webkit.WebResourceResponse? {
                            val host = request?.url?.host
                            if (isBlockedTurnRelayHost(host)) {
                                return android.webkit.WebResourceResponse(
                                    "text/plain",
                                    "UTF-8",
                                    204,
                                    "No Content",
                                    emptyMap(),
                                    java.io.ByteArrayInputStream(ByteArray(0))
                                )
                            }
                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun onPageStarted(
                            view: WebView?,
                            url: String?,
                            favicon: Bitmap?
                        ) {
                            view?.evaluateJavascript(TURN_RELAY_ICE_SANITIZER_JS, null)
                            if (SecureBrokerSessionManager.isAllowlistedAutoLoginUrl(url)) {
                                SecureBrokerSessionManager.getWebLocalStorageSnapshot(appContext)?.let { snapshot ->
                                    view?.evaluateJavascript(buildLocalStorageRestoreScript(snapshot), null)
                                }
                            }
                            if (!url.isNullOrBlank()) {
                                onLocationChanged(url, view?.canGoBack() == true)
                                if (BrokerSessionPrefs.isSecurityChallengeOrAuthUrl(url)) {
                                    onBrokerBlockStateChanged(true)
                                }
                            }
                            onProgressChanged(10, true)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            if (!url.isNullOrBlank()) {
                                updateStableBrokerUrl(url)
                                onLocationChanged(url, view?.canGoBack() == true)
                                BrokerSessionPrefs.saveBrokerUrlIfEligible(appContext, url)
                            }
                            installBrokerLoginCapture(view)
                            captureWebLocalStorageSnapshot(view)
                            persistEncryptedSession(synchronous = false)
                            hasCompletedFirstLoad = true
                            onProgressChanged(100, false)

                            // Check if IQ Option / Cloudflare rendered a genuine full-page block or security challenge
                            fun checkBrokerBlockAndGuard(targetView: WebView?) {
                                val currentUrl = targetView?.url ?: url
                                val isChallengeUrl = BrokerSessionPrefs.isSecurityChallengeOrAuthUrl(currentUrl)
                                targetView?.evaluateJavascript(BROKER_BLOCK_DETECT_JS) { raw ->
                                    val decoded = runCatching {
                                        val trimmed = raw?.trim().orEmpty()
                                        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
                                            org.json.JSONTokener(trimmed).nextValue().toString()
                                        } else {
                                            trimmed
                                        }
                                    }.getOrDefault(raw.orEmpty())
                                    val detected = detectBrokerBlockKindFromText(decoded, currentUrl)
                                    if (detected != blockKind) {
                                        blockKind = detected
                                        if (detected != BrokerBlockKind.NONE) {
                                            blockBannerDismissed = false
                                        }
                                    }
                                    onBrokerBlockStateChanged(detected != BrokerBlockKind.NONE || isChallengeUrl)
                                }
                            }
                            checkBrokerBlockAndGuard(view)
                            view?.postDelayed({
                                checkBrokerBlockAndGuard(view)
                                installBrokerLoginCapture(view)
                                captureWebLocalStorageSnapshot(view)
                            }, 2_500L)
                            view?.postDelayed({ maybeAttemptAutoLogin(view) }, 450L)
                        }

                        override fun doUpdateVisitedHistory(
                            view: WebView?,
                            url: String?,
                            isReload: Boolean
                        ) {
                            super.doUpdateVisitedHistory(view, url, isReload)
                            if (!url.isNullOrBlank()) {
                                updateStableBrokerUrl(url)
                                onLocationChanged(url, view?.canGoBack() == true)
                                BrokerSessionPrefs.saveBrokerUrlIfEligible(appContext, url)
                            }
                            installBrokerLoginCapture(view)
                            captureWebLocalStorageSnapshot(view)
                            persistEncryptedSession(synchronous = false)
                            view?.postDelayed({ maybeAttemptAutoLogin(view) }, 450L)
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val reqUrl = request?.url?.toString() ?: return false
                            if (BrokerSessionPrefs.isBlockedExternalEscapeUrl(reqUrl)) {
                                return true
                            }
                            // Never interrupt Cloudflare/WAF challenges, captchas, or login/auth flows
                            if (BrokerSessionPrefs.isSecurityChallengeOrAuthUrl(reqUrl)) {
                                onBrokerBlockStateChanged(true)
                                return false
                            }
                            if (reqUrl.startsWith("http://") || reqUrl.startsWith("https://")) {
                                return false
                            }
                            val handled = handleExternalBrokerIntentUrl(
                                context = appContext,
                                rawUrl = reqUrl,
                                webViewFallback = { fallbackUrl ->
                                    view?.loadUrl(fallbackUrl, BrokerSessionPrefs.BRAZIL_PT_HEADERS)
                                }
                            )
                            return handled || (!reqUrl.startsWith("about:") && !reqUrl.startsWith("data:") && !reqUrl.startsWith("blob:"))
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            if (request?.isForMainFrame == true) {
                                if (!hasCompletedFirstLoad && !coldStartRetried && view != null) {
                                    coldStartRetried = true
                                    view.postDelayed({
                                        runCatching {
                                            view.loadUrl(
                                                TrendLensUiState.TRADEROOM_URL,
                                                BrokerSessionPrefs.BRAZIL_PT_HEADERS
                                            )
                                        }
                                    }, 1_500L)
                                    return
                                }
                                val desc = error?.description?.toString()
                                    ?: "Não foi possível carregar o gráfico da IQ Option."
                                onError(desc)
                            }
                        }

                        override fun onRenderProcessGone(
                            view: WebView?,
                            detail: RenderProcessGoneDetail?
                        ): Boolean {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                persistEncryptedSession(synchronous = true)
                                view?.let { deadView ->
                                    (deadView.parent as? ViewGroup)?.removeView(deadView)
                                    deadView.destroy()
                                }
                                lastStableUrl = TrendLensUiState.TRADEROOM_URL
                                webViewGeneration++
                                onError(null)
                                return true
                            }
                            return false
                        }
                    }

                    mainWebView = this
                    loadUrl(
                        lastStableUrl.ifBlank { TrendLensUiState.TRADEROOM_URL },
                        BrokerSessionPrefs.BRAZIL_PT_HEADERS
                    )
                    onWebViewReady(this)
                }
            },
            update = { webView ->
                webView.visibility = if (isVisible) View.VISIBLE else View.GONE
            }
        )
        }

        if (isVisible && isLoading && errorMessage == null) {
            LinearProgressIndicator(
                progress = { (progress / 100f).coerceIn(0.05f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .align(Alignment.TopCenter),
                color = EmeraldBright,
                trackColor = TerminalBorder
            )
        }

        if (isVisible && autoLoginBannerMessage != null && errorMessage == null) {
            val accentColor = if (autoLoginPausedForUser) AmberWarning else EmeraldBright
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(TerminalSurfaceElevated.copy(alpha = 0.96f))
                    .border(1.dp, accentColor, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("auto_login_status_banner"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = autoLoginBannerMessage.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                if (autoLoginPausedForUser) {
                    TextButton(
                        onClick = {
                            autoLoginBannerMessage = null
                            onOpenAutoLoginConfig()
                        },
                        modifier = Modifier.testTag("auto_login_banner_configure_button")
                    ) {
                        Text(
                            text = "Configurar",
                            style = MaterialTheme.typography.labelMedium,
                            color = EmeraldBright,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                IconButton(
                    onClick = { autoLoginBannerMessage = null },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Fechar aviso de login automático",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        } else if (
            isVisible &&
            !autoLoginStatus.hasCredentials &&
            !hasLiveBrokerSession &&
            !vaultSetupBannerDismissed &&
            errorMessage == null &&
            blockKind == BrokerBlockKind.NONE
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(TerminalSurfaceElevated.copy(alpha = 0.96f))
                    .border(1.dp, EmeraldBright.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                    .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                    .testTag("auto_login_setup_banner"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = EmeraldBright,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.auto_login_setup_banner_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { onOpenAutoLoginConfig() },
                    modifier = Modifier.testTag("auto_login_setup_banner_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldBright,
                        contentColor = ObsidianBg
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(R.string.auto_login_setup_banner_button),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(
                    onClick = { vaultSetupBannerDismissed = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Dispensar aviso para salvar login",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        if (isVisible && !hasCompletedFirstLoad && isLoading && progress < 75 && errorMessage == null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(TerminalSurfaceElevated.copy(alpha = 0.94f))
                    .border(1.dp, TerminalBorder, RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .testTag("initial_broker_loading_card"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(26.dp),
                    color = EmeraldBright,
                    strokeWidth = 2.6.dp
                )
                Text(
                    text = "Carregando IQ Option Traderoom...",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "iqoption.com/pwa/traderoom",
                    style = MaterialTheme.typography.labelSmall,
                    color = IqOptionOrange,
                    textAlign = TextAlign.Center
                )
            }
        }

        if (isVisible && blockKind != BrokerBlockKind.NONE && !blockBannerDismissed && errorMessage == null) {
            val bannerTitle = when (blockKind) {
                BrokerBlockKind.WAF_OR_CLOUDFLARE -> "Proteção Anti-Bloqueio (Cloudflare / WAF)"
                BrokerBlockKind.BROWSER_OR_LOGIN_BLOCK -> "Bloqueio de navegador detectado"
                BrokerBlockKind.GEO_BLOCK -> "Bloqueio por IP fora do Brasil"
                BrokerBlockKind.NONE -> ""
            }
            val bannerBody = when (blockKind) {
                BrokerBlockKind.WAF_OR_CLOUDFLARE ->
                    "O robô pausou com segurança enquanto a verificação está na tela. Aguarde concluir ou toque em Desbloquear Sessão para limpar os cookies WAF e abrir em Chrome Mobile limpo."
                BrokerBlockKind.BROWSER_OR_LOGIN_BLOCK ->
                    "O login via Google/Chrome pode ser bloqueado no simulador. Entre com seu E-mail e Senha na tela Login Oficial ou salve no Cofre para logar automaticamente dentro do app."
                BrokerBlockKind.GEO_BLOCK ->
                    "A IQ Option bloqueou o IP da rede atual (EUA/VPN). No celular com internet do Brasil (sem VPN) ela abre direto; você também pode tentar o Login Oficial ou abrir no Chrome."
                BrokerBlockKind.NONE -> ""
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(10.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(TerminalSurfaceElevated.copy(alpha = 0.97f))
                    .border(1.dp, AmberWarning, RoundedCornerShape(14.dp))
                    .padding(12.dp)
                    .testTag("geo_block_brazil_banner"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (blockKind == BrokerBlockKind.GEO_BLOCK) Icons.Rounded.Language else Icons.Rounded.Security,
                            contentDescription = null,
                            tint = AmberWarning,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = bannerTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    IconButton(
                        onClick = { blockBannerDismissed = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Fechar aviso",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = bannerBody,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            blockKind = BrokerBlockKind.NONE
                            onClearWafCookies()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("anti_block_clear_waf_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldBright,
                            contentColor = ObsidianBg
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Desbloquear Sessão",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            blockKind = BrokerBlockKind.NONE
                            onSwitchBrokerUrl(BrokerSessionPrefs.LOGIN_BR_URL)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("anti_block_login_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Login,
                            contentDescription = null,
                            tint = TextPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Login Oficial",
                            color = TextPrimary,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onOpenExternalChrome() },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("anti_block_open_chrome_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                            tint = IqOptionOrange,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Abrir Chrome",
                            color = IqOptionOrange,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            blockKind = BrokerBlockKind.NONE
                            onToggleDesktopMode()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("anti_block_desktop_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DesktopWindows,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isDesktopMode) "Chrome Mobile" else "Modo Desktop",
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        if (isVisible && errorMessage != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(TerminalSurface.copy(alpha = 0.96f))
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Rounded.WifiOff,
                    contentDescription = null,
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Conexão indisponível",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Verifique sua conexão com a internet para carregar a corretora IQ Option Brasil ($errorMessage).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldBright,
                        contentColor = ObsidianBg
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Tentar novamente")
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(onClick = onOpenBrokerApp) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = null,
                        tint = IqOptionOrange,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Voltar à Corretora Brasil", color = IqOptionOrange)
                }
            }
        }

        popupWebView?.let { childView ->
            Dialog(
                onDismissRequest = { closePopup() },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(TerminalSurface)
                        .border(1.dp, TerminalBorder, RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TerminalToolbar)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Login / Autenticação",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        IconButton(onClick = { closePopup() }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Fechar janela de login",
                                tint = TextSecondary
                            )
                        }
                    }
                    if (popupCurrentUrl.contains("accounts.google.", ignoreCase = true) ||
                        popupCurrentUrl.contains("google.com/o/oauth", ignoreCase = true)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(TerminalSurfaceElevated)
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                .testTag("popup_google_fallback_banner"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = androidx.compose.ui.res.stringResource(R.string.popup_google_login_fallback_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = AmberWarning,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    closePopup()
                                    onSwitchBrokerUrl(BrokerSessionPrefs.LOGIN_BR_URL)
                                    if (!latestAutoLoginStatus.hasCredentials) {
                                        onOpenAutoLoginConfig()
                                    }
                                },
                                modifier = Modifier.testTag("popup_switch_to_email_login_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EmeraldBright,
                                    contentColor = ObsidianBg
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = androidx.compose.ui.res.stringResource(R.string.popup_google_login_email_button),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = {
                            childView.apply {
                                isFocusable = true
                                isFocusableInTouchMode = true
                                requestFocus()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun WebViewLifecycleObserver(webView: WebView?) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val appContext = LocalContext.current.applicationContext

    DisposableEffect(webView, lifecycleOwner, appContext) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    webView?.onResume()
                    webView?.resumeTimers()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    // Keep JS timers & WebSockets active across dialogs/brief switches so IQ Option does not drop the session
                    if (SecureBrokerSessionManager.isAllowlistedAutoLoginUrl(webView?.url)) {
                        runCatching { webView?.evaluateJavascript(CAPTURE_LOCAL_STORAGE_JS, null) }
                    }
                    runCatching { CookieManager.getInstance().flush() }
                    SecureBrokerSessionManager.captureAndPersistFromWebView(appContext, synchronous = true)
                }
                Lifecycle.Event.ON_STOP -> {
                    if (SecureBrokerSessionManager.isAllowlistedAutoLoginUrl(webView?.url)) {
                        runCatching { webView?.evaluateJavascript(CAPTURE_LOCAL_STORAGE_JS, null) }
                    }
                    runCatching { CookieManager.getInstance().flush() }
                    SecureBrokerSessionManager.captureAndPersistFromWebView(appContext, synchronous = true)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (SecureBrokerSessionManager.isAllowlistedAutoLoginUrl(webView?.url)) {
                runCatching { webView?.evaluateJavascript(CAPTURE_LOCAL_STORAGE_JS, null) }
            }
            runCatching { CookieManager.getInstance().flush() }
            SecureBrokerSessionManager.captureAndPersistFromWebView(appContext, synchronous = true)
            runCatching {
                webView?.stopLoading()
                webView?.destroy()
            }
        }
    }
}
