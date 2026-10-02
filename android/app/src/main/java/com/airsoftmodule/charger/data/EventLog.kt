package com.airsoftmodule.charger.data

import java.util.Locale

/** One entry of the module's flash event log (firmware/src/log.c). */
data class LogEntry(val boot: Int, val seconds: Int, val code: Int, val value: Int) {
    val kind get() = EventKind.of(code)
    val title get() = kind?.title ?: "Unknown event #$code"
    val severity get() = kind?.severity ?: Severity.ERROR
    val detail get() = kind?.format?.invoke(value) ?: ""
    val time: String get() = String.format(Locale.US, "+%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
}

enum class Severity { INFO, GOOD, WARN, ERROR }

/**
 * One charge, rebuilt from the log. With a single USB-C port the phone can never watch a charge,
 * so the module writes this report when it is unplugged from the charger.
 */
data class ChargeReport(
    val boot: Int,
    val complete: Boolean?,          // null = no result written (power lost hard)
    val fromMv: Int?,
    val toMv: Int?,
    val mAh: Int?,
    val minutes: Int?,
    val peakDc: Int?,
    val faults: List<String>,
)

fun chargeReports(log: List<LogEntry>): List<ChargeReport> =
    log.groupBy { it.boot }.mapNotNull { (boot, list) ->
        if (list.none { it.kind == EventKind.CHARGE_START }) return@mapNotNull null
        fun value(k: EventKind) = list.lastOrNull { it.kind == k }?.value
        val end = list.lastOrNull { it.kind == EventKind.CHARGE_DONE || it.kind == EventKind.CHARGE_UNPLUG }
        ChargeReport(
            boot = boot,
            complete = end?.let { it.kind == EventKind.CHARGE_DONE },
            fromMv = value(EventKind.REPORT_FROM) ?: value(EventKind.CHARGE_START),
            toMv = end?.value,
            mAh = value(EventKind.REPORT_MAH),
            minutes = value(EventKind.REPORT_MIN),
            peakDc = value(EventKind.REPORT_PEAK),
            faults = list.filter { it.code < 16 && it.severity != Severity.INFO }.map { it.title },
        )
    }.sortedBy { it.boot }

private fun v(mv: Int) = String.format(Locale.US, "%.3f V", mv / 1000.0)
private fun t(dc: Int) = String.format(Locale.US, "%.1f °C", dc.toShort() / 10.0)

/** Event codes: 0..15 = fault bits (same as Fault), 32+ = events. Mirrors firmware/src/log.h. */
enum class EventKind(val code: Int, val title: String, val severity: Severity, val format: (Int) -> String) {
    NO_BATT(0, "No battery detected", Severity.WARN, { "pack ${v(it)}" }),
    CELL_OV(1, "Cell over-voltage", Severity.ERROR, { "highest cell ${v(it)}" }),
    CELL_UV(2, "Cell deeply discharged", Severity.ERROR, { "lowest cell ${v(it)}" }),
    IMBALANCE(3, "Cells out of balance", Severity.WARN, { "spread $it mV" }),
    HOT(4, "Over-temperature", Severity.ERROR, { t(it) }),
    COLD(5, "Too cold to charge", Severity.WARN, { t(it) }),
    CHARGER(6, "Charger IC fault", Severity.ERROR, { "FAULT0 0x%02X · FAULT1 0x%02X".format(it shr 8, it and 0xFF) }),
    MONITOR(7, "Cell monitor offline", Severity.ERROR, { "" }),
    CHARGER_COMM(9, "Charger IC not answering", Severity.ERROR, { "" }),
    OVERVOLT(10, "USB over-voltage, charging cut", Severity.ERROR, { "USB ${String.format(Locale.US, "%.2f V", it / 1000.0)}" }),
    CHARGE_START(32, "Charging started", Severity.INFO, { "pack ${v(it)}" }),
    CHARGE_DONE(33, "Charge complete", Severity.GOOD, { "pack ${v(it)}" }),
    WATCHDOG(34, "Firmware restarted by watchdog", Severity.ERROR, { "the MCU hung and was reset" }),
    BMS_BOOT(35, "Cell monitor failed to start", Severity.ERROR, { "check the balance lead" }),
    CHARGE_STOP(36, "Charging stopped by a fault", Severity.ERROR, { bits -> Fault.entries.filter { bits and it.bit != 0 }.joinToString { it.title } }),
    CLEARED(37, "Log cleared", Severity.INFO, { "" }),
    CHARGE_UNPLUG(38, "Unplugged before full", Severity.WARN, { "pack ${v(it)}" }),
    REPORT_MAH(39, "Charged", Severity.INFO, { "$it mAh" }),
    REPORT_MIN(40, "Duration", Severity.INFO, { "$it min" }),
    REPORT_PEAK(41, "Peak pack temperature", Severity.INFO, { t(it) }),
    REPORT_FROM(42, "Started at", Severity.INFO, { "pack ${v(it)}" });

    /** report parts are shown folded into the charge result row */
    val isReportPart get() = code in 39..42

    companion object {
        private val byCode = entries.associateBy { it.code }
        fun of(code: Int) = byCode[code]
    }
}
