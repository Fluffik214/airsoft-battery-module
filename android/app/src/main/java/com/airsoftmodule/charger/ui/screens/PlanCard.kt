package com.airsoftmodule.charger.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.airsoftmodule.charger.data.DevConfig
import com.airsoftmodule.charger.data.Ocv
import com.airsoftmodule.charger.data.Status
import com.airsoftmodule.charger.ui.components.Panel
import com.airsoftmodule.charger.ui.components.SectionLabel
import com.airsoftmodule.charger.ui.components.duration
import com.airsoftmodule.charger.ui.theme.Num
import com.airsoftmodule.charger.ui.theme.Tac
import java.util.Locale
import kotlin.math.roundToInt

private enum class PlanKind(val label: String, val icon: ImageVector) {
    FULL("Full", Icons.Rounded.BatteryChargingFull),
    CUSTOM("Custom", Icons.Rounded.Tune),
    STORAGE("Storage", Icons.Rounded.Inventory2),
    OFF("Off", Icons.Rounded.Block),
}

private fun kindOf(cfg: DevConfig) = when (cfg["mode"]) {
    1 -> PlanKind.STORAGE
    2 -> PlanKind.OFF
    else -> if (cfg["tgt"] >= 100) PlanKind.FULL else PlanKind.CUSTOM
}

/** Banner shown while the module runs from this phone's 5 V (it never charges from it). */
@Composable
fun PhoneLockBanner(s: Status) {
    if (s.source != "LOW") return
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(16.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Phone power: monitor only", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                "Your phone gives 5 V and the module only charges from 12–17 V, so your phone's battery is never drained. Set the plan below, then put the module on a PD charger.",
                style = MaterialTheme.typography.bodySmall, color = Tac.Dim,
            )
        }
    }
}

/** Demo only: switch between "plugged into the phone" and "sitting on a PD charger". */
@Composable
fun DemoScenario(phone: Boolean, onChange: (Boolean) -> Unit) {
    Panel(Modifier.fillMaxWidth(), glow = Tac.Warn, padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Science, null, tint = Tac.Warn, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("DEMO · SIMULATE", style = MaterialTheme.typography.labelSmall, color = Tac.Warn, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(true to "On this phone", false to "Preview: on a charger").forEach { (v, label) ->
                val sel = v == phone
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .background(if (sel) Tac.Warn.copy(alpha = 0.16f) else Tac.Surface)
                        .border(1.dp, if (sel) Tac.Warn else Tac.Outline, RoundedCornerShape(12.dp))
                        .clickable { onChange(v) }.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(label, color = if (sel) Tac.Warn else Tac.Dim, style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

@Composable
fun ChargePlanCard(s: Status, cfg: DevConfig?, onPlan: (mode: Int, tgt: Int?) -> Unit) {
    val c = cfg ?: DevConfig.DEFAULT
    var kind by remember { mutableStateOf(kindOf(c)) }
    var custom by remember { mutableIntStateOf(if (c["tgt"] in 50..95) c["tgt"] else 80) }
    // follow the module when its config arrives or changes
    LaunchedEffect(cfg) {
        if (cfg != null) {
            kind = kindOf(cfg)
            if (cfg["tgt"] in 50..95) custom = cfg["tgt"]
        }
    }
    val accent = MaterialTheme.colorScheme.primary

    // what the module will do, computed like the firmware does
    val planCfg = DevConfig(
        c.values + when (kind) {
            PlanKind.FULL -> mapOf("mode" to 0, "tgt" to 100)
            PlanKind.CUSTOM -> mapOf("mode" to 0, "tgt" to custom)
            PlanKind.STORAGE -> mapOf("mode" to 1)
            PlanKind.OFF -> mapOf("mode" to 2)
        },
    )
    val targetMv = Ocv.target(planCfg)
    val targetPct = Ocv.pct(targetMv)
    val nowPct = s.soc
    val addMah = ((targetPct - nowPct).coerceAtLeast(0) * c["cap"] / 100.0)
    val ichNow = if (s.fast) c["ichg"] else c["ichs"]
    val minutes = if (addMah > 0) (addMah / ichNow * 60).roundToInt() + 10 else 0

    SectionLabel("Charge plan")
    Panel(Modifier.fillMaxWidth()) {
        Text("When it's on a charger", style = MaterialTheme.typography.titleMedium)
        Text(
            "Plug the module into a USB-C PD charger. When it sees 12–17 V for ${c["wait"]} s it charges by this plan and stops by itself. Above 17 V it cuts out instantly.",
            style = MaterialTheme.typography.bodySmall, color = Tac.Dim,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlanKind.entries.forEach { k ->
                val sel = k == kind
                val sub = when (k) {
                    PlanKind.FULL -> "100 %"
                    PlanKind.CUSTOM -> "$custom %"
                    PlanKind.STORAGE -> String.format(Locale.US, "%.2f V", c["stor"] / 1000.0)
                    PlanKind.OFF -> "monitor"
                }
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                        .background(if (sel) accent.copy(alpha = 0.16f) else Tac.Surface)
                        .border(1.dp, if (sel) accent else Tac.Outline, RoundedCornerShape(14.dp))
                        .clickable {
                            kind = k
                            when (k) {
                                PlanKind.FULL -> onPlan(0, 100)
                                PlanKind.CUSTOM -> onPlan(0, custom)
                                PlanKind.STORAGE -> onPlan(1, null)
                                PlanKind.OFF -> onPlan(2, null)
                            }
                        }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(k.icon, null, tint = if (sel) accent else Tac.Dim, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(k.label, style = MaterialTheme.typography.labelLarge, color = if (sel) accent else Tac.Text)
                    Text(sub, style = MaterialTheme.typography.bodySmall.merge(Num), color = Tac.Dim)
                }
            }
        }
        AnimatedVisibility(kind == PlanKind.CUSTOM) {
            Column(Modifier.padding(top = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Stop at", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text("$custom %", style = MaterialTheme.typography.titleLarge.merge(Num), color = accent)
                }
                Slider(
                    value = custom.toFloat(), valueRange = 50f..95f, steps = 8,
                    onValueChange = { custom = ((it / 5).roundToInt() * 5).coerceIn(50, 95) },
                    onValueChangeFinished = { onPlan(0, custom) },
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = Tac.Surface3, activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
                )
                Text("80 % is a good everyday choice: a lot less stress on the cells, ~20 % less runtime.", style = MaterialTheme.typography.bodySmall, color = Tac.Dim)
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Tac.Outline))
        Spacer(Modifier.height(12.dp))

        if (kind == PlanKind.OFF) {
            Text("The module will only monitor and balance. It will never charge.", style = MaterialTheme.typography.bodyMedium, color = Tac.Dim)
        } else {
            Row {
                PlanFact("Stops at", String.format(Locale.US, "%.2f V", targetMv / 1000.0), "per cell", Modifier.weight(1f))
                PlanFact("Pack", "$nowPct → $targetPct %", String.format(Locale.US, "%.1f V", targetMv * 3 / 1000.0), Modifier.weight(1f))
                PlanFact(
                    if (kind == PlanKind.STORAGE && nowPct > targetPct) "Bleeds" else "Adds",
                    if (addMah > 0) "${addMah.roundToInt()} mAh" else "—",
                    if (minutes > 0) "~${duration(minutes)}" else if (kind == PlanKind.STORAGE && nowPct > targetPct) "slowly" else "already there",
                    Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.airsoftmodule.charger.ui.components.Pill(
                if (s.fast) "FAST · JP2 bridged · $ichNow mA" else "SLOW · JP2 open · $ichNow mA",
                if (s.fast) Tac.Warn else MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(14.dp))
        Timeline(c["wait"], kind, targetPct)
    }
}

@Composable
private fun PlanFact(label: String, value: String, sub: String, modifier: Modifier) {
    Column(modifier) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Tac.Dim)
        Text(value, style = MaterialTheme.typography.titleSmall.merge(Num), fontWeight = FontWeight.Bold)
        Text(sub, style = MaterialTheme.typography.bodySmall.merge(Num), color = Tac.Dim)
    }
}

@Composable
private fun Timeline(waitS: Int, kind: PlanKind, pct: Int) {
    val steps = listOf(
        "Plug in a PD charger",
        "Sees 12–17 V for $waitS s",
        when (kind) {
            PlanKind.OFF -> "Monitors only"
            PlanKind.STORAGE -> "Goes to storage, stops"
            else -> "Charges to $pct %, stops"
        },
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        steps.forEachIndexed { i, txt ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(2.dp).background(if (i == 0) Color.Transparent else Tac.Outline))
                    Box(
                        Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) { Text("${i + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                    Box(Modifier.weight(1f).height(2.dp).background(if (i == steps.lastIndex) Color.Transparent else Tac.Outline))
                }
                Spacer(Modifier.height(6.dp))
                Text(txt, style = MaterialTheme.typography.bodySmall, color = Tac.Dim, textAlign = TextAlign.Center)
            }
        }
    }
}
