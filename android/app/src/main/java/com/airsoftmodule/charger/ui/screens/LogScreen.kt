package com.airsoftmodule.charger.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airsoftmodule.charger.data.ChargerViewModel
import com.airsoftmodule.charger.data.EventKind
import com.airsoftmodule.charger.data.LogEntry
import com.airsoftmodule.charger.data.Severity
import com.airsoftmodule.charger.data.chargeReports
import com.airsoftmodule.charger.ui.components.Panel
import com.airsoftmodule.charger.ui.components.Pill
import com.airsoftmodule.charger.ui.theme.Num
import com.airsoftmodule.charger.ui.theme.Tac

private enum class LogFilter(val label: String) { ALL("All"), ERRORS("Problems"), CHARGES("Charges") }

fun Severity.color(): Color = when (this) {
    Severity.ERROR -> Tac.Bad
    Severity.WARN -> Tac.Warn
    Severity.GOOD -> Tac.Ok
    Severity.INFO -> Tac.Dim
}

private fun LogEntry.icon(): ImageVector = when {
    kind == EventKind.CHARGE_START -> Icons.Rounded.BatteryChargingFull
    severity == Severity.ERROR -> Icons.Rounded.Error
    severity == Severity.WARN -> Icons.Rounded.Warning
    severity == Severity.GOOD -> Icons.Rounded.CheckCircle
    else -> Icons.Rounded.Info
}

@Composable
fun LogScreen(vm: ChargerViewModel, onBack: () -> Unit) {
    val entries by vm.log.collectAsStateWithLifecycle()
    val loading by vm.logLoading.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(LogFilter.ALL) }
    var confirm by remember { mutableStateOf(false) }
    val reports = chargeReports(entries).associateBy { it.boot }
    val shown = entries.filter { it.kind?.isReportPart != true }.filter {
        when (filter) {
            LogFilter.ALL -> true
            LogFilter.ERRORS -> it.severity == Severity.ERROR || it.severity == Severity.WARN
            LogFilter.CHARGES -> it.kind == EventKind.CHARGE_START || it.kind == EventKind.CHARGE_DONE || it.kind == EventKind.CHARGE_UNPLUG || it.kind == EventKind.CHARGE_STOP
        }
    }.asReversed()                                    // newest first
    val groups = shown.groupBy { it.boot }
    val problems = entries.count { it.severity == Severity.ERROR }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Text("Event log", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = vm::refreshLog) { Icon(Icons.Rounded.Refresh, "Refresh", tint = Tac.Dim) }
            IconButton(onClick = { confirm = true }, enabled = entries.isNotEmpty()) { Icon(Icons.Rounded.DeleteSweep, "Clear log", tint = Tac.Dim) }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Panel(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${entries.size} events stored", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Saved in the module's flash, so you see what happened on the charger even with no phone attached. Each problem is stored once per power-up.",
                                style = MaterialTheme.typography.bodySmall, color = Tac.Dim,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        if (problems > 0) Pill("$problems errors", Tac.Bad) else Pill("No errors", Tac.Ok)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LogFilter.entries.forEach { f ->
                        FilterChip(
                            selected = filter == f, onClick = { filter = f }, label = { Text(f.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary, containerColor = Tac.Surface, labelColor = Tac.Dim,
                            ),
                        )
                    }
                }
            }
            if (shown.isEmpty() && !loading) item {
                Text("Nothing here.", color = Tac.Dim, modifier = Modifier.padding(vertical = 24.dp).fillMaxWidth())
            }
            groups.forEach { (boot, list) ->
                item(key = "h$boot") {
                    Row(Modifier.padding(top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("POWER-UP #$boot", style = MaterialTheme.typography.labelSmall, color = Tac.Dim)
                        if (boot == vm.currentBoot) {
                            Spacer(Modifier.width(8.dp))
                            Pill("now", MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                items(list, key = { "${it.boot}-${it.seconds}-${it.code}-${it.value}" }) { e ->
                    val r = reports[e.boot]
                    val extra = if (r != null && (e.kind == EventKind.CHARGE_DONE || e.kind == EventKind.CHARGE_UNPLUG))
                        listOfNotNull(r.mAh?.let { "+$it mAh" }, r.minutes?.let { "$it min" }, r.peakDc?.let { "peak %.1f °C".format(it / 10.0) }).joinToString(" · ")
                    else ""
                    LogRow(e, extra)
                }
            }
        }
    }

    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Clear the event log?") },
        text = { Text("All stored events are erased from the module.", color = Tac.Dim) },
        confirmButton = { Button(onClick = { vm.clearLog(); confirm = false }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        containerColor = Tac.Surface2,
    )
}

@Composable
private fun LogRow(e: LogEntry, extra: String = "") {
    val c = e.severity.color()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Tac.Surface).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(c.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(e.icon(), null, tint = c, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, style = MaterialTheme.typography.titleSmall, color = if (e.severity == Severity.INFO) Tac.Text else c)
            if (e.detail.isNotEmpty()) Text(e.detail, style = MaterialTheme.typography.bodySmall.merge(Num), color = Tac.Dim)
            if (extra.isNotEmpty()) Text(extra, style = MaterialTheme.typography.bodySmall.merge(Num), color = Tac.Text)
        }
        Text(e.time, style = MaterialTheme.typography.bodySmall.merge(Num), color = Tac.Faint)
    }
    Spacer(Modifier.height(0.dp))
}
