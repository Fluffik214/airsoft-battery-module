package com.airsoftmodule.charger.ui.screens

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airsoftmodule.charger.data.ChargerViewModel
import com.airsoftmodule.charger.data.ChargeReport
import com.airsoftmodule.charger.data.Sample
import com.airsoftmodule.charger.data.chargeReports
import com.airsoftmodule.charger.ui.components.LabeledRow
import com.airsoftmodule.charger.ui.components.LineChart
import com.airsoftmodule.charger.ui.components.Panel
import com.airsoftmodule.charger.ui.components.SectionLabel
import com.airsoftmodule.charger.ui.components.Series
import com.airsoftmodule.charger.ui.theme.Tac
import java.util.Locale

private enum class Metric(val title: String) { CELLS("Cells"), CURRENT("Current"), PACK("Pack"), SOC("Charge %"), TEMP("Temperature") }
private enum class Window(val label: String, val ms: Long) { M5("5 min", 300_000), M15("15 min", 900_000), H1("1 h", 3_600_000), ALL("All", Long.MAX_VALUE) }

@Composable
fun GraphsScreen(vm: ChargerViewModel) {
    val history by vm.history.collectAsStateWithLifecycle()
    val events by vm.log.collectAsStateWithLifecycle()
    val f by vm.prefs.fahrenheit.state.collectAsStateWithLifecycle()
    var metric by rememberSaveable { mutableStateOf(Metric.CELLS) }
    var window by rememberSaveable { mutableStateOf(Window.M15) }
    val accent = MaterialTheme.colorScheme.primary

    val data: List<Sample> = remember(history, window) {
        val last = history.lastOrNull()?.t ?: 0L
        if (window == Window.ALL) history else history.filter { last - it.t <= window.ms }
    }
    val times = data.map { it.t }
    val tconv: (Int) -> Float = { dc -> if (f) dc / 10f * 9 / 5 + 32 else dc / 10f }
    val unitT = if (f) "°F" else "°C"
    val (series, fmt, span) = when (metric) {
        Metric.CELLS -> Triple(
            (0..2).map { k -> Series("Cell ${k + 1}", Tac.Cell[k], data.map { it.cells[k] / 1000f }) },
            { v: Float -> String.format(Locale.US, "%.2f", v) }, 0.05f,
        )
        Metric.CURRENT -> Triple(
            listOf(Series("Battery", accent, data.map { it.ibatMa / 1000f }), Series("USB", Tac.Cell[0], data.map { it.ibusMa / 1000f })),
            { v: Float -> String.format(Locale.US, "%.2f", v) }, 0.2f,
        )
        Metric.PACK -> Triple(listOf(Series("Pack", accent, data.map { it.packMv / 1000f })), { v: Float -> String.format(Locale.US, "%.2f", v) }, 0.1f)
        Metric.SOC -> Triple(listOf(Series("Charge", accent, data.map { it.soc.toFloat() })), { v: Float -> "${v.toInt()}%" }, 10f)
        Metric.TEMP -> Triple(
            listOf(Series("Pack", Tac.Warn, data.map { tconv(it.packTempDc) }), Series("Charger", Tac.Bad, data.map { tconv(it.chipTempDc) })),
            { v: Float -> String.format(Locale.US, "%.0f", v) }, 5f,
        )
    }
    val unit = when (metric) { Metric.CELLS, Metric.PACK -> "V"; Metric.CURRENT -> "A"; Metric.SOC -> "%"; Metric.TEMP -> unitT }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        ChargeHistory(chargeReports(events))
        SectionLabel("Live (while on this phone)")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric.entries.forEach { m ->
                FilterChip(
                    selected = metric == m, onClick = { metric = m }, label = { Text(m.title) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accent.copy(alpha = 0.18f), selectedLabelColor = accent,
                        containerColor = Tac.Surface, labelColor = Tac.Dim,
                    ),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Panel(Modifier.fillMaxWidth(), padding = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${metric.title} ($unit)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                series.forEach { s ->
                    Box(Modifier.size(8.dp).clip(CircleShape).background(s.color))
                    Spacer(Modifier.width(4.dp))
                    Text(s.name, style = MaterialTheme.typography.labelSmall, color = Tac.Dim)
                    Spacer(Modifier.width(10.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            LineChart(times, series, fmt, Modifier.fillMaxWidth().height(260.dp), minSpan = span)
            Spacer(Modifier.height(4.dp))
            Text("Touch and drag to read values. The phone can't be connected while the module charges, so this shows resting cells; charges are in the history above.", style = MaterialTheme.typography.bodySmall, color = Tac.Faint)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Window.entries.forEach { w ->
                FilterChip(
                    selected = window == w, onClick = { window = w }, label = { Text(w.label) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Tac.Surface3, selectedLabelColor = Tac.Text, containerColor = Tac.Bg, labelColor = Tac.Dim),
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = vm::clearHistory) { Icon(Icons.Rounded.DeleteSweep, "Clear history", tint = Tac.Dim) }
        }

        SectionLabel("Statistics")
        Panel(Modifier.fillMaxWidth()) {
            if (data.isEmpty()) Text("No data yet. Graphs fill up while the module is connected.", color = Tac.Dim, style = MaterialTheme.typography.bodyMedium)
            series.forEach { s ->
                if (s.values.isNotEmpty()) LabeledRow(
                    s.name,
                    "min ${fmt(s.values.min())} · avg ${fmt(s.values.average().toFloat())} · max ${fmt(s.values.max())}",
                    s.color,
                )
            }
            if (data.size > 1) {
                val mins = (data.last().t - data.first().t) / 60000
                LabeledRow("Time span", "$mins min · ${data.size} samples")
            }
        }
        if (metric == Metric.CELLS && data.isNotEmpty()) {
            SectionLabel("Balance trend")
            Panel(Modifier.fillMaxWidth()) {
                val spread = data.map { (it.cells.max() - it.cells.min()).toFloat() }
                LineChart(times, listOf(Series("Spread", Tac.Warn, spread)), { "${it.toInt()} mV" }, Modifier.fillMaxWidth().height(140.dp), minSpan = 20f)
                Text(
                    if (spread.size > 10 && spread.last() < spread.first() - 3) "The cells are getting closer together: balancing is working."
                    else "Spread = highest cell minus lowest cell. Lower is better.",
                    style = MaterialTheme.typography.bodySmall, color = Tac.Dim,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Bar chart of mAh put in per charge, newest on the right, from the module's own charge reports. */
@Composable
private fun ChargeHistory(reports: List<ChargeReport>) {
    SectionLabel("Charge history")
    Panel(Modifier.fillMaxWidth()) {
        if (reports.isEmpty()) {
            Text("No charges recorded yet. Every charge on a PD charger writes a report into the module.", style = MaterialTheme.typography.bodySmall, color = Tac.Dim)
            return@Panel
        }
        val last = reports.takeLast(12)
        val max = (last.maxOf { it.mAh ?: 0 }).coerceAtLeast(100)
        val accent = MaterialTheme.colorScheme.primary
        Row(Modifier.fillMaxWidth().height(130.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            last.forEach { r ->
                val c = when (r.complete) { true -> accent; false -> Tac.Warn; null -> Tac.Bad }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${r.mAh ?: 0}", style = MaterialTheme.typography.labelSmall.merge(com.airsoftmodule.charger.ui.theme.Num), color = Tac.Dim, maxLines = 1)
                    Box(
                        Modifier.fillMaxWidth().height((96f * (r.mAh ?: 0) / max).coerceAtLeast(3f).dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)).background(c),
                    )
                    Text("#${r.boot}", style = MaterialTheme.typography.labelSmall, color = Tac.Faint, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        val done = reports.count { it.complete == true }
        LabeledRow("Charges recorded", "${reports.size} ($done completed)")
        LabeledRow("Average per charge", "${reports.mapNotNull { it.mAh }.average().let { if (it.isNaN()) 0 else it.toInt() }} mAh")
        reports.mapNotNull { it.peakDc }.maxOrNull()?.let { LabeledRow("Hottest charge", String.format(Locale.US, "%.1f °C", it / 10.0)) }
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(accent to "completed", Tac.Warn to "unplugged early", Tac.Bad to "no result").forEach { (c, t) ->
                Box(Modifier.size(8.dp).clip(CircleShape).background(c)); Spacer(Modifier.width(4.dp))
                Text(t, style = MaterialTheme.typography.labelSmall, color = Tac.Dim); Spacer(Modifier.width(10.dp))
            }
        }
    }
}
