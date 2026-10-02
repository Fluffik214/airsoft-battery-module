package com.airsoftmodule.charger.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ElectricBolt
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airsoftmodule.charger.data.ChargerViewModel
import com.airsoftmodule.charger.data.Fault
import com.airsoftmodule.charger.data.ChargeReport
import com.airsoftmodule.charger.data.EventKind
import com.airsoftmodule.charger.data.chargeReports
import com.airsoftmodule.charger.data.Link
import com.airsoftmodule.charger.data.LogEntry
import com.airsoftmodule.charger.data.Severity
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import com.airsoftmodule.charger.data.Ocv
import com.airsoftmodule.charger.data.States
import com.airsoftmodule.charger.data.Status
import com.airsoftmodule.charger.data.Tips
import com.airsoftmodule.charger.ui.components.CellBar
import com.airsoftmodule.charger.ui.components.LabeledRow
import com.airsoftmodule.charger.ui.components.Panel
import com.airsoftmodule.charger.ui.components.Pill
import com.airsoftmodule.charger.ui.components.SectionLabel
import com.airsoftmodule.charger.ui.components.SocRing
import com.airsoftmodule.charger.ui.components.StatTile
import com.airsoftmodule.charger.ui.components.amps
import com.airsoftmodule.charger.ui.components.duration
import com.airsoftmodule.charger.ui.components.socColor
import com.airsoftmodule.charger.ui.components.temp
import com.airsoftmodule.charger.ui.components.uptime
import com.airsoftmodule.charger.ui.components.volts
import com.airsoftmodule.charger.ui.components.watts
import com.airsoftmodule.charger.ui.theme.Num
import com.airsoftmodule.charger.ui.theme.Tac
import java.util.Locale

@Composable
fun DashboardScreen(vm: ChargerViewModel, openTips: () -> Unit, openLog: () -> Unit) {
    val link by vm.link.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val s = status
    if (s == null || link is Link.Disconnected || link is Link.Error || link is Link.Searching) {
        NotConnected(link, onConnect = vm::connect, onDemo = vm::startDemo)
        return
    }
    val f by vm.prefs.fahrenheit.state.collectAsStateWithLifecycle()
    val showTip by vm.prefs.showTipOfDay.state.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val cfg by vm.config.collectAsStateWithLifecycle()
    val demoPhone by vm.demoPhone.collectAsStateWithLifecycle()
    val events by vm.log.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (link is Link.Demo) DemoScenario(demoPhone, vm::setDemoScenario)
        PhoneLockBanner(s)
        Hero(s)

        AnimatedVisibility(s.activeFaults.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { s.activeFaults.forEach { FaultCard(it) } }
        }

        SectionLabel("Cells", trailing = { SpreadBadge(s.spreadMv) })
        Panel(Modifier.fillMaxWidth()) {
            val hi = s.cellMv.maxOrNull() ?: 0
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (i in 0..2) {
                    val mark = if (s.spreadMv >= 10 && s.cellMv[i] == hi) Tac.Warn else null   // outline the highest cell
                    CellBar(i, s.cellMv[i], s.cellPct[i], s.balancing(i), mark, full = s.cellFull(i))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                when {
                    s.balanceMask != 0 -> "Bleeding the high cell${if (Integer.bitCount(s.balanceMask) > 1) "s" else ""} to even the pack out."
                    s.spreadMv <= 15 -> "Cells are well balanced."
                    s.spreadMv <= 50 -> "Small difference. Balancing kicks in near full charge."
                    else -> "Noticeable difference between cells. Leave the pack plugged in after it's full so it can balance."
                },
                style = MaterialTheme.typography.bodySmall, color = Tac.Dim, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            )
        }

        ChargePlanCard(s, cfg, vm::setPlan)

        SectionLabel("Power")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                "Input", if (s.vbusMv > 4000) volts(s.vbusMv, 1) else "—", Modifier.weight(1f),
                sub = if (s.vbusMv > 4000) "${amps(s.ibusMa)} · ${watts(s.inputW)}" else "no USB power",
                icon = Icons.Rounded.Usb,
                tint = when (s.source) { "PD" -> Tac.Text; "LOW" -> Tac.Warn; else -> Tac.Dim },
            )
            StatTile("Battery", volts(s.packMv), Modifier.weight(1f), sub = if (s.ibatMa != 0) "${amps(s.ibatMa)} · ${watts(s.chargeW)}" else "idle", icon = Icons.Rounded.BatteryChargingFull)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                "Pack temp", temp(s.packTempDc, f), Modifier.weight(1f), sub = "charger ${temp(s.chipTempDc, f)}", icon = Icons.Rounded.Thermostat,
                tint = when { s.packTempDc > 450 -> Tac.Bad; s.packTempDc > 380 -> Tac.Warn; else -> Tac.Text },
            )
            StatTile("Spread", "${s.spreadMv} mV", Modifier.weight(1f), sub = "highest − lowest cell", icon = Icons.Rounded.ElectricBolt,
                tint = when { s.spreadMv > 50 -> Tac.Bad; s.spreadMv > 15 -> Tac.Warn; else -> Tac.Text })
        }

        LastChargeCard(chargeReports(events).lastOrNull(), f, openLog)
        if (link is Link.Demo && !demoPhone) Text(
            "Preview runs 24× faster than real time. On a real module the phone can't be connected while it charges: the result shows up in Last charge next time you connect.",
            style = MaterialTheme.typography.bodySmall, color = Tac.Warn,
        )

        LogSummary(events, openLog)

        if (showTip) TipOfDay(onOpen = openTips, onHide = { vm.prefs.showTipOfDay.set(false) })
        Spacer(Modifier.height(16.dp))
    }
}

// ------------------------------------------------------------------ hero
@Composable
private fun Hero(s: Status) {
    val fault = s.state == "FAULT"
    val col by animateColorAsState(if (fault) Tac.Bad else socColor(s.soc), tween(600), label = "c")
    val charging = States.isCharging(s.state) && s.ibatMa > 0
    Panel(Modifier.fillMaxWidth(), glow = if (fault) Tac.Bad else null, padding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill(States.label(s.state), if (fault) Tac.Bad else MaterialTheme.colorScheme.primary, pulse = charging || fault)
            Spacer(Modifier.weight(1f))
            when (s.source) {
                "PD" -> Pill("PD ${String.format(Locale.US, "%.0f", s.vbusMv / 1000.0)} V", Tac.Dim, icon = Icons.Rounded.Bolt)
                "LOW" -> Pill("5 V source", Tac.Warn, icon = Icons.Rounded.Usb)
                else -> {}
            }
        }
        Spacer(Modifier.height(12.dp))
        SocRing(s.soc, charging, col, Modifier.size(232.dp).align(Alignment.CenterHorizontally)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${s.soc}", style = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp), color = Tac.Text)
                    Text("%", style = MaterialTheme.typography.headlineMedium, color = Tac.Dim, modifier = Modifier.padding(bottom = 12.dp, start = 2.dp))
                }
                Text(volts(s.packMv), style = MaterialTheme.typography.titleMedium.merge(Num), color = Tac.Dim)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(States.explain(s.state), style = MaterialTheme.typography.bodyMedium, color = Tac.Dim, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        if (s.state == "ARMING" && s.armS >= 0) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(12.dp)).background(Tac.Surface3).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Timer, null, tint = Tac.Warn, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Charging starts in ${s.armS} s", style = MaterialTheme.typography.titleSmall.merge(Num))
            }
        }
        if (s.state != "HOST" && s.targetMv > 0 && s.mode != "monitor") {
            Spacer(Modifier.height(8.dp))
            Text(
                "Target ${Ocv.pct(s.targetMv)} % · ${String.format(Locale.US, "%.2f", s.targetMv / 1000.0)} V/cell",
                style = MaterialTheme.typography.bodySmall.merge(Num), color = Tac.Faint, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            )
        }
        if (s.state == "CELLCV" && s.ichgMa > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Current limited to ${s.ichgMa} mA while the full cell is held",
                style = MaterialTheme.typography.bodySmall.merge(Num), color = Tac.Warn, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            )
        }
        if (s.etaMin >= 0 && charging) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(12.dp)).background(Tac.Surface3).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Timer, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Full in ~${duration(s.etaMin)}", style = MaterialTheme.typography.titleSmall.merge(Num))
            }
        }
    }
}

@Composable
fun LastChargeCard(r: ChargeReport?, fahrenheit: Boolean, openLog: () -> Unit) {
    SectionLabel("Last charge")
    Panel(Modifier.fillMaxWidth().clickable(onClick = openLog)) {
        if (r == null) {
            Text("No charge recorded yet", style = MaterialTheme.typography.titleSmall)
            Text(
                "Put the module on a USB-C PD charger. It charges on its own and writes a report you'll see here the next time you plug in your phone.",
                style = MaterialTheme.typography.bodySmall, color = Tac.Dim,
            )
            return@Panel
        }
        val (label, color) = when (r.complete) {
            true -> "Completed" to Tac.Ok
            false -> "Unplugged before full" to Tac.Warn
            null -> "No result (power cut)" to Tac.Bad
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill(label, color)
            Spacer(Modifier.weight(1f))
            Text("power-up #${r.boot}", style = MaterialTheme.typography.bodySmall, color = Tac.Faint)
        }
        Spacer(Modifier.height(12.dp))
        Row {
            Column(Modifier.weight(1f)) {
                Text("PACK", style = MaterialTheme.typography.labelSmall, color = Tac.Dim)
                Text(
                    "${r.fromMv?.let { String.format(Locale.US, "%.2f", it / 1000.0) } ?: "?"} → ${r.toMv?.let { String.format(Locale.US, "%.2f V", it / 1000.0) } ?: "?"}",
                    style = MaterialTheme.typography.titleMedium.merge(Num),
                )
            }
            Column(Modifier.weight(0.6f)) {
                Text("CHARGED", style = MaterialTheme.typography.labelSmall, color = Tac.Dim)
                Text(r.mAh?.let { "$it mAh" } ?: "—", style = MaterialTheme.typography.titleMedium.merge(Num))
            }
        }
        Spacer(Modifier.height(8.dp))
        LabeledRow("Time on charger", r.minutes?.let { duration(it) } ?: "—")
        LabeledRow("Peak pack temperature", r.peakDc?.let { temp(it, fahrenheit) } ?: "—",
            if ((r.peakDc ?: 0) > 450) Tac.Bad else Tac.Text)
        if (r.faults.isNotEmpty()) LabeledRow("Problems", r.faults.distinct().joinToString(), Tac.Bad)
    }
}

@Composable
private fun LogSummary(events: List<LogEntry>, openLog: () -> Unit) {
    val problems = events.filter { it.severity == Severity.ERROR || it.severity == Severity.WARN }
    val lastCharge = events.lastOrNull { it.kind == EventKind.CHARGE_DONE || it.kind == EventKind.CHARGE_UNPLUG }
    SectionLabel("Event log")
    Panel(Modifier.fillMaxWidth().clickable(onClick = openLog)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (problems.isEmpty()) "No problems recorded" else "${problems.size} problem${if (problems.size > 1) "s" else ""} recorded",
                    style = MaterialTheme.typography.titleSmall, color = if (problems.any { it.severity == Severity.ERROR }) Tac.Bad else Tac.Text,
                )
                Text(
                    lastCharge?.let { "Last charge: ${it.title.lowercase()} (power-up #${it.boot})" } ?: "No charges recorded yet",
                    style = MaterialTheme.typography.bodySmall, color = Tac.Dim,
                )
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Tac.Faint)
        }
        problems.takeLast(3).asReversed().forEach { e ->
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(e.severity.color()))
                Spacer(Modifier.width(10.dp))
                Text(e.title, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text("#${e.boot} ${e.time}", style = MaterialTheme.typography.bodySmall.merge(Num), color = Tac.Faint)
            }
        }
    }
}

@Composable
private fun SpreadBadge(spread: Int) {
    val c = when { spread <= 15 -> Tac.Ok; spread <= 50 -> Tac.Warn; else -> Tac.Bad }
    Pill("Δ $spread mV", c)
}

@Composable
private fun FaultCard(f: Fault) {
    var open by remember { mutableStateOf(true) }
    val c = if (f.blocking) Tac.Bad else Tac.Warn
    Panel(Modifier.fillMaxWidth().clickable { open = !open }, glow = c) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Warning, null, tint = c)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(f.title, style = MaterialTheme.typography.titleSmall, color = c)
                Text(f.detail, style = MaterialTheme.typography.bodySmall, color = Tac.Text)
            }
        }
        AnimatedVisibility(open) {
            Row(Modifier.padding(top = 10.dp).clip(RoundedCornerShape(10.dp)).background(c.copy(alpha = 0.08f)).padding(10.dp)) {
                Text("What to do: ", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = c)
                Text(f.fix, style = MaterialTheme.typography.bodySmall, color = Tac.Text)
            }
        }
    }
}

@Composable
private fun TipOfDay(onOpen: () -> Unit, onHide: () -> Unit) {
    val tip = remember { Tips.ofTheDay() }
    Panel(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lightbulb, null, tint = Tac.Warn, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("TIP OF THE DAY", style = MaterialTheme.typography.labelSmall, color = Tac.Warn, modifier = Modifier.weight(1f))
            IconButton(onClick = onHide, modifier = Modifier.size(28.dp)) { Icon(Icons.Rounded.Close, "Hide tips", tint = Tac.Faint, modifier = Modifier.size(16.dp)) }
        }
        Text(tip.title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(tip.body, style = MaterialTheme.typography.bodySmall, color = Tac.Dim)
    }
}

// ------------------------------------------------------------------ not connected
@Composable
private fun NotConnected(link: Link, onConnect: () -> Unit, onDemo: () -> Unit) {
    val t = rememberInfiniteTransition(label = "nc")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2200), RepeatMode.Restart), label = "p")
    val accent = MaterialTheme.colorScheme.primary
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                for (k in 0..2) {
                    val p = (pulse + k / 3f) % 1f
                    drawCircle(accent.copy(alpha = (1f - p) * 0.35f), radius = size.minDimension / 2 * (0.35f + 0.65f * p), style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
                }
                drawCircle(
                    Brush.radialGradient(listOf(accent.copy(alpha = 0.25f), Color.Transparent), center, size.minDimension / 3),
                    size.minDimension / 3,
                )
                drawLine(Tac.Outline, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
            }
            Box(Modifier.size(84.dp).clip(CircleShape).background(Tac.Surface2), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Usb, null, tint = accent, modifier = Modifier.size(44.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            when (link) { Link.Searching -> "Allow USB access"; is Link.Error -> "Connection problem"; else -> "Plug in the module" },
            style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            when (link) {
                Link.Searching -> "Android is asking if this app may talk to the charger. Tap OK in the dialog."
                is Link.Error -> link.message
                else -> "Connect the charger to this phone with a USB-C cable. The phone powers the module, so you can read every cell without a wall charger."
            },
            style = MaterialTheme.typography.bodyMedium, color = Tac.Dim, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onConnect, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Rounded.Usb, null); Spacer(Modifier.width(8.dp)); Text("Connect", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onDemo, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Tac.Text),
        ) { Icon(Icons.Rounded.Science, null); Spacer(Modifier.width(8.dp)); Text("Try demo mode") }
        Spacer(Modifier.height(28.dp))
        Panel(Modifier.fillMaxWidth()) {
            Text("CHECKLIST", style = MaterialTheme.typography.labelSmall, color = Tac.Dim)
            Spacer(Modifier.height(8.dp))
            listOf(
                "Battery balance plug (white JST-XH) plugged in",
                "Use a USB-C ↔ USB-C data cable (not charge-only)",
                "Phone has USB OTG / host support (most do)",
                "For charging: a USB-C PD charger, 20 W or more",
            ).forEachIndexed { i, txt ->
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(Tac.Surface3), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", style = MaterialTheme.typography.labelSmall, color = accent)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(txt, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
