package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.webkit.WebView
import com.example.model.NormalizedRect
import kotlin.math.roundToInt

fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

fun captureWebViewRegion(
    webView: WebView,
    area: NormalizedRect,
    onCaptured: (Bitmap?) -> Unit
) {
    captureWebViewRegionWithRetry(webView = webView, area = area, attempt = 0, onCaptured = onCaptured)
}

private fun captureWebViewRegionWithRetry(
    webView: WebView,
    area: NormalizedRect,
    attempt: Int,
    onCaptured: (Bitmap?) -> Unit
) {
    val width = webView.width
    val height = webView.height
    if (width <= 0 || height <= 0) {
        onCaptured(null)
        return
    }

    val activity = webView.context.findActivity()
    val window = activity?.window

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        window != null &&
        webView.isAttachedToWindow &&
        webView.isLaidOut &&
        webView.windowVisibility == android.view.View.VISIBLE
    ) {
        val location = IntArray(2)
        webView.getLocationInWindow(location)
        val decorW = window.decorView.width
        val decorH = window.decorView.height
        val rawLeft = location[0]
        val rawTop = location[1]
        val rawRight = location[0] + width
        val rawBottom = location[1] + height
        val rectInWindow = if (decorW > 1 && decorH > 1) {
            Rect(
                rawLeft.coerceIn(0, decorW - 1),
                rawTop.coerceIn(0, decorH - 1),
                rawRight.coerceIn(1, decorW),
                rawBottom.coerceIn(1, decorH)
            )
        } else {
            Rect(rawLeft, rawTop, rawRight, rawBottom)
        }
        val copyW = rectInWindow.width()
        val copyH = rectInWindow.height()
        if (copyW > 1 && copyH > 1) {
            val fullBitmap = runCatching {
                Bitmap.createBitmap(copyW, copyH, Bitmap.Config.ARGB_8888)
            }.getOrNull()
            val mainHandler = Handler(Looper.getMainLooper())
            if (fullBitmap != null) {
                try {
                    PixelCopy.request(
                        window,
                        rectInWindow,
                        fullBitmap,
                        { copyResult ->
                            runCatching {
                                if (copyResult == PixelCopy.SUCCESS) {
                                    val cropped = cropBitmapRegion(fullBitmap, area, maxOutputDimension = 1024)
                                    if (!fullBitmap.isRecycled) fullBitmap.recycle()
                                    if (cropped != null && hasVisibleChartContent(cropped)) {
                                        onCaptured(cropped)
                                    } else {
                                        if (cropped != null && !cropped.isRecycled) cropped.recycle()
                                        if (attempt == 0 && webView.isAttachedToWindow) {
                                            mainHandler.postDelayed(
                                                {
                                                    if (webView.isAttachedToWindow) {
                                                        captureWebViewRegionWithRetry(webView, area, attempt = 1, onCaptured = onCaptured)
                                                    } else {
                                                        onCaptured(null)
                                                    }
                                                },
                                                180L
                                            )
                                        } else {
                                            val fallback = runCatching {
                                                captureWebViewSoftwareFallback(webView, area)
                                            }.getOrNull()
                                            if (fallback != null && hasVisibleChartContent(fallback)) {
                                                onCaptured(fallback)
                                            } else {
                                                if (fallback != null && !fallback.isRecycled) fallback.recycle()
                                                onCaptured(null)
                                            }
                                        }
                                    }
                                } else {
                                    if (!fullBitmap.isRecycled) fullBitmap.recycle()
                                    if (attempt == 0 && webView.isAttachedToWindow) {
                                        mainHandler.postDelayed(
                                            {
                                                if (webView.isAttachedToWindow) {
                                                    captureWebViewRegionWithRetry(webView, area, attempt = 1, onCaptured = onCaptured)
                                                } else {
                                                    onCaptured(null)
                                                }
                                            },
                                            180L
                                        )
                                    } else {
                                        val fallback = runCatching {
                                            captureWebViewSoftwareFallback(webView, area)
                                        }.getOrNull()
                                        if (fallback != null && hasVisibleChartContent(fallback)) {
                                            onCaptured(fallback)
                                        } else {
                                            if (fallback != null && !fallback.isRecycled) fallback.recycle()
                                            onCaptured(null)
                                        }
                                    }
                                }
                            }.onFailure {
                                if (!fullBitmap.isRecycled) fullBitmap.recycle()
                                onCaptured(null)
                            }
                        },
                        mainHandler
                    )
                    return
                } catch (_: Throwable) {
                    if (!fullBitmap.isRecycled) fullBitmap.recycle()
                }
            }
        }
    }

    captureWebViewCanvasJsFallback(webView = webView, area = area) { jsBitmap ->
        if (jsBitmap != null && hasVisibleChartContent(jsBitmap)) {
            onCaptured(jsBitmap)
        } else {
            if (jsBitmap != null && !jsBitmap.isRecycled) jsBitmap.recycle()
            val fallback = runCatching { captureWebViewSoftwareFallback(webView, area) }.getOrNull()
            if (fallback != null && hasVisibleChartContent(fallback)) {
                onCaptured(fallback)
            } else {
                if (fallback != null && !fallback.isRecycled) fallback.recycle()
                onCaptured(null)
            }
        }
    }
}

private fun captureWebViewCanvasJsFallback(
    webView: WebView,
    area: NormalizedRect,
    onDone: (Bitmap?) -> Unit
) {
    val js = """
        (function() {
            try {
                var canvases = Array.prototype.slice.call(document.querySelectorAll('canvas'));
                var best = null;
                var bestArea = 0;
                for (var i = 0; i < canvases.length; i++) {
                    var c = canvases[i];
                    var r = c.getBoundingClientRect();
                    var a = (r.width || c.width || 0) * (r.height || c.height || 0);
                    if (a > bestArea && (c.width || 0) >= 64 && (c.height || 0) >= 64) {
                        bestArea = a;
                        best = c;
                    }
                }
                if (!best) return '';
                return best.toDataURL('image/jpeg', 0.72) || '';
            } catch (e) {
                return '';
            }
        })();
    """.trimIndent()
    runCatching {
        webView.evaluateJavascript(js) { raw ->
            val decoded = runCatching {
                val cleaned = raw?.trim()?.removeSurrounding("\"")
                    ?.replace("\\/", "/")
                    ?.replace("\\n", "")
                    .orEmpty()
                val marker = "base64,"
                val idx = cleaned.indexOf(marker)
                if (idx < 0) return@runCatching null
                val b64 = cleaned.substring(idx + marker.length)
                val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                val full = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching null
                val cropped = cropBitmapRegion(full, area, maxOutputDimension = 1024)
                if (cropped !== full && !full.isRecycled) full.recycle()
                cropped
            }.getOrNull()
            onDone(decoded)
        }
    }.onFailure {
        onDone(null)
    }
}

internal fun hasVisibleChartContent(bitmap: Bitmap): Boolean {
    if (bitmap.isRecycled || bitmap.width < 8 || bitmap.height < 8) return false
    val grid = 14
    var hasAlpha = false
    var firstRgb = 0
    var hasFirst = false
    for (gy in 0 until grid) {
        val py = (((gy + 0.5f) * bitmap.height) / grid).toInt().coerceIn(0, bitmap.height - 1)
        for (gx in 0 until grid) {
            val px = (((gx + 0.5f) * bitmap.width) / grid).toInt().coerceIn(0, bitmap.width - 1)
            val pixel = bitmap.getPixel(px, py)
            val alpha = (pixel ushr 24) and 0xFF
            if (alpha > 12) hasAlpha = true
            val rgb = pixel and 0x00FFFFFF
            if (!hasFirst) {
                firstRgb = rgb
                hasFirst = true
            } else if (hasAlpha) {
                val dr = kotlin.math.abs(((rgb ushr 16) and 0xFF) - ((firstRgb ushr 16) and 0xFF))
                val dg = kotlin.math.abs(((rgb ushr 8) and 0xFF) - ((firstRgb ushr 8) and 0xFF))
                val db = kotlin.math.abs((rgb and 0xFF) - (firstRgb and 0xFF))
                if (maxOf(dr, dg, db) > 6) {
                    return true
                }
            }
        }
    }
    return false
}

private fun captureWebViewSoftwareFallback(
    webView: WebView,
    area: NormalizedRect
): Bitmap? {
    val width = webView.width
    val height = webView.height
    if (width <= 0 || height <= 0) return null

    return try {
        val fullBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(fullBitmap)
        webView.draw(canvas)
        val cropped = cropBitmapRegion(fullBitmap, area)
        fullBitmap.recycle()
        cropped
    } catch (_: Exception) {
        null
    }
}

/**
 * Maps a [NormalizedRect] selected over a `ContentScale.Fit` viewport to the actual
 * bitmap pixels, stripping out any letterbox bars around the image.
 */
fun cropFittedBitmapRegion(
    source: Bitmap,
    viewportWidth: Int,
    viewportHeight: Int,
    area: NormalizedRect,
    maxOutputDimension: Int = 960
): Bitmap? {
    if (viewportWidth <= 0 || viewportHeight <= 0 || source.width <= 0 || source.height <= 0) {
        return cropBitmapRegion(source, area, maxOutputDimension)
    }

    val norm = area.normalized()
    val scale = minOf(
        viewportWidth.toFloat() / source.width.toFloat(),
        viewportHeight.toFloat() / source.height.toFloat()
    )
    if (scale <= 0f) return null

    val fittedW = source.width * scale
    val fittedH = source.height * scale
    val offsetX = (viewportWidth - fittedW) / 2f
    val offsetY = (viewportHeight - fittedH) / 2f

    val selLeftPx = norm.left * viewportWidth
    val selTopPx = norm.top * viewportHeight
    val selRightPx = norm.right * viewportWidth
    val selBottomPx = norm.bottom * viewportHeight

    val mappedLeft = ((selLeftPx - offsetX) / fittedW).coerceIn(0f, 1f)
    val mappedTop = ((selTopPx - offsetY) / fittedH).coerceIn(0f, 1f)
    val mappedRight = ((selRightPx - offsetX) / fittedW).coerceIn(0f, 1f)
    val mappedBottom = ((selBottomPx - offsetY) / fittedH).coerceIn(0f, 1f)

    val mappedArea = NormalizedRect(mappedLeft, mappedTop, mappedRight, mappedBottom)
    if (!mappedArea.isValid(0.03f)) {
        return null
    }
    return cropBitmapRegion(source, mappedArea, maxOutputDimension)
}

fun cropBitmapRegion(
    source: Bitmap,
    area: NormalizedRect,
    maxOutputDimension: Int = 960
): Bitmap? {
    val norm = area.normalized()
    if (!norm.isValid(0.02f)) return null

    val srcW = source.width
    val srcH = source.height
    if (srcW <= 0 || srcH <= 0) return null

    val cropX = (norm.left * srcW).roundToInt().coerceIn(0, srcW - 1)
    val cropY = (norm.top * srcH).roundToInt().coerceIn(0, srcH - 1)
    val cropW = (norm.width * srcW).roundToInt().coerceIn(1, srcW - cropX)
    val cropH = (norm.height * srcH).roundToInt().coerceIn(1, srcH - cropY)

    val readableSource = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        source.config == Bitmap.Config.HARDWARE
    ) {
        source.copy(Bitmap.Config.ARGB_8888, false) ?: source
    } else {
        source
    }

    return try {
        val cropped = Bitmap.createBitmap(readableSource, cropX, cropY, cropW, cropH)
        if (cropped.width > maxOutputDimension || cropped.height > maxOutputDimension) {
            val scale = maxOutputDimension.toFloat() / maxOf(cropped.width, cropped.height)
            val targetW = (cropped.width * scale).roundToInt().coerceAtLeast(1)
            val targetH = (cropped.height * scale).roundToInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(cropped, targetW, targetH, true)
            if (scaled !== cropped && cropped !== readableSource && cropped !== source) {
                cropped.recycle()
            }
            if (readableSource !== source && readableSource !== scaled) {
                readableSource.recycle()
            }
            scaled
        } else {
            if (cropped === readableSource && readableSource === source) {
                cropped.copy(Bitmap.Config.ARGB_8888, false) ?: cropped
            } else {
                if (readableSource !== source && readableSource !== cropped) {
                    readableSource.recycle()
                }
                cropped
            }
        }
    } catch (_: Exception) {
        null
    }
}

fun defaultChartRegionOnBitmap(source: Bitmap): NormalizedRect {
    val isTallPhoneScreenshot = source.height > source.width * 1.45f
    return if (isTallPhoneScreenshot) {
        // Tall mobile broker screenshot: frame the candlestick + RSI zone above trade buttons
        NormalizedRect(0.03f, 0.16f, 0.97f, 0.65f)
    } else {
        NormalizedRect(0.02f, 0.02f, 0.98f, 0.98f)
    }
}

fun mapBitmapRectToViewport(
    source: Bitmap,
    viewportWidth: Int,
    viewportHeight: Int,
    bitmapArea: NormalizedRect
): NormalizedRect {
    if (viewportWidth <= 0 || viewportHeight <= 0 || source.width <= 0 || source.height <= 0) {
        return bitmapArea.normalized()
    }
    val norm = bitmapArea.normalized()
    val scale = minOf(
        viewportWidth.toFloat() / source.width.toFloat(),
        viewportHeight.toFloat() / source.height.toFloat()
    )
    if (scale <= 0f) return norm

    val fittedW = source.width * scale
    val fittedH = source.height * scale
    val offsetX = (viewportWidth - fittedW) / 2f
    val offsetY = (viewportHeight - fittedH) / 2f

    return NormalizedRect(
        left = ((offsetX + norm.left * fittedW) / viewportWidth).coerceIn(0f, 1f),
        top = ((offsetY + norm.top * fittedH) / viewportHeight).coerceIn(0f, 1f),
        right = ((offsetX + norm.right * fittedW) / viewportWidth).coerceIn(0f, 1f),
        bottom = ((offsetY + norm.bottom * fittedH) / viewportHeight).coerceIn(0f, 1f)
    ).normalized()
}

fun decodeSampledBitmapFromUri(
    context: Context,
    uri: Uri,
    maxDimension: Int = 1440
): Bitmap? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val imageDecoderBitmap = runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val w = info.size.width
                val h = info.size.height
                if (w > maxDimension || h > maxDimension) {
                    val ratio = maxDimension.toFloat() / maxOf(w, h)
                    decoder.setTargetSize(
                        (w * ratio).roundToInt().coerceAtLeast(1),
                        (h * ratio).roundToInt().coerceAtLeast(1)
                    )
                }
            }
        }.getOrNull()
        if (imageDecoderBitmap != null) {
            return if (imageDecoderBitmap.config == Bitmap.Config.HARDWARE) {
                imageDecoderBitmap.copy(Bitmap.Config.ARGB_8888, false) ?: imageDecoderBitmap
            } else {
                imageDecoderBitmap
            }
        }
    }

    return runCatching {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOpts)
        }
        var sampleSize = 1
        if (boundsOpts.outWidth > maxDimension || boundsOpts.outHeight > maxDimension) {
            val halfW = boundsOpts.outWidth / 2
            val halfH = boundsOpts.outHeight / 2
            while ((halfW / sampleSize) >= maxDimension || (halfH / sampleSize) >= maxDimension) {
                sampleSize *= 2
            }
        }
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val decoded = BitmapFactory.decodeStream(stream, null, decodeOpts) ?: return@use null
            if (decoded.width > maxDimension || decoded.height > maxDimension) {
                val ratio = maxDimension.toFloat() / maxOf(decoded.width, decoded.height)
                val scaled = Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * ratio).roundToInt().coerceAtLeast(1),
                    (decoded.height * ratio).roundToInt().coerceAtLeast(1),
                    true
                )
                if (scaled != decoded) decoded.recycle()
                scaled
            } else {
                decoded
            }
        }
    }.getOrNull()
}
