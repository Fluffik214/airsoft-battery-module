package com.airsoftmodule.charger.data

enum class TipCategory(val title: String) { SAFETY("Safety"), CHARGING("Charging"), STORAGE("Storage"), FIELD("In the field"), MODULE("This module"), GEAR("AEG & gear") }

data class Tip(val category: TipCategory, val title: String, val body: String)

object Tips {
    val all = listOf(
        // ---- safety
        Tip(TipCategory.SAFETY, "Never charge a puffy pack",
            "A swollen (puffed) LiPo has gas inside from internal damage. Don't charge it, don't puncture it. Discharge it to about 3.0 V per cell in a fireproof place and recycle it."),
        Tip(TipCategory.SAFETY, "Charge where a fire can't spread",
            "A stock tube is a decent enclosure, but don't charge on a bed, sofa or inside a gear bag. A LiPo-safe bag or a ceramic tile is cheap insurance."),
        Tip(TipCategory.SAFETY, "Don't leave it unattended the first times",
            "Watch the first few charges of a new pack: check that the cells rise together and the pack stays cool. After that the module's limits do the babysitting, but stay nearby."),
        Tip(TipCategory.SAFETY, "Below 3.0 V per cell is damage",
            "Cells taken under ~3.0 V lose capacity permanently. Under 2.5 V the module refuses to charge, because copper can dissolve and short the cell later."),
        Tip(TipCategory.SAFETY, "Warm pack = normal, hot pack = stop",
            "A pack that's slightly warm after a charge or a game is fine. If you can't keep your hand on it, something is wrong: high internal resistance, a damaged cell or too much current."),
        // ---- charging
        Tip(TipCategory.CHARGING, "1C is the sweet spot",
            "1C means current = capacity: 1.45 A for a 1450 mAh pack. That's a full charge in about an hour and kind to the cells. The module's fast setting (1.4 A, JP2 bridged) is right there; the default slow setting is half that."),
        Tip(TipCategory.CHARGING, "Use a real PD charger",
            "The module asks for 15 V over USB-C PD. Any 20 W+ PD charger or power bank works. Phone ports and old 5 V chargers can only power the monitor, not the charging."),
        Tip(TipCategory.CHARGING, "4.20 V vs 4.15 V",
            "Charging to 4.20 V/cell gives full capacity. Stopping at 4.10–4.15 V costs ~5–10 % runtime but can roughly double cycle life. Great for practice days."),
        Tip(TipCategory.CHARGING, "Why the current drops near the end",
            "In the CV phase the charger holds the voltage and the current falls by itself. The last 10 % takes a while, which is normal. ETA includes this tail."),
        Tip(TipCategory.CHARGING, "Let it cool before charging",
            "Right after a heavy game the pack can be 40 °C+. Give it 15 minutes. The module pauses charging above your temperature limit anyway."),
        Tip(TipCategory.CHARGING, "Power banks work",
            "A USB-C PD power bank that supports 15 V lets you top up between games. A 20 000 mAh bank refills a 1450 mAh 3S pack about 4 times."),
        // ---- storage
        Tip(TipCategory.STORAGE, "Store at 3.8 V per cell",
            "Full packs age fast in storage, especially in a hot car. Switch to Storage mode after the last game day: the module charges or bleeds every cell to ~3.8 V."),
        Tip(TipCategory.STORAGE, "Cool and dry",
            "Store packs at room temperature or cooler, never in a sunny car. Heat plus full charge is the main way LiPos die."),
        Tip(TipCategory.STORAGE, "Check monthly",
            "LiPos self-discharge slowly. Plug in once a month in Monitor mode and check that every cell is still above 3.7 V."),
        // ---- field
        Tip(TipCategory.FIELD, "Quick check with your phone",
            "Pull the module out, plug a USB-C cable into your phone and open this app. You see every cell and the pack % without charging anything."),
        Tip(TipCategory.FIELD, "Trust the lowest cell",
            "The pack is only as good as its weakest cell. If one cell is at 3.5 V while the others are at 3.7 V, plan for that cell, not the average."),
        Tip(TipCategory.FIELD, "Voltage sags under fire",
            "Under trigger pull the voltage drops. Resting voltage is what the % shows, so read it after the gun has been idle for a minute."),
        Tip(TipCategory.FIELD, "Use a low-voltage alarm or MOSFET cutoff",
            "Running a LiPo flat in an AEG kills it. A cheap buzzer alarm on the balance lead or a MOSFET with cutoff stops you at ~3.3 V/cell."),
        // ---- module
        Tip(TipCategory.MODULE, "LED language",
            "Dim slow blink = 12–17 V seen, about to charge · Breathing = charging · Solid = full · Double blink = balancing · Fast blink = fault · Short flash every 2 s = phone connected / monitoring."),
        Tip(TipCategory.MODULE, "Your phone is never drained",
            "The module only charges when USB sits between 12 and 17 V for 5 seconds. Phones give 5 V, so on a phone it just reports. Above 17 V it cuts charging instantly."),
        Tip(TipCategory.MODULE, "Check the event log",
            "Every fault (over-voltage, hot pack, bad cell…) and every finished charge is stored in the module, even with no phone attached. Open Settings → Event log to see what happened while you were away."),
        Tip(TipCategory.MODULE, "Set it and forget it",
            "Pick a charge plan in the app (Full, a custom %, or Storage), then put the module on a PD charger. It charges to that level and stops. No need to babysit the voltage."),
        Tip(TipCategory.MODULE, "It switches itself off",
            "When you unplug USB the module turns off charger, monitor and MCU completely, so it won't drain the pack. Optionally it can keep balancing for a few minutes first."),
        Tip(TipCategory.MODULE, "Slow or fast: the JP2 jumper",
            "JP2 on the back of the board picks the charge current. Open (as shipped) = slow, 700 mA ≈ 0.5C: about 2 h from empty and gentle on a pack that lives in the stock. Bridge it with a blob of solder for fast, 1.4 A ≈ 1C: about 1 h. Both currents can be changed in Settings."),
        Tip(TipCategory.MODULE, "Full cells are held, not overcharged",
            "Like a hobby balance charger: when one cell reaches full, the module turns the current down to 50 mA and bleeds that cell so it stays put while the others catch up. No cell goes more than 10 mV over the target."),
        Tip(TipCategory.MODULE, "Balancing is slow on purpose",
            "The bleed only removes about 37 mA, so nothing gets hot inside the stock tube. A 50 mV difference can take a few hours, so leave it plugged in after it's full."),
        Tip(TipCategory.MODULE, "Updating the firmware",
            "Settings → Advanced → Bootloader puts the chip into USB DFU mode. Flash it from a PC with STM32CubeProgrammer or dfu-util. Bridging JP1 does the same if the firmware is broken."),
        Tip(TipCategory.MODULE, "Set your pack capacity",
            "The time-to-full estimate uses the capacity from Settings. Change it if you use something other than a 1450 mAh pack."),
        // ---- gear
        Tip(TipCategory.GEAR, "Deans connectors wear out",
            "If the Deans plug gets warm or loose, replace it. A bad connector wastes power and heats the pack lead."),
        Tip(TipCategory.GEAR, "Mind the balance lead in the stock tube",
            "Route the thin balance wires so the stock can't pinch them. A shorted balance lead can melt."),
        Tip(TipCategory.GEAR, "11.1 V is a lot for some AEGs",
            "3S packs make stock motors fire faster but stress the trigger contacts and gears. Use a MOSFET and check your gearbox can handle it."),
    )

    fun ofTheDay(): Tip {
        val day = (System.currentTimeMillis() / 86_400_000L).toInt()
        return all[day % all.size]
    }
}
