package com.airsoftmodule.charger.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airsoftmodule.charger.ui.theme.Num
import com.airsoftmodule.charger.ui.theme.Tac
import java.util.Locale

// ------------------------------------------------------------------ formatting
fun volts(mv: Int, digits: Int = 2) = String.format(Locale.US, "%.${digits}f V", mv / 1000.0)
fun amps(ma: Int) = if (kotlin.math.abs(ma) < 1000) "$ma mA" else String.format(Locale.US, "%.2f A", ma / 1000.0)
fun watts(w: Double) = String.format(Locale.US, "%.1f W", w)
fun temp(dc: Int, f: Boolean) = if (f) String.format(Locale.US, "%.1f °F", dc / 10.0 * 9 / 5 + 32) else String.format(Locale.US, "%.1f °C", dc / 10.0)
fun duration(min: Int) = when {
    min < 0 -> "—"
    min < 60 -> "$min min"
    else -> "${min / 60} h ${min % 60} min"
}
fun uptime(s: Int) = if (s < 3600) String.format(Locale.US, "%d:%02d", s / 60, s % 60) else String.format(Locale.US, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)

fun socColor(pct: Int): Color = when {
    pct < 20 -> Tac.Bad
    pct < 45 -> Tac.Warn
    else -> Tac.Ok
}

// ------------------------------------------------------------------ containers
@Composable
fun Panel(modifier: Modifier = Modifier, glow: Color? = null, padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Tac.Surface2, Tac.Surface)))
            .border(1.dp, glow?.copy(alpha = 0.45f) ?: Tac.Outline, shape)
            .padding(padding),
        content = content,
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 3.dp, height = 12.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(8.dp))
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = Tac.Dim, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, icon: ImageVector? = null, pulse: Boolean = false) {
    val alpha = if (pulse) {
        val t = rememberInfiniteTransition(label = "pill")
        t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a").value
    } else 1f
    Row(
        modifier.clip(CircleShape).background(color.copy(alpha = 0.14f)).border(1.dp, color.copy(alpha = 0.35f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color.copy(alpha = alpha), modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        } else {
            Box(Modifier.size(7.dp).clip(CircleShape).background(color.copy(alpha = alpha)))
            Spacer(Modifier.width(7.dp))
        }
        Text(text, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null, icon: ImageVector? = null, tint: Color = Tac.Text) {
    Panel(modifier, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Tac.Dim)
        }
        Spacer(Modifier.height(8.dp))
        Text(value, style = MaterialTheme.typography.titleLarge.merge(Num), color = tint, maxLines = 1)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall.merge(Num), color = Tac.Dim, maxLines = 1)
    }
}

// ------------------------------------------------------------------ SOC ring
@Composable
fun SocRing(soc: Int, charging: Boolean, color: Color, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val sweep by animateFloatAsState(soc.coerceIn(0, 100) / 100f, tween(900), label = "soc")
    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "ang")
    val track = Tac.Surface3
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.075f
            val inset = stroke / 2 + size.minDimension * 0.04f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val tl = Offset(inset, inset)
            // tick marks
            val r = size.minDimension / 2
            for (i in 0 until 60) {
                rotate(i * 6f) {
                    val major = i % 5 == 0
                    drawLine(
                        if (i <= sweep * 60) color.copy(alpha = if (major) 0.9f else 0.45f) else Tac.Outline,
                        Offset(center.x, center.y - r + 1f), Offset(center.x, center.y - r + if (major) 9f else 5f),
                        strokeWidth = if (major) 3f else 2f,
                    )
                }
            }
            drawArc(track, -90f, 360f, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            val brush = Brush.sweepGradient(listOf(color.copy(alpha = 0.35f), color, color), center)
            rotate(-90f) {
                drawArc(brush, 0f, 360f * sweep, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            if (charging) {
                // a light that runs along the filled arc while energy is flowing
                val a = Math.toRadians((angle * sweep - 90).toDouble())
                val rr = arcSize.width / 2
                val p = Offset(center.x + (rr * kotlin.math.cos(a)).toFloat(), center.y + (rr * kotlin.math.sin(a)).toFloat())
                drawCircle(Color.White.copy(alpha = 0.85f), stroke * 0.32f, p)
                drawCircle(color.copy(alpha = 0.35f), stroke * 0.9f, p)
            }
        }
        content()
    }
}

// ------------------------------------------------------------------ cell bar
@Composable
fun CellBar(index: Int, mv: Int, pct: Int, balancing: Boolean, highlight: Color?, modifier: Modifier = Modifier, full: Boolean = false) {
    val color = Tac.Cell[index]
    val fill by animateFloatAsState(pct.coerceIn(0, 100) / 100f, tween(700), label = "cell")
    val t = rememberInfiniteTransition(label = "bal")
    val flow by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "flow")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("CELL ${index + 1}", style = MaterialTheme.typography.labelSmall, color = color)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.width(54.dp).height(140.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val capW = size.width * 0.38f
                drawRoundRect(Tac.Outline, Offset((size.width - capW) / 2, 0f), Size(capW, 8f), CornerRadius(3f))
                val body = Size(size.width, size.height - 10f)
                val top = 10f
                drawRoundRect(Tac.Surface3, Offset(0f, top), body, CornerRadius(14f))
                drawRoundRect(highlight ?: Tac.Outline, Offset(0f, top), body, CornerRadius(14f), style = Stroke(if (highlight != null) 4f else 2f))
                val inner = 7f
                val h = (body.height - inner * 2) * fill
                if (h > 1f) drawRoundRect(
                    Brush.verticalGradient(listOf(color, color.copy(alpha = 0.55f)), startY = top + body.height - inner - h, endY = top + body.height),
                    Offset(inner, top + body.height - inner - h), Size(body.width - inner * 2, h), CornerRadius(9f),
                )
                // segment lines
                for (k in 1..4) {
                    val y = top + inner + (body.height - inner * 2) * k / 5f
                    drawLine(Tac.Bg.copy(alpha = 0.55f), Offset(inner, y), Offset(body.width - inner, y), 2f)
                }
                if (balancing) {
                    // drops falling out of the bottom = bleeding resistor active
                    for (k in 0..2) {
                        val ph = (flow + k / 3f) % 1f
                        drawCircle(Tac.Warn.copy(alpha = 1f - ph), 4f, Offset(size.width / 2, top + body.height * (0.25f + 0.6f * ph)))
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(String.format(Locale.US, "%.3f", mv / 1000.0), style = MaterialTheme.typography.titleMedium.merge(Num), color = Tac.Text)
        Text("$pct %", style = MaterialTheme.typography.bodySmall.merge(Num), color = Tac.Dim)
        if (full) Text("FULL", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = Tac.Ok)
        if (balancing) Text("BLEEDING", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = Tac.Warn)
    }
}

@Composable
fun LabeledRow(label: String, value: String, valueColor: Color = Tac.Text) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Tac.Dim, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = valueColor, style = MaterialTheme.typography.bodyMedium.merge(Num), fontWeight = FontWeight.Medium)
    }
}
