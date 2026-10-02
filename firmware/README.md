# Airsoft PD Charger: firmware guide

This firmware runs on the **STM32F042F6P6** of the Rev A board. It is bare-metal C: CMSIS registers plus TinyUSB for the USB serial port. There is no HAL and no RTOS.

| | |
|---|---|
| Flash used | **26.0 KB of 30 KB** (+ 1 KB event-log page + 1 KB settings page) |
| RAM used | **3.0 KB of 6 KB** (plus 1 KB reserved for the stack) |
| Clock | 48 MHz HSI48, trimmed to USB by CRS (no crystal) |
| USB | CDC-ACM virtual serial port. VID/PID `0x1209/0x0001` (pid.codes *test* ID) |
| Output | `build/airsoft_charger.bin` / `.hex` (also copied to `../release/`) |

---

## 1. What the module does

> **One USB-C port.** The module is *either* on a charger (charging, no phone) *or* on a phone (talking, 5 V, never charging). It is never both at once. The app therefore never sees a charge live. It sets the plan beforehand and reads the **charge report** from the log afterwards.

Charging is decided by **the USB voltage only**:

```
              USB-C plugged in → MCU boots (powered from VBUS via the LDO)
                                   │
          ┌────────────────────────┼─────────────────────────┐
     VBUS < minvin (12 V)     12 V ≤ VBUS ≤ 17 V        VBUS > 17 V
     phone / 5 V charger      (PD charger at 15 V)      (broken charger)
          │                        │                         │
     monitor only             stays in window for       charging cut at once
     (state HOST if the       cfg.wait_s (default 5 s)  (CHG_EN low in < 1 ms),
      app is talking,               │                   fault logged; must be back
      else MONITOR)           charge by the plan        in the window for 5 s again
                       ┌───────────┼──────────────┐
                  mode 0: to   mode 1: storage  mode 2: off
                  tgt %        (bleed if above) (monitor)
                       └── charger stops itself at the target (CV + termination)
     USB unplugged → (optional balancing for "bun" minutes) → charger SHUTDOWN
                   + monitor SHIP + PWR_HOLD low = ~1 µA total
```

- **Phone:** it gives 5 V, so the module never charges from it. It only answers the app's requests (`s?`, `c?`, `log?` …). Talking to the app does **not** affect charging any more; `host` is just information.
- **PD charger:** 15 V lands in the window, and after 5 s the module charges to the preset % and stops.
- **Over-voltage:** `app_fast()` runs on every main-loop pass, not just every 250 ms. After 3 readings in a row above 17 V it drops `CHG_EN`, which switches the charger off in hardware through Q1 and /CE. As a second layer, the BQ25798's own input over-voltage cutoff is set to 18 V (`VAC_OVP`, re-applied every 5 s).

> The limit is `VBUS_MAX_MV` = 17000 in `board.h`. PD chargers may give up to 15.75 V when asked for 15 V, which leaves about 1.2 V of margin for ADC error.

## 2. File map

| File | What it is | Edit it when… |
|---|---|---|
| `src/board.h` | Pin map, I²C addresses, VBUS divider, version strings | you change the schematic or bump the version |
| `src/main.c` | Startup order, main loop, USB send/receive, `delay_hook` | you change the loop timing or USB handling |
| `src/app.c / app.h` | **Everything that matters**: state machine, safety, balancing, LED, JSON status, commands | you change behavior |
| `src/config.c / config.h` | Settings struct, defaults, allowed ranges, flash save with CRC | you add or change a setting |
| `src/soc.c / soc.h` | LiPo voltage↔% table, IR correction while charging | your packs have a different curve |
| `src/log.c / log.h` | Event log in its own flash page (faults, charge start/end, watchdog resets) | you want to log more things |
| `src/bq25798.c / .h` | Charger IC driver (registers, ADC readout, enable/HIZ/shutdown) | you need another charger feature |
| `src/bq76920.c / .h` | Cell monitor driver (CRC I²C, gain/offset, NTC, balance, ship) | you change cell wiring or the NTC |
| `src/hw.c / hw.h` | Clock, SysTick, GPIO, ADC, I²C, LED PWM, watchdog, flash, ROM-bootloader jump | low-level hardware changes |
| `src/startup.c` | Vector table and reset handler (calls `boot_check()` for DFU) | almost never |
| `stm32f042f6.ld` | Linker script: 30 KB code + 1 KB `LOG` page + 1 KB `CFG` page + 6 KB RAM | almost never |
| `src/usb_descriptors.c` | USB VID/PID, names, serial number (from the chip UID) | **before selling anything** (VID/PID) |
| `src/tusb_config.h` | TinyUSB options and buffer sizes | rarely |
| `lib/tinyusb` | TinyUSB (trimmed to device + CDC + STM32 FSDEV) | never |
| `lib/cmsis` | ARM/ST register headers | never |
| `build.py` | Build script (no `make` needed) | adding source files |

---

## 3. Building

You need an `arm-none-eabi-gcc` toolchain. The one used here is xPack GCC 15.2.1, unpacked at `C:\Users\duzik\.xpack\arm-gcc`.

> **Windows tip:** keep the toolchain in a short path. GCC fails with "machine/_default_types.h: No such file" when its path is longer than about 260 characters.

```
cd firmware
python build.py --gcc C:/Users/duzik/.xpack/arm-gcc/bin
```

You can also put the toolchain on `PATH` or set the `ARM_GCC_BIN` environment variable. `python build.py clean` deletes `build/`.

The build prints flash and RAM usage at the end. **Keep FLASH under 30 KB.** The last two pages hold the event log and your settings, and the linker refuses to overflow into them.

To add a `.c` file, append it to `SRC` in `build.py`.

---

## 4. Flashing

The chip has a USB DFU bootloader in ROM, so no programmer is needed.

| Situation | How to get into the bootloader |
|---|---|
| Brand-new (blank) chip | Plug in USB. A blank STM32F042 boots straight into the ROM bootloader. |
| Firmware running | App → Settings → Advanced → **Bootloader (DFU)**, or send `dfu` over serial |
| Firmware broken | Bridge **JP1** (BOOT0 to 3V3) while plugging in USB |
| SWD instead | Test pads TP5 SWDIO, TP6 SWCLK, TP7 3V3, TP8 GND (back side) plus an ST-Link |

Then flash from a PC:

```
STM32CubeProgrammer:  Port = USB → Connect → Open airsoft_charger.bin at 0x08000000 → Download
dfu-util:             dfu-util -a 0 -s 0x08000000:leave -D airsoft_charger.bin
```

On Windows, dfu-util needs the WinUSB driver for "STM32 BOOTLOADER" (use Zadig). CubeProgrammer installs its own driver.

Settings live in the 1 KB page at `0x08007C00` and the event log at `0x08007800`. Flashing only the `.bin` keeps both. A full chip erase resets the settings to defaults and empties the log.

---

## 5. Settings (stored in flash)

All settings are set with `set <key> <value>`, and `save` writes them to flash. Defaults and limits are in `config.c`: change `config_defaults()` and the `fields[]` table.

| Key | Meaning | Default | Range | Unit |
|---|---|---|---|---|
| `mode` | 0 = charge to `tgt`, 1 = storage, 2 = never charge | 0 | 0–2 | |
| `tgt` | charge target in mode 0 (100 = full = `vcell`) | 100 | 50–100 | % |
| `wait` | USB must stay in the 12–17 V window this long before charging starts | 5 | 1–600 | s |
| `vcell` | full-charge voltage per cell | 4200 | 4000–4200 | mV |
| `ichs` | **slow** charge current, used while JP2 is open (default) | 700 | 100–3000 | mA |
| `ichg` | **fast** charge current, used when JP2 is bridged | 1400 | 100–3000 | mA |
| `iin` | USB input current limit (1.5 A keeps an 18 W 12 V charger and a 20 W 15 V charger within rating) | 1500 | 500–3000 | mA |
| `stor` | storage voltage per cell | 3800 | 3700–3900 | mV |
| `minvin` | lower edge of the charging window (the upper edge is `VBUS_MAX_MV` = 17 V in `board.h`) | 12000 | 9000–15000 | mV |
| `bal` | balancing on/off | 1 | 0–1 | |
| `balth` | balance when the cell spread is above this | 15 | 5–60 | mV |
| `balmin` | never bleed a cell below this voltage | 3900 | 3500–4150 | mV |
| `bun` | keep balancing on pack power after unplug | 0 | 0–60 | min |
| `imb` | refuse to charge above this cell spread | 300 | 50–500 | mV |
| `tmax` | max pack temperature while charging | 45 | 35–60 | °C |
| `cap` | pack capacity (used for the ETA) | 1450 | 200–10000 | mAh |
| `led` | LED brightness | 40 | 0–100 | % |
| `rate` | status report interval | 500 | 200–5000 | ms |

**How `tgt` becomes a voltage:** `target_mv()` in `app.c` runs `soc_to_mv(tgt)` (the inverse of the OCV table) and caps the result at `vcell`. The charger's VREG is set to 3 × that value. The BQ25798 then does a normal CC/CV charge to that voltage and terminates by itself.

| Target | Per cell | Pack |
|---|---|---|
| 80 % | ≈ 4.04 V | ≈ 12.1 V |
| 100 % | 4.20 V | 12.6 V |

**Adding a new setting:**

1. Add a field to `config_t` before `crc`.
2. Set its default in `config_defaults()`.
3. Add a line to `fields[]`.
4. Add it to `send_cfg()` in `app.c`.
5. If the layout changed, bump `CFG_MAGIC` so old flash contents are ignored instead of misread.
6. In the app, add it to `ConfigKey` in `Models.kt`.

---

## 6. Serial protocol (what the app speaks)

The protocol is plain text lines ending in `\n`. Any terminal works at any baud rate, but **DTR must be on**, because the firmware only sends while a terminal is open. Every reply is a single JSON line.

### Commands

| Command | Reply / effect |
|---|---|
| `s?` | status line now (status also streams every `rate` ms) |
| `c?` | `{"t":"cfg", …all keys…}` |
| `i?` | `{"t":"i","fw":"1.0.0","hw":"RevA","uid":"…","chg":1,"bms":1}` (chg/bms = chips found) |
| `set <key> <int>` | `{"t":"ok","m":"<key>"}` then the cfg line, or `{"t":"err","m":"bad key/value"}` |
| `save` | writes the settings to flash |
| `defaults` | resets the settings in RAM (send `save` afterwards to keep them) |
| `mode charge\|storage\|monitor` | same as `set mode 0/1/2` |
| `reboot` | soft reset |
| `dfu` | jump to the ROM USB bootloader |
| `ship` | refused while USB is present (shipping happens automatically on unplug) |
| `log?` | the whole event log: one `{"t":"log","b":boot,"s":sec,"c":code,"v":value}` per entry (oldest first), then `{"t":"logend","n":count,"boot":current}` |
| `logclr` | erase the event log |

### Status line

```json
{"t":"s","st":"HOST","md":"charge","src":"LOW","vbus":5040,"ibus":0,"pack":11520,"ibat":0,
 "c":[3841,3838,3840],"p":[48,47,48],"soc":48,"tp":243,"tc":312,"bal":0,"flt":256,"chg":0,
 "eta":-1,"up":12,"host":1,"arm":-1,"tgt":4200}
```

| Field | Meaning |
|---|---|
| `st` | state (see the next table) |
| `md` | `charge` / `storage` / `monitor` |
| `src` | `NONE` (< 4 V), `LOW` (below `minvin`), `PD` (in the window), `HIGH` (> 17 V) |
| `vbus` / `ibus` | USB voltage (mV) and current (mA) |
| `pack` / `ibat` | pack voltage (mV) and battery current (mA, + = charging) |
| `c[]` | cell 1–3 voltages (mV) |
| `p[]` | per-cell % |
| `soc` | pack % (average of the cells, IR-corrected while charging) |
| `tp` / `tc` | pack NTC / charger die temperature, 0.1 °C |
| `bal` | balance bitmask (bit0 = cell 1) |
| `flt` | fault bits (see section 8) |
| `chg` | charging enabled |
| `eta` | minutes to full, -1 = n/a |
| `up` | uptime (s) |
| `host` | 1 = the app has sent a command (information only) |
| `arm` | seconds until charging starts (VBUS in window, counting down), -1 = not counting |
| `tgt` | per-cell target voltage of the current plan (mV) |
| `logn` | number of entries in the event log. The app re-reads the log when this changes. |
| `ich` / `full` / `fast` | current charge-current limit (mA), all cells full (0/1), JP2 bridged = fast (0/1) |

### States (`st`)

| State | Meaning | LED |
|---|---|---|
| `ARMING` | 12–17 V seen, counting down `wait` | slow dim blink |
| `HOST` | on 5 V with the app talking (phone) | short flash every 2 s |
| `PRECHARGE`, `CC`, `CV`, `TOPOFF` | charging phases reported by the BQ25798 | breathing |
| `STORAGE` | charging toward the storage voltage | breathing |
| `DONE` | target reached | solid |
| `BALANCE` / `BLEED` | bleeding high cells / storage discharge | double blink |
| `MONITOR` | mode 2 or 5 V source, nothing charging | short flash |
| `FAULT` | a blocking fault (see `flt`) | fast blink |
| `UNPLUGGED` | no USB power, shutting down | off |

---

## 7. Main loop and timing (`main.c`)

```
hw_init → config_load → app_init (PWR_HOLD high, log_init + reset cause, init chips) → tusb_init → wdg_init (~2 s)
loop:  wdg_feed · app_fast (17 V cut) · tud_task · read USB lines
       every 250 ms: app_tick()       every loop: app_led()
       every cfg.rate ms (or on "s?"): send the status JSON
```

- **`app_tick()`**: reads both chips, evaluates faults, decides charging, picks the balance cells (every 2 s), sets the state, and handles unplug.
- **Charger watchdog**: kicked every 1 s. Its settings are rewritten every 5 s, so if the BQ25798 ever resets to its defaults, the firmware puts your values back.
- **`delay_ms()`**: calls `delay_hook()`, which keeps USB and the watchdog serviced during the 300–400 ms cell-monitor boot wait.

---

## 8. Safety rules (`app_tick()` in `app.c`)

| Bit | Name | Trigger | Blocks charging |
|---|---|---|---|
| 0 | `F_NOBATT` | pack < 6.0 V | yes |
| 1 | `F_CELL_OV` | any cell > 4250 mV **or** > `vcell` + 50 | yes |
| 2 | `F_CELL_UV` | any cell < 2500 mV (only with a battery present) | yes |
| 3 | `F_IMBAL` | spread > `imb` | yes |
| 4 | `F_HOT` | pack > `tmax`, or charger die > 110 °C | yes |
| 5 | `F_COLD` | pack < 0 °C (readings below −40 °C are treated as "no NTC") | yes |
| 6 | `F_CHG` | BQ25798 FAULT0 ≠ 0 or FAULT1 & 0xC4 | yes |
| 7 | `F_BMS` | cell monitor not answering | yes |
| 8 | `F_LOWSRC` | VBUS < `minvin` (info only: phone or 5 V charger; not logged) | no (but no charge) |
| 9 | `F_CHGCOMM` | charger not answering while USB is present | yes |
| 10 | `F_OVERVOLT` | VBUS > 17 V (`VBUS_MAX_MV`). Cut instantly by `app_fast()`. | yes |

**Extra rules:**

- **Per-cell charge control (like a hobby balance charger):** the BQ25798 only sees the whole pack, so the firmware handles single cells:
  - **Hold:** if any cell reaches target + 10 mV (`CELL_MAX_OVER`), charging pauses at once. It resumes in short bursts when that cell is ≤ target + 2 mV.
  - **Taper:** when the highest cell is within 20 mV of the target (`CELL_TAPER_AT`) and the spread is > `balth`, the charge current drops by 25 % every 2 s, down to 50 mA (`ICHG_MIN_MA`). The full cell's bleed then keeps it level while the lower cells fill. The current steps back up by 100 mA every 2 s once the highest cell is 60 mV below the target.
  - **Full:** every cell is within 25 mV of the target (`CELL_FULL_AT`), measured at < 150 mA so internal resistance can't fake it. Charging then stops and stays stopped until unplugged.
  - Shown as state `CELLCV`. The status adds `ich` (current limit in mA) and `full` (0/1).
  - **Speed limit:** the cells even out at the bleed rate (~37 mA per cell, 70 % duty). A normal pack (~25 mV apart) adds roughly 45 min; a badly neglected one (>100 mV) can take hours.
- **Balancing:** the highest cell bleeds when it is > `balmin` and the spread > `balth`. Cells 1 and 3 may bleed together, but adjacent cells never do (BQ76920 rule).
- **Storage mode:** if the lowest cell is already above `stor` + 20 mV, the firmware alternates bleeding cells 1+3 and cell 2. This only happens while armed, never on a phone.

To make the module stricter or looser, edit these numbers in `app_tick()`.

---

## 8b. Event log (`log.c`)

The log is a 1 KB flash page with 128 entries of 8 bytes each: `{boot, seconds since that power-up, code, value}`. When the page is full, the newest 64 entries are kept and the page is rewritten.

| Code | Event | Value |
|---|---|---|
| 0–10 | fault bit *n* started (see the table above) | the measurement that caused it (mV, mV spread, 0.1 °C, charger fault registers, USB mV) |
| 32 | charging started | pack mV |
| 33 | charge complete (target reached) | pack mV |
| 34 | firmware was reset by the watchdog (hang) | – |
| 35 | cell monitor failed to boot | – |
| 36 | charging stopped by a fault | fault bits |
| 37 | log cleared | – |
| 38 | unplugged before the target was reached | pack mV |
| 39 | charge report: mAh put into the pack | mAh |
| 40 | charge report: minutes from charge start to unplug | min |
| 41 | charge report: peak pack temperature | 0.1 °C |
| 42 | charge report: pack voltage when charging started | mV |

- **Faults** are logged **once per power-up** each (`log_once`). This keeps a flapping fault from wearing out the flash, which is rated for about 10 000 erase cycles.
- **Charge report:** `32` is written once when charging starts. When the charger is unplugged, the firmware still runs on the pack for about 1.5 s and writes the result: `33` (target reached) or `38` (unplugged early), followed by `42`, `39`, `40` and `41`. The app combines these into **Last charge** and **Charge history**.
- **Power-up number:** each power-up gets the next number, so the app can group events by power-up.

To log something new, call `log_event(code, value)` or `log_once(code, value)` with a code from 38 to 63, then add it to `EventKind` in the app's `EventLog.kt`.

## 9. Common customizations

| I want to… | Change |
|---|---|
| Change the charging window | `minvin` setting (lower edge) and `VBUS_MAX_MV` in `board.h` (upper edge) |
| Start charging faster or slower | `set wait <seconds>` (1–600) |
| Different default plan (e.g. 80 %) | `config_defaults()`: `cfg.tgt_pct = 80;` and bump `CFG_MAGIC` |
| A 2S or 4S pack | `target_mv()` multiplier (`* 3` in `charger_apply`), the `bq76920.c` cell mapping, `bq25798_configure` VREG clamps, the `F_NOBATT` threshold. The hardware (PROG resistor, cell wiring) must also change. |
| Faster charging | bridge **JP2** (back of the board, under the MCU) with solder → uses `ichg` (1.4 A) instead of `ichs` (0.7 A). Both are settable in the app. |
| Tighter "full" (closer to 4.20 V on every cell) | `CELL_FULL_AT` in `app.c` (default 25 mV; smaller = longer final balance) |
| Different LED patterns | `app_led()` in `app.c` (`b` = brightness 0–100, `t` = ms) |
| A different SOC curve | `ocv_mv[]` / `ocv_pc[]` in `soc.c` (keep the app's `Ocv` table in `Models.kt` the same) |
| Your own USB name | `strings[]` in `usb_descriptors.c` |
| Your own VID/PID | `USB_VID/USB_PID` in `usb_descriptors.c` **and** `res/xml/device_filter.xml` + `CdcConnection.VID/PID` in the app |
| New command | add a branch in `app_command()` (`app.c`) |
| New status field | add `kv(&w, "name", value);` in `app_status_json()`. Keep the line under 360 chars (the buffer size in `main.c`). |

---

## 10. Hardware notes the firmware relies on

| Item | Detail |
|---|---|
| USB pins | USB D−/D+ are on PA11/PA12 via the SYSCFG remap (`PA11_PA12_RMP`), which `hw.c` enables |
| BQ25798 | I²C 0x6B. PROG = 10.5 k → 3S, 1.5 MHz. /CE is driven by Q1 from `PIN_CHG_EN`, so charging needs **both** the pin high and the EN_CHG bit set. D+/D− are not connected, so BC1.2/HVDCP detection is disabled in `bq25798_init()`. |
| BQ7692003 | I²C 0x08 with CRC-8. Booted by a pulse on TS1 (`PIN_BMS_BOOT`). 3S wiring: cells are VC1−VC0, VC2−VC1, VC5−VC4. Cell input resistors R13–R16 = 56 Ω → ~37 mA balance current (datasheet min 40 Ω / max 50 mA). |
| CH224K | Hard-wired for 15 V (CFG1 = 56 k). The firmware only *measures* VBUS (÷ 11 on PA5). |
| JP2 | Solder jumper PB1 → GND (internal pull-up), on the back under U6. Open = slow (`ichs`), bridged = fast (`ichg`). Read every tick (`fast_jumper()` in `hw.c`). |
| Power | MCU 3.3 V from the AP7381 LDO. `PIN_PWR_HOLD` keeps it on from the pack after unplug, and it is dropped in `shutdown_now()`. |
| Watchdog | IWDG ≈ 2 s. A hang resets the MCU, and the charger is disabled again until the firmware re-decides. |

---

## 11. First power-up checklist

1. Flash it, then plug it into a **PC** (it enumerates as *PD 3S Charger*). Open any serial terminal and send `i?`. Both `chg` and `bms` should be `1` with a pack connected.
2. Send `s?` and check the cells against a multimeter. They should be within ~10 mV.
3. `set led 100` → the LED gets brighter (no save needed).
4. Unplug the PC and plug into a PD charger. The LED dims and slow-blinks for 5 s, then starts breathing (charging). Afterwards, connect the phone and check the **Event log**: you should see "Charging started".
5. Unplug: everything should be off within ~2 s (total pack drain about 1 µA).
