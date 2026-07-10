package com.pdfpocket.lite.features.fillsign

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.pdfpocket.lite.R

/** Zone de dessin au doigt pour signature ou paraphe, à la Acrobat. */
@Composable
fun SignaturePadDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (Bitmap) -> Unit
) {
    val paths = remember { mutableStateListOf<Path>() }
    var invalidations by remember { mutableIntStateOf(0) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.signature_pad_hint),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(Color.White)
                        .border(1.dp, Color.Gray)
                        .onSizeChanged { canvasSize = it }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    paths.add(Path().apply { moveTo(offset.x, offset.y) })
                                    invalidations++
                                },
                                onDrag = { change, _ ->
                                    paths.lastOrNull()?.lineTo(
                                        change.position.x, change.position.y
                                    )
                                    invalidations++
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Lecture de l'état pour forcer le retraçage à chaque geste.
                        @Suppress("UNUSED_EXPRESSION")
                        invalidations
                        paths.forEach { path ->
                            drawPath(
                                path = path,
                                color = Color.Black,
                                style = Stroke(
                                    width = 6f,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        paths.clear()
                        invalidations++
                    }) { Text(stringResource(R.string.signature_clear)) }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val bitmap = buildBitmap(paths, canvasSize)
                    if (bitmap != null) onConfirm(bitmap)
                },
                enabled = paths.isNotEmpty()
            ) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/** Trace les chemins sur un bitmap transparent puis recadre autour du tracé. */
private fun buildBitmap(paths: List<Path>, size: IntSize): Bitmap? {
    if (paths.isEmpty() || size.width <= 0 || size.height <= 0) return null
    return try {
        val full = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(full)
        val paint = Paint().apply {
            color = android.graphics.Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 6f
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        paths.forEach { canvas.drawPath(it.asAndroidPath(), paint) }

        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = 0f
        var bottom = 0f
        paths.forEach { path ->
            val bounds = path.getBounds()
            if (bounds.left < left) left = bounds.left
            if (bounds.top < top) top = bounds.top
            if (bounds.right > right) right = bounds.right
            if (bounds.bottom > bottom) bottom = bounds.bottom
        }
        val padding = 12f
        val x = (left - padding).toInt().coerceIn(0, size.width - 1)
        val y = (top - padding).toInt().coerceIn(0, size.height - 1)
        val width = ((right - left) + padding * 2).toInt().coerceIn(1, size.width - x)
        val height = ((bottom - top) + padding * 2).toInt().coerceIn(1, size.height - y)
        Bitmap.createBitmap(full, x, y, width, height)
    } catch (_: Exception) {
        null
    }
}
