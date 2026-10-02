# Ordering the Rev A board

## Upload

Upload `airsoft-module-rev-a-gerbers.zip`. It contains the Gerbers, the Excellon drill files (plated and unplated in separate files) and the drill maps.

## Board settings

| Setting | Value |
|---|---|
| Layers | 2 |
| Size | 135.1 × 21.1 mm |
| Thickness | 1.6 mm |
| Copper | 1 oz (35 µm) outer |
| Min track / space | 0.2 mm / 0.15 mm (standard capability) |
| Smallest drill | **0.25 mm vias** (62 of them). Standard at PCBWay. Some fabs add a small surcharge below 0.3 mm; accept it if asked. |
| Surface finish | **ENIG recommended.** Flat pads make the 0.4 mm-pitch BQ25798 QFN (U3) much easier to solder. HASL works but is bumpier. |
| Solder mask / silk | any colour |
| Castellations / edge plating | none |
| Remove order number | yes (if offered), or "specify a location" in the back-side text area |

## Stencil (recommended for hand assembly)

Order a **frameless stencil, top side**. The paste layer is `F_Paste.gtp` in the zip.

The bottom side has only 13 easy parts: 0402/0603 passives, JP2 and the test pads. Hand-solder those; no bottom stencil is needed.

## Assembly files (`assembly/`)

| File | Contents |
|---|---|
| `bom.csv` | Bill of materials. Generic passives have no part number (any brand). ICs and connectors have exact part numbers. |
| `pick-and-place.csv` | Part positions and rotations for both sides (for PCBA or reference) |
| `assembly-top.pdf` / `assembly-bottom.pdf` | Placement drawings with pad outlines and reference designators. The bottom one is mirrored, as seen from the back. |

## Before you place the order

1. Add your custom text on the back silkscreen in the reserved area (x 198–210 mm, y 101–116 mm), then re-export the Gerbers.
2. Check the J2 balance-plug orientation against your pack: pin 1 = black (negative).
3. Solder order: U3 (QFN) and U1 first with paste and hot air or a hot plate, then the other ICs, then passives, then connectors last. Leave JP2 open for slow charging.
