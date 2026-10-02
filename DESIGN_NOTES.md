# USB-C PD 3S LiPo charger module: design notes (Rev B)

Target pack: iPower 1450 mAh 3S 20C stick (110 × 20 × 15 mm). It has a Deans T-plug main lead and a white JST-XH 4-pin balance plug. Since Rev B the Deans lead goes straight to the gun; the module connects to the pack **only through the balance plug**.
Board outline: **135 × 20–21 mm**. The single USB-C port goes on one short edge.

## Power path
USB-C (J1, the only USB port) → CH224K asks for 15 V (R2 = 56k) → BQ25798 buck-boost charger → F1 (2.5 A fast) → **J2 pin 1 (pack +, red)**. The return current comes back on J2 pin 4 (pack −, black).
- **The pack is charged through its balance plug (J2, JST-XH)**, the way most airsoft balance chargers do it and as the pack maker recommends. There are no Deans / main-lead pads on the board (removed in Rev B). The battery's Deans lead goes straight to the gun, so gun current never touches the module.
- J2 carries the charge current on pins 1 and 4 and also senses every cell (pins 2 and 3 carry no charge current).
  - JST-XH is rated 3 A on AWG22 wire. Typical balance leads are 22–26 AWG; keep the charge current at or under ~2 A on 26 AWG. The module charges at 0.7 A (default) or 1.4 A (JP2 bridged).
  - The charge current makes cell 1 and cell 3 read high by the wire drop (about 20–40 mV at 0.7 A). The firmware pauses the charger for ~0.75 s every 10 s (every 4 s near the top) and makes every decision on those clean, no-current readings.
- **F1 = 2.5 A fast-acting 1206 fuse (AEM F1206SB2500V032TM, LCSC C310999, 50 A breaking capacity)** between J2 pin 1 and the charger. If anything on the board shorts, the pack would otherwise push tens of amps through the thin balance wires and burn them inside the stock. 2.5 A sits well above the 1.4 A maximum charge current (the 466-series derating rule gives 75 % continuous, and less when warm), so it never opens in normal use.
- **JP2 (FAST CHARGE solder jumper, back side under the MCU):** MCU PB1 to GND with the internal pull-up. Open (default) = slow charge, 700 mA. Bridged = fast charge, 1.4 A. Both currents can be changed in the app. Added 2026-10-02.
- The BQ7692003 measures each cell and balances them (~37 mA bleed: Rc = R13–R16 = 56 Ω, changed from 100 Ω on 2026-10-02 to roughly double the balance speed).
- The STM32F042F6P6 runs from 3V3 (AP7381). 3V3 comes from VBUS (a charger or a phone), or from the pack through Q2 while the MCU asserts PWR_HOLD.

## Field check (no extra pins)
1. Plug a phone into the module's USB-C port (C-to-C cable) and open the app.
2. The phone's 5 V powers the MCU. It reports volts and % per cell, settings and the event log over USB CDC.
3. Charging only happens at 12–17 V, so the phone is never drained. One USB-C port: the module is either on a charger or on a phone, never both.

## Idle battery drain (target ~1–2 µA)
BQ25798 in shutdown mode (0.5 µA) + BQ76920 in SHIP mode (~0.6 µA) + leakage. **After you first connect a pack, plug USB in once.** Until then the BQ25798 stays in battery-only mode and draws ~17 µA.

## Firmware bring-up (STM32F042, I2C1 on PF0/PF1)
1. On boot, set SYSCFG PA11_PA12_RMP so USB uses pins 17/18, and start USB CDC.
2. **BQ76920 (0x08, CRC on)**: drive PA4 high for 5 ms then set it to analog (this boots it from SHIP). Wait 250 ms, then set ADC_EN and read the gain/offset. Cells are VC1-VC0, VC2-VC1 and VC5-VC4 (3S wiring: VC2=VC3=VC4).
3. **BQ25798 (0x6B)**: clear AUTO_INDET_EN and HVDCP_EN, set IINDPM to suit the source, set **ICHG = 1.4 A** (1C for 1450 mAh), check VREG = 12.6 V, kick the watchdog (or disable it).
4. Only drive **CHG_EN (PA2) high** when all of these are true (as implemented in firmware/src/app.c):
   - VBUS_SENSE has been inside 12–17 V for `wait` seconds (default 5 s). Above 17 V, charging is cut instantly.
   - Every cell is between 2.5 V and the target, and the pack is present.
   - The cells are within `imb` (300 mV) of each other.
   - The temperature is within limits.
   - Note: the charger must offer 15 V (or 12 V) PD. The CH224K datasheet doesn't say what happens when 15 V isn't offered (it may stay at 5 V or take a lower level). If you see a 9 V brick charging-blocked at 9 V, lower `minvin` to 9000 in the app; the buck-boost charger handles 9 V fine.
5. While charging: per-cell control like a hobby balance charger. A full cell is held (charging pauses above target + 10 mV, current tapers to 50 mA) and bled while the others catch up. "Full" = every cell within 25 mV of the target. Never balance two adjacent cells at once. See firmware/README.md section 8.
6. On unplug (VBUS_SENSE falls or the BQ25798 INT fires):
   1. Assert **PWR_HOLD (PA7)**.
   2. Finish balancing if wanted.
   3. Write BQ25798 SDRV_CTRL = 01 (shutdown).
   4. Put the BQ76920 into SHIP.
   5. Release PWR_HOLD.
7. If it was powered by a phone (VBUS ≈ 5 V): report the cells over CDC and never enable charging.
8. Faults and a per-charge report are written to the event-log flash page; the app reads them later.

## Flashing
There's no SWD header. Bridge JP1 (BOOT0) and plug in USB-C to enter the ST ROM DFU bootloader, then use STM32CubeProgrammer or dfu-util. A blank STM32F042 also starts in the bootloader on its own.

## Before you order PCBs (check these)
- **Balance plug pinout**: J2 pin 4 = pack negative (black), pin 1 = pack positive (red), checked against the real pack plug on 2026-10-02. See `production/assembly/J2-orientation.png`.
- **Balance lead gauge**: check the wire gauge on your pack's balance lead. 22–24 AWG is fine for fast charge (1.4 A); on 26 AWG leave JP2 open (0.7 A).
- **CH224K VDD resistor (R1 = 1k, 1206)**: I couldn't read the value off WCH's reference schematic image. 1k is the common community value.
- **Layout**:
  - BQ25798 power loop as tight as possible (VBUS/PMID caps, L1, SYS caps).
  - Thermal-pad vias under U3.
  - TH1 at the board edge facing the pack.
  - Cell sense traces (R12–R16) kept away from SW1/SW2.

## PCB layout (Rev A, routed 2026-10-02)
- **Board:** 135 x 21 mm, 2 layers. 349 tracks, 154 vias (61 of them GND stitching). Ground pours on both layers, solid connection.
- **Final check:** DRC shows 0 errors, 0 unconnected and 0 schematic-parity issues. The only warnings are silkscreen (the USB-C outline at the edge, and the charger's pin-1 marker touching a cap pad).
- **Corners:** no 90-degree corners anywhere. Every trace bend is a 45-degree chamfer, every branch joins at 45 degrees (checked by `layout_work/audit.py`), and all copper zones have chamfered outlines (0.4 mm on power, 1 mm on GND).
- **Rules (in the .kicad_pro):** 0.15 mm clearance (0.2 mm on power nets), 0.2 mm signals, 0.8–1.0 mm power, vias 0.6/0.3 and 0.5/0.25.
- **Parts on the BACK side (8):**
  - C5, C6 (charger bootstrap caps, the TI-recommended placement)
  - C19 (REGN cap)
  - R5 (BATP sense resistor)
  - TP1–TP4 (Deans wire pads, removed in Rev B)

  Reflow the top first, then the back (or hand-solder the four 0402/0603 parts).
- **Deans pads (back, right end; removed in Rev B):**
  - TP1 BATT IN + and TP2 GUN OUT + share one MAIN_P copper area.
  - TP3 BATT IN − and TP4 GUN OUT − sit on the GND plane.
  - Gun current only flows pad to pad across about 1 mm of solid copper.
- **Back-side bus:** the 7 control/I²C lines run as parallel traces along the bottom edge of the back.
- **Custom text area (back):** x 198–209.8, y 101.5–116.2, about 12 × 15 mm (marked on B.Fab). It has no vias or traces, only the GND plane.
- **Reference designators:** hidden on silkscreen, kept on the Fab layers. Use KiCad's assembly view or the Fab PDF while placing parts.
- **Rebuilding the layout:** the scripts are in `layout_work/` and regenerate it deterministically (`route.py` + `regionB..E.py`).

## Rev A update (2026-10-02): J2 rotated + test pads
- **J2 (balance plug):**
  - Rotated 180°, so its opening faces the board end.
  - Moved 6 mm inward (x 218.4), so the plug body clears the two M2 screw heads at that end. Only the thin wires pass between them.
  - Its pin order is now reversed relative to the cell monitor. CELL1 and PACK_P each hop under the pads through the gaps between J2's pads. CELL2 stays on top. J2's GND pin has its own via.
- **Programming / rescue pads (back, under the MCU's top edge), 2 mm pitch:**
  - TP6 SWCLK, TP5 SWDIO, TP7 3V3, TP8 GND
  - Use them with an ST-Link if USB DFU ever fails.
  - NRST isn't brought out. If the firmware disables SWD, bridge JP1 (BOOT0) and the ROM bootloader starts instead.
- **Probe pads:**
  - TP9 VBUS (top, on the VBUS trace)
  - TP10 BAT (top, on the BAT_P trace)
  - TP11 I2C SDA and TP12 I2C SCL (top, next to the cell monitor; use them for a logic analyzer)
- **Final verification:**
  - ERC 0 errors.
  - Pin-by-pin netlist check: 204/204 pass.
  - DRC 0 errors, 0 unconnected, 0 schematic-parity issues.
  - No 90-degree corners (traces, junctions or zones).
  - No unintended vias in pads (only the CH224K thermal vias).

## Rev B (2026-10-02): charging through the balance plug + cell LEDs
- **Charge path:** the Deans wire pads TP1–TP4, the MAIN_P copper and its vias are gone. F1 (now 2.5 A) feeds J2 pin 1 (pack +) through a 0.8 mm trace that drops to the back layer at x 211.2 and comes up under the pad (two vias at each end). Board ground reaches the pack through J2 pin 4 (on both GND pours, plus two vias).
- **Cell LED pads (top, between the USB-C and the MCU), 2.5 mm pitch, silk A / B / C:**
  - TP13 LED A, TP14 LED B, TP15 LED C, each through a 100 Ω 0402 (R27–R29) to the MCU.
  - MCU pins: A = PA1 (pin 7), B = PA3 (pin 9), C = PA0 (pin 6). These pins used to go to the charger /INT, the CH224K PG and the BMS ALERT, which the firmware never read; those signals now only keep their pull resistors (R9, R3, R18).
  - **Remote LED board:** its own KiCad project in `led_board/` (schematic + PCB, checked by `led_board/verify_led.py`). One red/green LED per cell, wired in a triangle (charlieplexing, 3 wires and no GND wire). The part is Kingbright KPHBM-2012SURKCGKC (LCSC C598341, 4 pads: red anode 1 / cathode 2, green cathode 3 / anode 4; pins 1+3 go to the first line, pins 2+4 to the second). The first idea, any 2-pin bi-colour LED, works the same way:
    - cell 1 between A and B, cell 2 between B and C, cell 3 between C and A
    - red anode on the first pad of the pair, green anode on the second (for a common 2-pin bi-colour LED: the lead that lights red when it is positive goes to the first pad)
    - use standard red (~2.0 V) and yellow-green (~2.1 V, 565–570 nm) LEDs. "True green" (3 V) LEDs won't light from the 3.3 V MCU.
  - Firmware: red = cell still charging, green = cell at its target, all red blinking = fault. The LEDs are only on while a charger is connected and a charge (or storage run) is active. About 6 mA peak per LED, 1/3 duty.
- **JP2** (FAST CHARGE jumper, back, under the MCU) is now part of the layout scripts (`layout_work/regionG.py`), and the two logos plus the back-side text are carried into `layout_work/base.kicad_pcb`.
- **Final verification:**
  - ERC 0 errors, 0 warnings.
  - Pin-by-pin netlist check (`layout_work/verify.py`): 208/208 pass.
  - DRC 0 errors, 0 unconnected, 0 schematic-parity issues. 4 cosmetic warnings, the same as Rev A (USB-C silk at the edge, the charger's pin-1 marker, the TrueType back text).
  - The layout scripts reproduce the installed board exactly.

## Rev B fix (2026-10-02): J2 pin order matches the pack plug
- The first Rev B layout had J2 pin 1 = pack −. Checked against the real pack: looking at the plug's holes with the latch bumps up, the black wire is on the LEFT, so once plugged in it lands on header **pin 4**. The old order would have put the pack's negative on the board's pack + and shorted the cells through the board.
- New J2 order: **pin 1 = pack + (red), pin 2 = cell 2+, pin 3 = cell 1+, pin 4 = pack − (black)**. The pads now run GND, CELL1, CELL2, PACK_P top to bottom, the same order as the cell monitor inputs, so the old hops under J2 are gone.
- J2 now has a 3D model (`3dmodels/JST_XH_S4B-XH-SM4-TB.step`, from the EasyEDA official library). The latch windows are on the top of the connector. `production/assembly/J2-orientation.png` shows the pinout from the end and from above.
- Checks: ERC 0/0, `verify.py` 208/208, DRC 0 errors / 0 unconnected / 0 parity issues (same 4 cosmetic warnings).

