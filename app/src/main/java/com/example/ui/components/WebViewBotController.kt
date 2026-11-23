package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.webkit.WebView
import com.example.model.NormalizedRect
import com.example.model.SignalDirection
import com.example.model.TradeOutcome
import org.json.JSONObject
import org.json.JSONTokener
import java.util.Locale

data class BotPoint(
    val normX: Float,
    val normY: Float
)

data class BotExecutionResult(
    val success: Boolean,
    val detail: String,
    val balanceBeforeClick: Double? = null,
    val stakeAppliedInDom: Boolean = false
)

/**
 * Locates the broker's CALL ("Acima") or PUT ("Abaixo") button in the live WebView DOM
 * (or uses user-calibrated viewport coordinates for canvas-rendered buttons), optionally sets
 * the stake amount for Martingale (via DOM input, DOM stake-box tap, calibrated stake-box tap,
 * and native KeyEvents), captures pre-trade balance, and dispatches an authentic Android
 * touchscreen tap so Chromium treats it as a trusted user click.
 */
fun executeWebViewTradeButtonClick(
    webView: WebView,
    direction: SignalDirection,
    calibratedPoint: BotPoint?,
    calibratedAmountPoint: BotPoint? = null,
    knownBalanceBefore: Double? = null,
    stakeAmountToApply: Double? = null,
    stakePreApplied: Boolean = false,
    onResult: (BotExecutionResult) -> Unit
) {
    if (direction == SignalDirection.NEUTRAL) {
        onResult(BotExecutionResult(false, "Direção neutra não executa clique."))
        return
    }

    webView.post {
        val width = webView.width
        val height = webView.height
        if (width <= 0 || height <= 0) {
            onResult(BotExecutionResult(false, "Navegador ainda não dimensionado para clique."))
            return@post
        }

        dismissBlockingExpirationModalIfOpen(webView) {
            readWebViewBrokerBalance(webView) { domBalance ->
                val balanceBefore = domBalance ?: knownBalanceBefore
                val proceedWithClick: (Boolean) -> Unit = { stakeApplied ->
                    if (calibratedPoint != null) {
                        val px = (calibratedPoint.normX.coerceIn(0.02f, 0.98f)) * width
                        val py = (calibratedPoint.normY.coerceIn(0.05f, 0.98f)) * height
                        dispatchNativeTap(webView, px, py) {
                            val buttonName = if (direction == SignalDirection.CALL_UP) "Acima (CALL)" else "Abaixo (PUT)"
                            onResult(
                                BotExecutionResult(
                                    success = true,
                                    detail = "Robô clicou em $buttonName (calibrado)",
                                    balanceBeforeClick = balanceBefore,
                                    stakeAppliedInDom = stakeApplied
                                )
                            )
                        }
                    } else {
                        val script = buildFindTradeButtonScript(direction)
                        webView.evaluateJavascript(script) { rawResult ->
                            val parsed = parseDomButtonResult(rawResult)
                            if (parsed != null) {
                                val px = parsed.normX * width
                                val py = parsed.normY * height
                                dispatchNativeTap(webView, px, py) {
                                    val fallbackName = if (direction == SignalDirection.CALL_UP) "Acima (CALL)" else "Abaixo (PUT)"
                                    val label = parsed.label.ifBlank { fallbackName }
                                    onResult(
                                        BotExecutionResult(
                                            success = true,
                                            detail = "Robô clicou em $label",
                                            balanceBeforeClick = balanceBefore,
                                            stakeAppliedInDom = stakeApplied
                                        )
                                    )
                                }
                            } else {
                                val expected = if (direction == SignalDirection.CALL_UP) "Acima" else "Abaixo"
                                onResult(
                                    BotExecutionResult(
                                        success = false,
                                        detail = "Botão \"$expected\" não detectado na página. Toque em 'Calibrar botões' no painel abaixo.",
                                        balanceBeforeClick = balanceBefore,
                                        stakeAppliedInDom = stakeApplied
                                    )
                                )
                            }
                        }
                    }
                }

                if (stakePreApplied) {
                    proceedWithClick(true)
                } else if (stakeAmountToApply != null && stakeAmountToApply > 0.0) {
                    applyWebViewStakeAmount(
                        webView = webView,
                        stakeAmount = stakeAmountToApply,
                        calibratedAmountPoint = calibratedAmountPoint
                    ) { applied, needsSettleDelay ->
                        if (applied && needsSettleDelay) {
                            webView.postDelayed({ proceedWithClick(true) }, 160L)
                        } else {
                            proceedWithClick(applied)
                        }
                    }
                } else {
                    proceedWithClick(false)
                }
            }
        }
    }
}

private fun dismissBlockingExpirationModalIfOpen(
    webView: WebView,
    onReady: () -> Unit
) {
    webView.evaluateJavascript(buildDismissBlockingExpirationModalScript()) { raw ->
        val dismissed = raw?.contains("true") == true
        if (dismissed) {
            webView.postDelayed({ onReady() }, 200L)
        } else {
            onReady()
        }
    }
}

fun readWebViewBrokerBalance(
    webView: WebView,
    onResult: (Double?) -> Unit
) {
    webView.post {
        webView.evaluateJavascript(buildReadBrokerBalanceScript()) { raw ->
            onResult(parseBalanceEvalResult(raw))
        }
    }
}

fun applyWebViewStakeAmount(
    webView: WebView,
    stakeAmount: Double,
    calibratedAmountPoint: BotPoint? = null,
    onResult: (applied: Boolean, needsSettleDelay: Boolean) -> Unit
) {
    webView.post {
        val width = webView.width
        val height = webView.height
        webView.evaluateJavascript(buildSetStakeAmountScript(stakeAmount)) { raw ->
            val outcome = parseStakeApplyOutcome(raw)
            if (outcome.applied) {
                onResult(true, !outcome.alreadyMatches)
                return@evaluateJavascript
            }

            val tapTarget = calibratedAmountPoint
                ?: if (outcome.tapNormX != null && outcome.tapNormY != null) {
                    BotPoint(outcome.tapNormX, outcome.tapNormY)
                } else {
                    null
                }

            if (tapTarget == null || width <= 0 || height <= 0) {
                onResult(false, false)
                return@evaluateJavascript
            }

            val px = tapTarget.normX.coerceIn(0.02f, 0.98f) * width
            val py = tapTarget.normY.coerceIn(0.05f, 0.98f) * height
            dispatchNativeTap(webView, px, py) {
                webView.postDelayed({
                    webView.evaluateJavascript(buildApplyStakeToActiveOrPopupScript(stakeAmount)) { activeRaw ->
                        val activeApplied = parseStakeApplyResult(activeRaw)
                        if (activeApplied) {
                            onResult(true, true)
                        } else {
                            dispatchNativeStakeKeyEvents(webView, stakeAmount) {
                                webView.postDelayed({ onResult(true, true) }, 220L)
                            }
                        }
                    }
                }, 420L)
            }
        }
    }
}

private fun dispatchNativeStakeKeyEvents(
    webView: WebView,
    stakeAmount: Double,
    onFinished: () -> Unit = {}
) {
    val formatted = formatStakeForInput(stakeAmount)
    webView.requestFocus()
    val downCtrlA = KeyEvent(
        0L,
        0L,
        KeyEvent.ACTION_DOWN,
        KeyEvent.KEYCODE_A,
        0,
        KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
    )
    val upCtrlA = KeyEvent(
        0L,
        0L,
        KeyEvent.ACTION_UP,
        KeyEvent.KEYCODE_A,
        0,
        KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
    )
    webView.dispatchKeyEvent(downCtrlA)
    webView.dispatchKeyEvent(upCtrlA)

    val sequence = buildList {
        add(KeyEvent.KEYCODE_MOVE_END)
        repeat(8) { add(KeyEvent.KEYCODE_DEL) }
        formatted.forEach { ch ->
            when (ch) {
                '0' -> add(KeyEvent.KEYCODE_0)
                '1' -> add(KeyEvent.KEYCODE_1)
                '2' -> add(KeyEvent.KEYCODE_2)
                '3' -> add(KeyEvent.KEYCODE_3)
                '4' -> add(KeyEvent.KEYCODE_4)
                '5' -> add(KeyEvent.KEYCODE_5)
                '6' -> add(KeyEvent.KEYCODE_6)
                '7' -> add(KeyEvent.KEYCODE_7)
                '8' -> add(KeyEvent.KEYCODE_8)
                '9' -> add(KeyEvent.KEYCODE_9)
                '.', ',' -> add(KeyEvent.KEYCODE_PERIOD)
            }
        }
        add(KeyEvent.KEYCODE_ENTER)
    }

    var delayMs = 24L
    sequence.forEachIndexed { idx, keyCode ->
        val scheduledDelay = delayMs
        webView.postDelayed({
            dispatchKeyTap(webView, keyCode)
            if (idx == sequence.lastIndex) {
                onFinished()
            }
        }, scheduledDelay)
        delayMs += kotlin.random.Random.nextLong(26L, 54L)
    }
}

private fun dispatchKeyTap(webView: WebView, keyCode: Int) {
    val down = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
    val up = KeyEvent(KeyEvent.ACTION_UP, keyCode)
    webView.dispatchKeyEvent(down)
    webView.dispatchKeyEvent(up)
}

internal data class DomButtonHit(
    val normX: Float,
    val normY: Float,
    val label: String
)

internal fun parseDomButtonResult(rawEvalResult: String?): DomButtonHit? {
    if (rawEvalResult.isNullOrBlank() || rawEvalResult == "null") return null
    return try {
        val normalized = if (rawEvalResult.startsWith("\"") && rawEvalResult.endsWith("\"")) {
            org.json.JSONTokener(rawEvalResult).nextValue().toString()
        } else {
            rawEvalResult
        }
        val json = JSONObject(normalized)
        if (!json.optBoolean("found", false)) return null
        val nx = json.optDouble("normX", -1.0).toFloat()
        val ny = json.optDouble("normY", -1.0).toFloat()
        if (nx !in 0.02f..0.98f || ny !in 0.30f..0.995f) return null
        DomButtonHit(
            normX = nx,
            normY = ny,
            label = json.optString("label", "").trim()
        )
    } catch (_: Exception) {
        null
    }
}

private fun dispatchNativeTap(
    webView: WebView,
    x: Float,
    y: Float,
    onDispatched: () -> Unit
) {
    val maxX = (webView.width - 2).coerceAtLeast(2).toFloat()
    val maxY = (webView.height - 2).coerceAtLeast(2).toFloat()
    val jitterX = (kotlin.random.Random.nextFloat() * 8f - 4f)
    val jitterY = (kotlin.random.Random.nextFloat() * 6f - 3f)
    val tapX = (x + jitterX).coerceIn(2f, maxX)
    val tapY = (y + jitterY).coerceIn(2f, maxY)
    val pressure = 0.68f + kotlin.random.Random.nextFloat() * 0.26f
    val touchSize = 0.09f + kotlin.random.Random.nextFloat() * 0.06f
    val holdDurationMs = kotlin.random.Random.nextLong(62L, 118L)

    val downTime = SystemClock.uptimeMillis()
    val downEvent = MotionEvent.obtain(
        downTime,
        downTime,
        MotionEvent.ACTION_DOWN,
        tapX,
        tapY,
        pressure,
        touchSize,
        0,
        1f,
        1f,
        0,
        0
    ).apply {
        source = InputDevice.SOURCE_TOUCHSCREEN
    }
    try {
        webView.dispatchTouchEvent(downEvent)
    } finally {
        downEvent.recycle()
    }

    webView.postDelayed({
        val upTime = SystemClock.uptimeMillis()
        val releaseX = (tapX + (kotlin.random.Random.nextFloat() * 2f - 1f)).coerceIn(2f, maxX)
        val releaseY = (tapY + (kotlin.random.Random.nextFloat() * 2f - 1f)).coerceIn(2f, maxY)
        val upEvent = MotionEvent.obtain(
            downTime,
            upTime,
            MotionEvent.ACTION_UP,
            releaseX,
            releaseY,
            pressure,
            touchSize,
            0,
            1f,
            1f,
            0,
            0
        ).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        try {
            webView.dispatchTouchEvent(upEvent)
        } finally {
            upEvent.recycle()
        }
        onDispatched()
    }, holdDurationMs)
}

internal fun buildFindTradeButtonScript(direction: SignalDirection): String {
    val isCall = direction == SignalDirection.CALL_UP
    val exactTerms = if (isCall) {
        "['acima','call','higher','above','compra','comprar','buy','subir','alta']"
    } else {
        "['abaixo','put','lower','below','venda','vender','sell','descer','baixa']"
    }
    val oppositeTerms = if (isCall) {
        "['abaixo','put','lower','below','venda','vender','sell','descer','baixa']"
    } else {
        "['acima','call','higher','above','compra','comprar','buy','subir','alta']"
    }
    val preferGreen = if (isCall) "true" else "false"

    return """
        (function() {
          try {
            var exactList = $exactTerms;
            var oppositeList = $oppositeTerms;
            var preferGreen = $preferGreen;
            var vw = window.innerWidth || document.documentElement.clientWidth || 1;
            var vh = window.innerHeight || document.documentElement.clientHeight || 1;

            function collectRoots(root, out) {
              if (!root) return;
              out.push(root);
              var all = root.querySelectorAll ? root.querySelectorAll('*') : [];
              for (var i = 0; i < all.length; i++) {
                if (all[i].shadowRoot) {
                  collectRoots(all[i].shadowRoot, out);
                }
              }
            }

            var roots = [];
            collectRoots(document, roots);
            var iframes = document.querySelectorAll('iframe');
            for (var f = 0; f < iframes.length; f++) {
              try {
                if (iframes[f].contentDocument) {
                  collectRoots(iframes[f].contentDocument, roots);
                }
              } catch (ignore) {}
            }

            function parseRgb(bg) {
              if (!bg) return null;
              var m = bg.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)/);
              if (!m) return null;
              return { r: parseInt(m[1], 10), g: parseInt(m[2], 10), b: parseInt(m[3], 10) };
            }

            var best = null;
            for (var rIdx = 0; rIdx < roots.length; rIdx++) {
              var nodes = roots[rIdx].querySelectorAll('button, [role="button"], a, div, span');
              for (var i = 0; i < nodes.length; i++) {
                var el = nodes[i];
                var rawText = (el.innerText || el.textContent || el.getAttribute('aria-label') || '').trim();
                var text = rawText.toLowerCase().replace(/\s+/g, ' ');
                var testAttr = ((el.getAttribute('data-test-id') || '') + ' ' + (el.getAttribute('data-testid') || '') + ' ' + (el.className || '')).toString().toLowerCase();
                if (text.length > 40) continue;

                var hasOpposite = false;
                for (var o = 0; o < oppositeList.length; o++) {
                  if (text.indexOf(oppositeList[o]) !== -1) { hasOpposite = true; break; }
                }
                if (hasOpposite) continue;

                var clickable = (el.closest && el.closest('button, [role="button"], a')) || el;
                var rect = clickable.getBoundingClientRect();
                if (!rect || rect.width < 44 || rect.height < 26) continue;
                if (rect.width > vw * 0.9 || rect.height > vh * 0.35) continue;

                var cx = rect.left + rect.width / 2;
                var cy = rect.top + rect.height / 2;
                if (cx < vw * 0.02 || cx > vw * 0.98 || cy < vh * 0.35 || cy > vh * 0.995) continue;

                var style = window.getComputedStyle(clickable);
                if (!style || style.display === 'none' || style.visibility === 'hidden' || parseFloat(style.opacity || '1') < 0.1) continue;

                var rgb = parseRgb(style.backgroundColor);
                var matchesColor = false;
                if (rgb) {
                  if (preferGreen && rgb.g >= 100 && rgb.g > rgb.r + 30 && rgb.g > rgb.b + 20) matchesColor = true;
                  if (!preferGreen && rgb.r >= 130 && rgb.r > rgb.g + 40 && rgb.r > rgb.b + 30) matchesColor = true;
                }

                var score = 0;
                for (var t = 0; t < exactList.length; t++) {
                  var term = exactList[t];
                  if (text === term) { score += 120; break; }
                  if (text.indexOf(term) !== -1) { score += 70; break; }
                  if (testAttr.indexOf(term) !== -1) { score += 45; break; }
                }
                var isExplicitButton = clickable.tagName === 'BUTTON' || clickable.getAttribute('role') === 'button';
                // Support icon-only green/red action buttons rendered in the bottom trade panel
                if (score === 0 && matchesColor && isExplicitButton && rect.top >= vh * 0.62 && rect.width >= vw * 0.14) {
                  score += 65;
                }
                if (score === 0) continue;

                if (matchesColor) score += 50;
                if (rect.top >= vh * 0.55) score += 25;
                if (isExplicitButton) score += 20;

                if (!best || score > best.score) {
                  best = {
                    score: score,
                    normX: cx / vw,
                    normY: cy / vh,
                    label: rawText.slice(0, 24)
                  };
                }
              }
            }

            if (!best) return JSON.stringify({ found: false });
            return JSON.stringify({ found: true, normX: best.normX, normY: best.normY, label: best.label });
          } catch (err) {
            return JSON.stringify({ found: false });
          }
        })();
    """.trimIndent()
}

/**
 * Deterministic visual detector for solid green (CALL / Acima) or solid red (PUT / Abaixo)
 * action buttons inside canvas-rendered broker traderooms (such as IQ Option PWA).
 * Scans the captured lower-panel bitmap on a 24x12 grid, locates the largest dense contiguous
 * rectangular button cluster, and returns its normalized center coordinates in the full WebView.
 */
internal fun detectTradeButtonFromBottomBitmap(
    bottomBitmap: Bitmap,
    direction: SignalDirection,
    regionTopNorm: Float = 0.65f
): BotPoint? {
    if (direction == SignalDirection.NEUTRAL || bottomBitmap.isRecycled) return null
    val w = bottomBitmap.width
    val h = bottomBitmap.height
    if (w < 40 || h < 20) return null

    val cols = 24
    val rows = 12
    val cellW = w.toFloat() / cols
    val cellH = h.toFloat() / rows
    val active = BooleanArray(cols * rows)
    val wantGreen = direction == SignalDirection.CALL_UP

    val stepX = (w / 140).coerceAtLeast(1)
    val stepY = (h / 72).coerceAtLeast(1)

    for (r in 0 until rows) {
        val y0 = (r * cellH).toInt().coerceIn(0, h - 1)
        val y1 = (((r + 1) * cellH).toInt()).coerceIn(y0 + 1, h)
        for (c in 0 until cols) {
            val x0 = (c * cellW).toInt().coerceIn(0, w - 1)
            val x1 = (((c + 1) * cellW).toInt()).coerceIn(x0 + 1, w)
            var matched = 0
            var total = 0
            var y = y0
            while (y < y1) {
                var x = x0
                while (x < x1) {
                    val px = bottomBitmap.getPixel(x, y)
                    val red = Color.red(px)
                    val green = Color.green(px)
                    val blue = Color.blue(px)
                    val isMatch = if (wantGreen) {
                        green >= 100 && (green - red) >= 30 && (green - blue) >= 20
                    } else {
                        red >= 130 && (red - green) >= 40 && (red - blue) >= 35
                    }
                    if (isMatch) matched++
                    total++
                    x += stepX
                }
                y += stepY
            }
            if (total > 0 && matched.toFloat() / total.toFloat() >= 0.45f) {
                active[r * cols + c] = true
            }
        }
    }

    val visited = BooleanArray(cols * rows)
    var bestScore = -1f
    var bestNormX = -1f
    var bestLocalNormY = -1f

    val queue = IntArray(cols * rows)
    for (idx in active.indices) {
        if (!active[idx] || visited[idx]) continue
        var head = 0
        var tail = 0
        queue[tail++] = idx
        visited[idx] = true

        var count = 0
        var sumC = 0f
        var sumR = 0f
        var minC = cols
        var maxC = -1
        var minR = rows
        var maxR = -1

        while (head < tail) {
            val cur = queue[head++]
            val cr = cur / cols
            val cc = cur % cols
            count++
            sumC += cc + 0.5f
            sumR += cr + 0.5f
            if (cc < minC) minC = cc
            if (cc > maxC) maxC = cc
            if (cr < minR) minR = cr
            if (cr > maxR) maxR = cr

            if (cc > 0) {
                val n = cur - 1
                if (active[n] && !visited[n]) { visited[n] = true; queue[tail++] = n }
            }
            if (cc + 1 < cols) {
                val n = cur + 1
                if (active[n] && !visited[n]) { visited[n] = true; queue[tail++] = n }
            }
            if (cr > 0) {
                val n = cur - cols
                if (active[n] && !visited[n]) { visited[n] = true; queue[tail++] = n }
            }
            if (cr + 1 < rows) {
                val n = cur + cols
                if (active[n] && !visited[n]) { visited[n] = true; queue[tail++] = n }
            }
        }

        val spanC = (maxC - minC + 1)
        val spanR = (maxR - minR + 1)
        val fillRatio = count.toFloat() / (spanC * spanR).toFloat()
        // Reject thin candle lines, vertical strips, or tiny badges — require genuine rectangular button mass
        if (count < 4 || spanC < 3 || spanR < 1 || fillRatio < 0.45f) continue

        val avgC = sumC / count
        val avgR = sumR / count
        val localNormY = (avgR / rows.toFloat()).coerceIn(0.05f, 0.96f)
        // Prefer larger solid clusters located in the lower half of the bottom panel (where Acima/Abaixo live)
        val score = count.toFloat() * fillRatio * (0.85f + 0.35f * localNormY)
        if (score > bestScore) {
            bestScore = score
            bestNormX = (avgC / cols.toFloat()).coerceIn(0.05f, 0.95f)
            bestLocalNormY = localNormY
        }
    }

    if (bestScore < 0f || bestNormX < 0f || bestLocalNormY < 0f) return null
    val fullNormY = (regionTopNorm + bestLocalNormY * (1f - regionTopNorm)).coerceIn(0.66f, 0.985f)
    return BotPoint(normX = bestNormX, normY = fullNormY)
}

internal fun parseBrokerMoneyValue(raw: String?): Double? {
    if (raw.isNullOrBlank()) return null
    val tokenMatch = Regex("[0-9]+(?:[.,][0-9]+)*").find(raw) ?: return null
    val token = tokenMatch.value
    val lastComma = token.lastIndexOf(',')
    val lastDot = token.lastIndexOf('.')

    val normalized = when {
        lastComma >= 0 && lastDot >= 0 -> {
            if (lastComma > lastDot) {
                token.replace(".", "").replace(",", ".")
            } else {
                token.replace(",", "")
            }
        }
        lastComma >= 0 -> {
            val decimals = token.length - lastComma - 1
            if (decimals in 1..2) {
                token.replace(",", ".")
            } else {
                token.replace(",", "")
            }
        }
        lastDot >= 0 -> {
            val decimals = token.length - lastDot - 1
            if (decimals == 3 && token.count { it == '.' } >= 1) {
                token.replace(".", "")
            } else {
                token
            }
        }
        else -> token
    }
    return normalized.toDoubleOrNull()
}

fun resolveTradeOutcomeFromBalance(
    balanceBeforeTrade: Double?,
    balanceNow: Double?,
    stakeAmount: Double
): TradeOutcome? {
    if (balanceBeforeTrade == null || balanceNow == null) return null
    val delta = balanceNow - balanceBeforeTrade
    return when {
        delta >= 0.01 -> TradeOutcome.WIN
        delta <= -(stakeAmount * 0.50).coerceAtLeast(0.10) -> TradeOutcome.LOSS
        else -> null
    }
}

internal fun parseBalanceEvalResult(rawEvalResult: String?): Double? {
    if (rawEvalResult.isNullOrBlank() || rawEvalResult == "null") return null
    return try {
        val normalized = if (rawEvalResult.startsWith("\"") && rawEvalResult.endsWith("\"")) {
            JSONTokener(rawEvalResult).nextValue().toString()
        } else {
            rawEvalResult
        }
        val json = JSONObject(normalized)
        if (!json.optBoolean("found", false)) return null
        parseBrokerMoneyValue(json.optString("raw", ""))
    } catch (_: Exception) {
        null
    }
}

internal data class StakeApplyOutcome(
    val applied: Boolean,
    val alreadyMatches: Boolean = false,
    val tapNormX: Float? = null,
    val tapNormY: Float? = null
)

internal fun parseStakeApplyOutcome(rawEvalResult: String?): StakeApplyOutcome {
    if (rawEvalResult.isNullOrBlank() || rawEvalResult == "null") return StakeApplyOutcome(false)
    return try {
        val normalized = if (rawEvalResult.startsWith("\"") && rawEvalResult.endsWith("\"")) {
            JSONTokener(rawEvalResult).nextValue().toString()
        } else {
            rawEvalResult
        }
        val json = JSONObject(normalized)
        val found = json.optBoolean("found", false)
        if (found) {
            StakeApplyOutcome(
                applied = true,
                alreadyMatches = json.optBoolean("alreadyMatches", false)
            )
        } else {
            val tx = json.optDouble("tapNormX", -1.0).toFloat()
            val ty = json.optDouble("tapNormY", -1.0).toFloat()
            if (tx in 0.02f..0.98f && ty in 0.35f..0.98f) {
                StakeApplyOutcome(applied = false, tapNormX = tx, tapNormY = ty)
            } else {
                StakeApplyOutcome(applied = false)
            }
        }
    } catch (_: Exception) {
        StakeApplyOutcome(false)
    }
}

internal fun parseStakeApplyResult(rawEvalResult: String?): Boolean {
    return parseStakeApplyOutcome(rawEvalResult).applied
}

private fun formatStakeForInput(stakeAmount: Double): String {
    val rounded = kotlin.math.round(stakeAmount * 100.0) / 100.0
    return if (kotlin.math.abs(rounded - rounded.toLong().toDouble()) < 0.005) {
        rounded.toLong().toString()
    } else {
        String.format(Locale.US, "%.2f", rounded)
    }
}

internal fun buildDismissBlockingExpirationModalScript(): String {
    return """
        (function() {
          try {
            var vw = window.innerWidth || document.documentElement.clientWidth || 1;
            var vh = window.innerHeight || document.documentElement.clientHeight || 1;
            var all = document.querySelectorAll('div, section, aside');
            for (var i = 0; i < all.length; i++) {
              var el = all[i];
              var txt = (el.innerText || '').toLowerCase();
              if (!txt || txt.length > 420) continue;
              if (txt.indexOf('será fechada após') === -1 && txt.indexOf('will be closed after') === -1) continue;
              var rect = el.getBoundingClientRect ? el.getBoundingClientRect() : null;
              if (!rect || rect.width < vw * 0.4 || rect.height < 80) continue;
              var closeBtns = el.querySelectorAll('button, [role="button"], [class*="close"], svg, span, div');
              for (var j = 0; j < closeBtns.length; j++) {
                var cb = closeBtns[j];
                var r = cb.getBoundingClientRect ? cb.getBoundingClientRect() : null;
                if (!r || r.width < 12 || r.width > 72 || r.height < 12 || r.height > 72) continue;
                var bt = (cb.innerText || '').trim().toLowerCase();
                var attr = ((cb.getAttribute && (cb.getAttribute('aria-label') || '') || '') + ' ' + (cb.className || '')).toString().toLowerCase();
                var topRight = r.top <= rect.top + 90 && r.left >= rect.left + rect.width * 0.65;
                if (bt === '×' || bt === '✕' || bt === 'x' || attr.indexOf('close') !== -1 || (topRight && cb.tagName.toLowerCase() === 'svg')) {
                  var clickable = (cb.closest && cb.closest('button, [role="button"]')) || cb;
                  clickable.click();
                  return true;
                }
              }
            }
            return false;
          } catch (e) {
            return false;
          }
        })();
    """.trimIndent()
}

internal fun buildReadBrokerBalanceScript(): String {
    return """
        (function() {
          try {
            var vw = window.innerWidth || document.documentElement.clientWidth || 1;
            var vh = window.innerHeight || document.documentElement.clientHeight || 1;
            function collectRoots(root, out) {
              if (!root) return;
              out.push(root);
              var all = root.querySelectorAll ? root.querySelectorAll('*') : [];
              for (var i = 0; i < all.length; i++) {
                if (all[i].shadowRoot) collectRoots(all[i].shadowRoot, out);
              }
            }
            var roots = [];
            collectRoots(document, roots);
            var iframes = document.querySelectorAll('iframe');
            for (var f = 0; f < iframes.length; f++) {
              try { if (iframes[f].contentDocument) collectRoots(iframes[f].contentDocument, roots); } catch (e) {}
            }

            var best = null;
            for (var r = 0; r < roots.length; r++) {
              var nodes = roots[r].querySelectorAll('[data-test-id*="balance"], [data-testid*="balance"], [data-test-id*="wallet"], [class*="balance"], [class*="wallet"], [aria-label*="balance"], [aria-label*="saldo"], header span, header div, div, span');
              for (var i = 0; i < nodes.length; i++) {
                var el = nodes[i];
                var txt = (el.innerText || el.textContent || '').trim().replace(/\s+/g, ' ');
                if (!txt || txt.length > 36 || !/[0-9]/.test(txt)) continue;
                var low = txt.toLowerCase();
                if (/%|\bmin\b|\bseg\b|\bsec\b/.test(low)) continue;
                var attr = ((el.getAttribute('data-test-id') || '') + ' ' + (el.getAttribute('data-testid') || '') + ' ' + (el.className || '')).toLowerCase();
                if (/profit|payout|lucro/.test(attr)) continue;
                var hasCurrency = /(R\x24|\x24|€|£)\s*[0-9]|[0-9]\s*(R\x24|\x24|€|£)/.test(txt);
                var hasBalanceAttr = attr.indexOf('balance') !== -1 || attr.indexOf('saldo') !== -1 || attr.indexOf('wallet') !== -1;
                if (!hasCurrency && !hasBalanceAttr) continue;
                var rect = el.getBoundingClientRect ? el.getBoundingClientRect() : null;
                if (!rect || rect.width <= 0 || rect.height <= 0) continue;
                if (rect.top > vh * 0.28) continue;
                var score = 10;
                if (hasBalanceAttr) score += 80;
                if (hasCurrency) score += 35;
                if (txt.length <= 22) score += 15;
                if (rect.top <= vh * 0.14) score += 20;
                if (!best || score > best.score) {
                  best = { score: score, raw: txt };
                }
              }
            }
            if (!best) return JSON.stringify({ found: false });
            return JSON.stringify({ found: true, raw: best.raw });
          } catch (err) {
            return JSON.stringify({ found: false });
          }
        })();
    """.trimIndent()
}

internal fun buildSetStakeAmountScript(stakeAmount: Double): String {
    val formattedDot = formatStakeForInput(stakeAmount)
    return """
        (function() {
          try {
            var targetNum = $stakeAmount;
            var vw = window.innerWidth || document.documentElement.clientWidth || 1;
            var vh = window.innerHeight || document.documentElement.clientHeight || 1;
            function collectRoots(root, out) {
              if (!root) return;
              out.push(root);
              var all = root.querySelectorAll ? root.querySelectorAll('*') : [];
              for (var i = 0; i < all.length; i++) {
                if (all[i].shadowRoot) collectRoots(all[i].shadowRoot, out);
              }
            }
            var roots = [];
            collectRoots(document, roots);
            var iframes = document.querySelectorAll('iframe');
            for (var f = 0; f < iframes.length; f++) {
              try { if (iframes[f].contentDocument) collectRoots(iframes[f].contentDocument, roots); } catch (e) {}
            }

            function ownMeta(el, maxDepth) {
              var cur = el;
              var parts = [];
              for (var d = 0; d < maxDepth && cur; d++) {
                if (cur.getAttribute) {
                  parts.push(cur.getAttribute('data-test-id') || '');
                  parts.push(cur.getAttribute('data-testid') || '');
                  parts.push(cur.getAttribute('aria-label') || '');
                }
                parts.push((cur.className || '').toString());
                cur = cur.parentElement;
              }
              return parts.join(' ').toLowerCase();
            }

            function parseNum(raw) {
              if (!raw) return NaN;
              var m = raw.match(/[0-9]+(?:[.,][0-9]+)*/);
              if (!m) return NaN;
              var tok = m[0];
              var lc = tok.lastIndexOf(',');
              var ld = tok.lastIndexOf('.');
              var norm = tok;
              if (lc >= 0 && ld >= 0) {
                norm = lc > ld ? tok.replace(/\./g, '').replace(',', '.') : tok.replace(/,/g, '');
              } else if (lc >= 0) {
                norm = (tok.length - lc - 1 <= 2) ? tok.replace(',', '.') : tok.replace(/,/g, '');
              } else if (ld >= 0 && (tok.length - ld - 1 === 3)) {
                norm = tok.replace(/\./g, '');
              }
              return parseFloat(norm);
            }

            // Pass 1: Direct <input> inside the trading panel (strict 2-level metadata check)
            var bestInput = null;
            var bestScore = -1;
            for (var r = 0; r < roots.length; r++) {
              var inputs = roots[r].querySelectorAll('input');
              for (var i = 0; i < inputs.length; i++) {
                var inp = inputs[i];
                if (inp.disabled || inp.readOnly) continue;
                var type = (inp.type || 'text').toLowerCase();
                if (type === 'hidden' || type === 'password' || type === 'email' || type === 'search' || type === 'checkbox' || type === 'radio') continue;
                var rect = inp.getBoundingClientRect ? inp.getBoundingClientRect() : null;
                if (!rect || rect.width < 20 || rect.height < 14 || rect.top < vh * 0.35) continue;
                var meta = ownMeta(inp, 3) + ' ' + (inp.getAttribute('placeholder') || '') + ' ' + (inp.name || '');
                var parentTxt = ((inp.parentElement && inp.parentElement.innerText) || '').toLowerCase();
                if (/hor[aá]rio|expir|tempo|time|durat|search|email/.test(meta + ' ' + parentTxt)) continue;
                var score = 0;
                if (/amount|valor|stake|invest|quantia|deal/.test(meta) || /valor|amount|stake|invest/.test(parentTxt)) score += 90;
                if (inp.inputMode === 'decimal' || inp.inputMode === 'numeric' || type === 'number') score += 35;
                if (/^[0-9]+([.,][0-9]+)?$/.test((inp.value || '').trim())) score += 25;
                if (rect.top >= vh * 0.45) score += 20;
                if (score > bestScore && score >= 45) {
                  bestScore = score;
                  bestInput = inp;
                }
              }
            }

            if (bestInput) {
              var curVal = parseNum((bestInput.value || '').trim());
              if (!isNaN(curVal) && Math.abs(curVal - targetNum) < 0.01) {
                return JSON.stringify({ found: true, alreadyMatches: true, applied: (bestInput.value || '').trim() });
              }
              var useComma = (bestInput.value || '').indexOf(',') !== -1;
              var targetStr = useComma ? "$formattedDot".replace('.', ',') : "$formattedDot";
              var setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value');
              bestInput.focus();
              if (setter && setter.set) {
                setter.set.call(bestInput, targetStr);
              } else {
                bestInput.value = targetStr;
              }
              bestInput.dispatchEvent(new Event('input', { bubbles: true }));
              bestInput.dispatchEvent(new Event('change', { bubbles: true }));
              bestInput.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', keyCode: 13, bubbles: true }));
              bestInput.dispatchEvent(new KeyboardEvent('keyup', { key: 'Enter', code: 'Enter', keyCode: 13, bubbles: true }));
              bestInput.blur();
              return JSON.stringify({ found: true, alreadyMatches: false, applied: targetStr });
            }

            // Pass 2: Match both '$ 2' / 'R$ 4' boxes AND IQ Option mobile pill 'Valor ($) 2' / 'Valor (R$) 4' (NEVER Horário/Expiration)
            var bestBox = null;
            var bestBoxScore = -1;
            for (var r2 = 0; r2 < roots.length; r2++) {
              var nodes = roots[r2].querySelectorAll('[data-test-id*="amount"], [data-testid*="amount"], [data-test-id*="invest"], [class*="amount"], [class*="invest"], button, [role="button"], div, span');
              for (var j = 0; j < nodes.length; j++) {
                var el = nodes[j];
                var txt = (el.innerText || el.textContent || '').trim().replace(/\s+/g, ' ');
                if (!txt || txt.length > 32) continue;
                var lower = txt.toLowerCase();
                if (/acima|abaixo|call|put|buy|sell|compra|venda|hor[aá]rio|expir|tempo|time|durat|min|seg|sec|hora|hour|%|:/i.test(lower)) continue;
                var meta = ownMeta(el, 3);
                if (/hor[aá]rio|expir|tempo|time|durat|balance|saldo|profit|lucro|payout/.test(meta)) continue;

                var hasStrictCurrency = /^(?:(?:valor|investimento|amount|quantia|stake)\s*)?(R\x24|\x24|€|£)\s*[0-9]+([.,][0-9]+)?$/.test(lower) ||
                                        /^(?:(?:valor|investimento|amount|quantia|stake)\s*)?[0-9]+([.,][0-9]+)?\s*(R\x24|\x24|€|£)$/.test(lower);
                var hasValorLabelAndNumber = /^(?:-?\s*)?(?:valor|investimento|amount|quantia|stake)\s*(?:\(\s*(?:r\x24|\x24|€|£)\s*\))?\s*[0-9]+([.,][0-9]+)?(?:\s*\+)?$/.test(lower);
                var parentTxt2 = ((el.parentElement && (el.parentElement.innerText || el.parentElement.textContent)) || '').trim().replace(/\s+/g, ' ').toLowerCase();
                var isNumericUnderValorLabel = /^[0-9]+([.,][0-9]+)?$/.test(lower) &&
                                               (/(valor|investimento|amount|quantia|stake)/.test(meta) || /(valor|investimento|amount|quantia|stake)/.test(parentTxt2)) &&
                                               !/hor[aá]rio|expir|tempo|min|seg|saldo|balance|%/.test(parentTxt2);
                if (!hasStrictCurrency && !hasValorLabelAndNumber && !isNumericUnderValorLabel) continue;

                var rect = el.getBoundingClientRect ? el.getBoundingClientRect() : null;
                if (!rect || rect.width < 20 || rect.height < 16 || rect.width > vw * 0.60 || rect.height > vh * 0.18) continue;
                if (rect.top < vh * 0.42 || rect.top > vh * 0.96) continue;

                var bScore = 50;
                if (/amount|valor|stake|invest|quantia/.test(meta)) bScore += 70;
                if (hasValorLabelAndNumber || isNumericUnderValorLabel) bScore += 25;
                if (isNumericUnderValorLabel && rect.width <= vw * 0.28) bScore += 20;
                if (rect.top >= vh * 0.50) bScore += 20;
                if (bScore > bestBoxScore) {
                  bestBoxScore = bScore;
                  bestBox = {
                    txt: txt,
                    normX: (rect.left + rect.width / 2) / vw,
                    normY: (rect.top + rect.height / 2) / vh
                  };
                }
              }
            }
            if (bestBox) {
              var boxNum = parseNum(bestBox.txt);
              if (!isNaN(boxNum) && Math.abs(boxNum - targetNum) < 0.01) {
                return JSON.stringify({ found: true, alreadyMatches: true, applied: bestBox.txt });
              }
              return JSON.stringify({ found: false, tapNormX: bestBox.normX, tapNormY: bestBox.normY });
            }
            return JSON.stringify({ found: false });
          } catch (err) {
            return JSON.stringify({ found: false });
          }
        })();
    """.trimIndent()
}

internal fun buildApplyStakeToActiveOrPopupScript(stakeAmount: Double): String {
    val formattedDot = formatStakeForInput(stakeAmount)
    return """
        (function() {
          try {
            function collectRoots(root, out) {
              if (!root) return;
              out.push(root);
              var all = root.querySelectorAll ? root.querySelectorAll('*') : [];
              for (var i = 0; i < all.length; i++) {
                if (all[i].shadowRoot) collectRoots(all[i].shadowRoot, out);
              }
            }
            var roots = [];
            collectRoots(document, roots);
            var iframes = document.querySelectorAll('iframe');
            for (var f = 0; f < iframes.length; f++) {
              try { if (iframes[f].contentDocument) collectRoots(iframes[f].contentDocument, roots); } catch (e) {}
            }

            function fireTap(node) {
              if (!node) return;
              try {
                if (window.PointerEvent) {
                  node.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true, cancelable: true }));
                  node.dispatchEvent(new PointerEvent('pointerup', { bubbles: true, cancelable: true }));
                }
                node.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true }));
                node.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, cancelable: true }));
              } catch (ignore) {}
              if (typeof node.click === 'function') node.click();
            }

            var target = document.activeElement;
            if (!target || (target.tagName !== 'INPUT' && target.tagName !== 'TEXTAREA')) {
              target = null;
              for (var rIdx = 0; rIdx < roots.length && !target; rIdx++) {
                var inputs = roots[rIdx].querySelectorAll('input:not([disabled]):not([readonly]), textarea:not([disabled]):not([readonly])');
                for (var i = 0; i < inputs.length; i++) {
                  var t = (inputs[i].type || 'text').toLowerCase();
                  if (t === 'hidden' || t === 'password' || t === 'email' || t === 'search' || t === 'checkbox' || t === 'radio') continue;
                  var r = inputs[i].getBoundingClientRect ? inputs[i].getBoundingClientRect() : null;
                  if (r && r.width > 20 && r.height > 14) {
                    target = inputs[i];
                    break;
                  }
                }
              }
            }
            if (target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA')) {
              var useComma = (target.value || '').indexOf(',') !== -1;
              var targetStr = useComma ? "$formattedDot".replace('.', ',') : "$formattedDot";
              var setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value');
              target.focus();
              if (setter && setter.set) {
                setter.set.call(target, targetStr);
              } else {
                target.value = targetStr;
              }
              target.dispatchEvent(new Event('input', { bubbles: true }));
              target.dispatchEvent(new Event('change', { bubbles: true }));
              target.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', keyCode: 13, bubbles: true }));
              target.dispatchEvent(new KeyboardEvent('keyup', { key: 'Enter', code: 'Enter', keyCode: 13, bubbles: true }));
              for (var rBtn = 0; rBtn < roots.length; rBtn++) {
                var btns = roots[rBtn].querySelectorAll('button, [role="button"]');
                for (var b = 0; b < btns.length; b++) {
                  var bt = (btns[b].innerText || '').trim().toLowerCase();
                  if (bt === 'ok' || bt === 'aplicar' || bt === 'apply' || bt === 'confirmar' || bt === 'confirm' || bt === 'concluir' || bt === 'pronto' || bt === 'salvar' || bt === 'save' || bt === 'done' || bt === '✓' || bt === '✔') {
                    fireTap(btns[b]);
                    break;
                  }
                }
              }
              return JSON.stringify({ found: true, applied: targetStr });
            }

            // Custom on-screen HTML numeric keypad fallback (e.g. mobile IQ Option stake modal)
            var digitBtns = {};
            var clearBtn = null;
            var confirmBtn = null;
            var iconBackspaceCandidates = [];
            for (var rPad = 0; rPad < roots.length; rPad++) {
              var allBtns = roots[rPad].querySelectorAll('button, [role="button"], div, span');
              for (var k = 0; k < allBtns.length; k++) {
                var el = allBtns[k];
                var br = el.getBoundingClientRect ? el.getBoundingClientRect() : null;
                if (!br || br.width < 22 || br.height < 22 || br.width > 240 || br.height > 120) continue;
                var txt = (el.innerText || el.textContent || '').trim();
                if (/^[0-9.,]$/.test(txt) && !digitBtns[txt]) {
                  digitBtns[txt] = { el: el, rect: br };
                }
                var low = txt.toLowerCase();
                var attr = ((el.getAttribute && (el.getAttribute('aria-label') || '') || '') + ' ' +
                            (el.getAttribute && (el.getAttribute('data-test-id') || '') || '') + ' ' +
                            (el.className || '')).toString().toLowerCase();
                if (!clearBtn && (txt === '⌫' || txt === '←' || low === 'c' || low === 'del' ||
                    attr.indexOf('backspace') !== -1 || attr.indexOf('delete') !== -1 ||
                    attr.indexOf('erase') !== -1 || attr.indexOf('clear') !== -1)) {
                  clearBtn = el;
                }
                if (!clearBtn && !txt && el.querySelector && el.querySelector('svg') &&
                    (el.tagName === 'BUTTON' || el.getAttribute('role') === 'button')) {
                  iconBackspaceCandidates.push({ el: el, rect: br });
                }
                if (!confirmBtn && (low === 'ok' || low === 'aplicar' || low === 'apply' ||
                    low === 'confirmar' || low === 'confirm' || low === 'concluir' || low === 'pronto' ||
                    low === 'salvar' || low === 'save' || txt === '✓' || txt === '✔' || low === 'done')) {
                  confirmBtn = el;
                }
              }
            }
            var foundDigits = 0;
            for (var d = 0; d <= 9; d++) {
              if (digitBtns[String(d)]) foundDigits++;
            }
            if (foundDigits >= 8) {
              // On mobile keypads where Backspace is an icon-only SVG button next to '0'
              if (!clearBtn && digitBtns['0']) {
                var zeroRect = digitBtns['0'].rect;
                for (var ic = 0; ic < iconBackspaceCandidates.length; ic++) {
                  var cand = iconBackspaceCandidates[ic];
                  if (Math.abs(cand.rect.top - zeroRect.top) < 42 && cand.rect.left > zeroRect.left) {
                    clearBtn = cand.el;
                    break;
                  }
                }
              }
              if (clearBtn) {
                for (var c = 0; c < 8; c++) fireTap(clearBtn);
              }
              var seq = "$formattedDot";
              for (var s = 0; s < seq.length; s++) {
                var ch = seq.charAt(s);
                var entry = digitBtns[ch] || (ch === '.' ? digitBtns[','] : null);
                if (entry && entry.el) fireTap(entry.el);
              }
              if (confirmBtn) fireTap(confirmBtn);
              return JSON.stringify({ found: true, applied: seq });
            }
            return JSON.stringify({ found: false });
          } catch (err) {
            return JSON.stringify({ found: false });
          }
        })();
    """.trimIndent()
}

data class ChartBoundsDetection(
    val ready: Boolean,
    val bounds: NormalizedRect? = null
)

fun checkWebViewChartReady(
    webView: WebView,
    onResult: (Boolean) -> Unit
) {
    detectWebViewChartBounds(webView) { detection ->
        onResult(detection.ready)
    }
}

fun detectWebViewChartBounds(
    webView: WebView,
    onResult: (ChartBoundsDetection) -> Unit
) {
    webView.post {
        webView.evaluateJavascript(buildCheckChartReadyScript()) { raw ->
            onResult(parseChartBoundsResult(raw))
        }
    }
}

internal fun parseChartBoundsResult(rawEvalResult: String?): ChartBoundsDetection {
    if (rawEvalResult.isNullOrBlank() || rawEvalResult == "null") {
        return ChartBoundsDetection(ready = false, bounds = null)
    }
    return try {
        val normalized = if (rawEvalResult.startsWith("\"") && rawEvalResult.endsWith("\"")) {
            JSONTokener(rawEvalResult).nextValue().toString()
        } else {
            rawEvalResult
        }
        val json = JSONObject(normalized)
        val ready = json.optBoolean("ready", false)
        if (!ready) return ChartBoundsDetection(ready = false, bounds = null)
        val hasBounds = json.has("left") && json.has("top") && json.has("right") && json.has("bottom")
        val bounds = if (hasBounds) {
            val l = json.optDouble("left", -1.0).toFloat().coerceIn(0.02f, 0.40f)
            val t = json.optDouble("top", -1.0).toFloat().coerceIn(0.06f, 0.55f)
            val r = json.optDouble("right", -1.0).toFloat().coerceIn(0.60f, 0.98f)
            val b = json.optDouble("bottom", -1.0).toFloat().coerceIn(0.35f, 0.88f)
            NormalizedRect(l, t, r, b).normalized().takeIf { it.isValid(0.15f) }
        } else {
            null
        }
        ChartBoundsDetection(ready = true, bounds = bounds)
    } catch (_: Exception) {
        ChartBoundsDetection(ready = false, bounds = null)
    }
}

internal fun parseChartReadyResult(rawEvalResult: String?): Boolean {
    return parseChartBoundsResult(rawEvalResult).ready
}

internal fun buildCheckChartReadyScript(): String {
    return """
        (function() {
          try {
            var vw = window.innerWidth || document.documentElement.clientWidth || 1;
            var vh = window.innerHeight || document.documentElement.clientHeight || 1;
            var docs = [document];
            var iframes = document.querySelectorAll('iframe');
            for (var f = 0; f < iframes.length; f++) {
              try {
                if (iframes[f].contentDocument) docs.push(iframes[f].contentDocument);
              } catch (ignore) {}
            }

            // Never treat a visible password login form as a ready trading chart
            for (var d = 0; d < docs.length; d++) {
              var pwdInputs = docs[d].querySelectorAll('input[type="password"]');
              for (var p = 0; p < pwdInputs.length; p++) {
                var pr = pwdInputs[p].getBoundingClientRect ? pwdInputs[p].getBoundingClientRect() : null;
                if (!pr || pr.width < 30 || pr.height < 14) continue;
                var ps = window.getComputedStyle(pwdInputs[p]);
                if (ps && ps.display !== 'none' && ps.visibility !== 'hidden' && parseFloat(ps.opacity || '1') > 0.1) {
                  return JSON.stringify({ ready: false, reason: 'login_visible' });
                }
              }
            }

            // Find the largest visible chart <canvas>
            var bestRect = null;
            var bestArea = 0;
            for (var d2 = 0; d2 < docs.length; d2++) {
              var canvases = docs[d2].querySelectorAll('canvas');
              for (var c = 0; c < canvases.length; c++) {
                var cr = canvases[c].getBoundingClientRect ? canvases[c].getBoundingClientRect() : null;
                if (!cr || cr.width < Math.min(160, vw * 0.45) || cr.height < Math.min(90, vh * 0.18)) continue;
                var cs = window.getComputedStyle(canvases[c]);
                if (!cs || cs.display === 'none' || cs.visibility === 'hidden' || parseFloat(cs.opacity || '1') <= 0.1) continue;
                var area = cr.width * cr.height;
                if (area > bestArea) {
                  bestArea = area;
                  bestRect = cr;
                }
              }
            }

            if (!bestRect) {
              return JSON.stringify({ ready: false });
            }

            // Detect top edge of bottom CALL/PUT controls so auto-area never overlaps trade buttons
            var controlsTopNorm = null;
            for (var d3 = 0; d3 < docs.length; d3++) {
              var btns = docs[d3].querySelectorAll('button, [role="button"]');
              for (var b = 0; b < btns.length; b++) {
                var bt = (btns[b].innerText || btns[b].textContent || '').trim().toLowerCase();
                if (!bt || bt.length > 24) continue;
                if (/^(acima|abaixo|call|put|higher|lower|comprar|vender|buy|sell)$/.test(bt)) {
                  var br = btns[b].getBoundingClientRect ? btns[b].getBoundingClientRect() : null;
                  if (br && br.width >= 44 && br.height >= 26 && br.top > vh * 0.48) {
                    var topN = br.top / vh;
                    if (controlsTopNorm === null || topN < controlsTopNorm) {
                      controlsTopNorm = topN;
                    }
                  }
                }
              }
            }

            var left = Math.max(0.02, Math.min(0.35, (bestRect.left / vw) + 0.01));
            var right = Math.min(0.98, Math.max(0.65, (bestRect.right / vw) - 0.01));
            var top = (bestRect.top / vh);
            var bottom = (bestRect.bottom / vh);

            // When IQ Option renders a fullscreen canvas behind floating top/bottom HUD overlays,
            // automatically inset the top header bar and bottom controls to frame the candles cleanly.
            if (top < 0.08 && bottom > 0.78) {
              top = 0.14;
              bottom = controlsTopNorm !== null ? Math.min(0.72, Math.max(0.45, controlsTopNorm - 0.04)) : 0.68;
            } else {
              top = Math.max(0.08, Math.min(0.45, top + 0.01));
              var maxBottom = controlsTopNorm !== null ? Math.min(0.84, Math.max(0.45, controlsTopNorm - 0.03)) : 0.78;
              bottom = Math.min(maxBottom, Math.max(top + 0.22, bottom - 0.01));
            }

            return JSON.stringify({
              ready: true,
              left: left,
              top: top,
              right: right,
              bottom: bottom
            });
          } catch (err) {
            return JSON.stringify({ ready: false });
          }
        })();
    """.trimIndent()
}

internal data class ExpiryBoxProbeOutcome(
    val alreadyMatches: Boolean,
    val tapNormX: Float? = null,
    val tapNormY: Float? = null,
    val currentText: String = ""
)

internal fun parseExpiryBoxProbeOutcome(rawEvalResult: String?): ExpiryBoxProbeOutcome {
    if (rawEvalResult.isNullOrBlank() || rawEvalResult == "null") {
        return ExpiryBoxProbeOutcome(alreadyMatches = false)
    }
    return try {
        val normalized = if (rawEvalResult.startsWith("\"") && rawEvalResult.endsWith("\"")) {
            JSONTokener(rawEvalResult).nextValue().toString()
        } else {
            rawEvalResult
        }
        val json = JSONObject(normalized)
        val alreadyMatches = json.optBoolean("alreadyMatches", false)
        val tx = json.optDouble("tapNormX", -1.0).toFloat()
        val ty = json.optDouble("tapNormY", -1.0).toFloat()
        val validCoords = tx in 0.02f..0.98f && ty in 0.15f..0.98f
        ExpiryBoxProbeOutcome(
            alreadyMatches = alreadyMatches,
            tapNormX = if (validCoords) tx else null,
            tapNormY = if (validCoords) ty else null,
            currentText = json.optString("currentText", "").trim()
        )
    } catch (_: Exception) {
        ExpiryBoxProbeOutcome(alreadyMatches = false)
    }
}

internal fun parseExpirySelectResult(rawEvalResult: String?): Boolean {
    if (rawEvalResult.isNullOrBlank() || rawEvalResult == "null") return false
    return try {
        val normalized = if (rawEvalResult.startsWith("\"") && rawEvalResult.endsWith("\"")) {
            JSONTokener(rawEvalResult).nextValue().toString()
        } else {
            rawEvalResult
        }
        val json = JSONObject(normalized)
        json.optBoolean("selected", false)
    } catch (_: Exception) {
        false
    }
}

/**
 * Automatically switches the broker's trade expiration selector ("Horário" / "Tempo" / "Expiração")
 * inside the live IQ Option WebView to 30s, 1 min, 2 min, or 5 min before executing entries.
 */
fun applyWebViewExpirySeconds(
    webView: WebView,
    expirySeconds: Int,
    onResult: (applied: Boolean, changed: Boolean) -> Unit
) {
    val normalizedSec = com.example.model.normalizeAdaptiveExpirySeconds(expirySeconds)
    webView.post {
        val width = webView.width
        val height = webView.height
        if (width <= 0 || height <= 0) {
            onResult(false, false)
            return@post
        }

        webView.evaluateJavascript(buildFindExpiryBoxScript(normalizedSec)) { rawProbe ->
            val probe = parseExpiryBoxProbeOutcome(rawProbe)
            if (probe.alreadyMatches) {
                onResult(true, false)
                return@evaluateJavascript
            }
            val tapX = probe.tapNormX
            val tapY = probe.tapNormY
            if (tapX == null || tapY == null) {
                onResult(false, false)
                return@evaluateJavascript
            }

            val px = tapX.coerceIn(0.02f, 0.98f) * width
            val py = tapY.coerceIn(0.15f, 0.98f) * height
            dispatchNativeTap(webView, px, py) {
                webView.postDelayed({
                    webView.evaluateJavascript(buildSelectExpiryOptionScript(normalizedSec)) { rawSelect ->
                        val selected = parseExpirySelectResult(rawSelect)
                        if (selected) {
                            webView.postDelayed({ onResult(true, true) }, 180L)
                        } else {
                            // Dismiss any opened expiration menu cleanly if option wasn't matched
                            dismissBlockingExpirationModalIfOpen(webView) {
                                onResult(false, false)
                            }
                        }
                    }
                }, 380L)
            }
        }
    }
}

internal fun buildFindExpiryBoxScript(expirySeconds: Int): String {
    val targetRegex = when (com.example.model.normalizeAdaptiveExpirySeconds(expirySeconds)) {
        30 -> "/(^|\\s)(?:30\\s*(?:s|seg|segs|sec|secs|segundos?)|00?:30)(\\s|$)/i"
        60 -> "/(^|\\s)(?:1\\s*(?:min|mins|m|minuto|minutos)|m1|01:00)(\\s|$)/i"
        120 -> "/(^|\\s)(?:2\\s*(?:min|mins|m|minutos?)|m2|02:00)(\\s|$)/i"
        else -> "/(^|\\s)(?:5\\s*(?:min|mins|m|minutos?)|m5|05:00)(\\s|$)/i"
    }
    return """
        (function() {
          try {
            var targetRe = $targetRegex;
            var vw = window.innerWidth || document.documentElement.clientWidth || 1;
            var vh = window.innerHeight || document.documentElement.clientHeight || 1;

            function collectRoots(root, out) {
              if (!root) return;
              out.push(root);
              var all = root.querySelectorAll ? root.querySelectorAll('*') : [];
              for (var i = 0; i < all.length; i++) {
                if (all[i].shadowRoot) collectRoots(all[i].shadowRoot, out);
              }
            }
            var roots = [];
            collectRoots(document, roots);
            var iframes = document.querySelectorAll('iframe');
            for (var f = 0; f < iframes.length; f++) {
              try { if (iframes[f].contentDocument) collectRoots(iframes[f].contentDocument, roots); } catch (e) {}
            }

            function ownMeta(el, maxDepth) {
              var cur = el;
              var parts = [];
              for (var d = 0; d < maxDepth && cur; d++) {
                if (cur.getAttribute) {
                  parts.push(cur.getAttribute('data-test-id') || '');
                  parts.push(cur.getAttribute('data-testid') || '');
                  parts.push(cur.getAttribute('aria-label') || '');
                }
                parts.push((cur.className || '').toString());
                cur = cur.parentElement;
              }
              return parts.join(' ').toLowerCase();
            }

            var best = null;
            var bestScore = -1;
            for (var r = 0; r < roots.length; r++) {
              var nodes = roots[r].querySelectorAll(
                '[data-test-id*="expir"], [data-testid*="expir"], [data-test-id*="time"], [data-testid*="time"], ' +
                '[class*="expir"], [class*="time"], [class*="durat"], button, [role="button"], div, span'
              );
              for (var i = 0; i < nodes.length; i++) {
                var el = nodes[i];
                var txt = (el.innerText || el.textContent || '').trim().replace(/\s+/g, ' ');
                if (!txt || txt.length > 36) continue;
                var lower = txt.toLowerCase();
                if (/acima|abaixo|call|put|buy|sell|compra|venda|valor|amount|stake|invest|saldo|balance|lucro|profit|payout|%|r\x24|\x24|€|£/.test(lower)) continue;

                var meta = ownMeta(el, 3);
                if (/amount|valor|stake|invest|balance|saldo|profit|lucro|payout/.test(meta)) continue;

                var hasExpiryMeta = /expir|hor[aá]rio|tempo|durat|timeframe|timer|time-select|expiration/.test(meta);
                var hasExpiryLabel = /hor[aá]rio|expir|tempo|time|dura[cç][aã]o/.test(lower);
                var hasDurationValue = /(?:^|\s)(?:[0-9]{1,2}\s*(?:s|seg|sec|m|min|mins|h)|[0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?)(?:\s|$)/.test(lower);
                if (!hasExpiryMeta && !hasExpiryLabel && !hasDurationValue) continue;
                if (!hasDurationValue && !hasExpiryMeta) continue;

                var clickable = (el.closest && el.closest('button, [role="button"]')) || el;
                var rect = clickable.getBoundingClientRect ? clickable.getBoundingClientRect() : null;
                if (!rect || rect.width < 28 || rect.height < 18 || rect.width > vw * 0.65 || rect.height > vh * 0.20) continue;
                if (rect.top < vh * 0.25 || rect.top > vh * 0.96) continue;

                var style = window.getComputedStyle(clickable);
                if (!style || style.display === 'none' || style.visibility === 'hidden' || parseFloat(style.opacity || '1') < 0.1) continue;

                var score = 20;
                if (hasExpiryMeta) score += 75;
                if (hasExpiryLabel) score += 45;
                if (hasDurationValue) score += 40;
                if (rect.top >= vh * 0.45) score += 25;
                if (clickable.tagName === 'BUTTON' || clickable.getAttribute('role') === 'button') score += 15;

                if (score > bestScore) {
                  bestScore = score;
                  best = {
                    txt: txt,
                    matches: targetRe.test(lower),
                    normX: (rect.left + rect.width / 2) / vw,
                    normY: (rect.top + rect.height / 2) / vh
                  };
                }
              }
            }

            if (!best) return JSON.stringify({ alreadyMatches: false });
            return JSON.stringify({
              alreadyMatches: best.matches,
              tapNormX: best.normX,
              tapNormY: best.normY,
              currentText: best.txt
            });
          } catch (err) {
            return JSON.stringify({ alreadyMatches: false });
          }
        })();
    """.trimIndent()
}

internal fun buildSelectExpiryOptionScript(expirySeconds: Int): String {
    val normalizedSec = com.example.model.normalizeAdaptiveExpirySeconds(expirySeconds)
    val exactPatterns = when (normalizedSec) {
        30 -> "['30s','30 s','30 seg','30 segs','30 sec','30 segundos','0:30','00:30']"
        60 -> "['1 min','1min','1 m','1m','m1','1 minuto','01:00','1:00']"
        120 -> "['2 min','2min','2 m','2m','m2','2 minutos','02:00','2:00']"
        else -> "['5 min','5min','5 m','5m','m5','5 minutos','05:00','5:00']"
    }
    val fuzzyRegex = when (normalizedSec) {
        30 -> "/(^|\\s)(?:30\\s*(?:s|seg|sec|segundos?)|00?:30)(\\s|$)/i"
        60 -> "/(^|\\s)(?:1\\s*(?:min|m|minuto)|m1|01:00)(\\s|$)/i"
        120 -> "/(^|\\s)(?:2\\s*(?:min|m|minutos?)|m2|02:00)(\\s|$)/i"
        else -> "/(^|\\s)(?:5\\s*(?:min|m|minutos?)|m5|05:00)(\\s|$)/i"
    }
    return """
        (function() {
          try {
            var exactList = $exactPatterns;
            var fuzzyRe = $fuzzyRegex;
            var vw = window.innerWidth || document.documentElement.clientWidth || 1;
            var vh = window.innerHeight || document.documentElement.clientHeight || 1;

            function collectRoots(root, out) {
              if (!root) return;
              out.push(root);
              var all = root.querySelectorAll ? root.querySelectorAll('*') : [];
              for (var i = 0; i < all.length; i++) {
                if (all[i].shadowRoot) collectRoots(all[i].shadowRoot, out);
              }
            }
            var roots = [];
            collectRoots(document, roots);
            var iframes = document.querySelectorAll('iframe');
            for (var f = 0; f < iframes.length; f++) {
              try { if (iframes[f].contentDocument) collectRoots(iframes[f].contentDocument, roots); } catch (e) {}
            }

            function fireTap(node) {
              if (!node) return;
              try {
                if (window.PointerEvent) {
                  node.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true, cancelable: true }));
                  node.dispatchEvent(new PointerEvent('pointerup', { bubbles: true, cancelable: true }));
                }
                node.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true }));
                node.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, cancelable: true }));
              } catch (ignore) {}
              if (typeof node.click === 'function') node.click();
            }

            var best = null;
            var bestScore = -1;
            for (var r = 0; r < roots.length; r++) {
              var nodes = roots[r].querySelectorAll(
                '[role="option"], [role="menuitem"], [role="listitem"], li, button, [role="button"], div, span'
              );
              for (var i = 0; i < nodes.length; i++) {
                var el = nodes[i];
                var txt = (el.innerText || el.textContent || '').trim().replace(/\s+/g, ' ');
                if (!txt || txt.length > 32) continue;
                var lower = txt.toLowerCase();
                if (/acima|abaixo|call|put|valor|amount|saldo|balance|hor[aá]rio\s+atual/.test(lower)) continue;

                var score = 0;
                for (var eIdx = 0; eIdx < exactList.length; eIdx++) {
                  if (lower === exactList[eIdx]) { score += 130; break; }
                }
                if (score === 0 && fuzzyRe.test(lower)) {
                  score += 80;
                }
                if (score === 0) continue;

                var clickable = (el.closest && el.closest('[role="option"], [role="menuitem"], li, button, [role="button"]')) || el;
                var rect = clickable.getBoundingClientRect ? clickable.getBoundingClientRect() : null;
                if (!rect || rect.width < 24 || rect.height < 18 || rect.width > vw * 0.92 || rect.height > vh * 0.20) continue;
                if (rect.top < 0 || rect.bottom > vh + 10) continue;

                var style = window.getComputedStyle(clickable);
                if (!style || style.display === 'none' || style.visibility === 'hidden' || parseFloat(style.opacity || '1') < 0.1) continue;

                var role = (clickable.getAttribute && clickable.getAttribute('role')) || '';
                if (role === 'option' || role === 'menuitem' || clickable.tagName === 'LI') score += 50;
                var parent = clickable.parentElement;
                var parentClass = parent ? (parent.className || '').toString().toLowerCase() : '';
                if (/popup|modal|dropdown|menu|list|expir|sheet|popover/.test(parentClass)) score += 35;

                if (score > bestScore) {
                  bestScore = score;
                  best = clickable;
                }
              }
            }

            if (best) {
              fireTap(best);
              return JSON.stringify({ selected: true });
            }
            return JSON.stringify({ selected: false });
          } catch (err) {
            return JSON.stringify({ selected: false });
          }
        })();
    """.trimIndent()
}
