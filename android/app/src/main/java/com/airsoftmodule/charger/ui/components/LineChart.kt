package com.airsoftmodule.charger.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airsoftmodule.charger.ui.theme.Tac
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Series(val name: String, val color: Color, val values: List<Float>)

/**
 * Multi-series line chart with grid, auto range, area fill for single series,
 * and a touch scrubber that shows the values under the finger.
 */
@Composable
fun LineChart(
    times: List<Long>,
    series: List<Series>,
    format: (Float) -> String,
    modifier: Modifier = Modifier,
    minSpan: Float = 1f,
) {
    val measurer = rememberTextMeasurer()
    var touchX by remember { mutableStateOf<Float?>(null) }
    val label = TextStyle(color = Tac.Faint, fontSize = 10.sp, fontFeatureSettings = "tnum")
    val clock = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    Box(modifier) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        touchX = down.position.x
                        do {
                            val ev = awaitPointerEvent()
                            ev.changes.firstOrNull()?.let { touchX = it.position.x; it.consume() }
                        } while (ev.changes.any { it.pressed })
                        touchX = null
                    }
                },
        ) {
            val left = 46.dp.toPx()
            val bottom = size.height - 26f
            val top = 8f
            val right = size.width - 6f
            val all = series.flatMap { it.values }
            if (all.isEmpty() || times.size < 2) {
                drawText(measurer, "Waiting for data…", Offset(left, size.height / 2 - 10f), TextStyle(color = Tac.Faint, fontSize = 13.sp))
                return@Canvas
            }
            var lo = all.min()
            var hi = all.max()
            if (hi - lo < minSpan) { val mid = (hi + lo) / 2; lo = mid - minSpan / 2; hi = mid + minSpan / 2 }
            val pad = (hi - lo) * 0.08f
            lo -= pad; hi += pad
            val t0 = times.first(); val t1 = times.last().coerceAtLeast(t0 + 1)
            fun x(t: Long) = left + (right - left) * (t - t0).toFloat() / (t1 - t0)
            fun y(v: Float) = bottom - (bottom - top) * (v - lo) / (hi - lo)

            // grid + y labels
            val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 8f))
            for (k in 0..4) {
                val v = lo + (hi - lo) * k / 4
                val yy = y(v)
                drawLine(Tac.Outline, Offset(left, yy), Offset(right, yy), 1f, pathEffect = dash)
                val tl = measurer.measure(format(v), label)
                drawText(tl, topLeft = Offset(left - tl.size.width - 8f, yy - tl.size.height / 2))
            }
            // x labels
            for (k in 0..3) {
                val t = t0 + (t1 - t0) * k / 3
                val tl = measurer.measure(clock.format(Date(t)), label)
                val xx = (x(t) - tl.size.width / 2).coerceIn(left, right - tl.size.width)
                drawText(tl, topLeft = Offset(xx, bottom + 8f))
            }
            // series
            val step = maxOf(1, times.size / 600)          // keep path sizes sane on long sessions
            series.forEach { s ->
                val path = Path()
                var first = true
                var i = 0
                while (i < s.values.size) {
                    val px = x(times[i]); val py = y(s.values[i])
                    if (first) { path.moveTo(px, py); first = false } else path.lineTo(px, py)
                    i += step
                }
                if (series.size == 1) {
                    val area = Path().apply {
                        addPath(path)
                        lineTo(x(times[(s.values.size - 1) / step * step]), bottom)
                        lineTo(left, bottom)
                        close()
                    }
                    drawPath(area, Brush.verticalGradient(listOf(s.color.copy(alpha = 0.28f), Color.Transparent), top, bottom))
                }
                drawPath(path, s.color, style = Stroke(4f, join = StrokeJoin.Round))
            }
            // scrubber
            touchX?.let { tx ->
                val cx = tx.coerceIn(left, right)
                val t = t0 + ((cx - left) / (right - left) * (t1 - t0)).toLong()
                val idx = times.indexOfFirst { it >= t }.let { if (it < 0) times.lastIndex else it }
                val xx = x(times[idx])
                drawLine(Tac.Dim, Offset(xx, top), Offset(xx, bottom), 2f)
                val lines = listOf(clock.format(Date(times[idx])) + ":" + SimpleDateFormat("ss", Locale.US).format(Date(times[idx]))) +
                    series.map { "${it.name}  ${format(it.values[idx])}" }
                val measured = lines.mapIndexed { n, s ->
                    measurer.measure(s, TextStyle(color = if (n == 0) Tac.Dim else series[n - 1].color, fontSize = 12.sp, fontFeatureSettings = "tnum"))
                }
                val w = measured.maxOf { it.size.width } + 24f
                val h = measured.sumOf { it.size.height } + 18f
                val bx = if (xx + w + 12f < right) xx + 12f else xx - w - 12f
                drawRoundRect(Tac.Surface3.copy(alpha = 0.95f), Offset(bx, top), Size(w, h.toFloat()), CornerRadius(14f))
                var yy = top + 9f
                measured.forEach { drawText(it, topLeft = Offset(bx + 12f, yy)); yy += it.size.height }
                series.forEach { s -> drawCircle(s.color, 7f, Offset(xx, y(s.values[idx]))); drawCircle(Tac.Bg, 3.5f, Offset(xx, y(s.values[idx]))) }
            }
        }
    }
}
