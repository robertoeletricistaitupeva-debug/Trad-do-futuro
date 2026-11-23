package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.NormalizedRect
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TerminalBorderStrong
import com.example.ui.theme.TerminalSurfaceElevated
import com.example.ui.theme.TextPrimary
import kotlin.math.abs

private enum class DragMode {
    NONE,
    DRAW_NEW,
    MOVE,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT
}

@Composable
fun AreaSelectionOverlay(
    isMarkingArea: Boolean,
    draftSelection: NormalizedRect?,
    markedArea: NormalizedRect?,
    onDraftChange: (NormalizedRect) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onAutoDetect: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (isMarkingArea) {
        val activeRect = (draftSelection ?: NormalizedRect(0.08f, 0.22f, 0.92f, 0.78f)).normalized()
        var dragMode by remember { mutableStateOf(DragMode.NONE) }
        var dragOrigin by remember { mutableStateOf(Offset.Zero) }
        var rectAtDragStart by remember { mutableStateOf(activeRect) }

        Box(modifier = modifier.fillMaxSize()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                val h = size.height.toFloat().coerceAtLeast(1f)
                                val nx = (startOffset.x / w).coerceIn(0f, 1f)
                                val ny = (startOffset.y / h).coerceIn(0f, 1f)
                                val cur = (draftSelection ?: activeRect).normalized()
                                rectAtDragStart = cur
                                dragOrigin = Offset(nx, ny)

                                val handleTol = 0.08f
                                dragMode = when {
                                    abs(nx - cur.left) < handleTol && abs(ny - cur.top) < handleTol -> DragMode.TOP_LEFT
                                    abs(nx - cur.right) < handleTol && abs(ny - cur.top) < handleTol -> DragMode.TOP_RIGHT
                                    abs(nx - cur.left) < handleTol && abs(ny - cur.bottom) < handleTol -> DragMode.BOTTOM_LEFT
                                    abs(nx - cur.right) < handleTol && abs(ny - cur.bottom) < handleTol -> DragMode.BOTTOM_RIGHT
                                    nx in cur.left..cur.right && ny in cur.top..cur.bottom -> DragMode.MOVE
                                    else -> DragMode.DRAW_NEW
                                }

                                if (dragMode == DragMode.DRAW_NEW) {
                                    onDraftChange(NormalizedRect(nx, ny, nx, ny))
                                }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                val h = size.height.toFloat().coerceAtLeast(1f)
                                val nx = (change.position.x / w).coerceIn(0f, 1f)
                                val ny = (change.position.y / h).coerceIn(0f, 1f)
                                val dx = nx - dragOrigin.x
                                val dy = ny - dragOrigin.y
                                val base = rectAtDragStart
                                val minSize = 0.08f

                                val updated = when (dragMode) {
                                    DragMode.DRAW_NEW -> NormalizedRect(
                                        left = minOf(dragOrigin.x, nx),
                                        top = minOf(dragOrigin.y, ny),
                                        right = maxOf(dragOrigin.x, nx),
                                        bottom = maxOf(dragOrigin.y, ny)
                                    )
                                    DragMode.MOVE -> {
                                        val rectW = base.width
                                        val rectH = base.height
                                        val newL = (base.left + dx).coerceIn(0f, 1f - rectW)
                                        val newT = (base.top + dy).coerceIn(0f, 1f - rectH)
                                        NormalizedRect(newL, newT, newL + rectW, newT + rectH)
                                    }
                                    DragMode.TOP_LEFT -> NormalizedRect(
                                        left = nx.coerceAtMost(base.right - minSize),
                                        top = ny.coerceAtMost(base.bottom - minSize),
                                        right = base.right,
                                        bottom = base.bottom
                                    )
                                    DragMode.TOP_RIGHT -> NormalizedRect(
                                        left = base.left,
                                        top = ny.coerceAtMost(base.bottom - minSize),
                                        right = nx.coerceAtLeast(base.left + minSize),
                                        bottom = base.bottom
                                    )
                                    DragMode.BOTTOM_LEFT -> NormalizedRect(
                                        left = nx.coerceAtMost(base.right - minSize),
                                        top = base.top,
                                        right = base.right,
                                        bottom = ny.coerceAtLeast(base.top + minSize)
                                    )
                                    DragMode.BOTTOM_RIGHT -> NormalizedRect(
                                        left = base.left,
                                        top = base.top,
                                        right = nx.coerceAtLeast(base.left + minSize),
                                        bottom = ny.coerceAtLeast(base.top + minSize)
                                    )
                                    DragMode.NONE -> base
                                }
                                onDraftChange(updated)
                            },
                            onDragEnd = {
                                val cur = (draftSelection ?: activeRect).normalized()
                                if (!cur.isValid(0.08f)) {
                                    onDraftChange(rectAtDragStart)
                                }
                                dragMode = DragMode.NONE
                            }
                        )
                    }
            ) {
                // Dimmed scrim with transparent cutout for selected chart zone
                drawRect(color = Color(0xA6050B0D))

                val leftPx = activeRect.left * size.width
                val topPx = activeRect.top * size.height
                val rectW = (activeRect.right - activeRect.left) * size.width
                val rectH = (activeRect.bottom - activeRect.top) * size.height

                if (rectW > 2f && rectH > 2f) {
                    drawRoundRect(
                        color = Color.Transparent,
                        topLeft = Offset(leftPx, topPx),
                        size = Size(rectW, rectH),
                        cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                        blendMode = BlendMode.Clear
                    )

                    // Subtle 3x3 framing grid inside selected chart zone
                    val gridColor = EmeraldMint.copy(alpha = 0.22f)
                    for (i in 1..2) {
                        val gx = leftPx + rectW * (i / 3f)
                        drawLine(
                            color = gridColor,
                            start = Offset(gx, topPx),
                            end = Offset(gx, topPx + rectH),
                            strokeWidth = 1.dp.toPx()
                        )
                        val gy = topPx + rectH * (i / 3f)
                        drawLine(
                            color = gridColor,
                            start = Offset(leftPx, gy),
                            end = Offset(leftPx + rectW, gy),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // Crisp selection border
                    drawRoundRect(
                        color = EmeraldBright,
                        topLeft = Offset(leftPx, topPx),
                        size = Size(rectW, rectH),
                        cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // Corner handle circles
                    val handleRadius = 7.dp.toPx()
                    val corners = listOf(
                        Offset(leftPx, topPx),
                        Offset(leftPx + rectW, topPx),
                        Offset(leftPx, topPx + rectH),
                        Offset(leftPx + rectW, topPx + rectH)
                    )
                    corners.forEach { pt ->
                        drawCircle(color = ObsidianBg, radius = handleRadius + 2.dp.toPx(), center = pt)
                        drawCircle(color = EmeraldMint, radius = handleRadius, center = pt)
                    }
                }
            }

            // Instruction badge at top
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp, start = 16.dp, end = 16.dp)
                    .background(
                        color = TerminalSurfaceElevated.copy(alpha = 0.94f),
                        shape = RoundedCornerShape(999.dp)
                    )
                    .border(1.dp, TerminalBorderStrong, RoundedCornerShape(999.dp))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.CropFree,
                    contentDescription = null,
                    tint = EmeraldBright,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Arraste os cantos sobre os candles e o RSI",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary
                )
            }

            // Action buttons at bottom of selection overlay
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.testTag("cancel_area_button"),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = TerminalSurfaceElevated.copy(alpha = 0.95f),
                        contentColor = TextPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.action_cancel))
                }

                if (onAutoDetect != null) {
                    OutlinedButton(
                        onClick = onAutoDetect,
                        modifier = Modifier.testTag("auto_area_button"),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = TerminalSurfaceElevated.copy(alpha = 0.95f),
                            contentColor = EmeraldBright
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoFixHigh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.action_auto_area))
                    }
                }

                Button(
                    onClick = onConfirm,
                    enabled = activeRect.isValid(),
                    modifier = Modifier.testTag("confirm_area_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldBright,
                        contentColor = ObsidianBg
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.action_confirm_area))
                }
            }
        }
    } else if (markedArea != null) {
        // Non-blocking visual dashed guide showing the active monitored chart zone
        val rect = markedArea.normalized()
        Canvas(modifier = modifier.fillMaxSize()) {
            val leftPx = rect.left * size.width
            val topPx = rect.top * size.height
            val rectW = rect.width * size.width
            val rectH = rect.height * size.height
            if (rectW > 2f && rectH > 2f) {
                drawRoundRect(
                    color = EmeraldBright.copy(alpha = 0.72f),
                    topLeft = Offset(leftPx, topPx),
                    size = Size(rectW, rectH),
                    cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                    style = Stroke(
                        width = 1.6.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f), 0f)
                    )
                )
            }
        }
    }
}
