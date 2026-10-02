package com.airsoftmodule.charger.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.UsbOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airsoftmodule.charger.BuildConfig
import com.airsoftmodule.charger.data.ChargerViewModel
import com.airsoftmodule.charger.data.ConfigKey
import com.airsoftmodule.charger.data.DevConfig
import com.airsoftmodule.charger.data.Link
import com.airsoftmodule.charger.ui.components.LabeledRow
import com.airsoftmodule.charger.ui.components.Panel
import com.airsoftmodule.charger.ui.components.SectionLabel
import com.airsoftmodule.charger.ui.theme.Accents
import com.airsoftmodule.charger.ui.theme.Num
import com.airsoftmodule.charger.ui.theme.Tac
import java.util.Locale
import kotlin.math.roundToInt

private data class Preset(val name: String, val desc: String, val values: (cap: Int) -> Map<String, Int>)

private val presets = listOf(
    Preset("Game day", "4.20 V · slow 0.5C / fast 1C") { cap -> mapOf("vcell" to 4200, "ichs" to snap(cap / 2, 3000), "ichg" to snap(cap, 3000)) },
    Preset("Quick top-up", "4.20 V · slow 1C / fast 1.5C") { cap -> mapOf("vcell" to 4200, "ichs" to snap(cap, 3000), "ichg" to snap(cap * 3 / 2, 3000)) },
    Preset("Long life", "4.10 V · slow 0.5C / fast 0.7C") { cap -> mapOf("vcell" to 4100, "ichs" to snap(cap / 2, 3000), "ichg" to snap(cap * 7 / 10, 3000)) },
    Preset("Gentle", "4.15 V · slow 0.3C / fast 0.5C") { cap -> mapOf("vcell" to 4150, "ichs" to snap(cap * 3 / 10, 3000), "ichg" to snap(cap / 2, 3000), "tmax" to 40) },
)

private fun snap(ma: Int, max: Int) = ((ma / 50) * 50).coerceIn(100, max)

@Composable
fun SettingsScreen(vm: ChargerViewModel, openConsole: () -> Unit, openLog: () -> Unit) {
    val link by vm.link.collectAsStateWithLifecycle()
    val device by vm.config.collectAsStateWithLifecycle()
    val info by vm.info.collectAsStateWithLifecycle()
    val online = link is Link.Connected || link is Link.Demo
    val draft = remember { mutableStateMapOf<String, Int>() }
    val cfg = device ?: DevConfig.DEFAULT
    // fresh values from the module replace untouched fields
    LaunchedEffect(device) { device?.values?.forEach { (k, v) -> if (k !in draft || draft[k] == v) draft.remove(k) } }
    fun value(k: ConfigKey) = draft[k.key] ?: cfg[k.key]
    fun set(k: ConfigKey, v: Int) { if (v == cfg[k.key]) draft.remove(k.key) else draft[k.key] = v }
    val changes = draft.filter { (k, v) -> cfg[k] != v }
    var confirm by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            if (!online) Panel(Modifier.fillMaxWidth(), glow = Tac.Warn) {
                Text("Module not connected", style = MaterialTheme.typography.titleSmall, color = Tac.Warn)
                Text("Charger settings show their defaults and are read-only until you connect (or start demo mode).", style = MaterialTheme.typography.bodySmall, color = Tac.Dim)
            }

            SectionLabel("Quick presets")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                presets.forEach { p ->
                    val vals = p.values(value(ConfigKey.CAPACITY))
                    val active = vals.all { (k, v) -> (draft[k] ?: cfg[k]) == v }
                    Column(
                        Modifier.width(150.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Tac.Surface)
                            .border(1.dp, if (active) MaterialTheme.colorScheme.primary else Tac.Outline, RoundedCornerShape(16.dp))
                            .clickable(enabled = online) { vals.forEach { (k, v) -> set(ConfigKey.byKey.getValue(k), v) } }
                            .padding(12.dp),
                    ) {
                        Text(p.name, style = MaterialTheme.typography.titleSmall, color = if (active) MaterialTheme.colorScheme.primary else Tac.Text)
                        Text(p.desc, style = MaterialTheme.typography.bodySmall, color = Tac.Dim)
                    }
                }
            }

            SectionLabel("Charging")
            Panel(Modifier.fillMaxWidth()) {
                val cap = value(ConfigKey.CAPACITY)
                SliderSetting("Full charge voltage", "Per cell. 4.20 V = full capacity, lower = longer pack life.", ConfigKey.VCELL, value(ConfigKey.VCELL), online,
                    fmt = { String.format(Locale.US, "%.2f V", it / 1000.0) }, extra = { "pack ${String.format(Locale.US, "%.2f", it * 3 / 1000.0)} V" }) { set(ConfigKey.VCELL, it) }
                val fastNow = vm.status.collectAsStateWithLifecycle().value?.fast
                if (fastNow != null) Text(
                    if (fastNow) "JP2 is bridged: the module uses the FAST current." else "JP2 is open: the module uses the SLOW current. Bridge JP2 with solder for fast charging.",
                    style = MaterialTheme.typography.bodySmall, color = if (fastNow) Tac.Warn else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                SliderSetting("Slow charge current", "Used while JP2 is open (default). Gentle and cool, good for a pack that lives in the stock.", ConfigKey.ICHG_SLOW, value(ConfigKey.ICHG_SLOW), online,
                    fmt = { "$it mA" }, extra = { String.format(Locale.US, "%.1fC", it.toDouble() / cap) },
                    warn = { it > cap * 2 }) { set(ConfigKey.ICHG_SLOW, it) }
                SliderSetting("Fast charge current", "Used when JP2 is bridged with solder. Higher is faster but warmer. 1C is the sweet spot.", ConfigKey.ICHG, value(ConfigKey.ICHG), online,
                    fmt = { "$it mA" }, extra = { String.format(Locale.US, "%.1fC", it.toDouble() / cap) },
                    warn = { it > cap * 2 }) { set(ConfigKey.ICHG, it) }
                SliderSetting("USB input limit", "Max current drawn from the USB charger. Lower it for weak power banks.", ConfigKey.IIN, value(ConfigKey.IIN), online,
                    fmt = { "$it mA" }, extra = { "${(it * 15 / 1000.0).roundToInt()} W at 15 V" }) { set(ConfigKey.IIN, it) }
                SliderSetting("Charge start delay", "USB must stay between the minimum and 17 V this long before charging starts.", ConfigKey.WAIT, value(ConfigKey.WAIT), online,
                    fmt = { if (it < 60) "$it s" else String.format(Locale.US, "%d:%02d min", it / 60, it % 60) }) { set(ConfigKey.WAIT, it) }
                SliderSetting("Minimum charging voltage", "Below this USB voltage the module only monitors (phones give 5 V). The upper limit is fixed at 17 V.", ConfigKey.MIN_VIN, value(ConfigKey.MIN_VIN), online,
                    fmt = { String.format(Locale.US, "%.1f V", it / 1000.0) }, warn = { it < 11000 }, last = true) { set(ConfigKey.MIN_VIN, it) }
            }

            SectionLabel("Battery pack")
            Panel(Modifier.fillMaxWidth()) {
                SliderSetting("Capacity", "Used for the time-to-full estimate and C-rate.", ConfigKey.CAPACITY, value(ConfigKey.CAPACITY), online, fmt = { "$it mAh" }) { set(ConfigKey.CAPACITY, it) }
                SliderSetting("Storage voltage", "Per cell, used by Storage mode. 3.80 V is ideal.", ConfigKey.STORAGE, value(ConfigKey.STORAGE), online,
                    fmt = { String.format(Locale.US, "%.2f V", it / 1000.0) }, last = true) { set(ConfigKey.STORAGE, it) }
            }

            SectionLabel("Balancing")
            Panel(Modifier.fillMaxWidth()) {
                ToggleSetting("Balance cells", "Bleed high cells to even out the pack.", value(ConfigKey.BAL_EN) == 1, online) { set(ConfigKey.BAL_EN, if (it) 1 else 0) }
                SliderSetting("Start threshold", "Balance when cells differ by more than this.", ConfigKey.BAL_TH, value(ConfigKey.BAL_TH), online && value(ConfigKey.BAL_EN) == 1, fmt = { "$it mV" }) { set(ConfigKey.BAL_TH, it) }
                SliderSetting("Only above", "Cells below this voltage are never bled.", ConfigKey.BAL_MIN, value(ConfigKey.BAL_MIN), online && value(ConfigKey.BAL_EN) == 1,
                    fmt = { String.format(Locale.US, "%.2f V", it / 1000.0) }) { set(ConfigKey.BAL_MIN, it) }
                SliderSetting("Keep balancing after unplug", "Runs from the pack, then switches off. 0 = off right away.", ConfigKey.BAL_UNPLUG, value(ConfigKey.BAL_UNPLUG), online && value(ConfigKey.BAL_EN) == 1,
                    fmt = { if (it == 0) "off" else "$it min" }, last = true) { set(ConfigKey.BAL_UNPLUG, it) }
            }

            SectionLabel("Safety limits")
            Panel(Modifier.fillMaxWidth()) {
                SliderSetting("Max cell difference", "Refuse to charge a pack this far out of balance.", ConfigKey.IMB_MAX, value(ConfigKey.IMB_MAX), online, fmt = { "$it mV" }, warn = { it > 400 }) { set(ConfigKey.IMB_MAX, it) }
                SliderSetting("Max temperature", "Charging pauses above this pack temperature.", ConfigKey.TMAX, value(ConfigKey.TMAX), online, fmt = { "$it °C" }, warn = { it > 50 }, last = true) { set(ConfigKey.TMAX, it) }
            }

            SectionLabel("Module")
            Panel(Modifier.fillMaxWidth()) {
                SliderSetting("LED brightness", "The status LED. 0 = dark (stealth).", ConfigKey.LED, value(ConfigKey.LED), online, fmt = { if (it == 0) "off" else "$it %" }) { set(ConfigKey.LED, it) }
                SliderSetting("Report rate", "How often the module sends data. Faster = smoother graphs.", ConfigKey.RATE, value(ConfigKey.RATE), online,
                    fmt = { String.format(Locale.US, "%.1f s", it / 1000.0) }, last = true) { set(ConfigKey.RATE, it) }
            }

            AppSettings(vm)

            SectionLabel("Advanced")
            Panel(Modifier.fillMaxWidth(), padding = 4.dp) {
                ActionRow(Icons.Rounded.History, "Event log", "Errors and charges recorded by the module", onClick = openLog)
                ActionRow(Icons.Rounded.Terminal, "Console", "Send raw commands and see the protocol", onClick = openConsole)
                ActionRow(Icons.Rounded.Restore, "Factory defaults", "Reset all charger settings", enabled = online) { confirm = "defaults" }
                ActionRow(Icons.Rounded.RestartAlt, "Restart module", "Reboots the MCU (charging pauses for a moment)", enabled = online) { vm.send("reboot") }
                ActionRow(Icons.Rounded.SystemUpdateAlt, "Bootloader (DFU)", "For firmware updates from a PC", enabled = link is Link.Connected, tint = Tac.Warn) { confirm = "dfu" }
                if (link is Link.Connected) ActionRow(Icons.Rounded.UsbOff, "Disconnect", "Release the USB device", onClick = vm::disconnect)
                else if (link is Link.Demo) ActionRow(Icons.Rounded.Science, "Exit demo mode", "Back to the real module", onClick = vm::stopDemo)
                else ActionRow(Icons.Rounded.Usb, "Connect", "Look for the module on USB", onClick = vm::connect)
            }

            SectionLabel("About")
            Panel(Modifier.fillMaxWidth()) {
                LabeledRow("App", BuildConfig.VERSION_NAME)
                LabeledRow("Firmware", info?.fw ?: "—")
                LabeledRow("Hardware", info?.hw ?: "—")
                LabeledRow("Device ID", info?.uid ?: "—")
                LabeledRow("Charger IC (BQ25798)", info?.let { if (it.chargerOk) "OK" else "not found" } ?: "—", if (info?.chargerOk == false) Tac.Bad else Tac.Text)
                LabeledRow("Cell monitor (BQ76920)", info?.let { if (it.monitorOk) "OK" else "not found" } ?: "—", if (info?.monitorOk == false) Tac.Bad else Tac.Text)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Memory, null, tint = Tac.Faint, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("STM32F042 · BQ25798 buck-boost · BQ76920 monitor · CH224K PD", style = MaterialTheme.typography.bodySmall, color = Tac.Faint)
                }
            }
            Spacer(Modifier.height(if (changes.isNotEmpty()) 110.dp else 24.dp))
        }

        // pending changes bar
        AnimatedVisibility(
            changes.isNotEmpty() && online, Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(),
        ) {
            Row(
                Modifier.padding(12.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Tac.Surface3)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(18.dp)).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${changes.size} unsaved change${if (changes.size > 1) "s" else ""}", style = MaterialTheme.typography.titleSmall)
                    Text("Stored in the module's memory", style = MaterialTheme.typography.bodySmall, color = Tac.Dim)
                }
                TextButton(onClick = { draft.clear() }) { Text("Discard", color = Tac.Dim) }
                Button(onClick = { vm.applyConfig(changes) }, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Apply", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    when (confirm) {
        "dfu" -> ConfirmDialog(
            "Enter bootloader?",
            "The module stops charging and shows up as \"STM32 BOOTLOADER\" on USB. Flash new firmware from a PC with STM32CubeProgrammer or dfu-util. Unplug and re-plug to return to normal.",
            "Enter DFU", onDismiss = { confirm = null },
        ) { vm.send("dfu"); confirm = null }
        "defaults" -> ConfirmDialog(
            "Reset to factory defaults?", "All charger settings go back to the defaults (4.20 V, 1.4 A, 1450 mAh…) and are saved.",
            "Reset", onDismiss = { confirm = null },
        ) { vm.factoryDefaults(); draft.clear(); confirm = null }
    }
}

@Composable
private fun AppSettings(vm: ChargerViewModel) {
    val accent by vm.prefs.accent.state.collectAsStateWithLifecycle()
    val fahr by vm.prefs.fahrenheit.state.collectAsStateWithLifecycle()
    val keep by vm.prefs.keepScreenOn.state.collectAsStateWithLifecycle()
    val vib by vm.prefs.vibrate.state.collectAsStateWithLifecycle()
    val tip by vm.prefs.showTipOfDay.state.collectAsStateWithLifecycle()
    val demo by vm.prefs.demoOnStart.state.collectAsStateWithLifecycle()
    SectionLabel("App")
    Panel(Modifier.fillMaxWidth()) {
        Text("Accent", style = MaterialTheme.typography.titleSmall)
        Text(Accents[accent.coerceIn(0, Accents.lastIndex)].name, style = MaterialTheme.typography.bodySmall, color = Tac.Dim)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Accents.forEachIndexed { i, a ->
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(a.color)
                        .border(3.dp, if (i == accent) Tac.Text else Color.Transparent, CircleShape)
                        .clickable { vm.prefs.accent.set(i) },
                    contentAlignment = Alignment.Center,
                ) { if (i == accent) Icon(Icons.Rounded.Check, null, tint = a.onColor, modifier = Modifier.size(18.dp)) }
            }
        }
        Spacer(Modifier.height(12.dp))
        ToggleSetting("Fahrenheit", "Show temperatures in °F", fahr, true) { vm.prefs.fahrenheit.set(it) }
        ToggleSetting("Keep screen on", "While the app is open", keep, true) { vm.prefs.keepScreenOn.set(it) }
        ToggleSetting("Vibrate", "When a fault shows up while connected", vib, true) { vm.prefs.vibrate.set(it) }
        ToggleSetting("Tip of the day", "On the status screen", tip, true) { vm.prefs.showTipOfDay.set(it) }
        ToggleSetting("Start in demo mode", "Handy for showing the app off", demo, true) { vm.prefs.demoOnStart.set(it) }
    }
}

// ------------------------------------------------------------------ building blocks
@Composable
private fun SliderSetting(
    title: String, desc: String, key: ConfigKey, value: Int, enabled: Boolean,
    fmt: (Int) -> String, extra: ((Int) -> String)? = null, warn: (Int) -> Boolean = { false }, last: Boolean = false,
    onChange: (Int) -> Unit,
) {
    val w = warn(value)
    val color = if (w) Tac.Warn else MaterialTheme.colorScheme.primary
    Column(Modifier.padding(bottom = if (last) 0.dp else 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = if (enabled) Tac.Text else Tac.Faint)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = Tac.Dim)
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(fmt(value), style = MaterialTheme.typography.titleMedium.merge(Num), color = if (enabled) color else Tac.Faint)
                if (extra != null) Text(extra(value), style = MaterialTheme.typography.bodySmall.merge(Num), color = Tac.Dim)
            }
        }
        val steps = ((key.max - key.min) / key.step - 1).coerceIn(0, 200)
        Slider(
            value = value.toFloat(), enabled = enabled,
            onValueChange = { v -> onChange((((v - key.min) / key.step).roundToInt() * key.step + key.min).coerceIn(key.min, key.max)) },
            valueRange = key.min.toFloat()..key.max.toFloat(),
            steps = if ((key.max - key.min) / key.step <= 60) steps else 0,
            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color, inactiveTrackColor = Tac.Surface3, activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
        )
    }
}

@Composable
private fun ToggleSetting(title: String, desc: String, on: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!on) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (enabled) Tac.Text else Tac.Faint)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = Tac.Dim)
        }
        Switch(
            checked = on, onCheckedChange = onChange, enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary, uncheckedTrackColor = Tac.Surface3, uncheckedBorderColor = Tac.Outline),
        )
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, desc: String, enabled: Boolean = true, tint: Color = MaterialTheme.colorScheme.primary, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(enabled = enabled, onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Tac.Surface3), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (enabled) tint else Tac.Faint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (enabled) Tac.Text else Tac.Faint)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = Tac.Dim)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Tac.Faint)
    }
}

@Composable
private fun ConfirmDialog(title: String, text: String, action: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Info, null, tint = Tac.Warn) },
        title = { Text(title) },
        text = { Text(text, color = Tac.Dim) },
        confirmButton = { Button(onClick = onConfirm) { Text(action) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = Tac.Surface2,
    )
}
