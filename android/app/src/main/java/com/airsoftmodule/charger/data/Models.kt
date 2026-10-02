package com.airsoftmodule.charger.data

import org.json.JSONObject

/** One status report from the module (all integers, same units as the firmware). */
data class Status(
    val state: String = "BOOT",
    val mode: String = "charge",
    val source: String = "NONE",
    val vbusMv: Int = 0,
    val ibusMa: Int = 0,
    val packMv: Int = 0,
    val ibatMa: Int = 0,
    val cellMv: List<Int> = listOf(0, 0, 0),
    val cellPct: List<Int> = listOf(0, 0, 0),
    val soc: Int = 0,
    val packTempDc: Int = 0,
    val chipTempDc: Int = 0,
    val balanceMask: Int = 0,
    val faults: Int = 0,
    val charging: Boolean = false,
    val etaMin: Int = -1,
    val uptimeS: Int = 0,
    /** a phone/PC is connected: the module will not charge in this power cycle */
    val host: Boolean = false,
    /** seconds until the module starts charging (no phone seen yet), -1 if not waiting */
    val armS: Int = -1,
    /** per-cell voltage the current plan charges to */
    val targetMv: Int = 0,
    /** entries in the module's event log */
    val logCount: Int = 0,
    /** charge-current limit right now (lowered while a full cell is held), 0 = not charging */
    val ichgMa: Int = 0,
    /** every cell reached the target */
    val full: Boolean = false,
    /** JP2 solder jumper bridged: fast charge current is used */
    val fast: Boolean = false,
) {
    /** a cell counts as full when it is within 10 mV of the target */
    fun cellFull(i: Int) = targetMv > 0 && cellMv[i] >= targetMv - 10
    val spreadMv get() = (cellMv.maxOrNull() ?: 0) - (cellMv.minOrNull() ?: 0)
    val inputW get() = vbusMv / 1000.0 * ibusMa / 1000.0
    val chargeW get() = packMv / 1000.0 * ibatMa / 1000.0
    fun balancing(cell: Int) = balanceMask and (1 shl cell) != 0
    val activeFaults get() = Fault.entries.filter { faults and it.bit != 0 }

    companion object {
        fun parse(o: JSONObject) = Status(
            state = o.optString("st", "BOOT"),
            mode = o.optString("md", "charge"),
            source = o.optString("src", "NONE"),
            vbusMv = o.optInt("vbus"),
            ibusMa = o.optInt("ibus"),
            packMv = o.optInt("pack"),
            ibatMa = o.optInt("ibat"),
            cellMv = o.optJSONArray("c")?.let { a -> List(3) { a.optInt(it) } } ?: listOf(0, 0, 0),
            cellPct = o.optJSONArray("p")?.let { a -> List(3) { a.optInt(it) } } ?: listOf(0, 0, 0),
            soc = o.optInt("soc"),
            packTempDc = o.optInt("tp"),
            chipTempDc = o.optInt("tc"),
            balanceMask = o.optInt("bal"),
            faults = o.optInt("flt"),
            charging = o.optInt("chg") != 0,
            etaMin = o.optInt("eta", -1),
            uptimeS = o.optInt("up"),
            host = o.optInt("host") != 0,
            armS = o.optInt("arm", -1),
            targetMv = o.optInt("tgt"),
            logCount = o.optInt("logn"),
            ichgMa = o.optInt("ich"),
            full = o.optInt("full") != 0,
            fast = o.optInt("fast") != 0,
        )
    }
}

/** Settings stored in the module's flash. Keys and ranges mirror firmware/src/config.c. */
data class DevConfig(val values: Map<String, Int>) {
    operator fun get(k: String) = values[k] ?: (ConfigKey.byKey[k]?.default ?: 0)

    companion object {
        fun parse(o: JSONObject) = DevConfig(ConfigKey.entries.associate { it.key to o.optInt(it.key, it.default) })
        val DEFAULT = DevConfig(ConfigKey.entries.associate { it.key to it.default })
    }
}

data class DevInfo(val fw: String, val hw: String, val uid: String, val chargerOk: Boolean, val monitorOk: Boolean) {
    companion object {
        fun parse(o: JSONObject) = DevInfo(
            o.optString("fw"), o.optString("hw"), o.optString("uid"), o.optInt("chg") != 0, o.optInt("bms") != 0,
        )
    }
}

enum class ConfigKey(val key: String, val min: Int, val max: Int, val step: Int, val default: Int) {
    VCELL("vcell", 4000, 4200, 10, 4200),
    ICHG("ichg", 100, 3000, 50, 1400),
    IIN("iin", 500, 3000, 100, 2000),
    STORAGE("stor", 3700, 3900, 10, 3800),
    BAL_MIN("balmin", 3500, 4150, 10, 3900),
    MIN_VIN("minvin", 9000, 15000, 500, 12000),
    IMB_MAX("imb", 50, 500, 10, 300),
    CAPACITY("cap", 200, 10000, 50, 1450),
    RATE("rate", 200, 5000, 100, 500),
    BAL_TH("balth", 5, 60, 1, 15),
    TMAX("tmax", 35, 60, 1, 45),
    LED("led", 0, 100, 5, 40),
    BAL_EN("bal", 0, 1, 1, 1),
    MODE("mode", 0, 2, 1, 0),
    BAL_UNPLUG("bun", 0, 60, 1, 0),
    TARGET("tgt", 50, 100, 5, 100),
    WAIT("wait", 1, 600, 1, 5),
    ICHG_SLOW("ichs", 100, 3000, 50, 700);

    companion object { val byKey = entries.associateBy { it.key } }
}

enum class Fault(val bit: Int, val title: String, val detail: String, val fix: String, val blocking: Boolean = true) {
    NO_BATT(1 shl 0, "No battery", "The pack voltage is too low to be a connected 3S pack.",
        "Plug in both the Deans lead and the balance lead."),
    CELL_OV(1 shl 1, "Cell over-voltage", "A cell is above the safe maximum.",
        "Charging is stopped. If it stays, the cell may be damaged: measure it with a multimeter."),
    CELL_UV(1 shl 2, "Cell deeply discharged", "A cell is below 2.5 V, so charging is refused.",
        "Over-discharged LiPo cells can be unsafe. Retire the pack, or recover it carefully with a hobby charger outdoors."),
    IMBALANCE(1 shl 3, "Cells out of balance", "The difference between cells is above your limit.",
        "Let the module balance in Monitor mode, or raise the limit in Settings if you know the pack is fine."),
    HOT(1 shl 4, "Too hot", "The pack or the charger chip is above the temperature limit.",
        "Take the module out of the stock tube or lower the charge current. Charging resumes when it cools down."),
    COLD(1 shl 5, "Too cold", "The pack is below 0 °C. Charging a cold LiPo plates lithium.",
        "Warm the pack to room temperature first."),
    CHARGER(1 shl 6, "Charger fault", "The charger chip reported a fault (input, battery or thermal).",
        "Re-plug the USB cable. If it keeps coming back, check the charger with a different power supply."),
    MONITOR(1 shl 7, "Cell monitor offline", "The cell monitor chip is not answering.",
        "Check the balance lead. The monitor runs from the pack, so it needs the battery plugged in."),
    LOW_SOURCE(1 shl 8, "Below 12 V: monitor only", "This source gives less than 12 V (a phone or a 5 V charger), so the module only reports.",
        "That's expected on a phone. To charge, use a USB-C PD charger that supplies 15 V (≥ 20 W).", blocking = false),
    CHARGER_COMM(1 shl 9, "Charger offline", "The charger chip is not answering on I²C.",
        "Re-plug the module. If it persists, inspect U2 for solder bridges."),
    OVERVOLT(1 shl 10, "USB over-voltage", "The USB voltage went above 17 V, so charging was cut instantly.",
        "Use a different USB-C PD charger. A healthy one gives 15 V (max ~15.75 V)."),
}

/** Human-friendly description of the state names the firmware reports. */
object States {
    fun label(st: String) = when (st) {
        "UNPLUGGED" -> "On battery"
        "MONITOR" -> "Monitoring"
        "WAIT" -> "Waiting"
        "PRECHARGE" -> "Pre-charge"
        "CC" -> "Charging · CC"
        "CV" -> "Charging · CV"
        "TOPOFF" -> "Topping off"
        "DONE" -> "Fully charged"
        "BALANCE" -> "Balancing"
        "STORAGE" -> "Storage charge"
        "BLEED" -> "Discharging to storage"
        "FAULT" -> "Fault"
        "HOST" -> "Connected · 5 V"
        "ARMING" -> "Starting soon"
        "CELLCV" -> "Balance charging"
        else -> "Starting…"
    }

    fun explain(st: String) = when (st) {
        "UNPLUGGED" -> "USB power is gone. The module will finish up and switch itself off."
        "MONITOR" -> "Reading the cells only. Nothing is charging."
        "WAIT" -> "The charger is waiting to start."
        "PRECHARGE" -> "The pack is very low, so it is charged gently first."
        "CC" -> "Constant current: the bulk of the charge goes in here."
        "CV" -> "Constant voltage: current tapers off as the pack fills up."
        "TOPOFF" -> "Final top-off phase."
        "DONE" -> "The pack is full. You can unplug."
        "BALANCE" -> "Bleeding the highest cell to match the others."
        "STORAGE" -> "Charging to the storage voltage."
        "BLEED" -> "Above storage voltage: slowly bleeding the cells down."
        "FAULT" -> "Something needs your attention. See below."
        "HOST" -> "Connected to this phone. A phone only gives 5 V, so the module just reports and never charges from it."
        "ARMING" -> "12–17 V detected. Charging starts when the countdown ends."
        "CELLCV" -> "A cell is full: it's held there and bled while the current is turned down, so the other cells can catch up."
        else -> ""
    }

    fun isCharging(st: String) = st in setOf("PRECHARGE", "CC", "CV", "TOPOFF", "STORAGE", "CELLCV")
}

data class Sample(
    val t: Long,
    val cells: List<Int>,
    val packMv: Int,
    val ibatMa: Int,
    val ibusMa: Int,
    val packTempDc: Int,
    val chipTempDc: Int,
    val soc: Int,
)

data class ConsoleLine(val t: Long, val out: Boolean, val text: String)

sealed interface Link {
    data object Disconnected : Link
    data object Searching : Link
    data class Connected(val name: String) : Link
    data object Demo : Link
    data class Error(val message: String) : Link
}

/** LiPo open-circuit voltage curve, same table as firmware/src/soc.c */
object Ocv {
    private val mv = intArrayOf(3300, 3500, 3600, 3700, 3750, 3790, 3830, 3870, 3920, 3980, 4060, 4110, 4150, 4200)
    private val pc = intArrayOf(0, 3, 6, 15, 25, 35, 45, 55, 65, 75, 85, 92, 96, 100)

    fun pct(v: Int): Int {
        if (v <= mv[0]) return 0
        if (v >= mv.last()) return 100
        for (i in 1 until mv.size) if (v <= mv[i]) return pc[i - 1] + (pc[i] - pc[i - 1]) * (v - mv[i - 1]) / (mv[i] - mv[i - 1])
        return 100
    }

    fun mv(p: Int): Int {
        if (p <= pc[0]) return mv[0]
        if (p >= pc.last()) return mv.last()
        for (i in 1 until pc.size) if (p <= pc[i]) return mv[i - 1] + (mv[i] - mv[i - 1]) * (p - pc[i - 1]) / (pc[i] - pc[i - 1])
        return mv.last()
    }

    /** per-cell charge target for a plan (mirrors target_mv() in firmware/src/app.c) */
    fun target(cfg: DevConfig): Int = when (cfg["mode"]) {
        1 -> cfg["stor"]
        else -> if (cfg["tgt"] >= 100) cfg["vcell"] else minOf(mv(cfg["tgt"]), cfg["vcell"])
    }
}
