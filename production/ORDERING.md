# Ordering the Rev B board

## Upload

Upload `airsoft-module-rev-b-gerbers.zip`. It contains the Gerbers, the Excellon drill files (plated and unplated in separate files) and the drill maps.

## Board settings

| Setting | Value |
|---|---|
| Layers | 2 |
| Size | 135.1 × 21.1 mm |
| Thickness | 1.6 mm |
| Copper | 1 oz (35 µm) outer |
| Min track / space | 0.2 mm / 0.15 mm (standard capability) |
| Smallest drill | **0.25 mm vias** (60 of them). Standard at PCBWay. Some fabs add a small surcharge below 0.3 mm; accept it if asked. |
| Surface finish | **ENIG recommended.** Flat pads make the 0.4 mm-pitch BQ25798 QFN (U3) much easier to solder. HASL works but is bumpier. |
| Solder mask / silk | any colour |
| Castellations / edge plating | none |
| Remove order number | yes (if offered), or "specify a location" in the back-side text area |

## Stencil (recommended for hand assembly)

Order a **frameless stencil, top side**. The paste layer is `F_Paste.gtp` in the zip.

The bottom side has only 9 easy parts: C5, C6, C19, R5 (0402/0603), JP2 and the SWD test pads TP5–TP8. Hand-solder those; no bottom stencil is needed.

## Assembly files (`assembly/`)

| File | Contents |
|---|---|
| `bom_lcsc.csv` | **Upload this to the LCSC BOM tool (lcsc.com/bom).** Every line has a verified LCSC part number (C-number) and the quantity for one board; multiply in the web tool. |
| `parts_not_at_lcsc.csv` | **Buy these somewhere else** (Mouser, DigiKey, TME, Farnell). Right now that's only **U5 = Diodes AP7381-33SA-7**: the LCSC listing was discontinued. Don't swap in another 3.3 V SOT-23 regulator. The common ones (TLV760, LM3480, HT7533, ME6203) have a different pin order and would put USB voltage on the 3.3 V rail. |
| `bom_jlcpcb.csv` | The same parts in JLCPCB assembly format (Comment / Designator / Footprint / LCSC Part #), in case JLC assembles the boards later. |
| `bom.csv` | Design BOM from the schematic, with values and notes. |
| `pick-and-place.csv` | Part positions and rotations for both sides (for PCBA or reference) |
| `assembly-top.pdf` / `assembly-bottom.pdf` | Placement drawings with pad outlines and reference designators. The bottom one is mirrored, as seen from the back. |

## Parts notes (checked 2026-10-02)

- **U5 AP7381-33SA-7:** not available at LCSC; see `parts_not_at_lcsc.csv`.
- **U1 CH224K (C970725):** only 3 in stock at LCSC. Fine for up to 3 boards; for more, buy extra CH224K elsewhere.
- **C31/C32/C34/C35:** C6119778 (100 nF 25 V), the part LCSC's BOM tool proposes for this line. The design needs ≥ 16 V.
- **F1 (C310999):** AEM F1206SB2500V032TM, 2.5 A fast-acting 1206 fuse, 50 A breaking capacity. Rev B charges the pack through the balance plug; F1 protects those thin wires. Don't fit a bigger fuse.
- **R27–R29:** 100 Ω 0402, the same part as R5 (C25076), for the cell LED lines.
- **Minimum order:** LCSC sells most 0402/0603 passives in lots of 100, so expect spare parts.
- **Stock changes daily:** re-check the BOM tool result right before you pay.

## Before you place the order

1. Add your custom text on the back silkscreen in the reserved area (x 198–210 mm, y 101–116 mm), then re-export the Gerbers.
2. Check the J2 balance-plug orientation against your pack (`assembly/J2-orientation.png`): pin 4 = black (pack −), pin 1 = red (pack +). This matches a plug whose black wire is on the left when you look at its holes with the latch bumps up. The pack charges through this plug, so a mismatch would short the cells.
3. Check the wire gauge of your pack's balance lead: 22–24 AWG is fine for fast charge (1.4 A, JP2 bridged); on 26 AWG leave JP2 open (0.7 A).
4. Solder order: U3 (QFN) and U1 first with paste and hot air or a hot plate, then the other ICs, then passives, then connectors last. Leave JP2 open for slow charging.

## Cell LED board (separate small board, `led_board/`)

Its own KiCad project: `led_board/led_board.kicad_sch` and `.kicad_pcb`. It has three LEDs (S1-S3, one per cell) and three wire pads TP13 / TP14 / TP15. The pads have the same names as the module's pads (A / B / C), so each wire joins the two pads with the same name.

- **LED:** Kingbright **KPHBM-2012SURKCGKC**, LCSC **C598341**, 2.0 x 1.25 mm, 4 pads: red 1.95 V + yellow-green 2.1 V in one package. Order 3 (plus spares).
- No resistors on this board: they are on the module (R27-R29).
- Wiring is in the schematic and checked pin by pin (`led_board/verify_led.py`). Red = cell still charging, green = cell full, all red blinking = fault.
- Board: 20 x 10 mm, 2 layers, 1 mm corner radius, two M2 (2.2 mm) mounting holes centred on the short sides.
- **Files:** `production/led_board/`: upload `led-board-rev-a-gerbers.zip` (same fab settings as the main board; 0.3 mm vias, so no small-drill surcharge). `bom_lcsc.csv` is for the LCSC BOM tool, `led-board-rev-a-production.zip` has everything.
- **Soldering:** the LEDs go on top (polarity mark on the pad 2/3 end, see `assembly-top.pdf`). The wire pads are on the back, marked A / B / C: wire A to the module's A, B to B, C to C.
