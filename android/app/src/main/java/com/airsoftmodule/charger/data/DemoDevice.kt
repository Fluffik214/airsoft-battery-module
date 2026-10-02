package com.airsoftmodule.charger.data

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Fake module for demo mode. Simulates a full PD charge of a 1450 mAh 3S pack (sped up),
 * including CC -> CV -> balance -> done, and speaks the same text protocol as the firmware.
 */
class DemoDevice(private val emit: (String) -> Unit) {
    private val cfg = ConfigKey.entries.associate { it.key to it.default }.toMutableMap()
    private val cells = doubleArrayOf(3.640, 3.612, 3.668)      // open-circuit volts
    private var temp = 24.0
    private var chip = 31.0
    private var upS = 0.0
    private var bal = 0
    private var balTimer = 0.0
    private var hold = false
    private val speed = 24.0                                    // simulated seconds per real second
    private var simS = 0.0
    /** true = behave like the real module on a phone (charging locked); false = pretend it sits on a PD charger */
    private var logCleared = false
    var phone = true
        set(v) { field = v; simS = 0.0; hold = false }

    private fun ocvToPct(v: Double) = Ocv.pct((v * 1000).toInt())

    fun tick(dtReal: Double) {
        val dt = dtReal * speed
        upS += dtReal
        simS += dt
        val mode = cfg["mode"]!!
        val targetMv = Ocv.target(DevConfig(cfg))
        val target = targetMv / 1000.0
        val armLeft = if (phone) -1 else maxOf(0, (cfg["wait"]!! - simS).toInt())
        val armed = !phone && armLeft == 0
        val hi = cells.max()
        val lo = cells.min()
        if (hi >= target + 0.003) hold = true
        if (hold && hi < target - 0.04) hold = false
        val charging = armed && mode != 2 && !hold && !(mode == 1 && lo >= target - 0.02)
        val ichg = cfg["ichs"]!! / 1000.0          // demo board: JP2 open = slow current
        val cap = cfg["cap"]!! / 1000.0 * 3600.0                    // coulombs
        // CV taper: current falls as the highest cell approaches the target
        val ir = 0.025
        var i = if (charging) min(ichg, max(0.0, (target - hi) / ir * 0.9 + 0.03)) else 0.0
        if (charging && hi < 3.0) i = min(i, ichg * 0.2)
        // per-cell control like the firmware: a cell near the target with uneven cells -> current down to 50 mA
        val cellcv = charging && hi > target - 0.02 && (hi - lo) * 1000 > cfg["balth"]!!
        if (cellcv) i = min(i, 0.05)
        if (charging && i < 0.08 && !cellcv) i = 0.0
        for (k in 0..2) {
            var d = i * dt / cap * 0.6                          // ~0.6 V across the useful range
            if (bal and (1 shl k) != 0) d -= 0.050 * dt / cap * 0.6
            cells[k] = (cells[k] + d + Random.nextDouble(-0.0003, 0.0003)).coerceIn(3.0, 4.25)
        }
        // balancing: highest cell, cells 1 and 3 can bleed together
        balTimer += dtReal
        if (balTimer > 2) {
            balTimer = 0.0
            val h = cells.max(); val l = cells.min()
            bal = 0
            if (cfg["bal"] == 1 && h * 1000 > cfg["balmin"]!! && (h - l) * 1000 > cfg["balth"]!!) {
                val k = cells.indexOfFirst { it == h }
                bal = 1 shl k
                val other = if (k == 0) 2 else if (k == 2) 0 else -1
                if (other >= 0 && (cells[other] - l) * 1000 > cfg["balth"]!!) bal = bal or (1 shl other)
            }
        }
        temp += ((24.0 + i * 6.0) - temp) * 0.01 * dt / 10
        chip += ((32.0 + i * 9.0) - chip) * 0.02 * dt / 10
        val vbus = if (phone) 5050 + Random.nextInt(-20, 20) else 15020 + Random.nextInt(-25, 25)
        val pack = cells.sum()
        val measured = cells.map { it + i * ir }
        val ibus = if (i > 0) (pack * i / 15.0 / 0.93 * 1000 + 25).toInt() else 18
        val stat = when {
            phone -> "HOST"
            !armed -> "ARMING"
            mode == 2 -> if (bal != 0) "BALANCE" else "MONITOR"
            charging && i > 0 -> if (mode == 1) "STORAGE" else if (cellcv) "CELLCV" else if (i > ichg * 0.97) "CC" else if (i > 0.15) "CV" else "TOPOFF"
            mode == 1 && bal != 0 -> "BLEED"
            bal != 0 -> "BALANCE"
            else -> "DONE"
        }
        val soc = cells.map { ocvToPct(it) }.average().toInt()
        val eta = if (i > 0.05) ((100 - soc) * cfg["cap"]!! / 100 * 60 / (i * 1000)).toInt() + if (stat == "CV") 10 else 15 else -1
        val spread = ((cells.max() - cells.min()) * 1000).toInt()
        val flt = (if (spread > cfg["imb"]!!) Fault.IMBALANCE.bit else 0) or (if (phone) Fault.LOW_SOURCE.bit else 0)
        val o = JSONObject()
            .put("t", "s").put("st", if (flt and Fault.IMBALANCE.bit != 0 && armed && mode != 2) "FAULT" else stat)
            .put("md", listOf("charge", "storage", "monitor")[mode]).put("src", if (phone) "LOW" else "PD")
            .put("vbus", vbus).put("ibus", ibus).put("pack", (measured.sum() * 1000).toInt())
            .put("ibat", (i * 1000).toInt() + if (i > 0) Random.nextInt(-6, 6) else 0)
            .put("c", JSONArray(measured.map { (it * 1000).toInt() }))
            .put("p", JSONArray(cells.map { ocvToPct(it) }))
            .put("soc", soc).put("tp", (temp * 10).toInt()).put("tc", (chip * 10).toInt())
            .put("bal", bal).put("flt", flt).put("chg", if (i > 0) 1 else 0).put("eta", eta).put("up", upS.toInt())
            .put("host", if (phone) 1 else 0).put("arm", armLeft).put("tgt", targetMv).put("ich", (i * 1000).toInt()).put("fast", 0).put("full", if (!charging && mode == 0 && lo >= target - 0.025) 1 else 0).put("logn", if (logCleared) 1 else 27)
        emit(o.toString())
    }

    private fun cfgJson() = JSONObject().apply { put("t", "cfg"); cfg.forEach { (k, v) -> put(k, v) } }.toString()
    private fun ok(m: String) = JSONObject().put("t", "ok").put("m", m).toString()
    private fun err(m: String) = JSONObject().put("t", "err").put("m", m).toString()

    fun command(line: String) {
        val p = line.trim().split(" ")
        when {
            p[0] == "s?" -> tick(0.0)
            p[0] == "c?" -> emit(cfgJson())
            p[0] == "i?" -> emit("""{"t":"i","fw":"1.0.0-demo","hw":"RevA","uid":"DEMO0001","chg":1,"bms":1}""")
            p[0] == "set" && p.size == 3 -> {
                val key = ConfigKey.byKey[p[1]]
                val v = p[2].toIntOrNull()
                if (key != null && v != null && v in key.min..key.max) { cfg[key.key] = v; emit(ok(key.key)); emit(cfgJson()) }
                else emit(err("bad key/value"))
            }
            p[0] == "save" -> emit(ok("save"))
            p[0] == "defaults" -> { ConfigKey.entries.forEach { cfg[it.key] = it.default }; emit(ok("defaults")); emit(cfgJson()) }
            p[0] == "mode" && p.size == 2 -> {
                val m = listOf("charge", "storage", "monitor").indexOf(p[1])
                if (m >= 0) { cfg["mode"] = m; emit(ok(p[1])); emit(cfgJson()) } else emit(err("mode?"))
            }
            p[0] == "reboot" -> { emit(ok("reboot")); upS = 0.0 }
            p[0] == "dfu" -> emit(err("demo device has no bootloader"))
            p[0] == "ship" -> emit(ok("ship (works only unplugged)"))
            p[0] == "log?" -> {
                val now = upS.toInt()
                listOf(
                    listOf(6, 5, 32, 11300), listOf(6, 2650, 33, 12600), listOf(6, 2650, 42, 11300), listOf(6, 2650, 39, 1080), listOf(6, 2650, 40, 44), listOf(6, 2650, 41, 298),
                    listOf(7, 5, 32, 11180), listOf(7, 3010, 33, 12590), listOf(7, 3010, 42, 11180), listOf(7, 3010, 39, 1190), listOf(7, 3010, 40, 50), listOf(7, 3010, 41, 312),
                    listOf(8, 5, 32, 11420), listOf(8, 840, 4, 462), listOf(8, 840, 36, 16), listOf(8, 1500, 38, 12050), listOf(8, 1500, 42, 11420), listOf(8, 1500, 39, 610), listOf(8, 1500, 40, 25), listOf(8, 1500, 41, 462),
                    listOf(9, 5, 10, 17240),
                    listOf(10, 1, 0, 1200),
                    listOf(11, 5, 32, 10980), listOf(11, 2900, 33, 12580), listOf(11, 2900, 42, 10980), listOf(11, 2900, 39, 1320), listOf(11, 2900, 40, 48), listOf(11, 2900, 41, 305),
                ).filter { !logCleared }.forEach { (b, s, c, v) ->
                    emit(JSONObject().put("t", "log").put("b", b).put("s", s).put("c", c).put("v", v).toString())
                }
                if (logCleared) emit(JSONObject().put("t", "log").put("b", 12).put("s", now).put("c", 37).put("v", 0).toString())
                emit(JSONObject().put("t", "logend").put("n", if (logCleared) 1 else 27).put("boot", 12).toString())
            }
            p[0] == "logclr" -> { logCleared = true; emit(ok("log cleared")) }
            p[0] == "drain" -> { for (k in 0..2) cells[k] = 3.55 + k * 0.02; emit(ok("demo: pack drained")) }
            else -> emit(err("unknown command"))
        }
    }
}
