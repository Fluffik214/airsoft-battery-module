# Airsoft Battery Module

This is a USB-C PD charger and cell monitor for a **3S LiPo** that lives in an airsoft stock tube. It has one USB-C port. A PD charger charges the pack through it, and a phone plugged into the same port shows each cell's voltage and charge % in the companion Android app.

| | |
|---|---|
| Board | 135 × 21 mm, 2 layers, SMT only, no BGA |
| Pack | 3S LiPo (made for an iPower 1450 mAh stick). Deans main lead, JST-XH balance plug |
| Charging | USB-C PD at 15 V (CH224K) → BQ25798 buck-boost charger |
| Monitoring | BQ7692003 (per-cell voltage, NTC, balancing) → STM32F042 → USB CDC |
| Idle drain | about 1 µA when unplugged |

## How it works

- **One port, two jobs.** On a PD charger the module charges. On a phone it only reports (5 V never charges, so the phone isn't drained). It never does both at once.
- **Charging rules:**
  - It starts after USB has stayed between 12 and 17 V for 5 s, then charges to your preset: Full, a custom %, Storage (3.80 V/cell) or Off.
  - If USB goes above 17 V, charging is cut immediately.
- **Per-cell control, like a hobby balance charger.** When a cell is full, it is held there and bled while the current is turned down, so the other cells can catch up. No cell goes more than 10 mV over the target.
- **JP2 solder jumper (back of the board).** Open (default) = slow charge, 700 mA. Bridged = fast charge, 1.4 A. Both currents can be changed in the app.
- **Event log in flash.** Faults (over-voltage, over-temperature, bad cell…) and a report for every charge are stored on the module. The app shows them the next time a phone is connected.

## Repository layout

| Path | Contents |
|---|---|
| `usbc pd airsoft module.kicad_*` | KiCad 10 project: schematic, routed PCB, design rules |
| `schematic.pdf`, `bom.csv` | Schematic printout and bill of materials |
| `DESIGN_NOTES.md` | Hardware notes: power path, pinouts, checks before first power-up |
| `layout_work/` | Python scripts that generated the schematic and placed/routed the PCB |
| `firmware/` | STM32F042 firmware: bare-metal C plus TinyUSB. **`firmware/README.md` is the full guide**, covering protocol, settings, safety rules and how to customize |
| `android/` | Android app: Kotlin, Jetpack Compose, Material 3 |
| `release/` | Prebuilt firmware (`.bin` / `.hex`) and app (`.apk`) |

## Quick start

1. **Flash the firmware.** A blank chip starts in the USB DFU bootloader. You can also bridge JP1 while plugging in. Then:
   ```
   dfu-util -a 0 -s 0x08000000:leave -D release/airsoft_charger.bin
   ```
   STM32CubeProgrammer works too.
2. **Install the app.** Copy `release/PDCharger-1.0.0.apk` to the phone and install it.
3. **Check the pack.** Plug the module into the phone with a USB-C cable and open the app.
4. **Charge it.** Put the module on a USB-C PD charger that offers 15 V (or 12 V).

### Building from source

- **Firmware:** needs `arm-none-eabi-gcc` (tested with xPack 15.2.1). Run:
  ```
  python firmware/build.py --gcc <path-to-toolchain-bin>
  ```
- **App:** needs JDK 17 and the Android SDK. Run:
  ```
  cd android && ./gradlew assembleRelease
  ```

## Safety

LiPo batteries can catch fire. This is a hobby project with **no warranty**. Check `DESIGN_NOTES.md` before the first power-up. In particular, confirm that the balance plug's pin 1 is the pack's negative wire.

## License

© Fluff. Licensed under **[Creative Commons Attribution-NonCommercial-ShareAlike 4.0](LICENSE)** (CC BY-NC-SA 4.0):

- You may copy, build and change this project for **non-commercial** use, if you credit the author and share your changes under the same license.
- **Commercial use, including selling boards, kits or devices built from this design, needs written permission from the author.**

Third-party code in `firmware/lib/` keeps its own license:

- **TinyUSB:** MIT, see `firmware/lib/tinyusb/LICENSE`.
- **ARM CMSIS core headers:** Apache-2.0 (SPDX header in each file).
- **ST device headers:** Apache-2.0, see `firmware/lib/cmsis/LICENSE-ST-cmsis-device-f0.md`.
