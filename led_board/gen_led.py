"""Generate the schematic of the cell-LED board (remote board on top of the stock tube).

python led_board/gen_led.py led_board/led_board.kicad_sch

Reuses the symbol/label/emit machinery of layout_work/gen.py (same net-label-on-pin-endpoint style,
so every connection is explicit and ERC-checkable); only the parts below are this board's own.

Wiring (matches the main board firmware, hw.c cled_scan): cell i sits between line i and line i+1
(cell 1 A-B, cell 2 B-C, cell 3 C-A). Each LED is a Kingbright KPHBM-2012SURKCGKC (4 pads, datasheet
pinout: red anode 1 / red cathode 2, green cathode 3 / green anode 4). Wired back to back:
  pins 1 + 3 -> first line  (red lights when it is +)
  pins 2 + 4 -> second line (green lights when it is +)
"""
import os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
LW = os.path.join(os.path.dirname(HERE), 'layout_work')
sys.path.insert(0, LW)
src = open(os.path.join(LW, 'gen.py'), encoding='utf8').read()
helpers = src[:src.index('# ================================================================= BLOCK A')]
emit = src[src.index('# ================================================================= emit'):]
helpers = helpers.replace('PROJECT = "usbc pd airsoft module"', 'PROJECT = "led_board"')
helpers = helpers.replace('ROOT_UUID = "5732c36f-2098-4755-bf77-38f4abb30fd8"', 'ROOT_UUID = "7b0f5f0e-3c1a-4f53-9d2a-6c1ed0a1b2c3"')
old_title = emit[emit.index("out.append('\\t(title_block"):emit.index("out.append(lib_symbols_sexpr())")]
emit = emit.replace(old_title,
    "out.append('\\t(title_block\\n\\t\\t(title \"Cell LED board\")\\n'\n"
    "           '\\t\\t(date \"2026-10-02\")\\n\\t\\t(rev \"A\")\\n'\n"
    "           '\\t\\t(comment 1 \"3 red/green LEDs, one per cell, charlieplexed from the charger pads A/B/C\")\\n'\n"
    "           '\\t\\t(comment 2 \"No parts but the LEDs: the 100R resistors are on the charger board (R27-R29)\")\\n\\t)\\n')\n")
emit = emit.replace("(paper \"A2\")", "(paper \"A4\")")

exec(compile(helpers, 'gen.py:helpers', 'exec'))

# ================================================================= the board
LED_FP = 'LED_SMD:LED_Kingbright_APHBM2012_2x1.25mm'
LED_PROPS = {'MPN': 'Kingbright KPHBM-2012SURKCGKC', 'LCSC': 'C598341',
             'Note': 'pin1 red A, pin2 red K, pin3 green K, pin4 green A (datasheet)'}
LINES = ['LED_A', 'LED_B', 'LED_C']

box(10, 10, 287, 200, "CELL LED BOARD (remote, top of the stock tube)")
for i in range(3):
    first, second = LINES[i], LINES[(i + 1) % 3]
    x = 60.96 + i * 76.2
    place('Device:LED_Dual_AKKA', 'D', 'KPHBM-2012SURKCGKC', LED_FP, x, 76.2,
          {'1': first, '2': second, '3': first, '4': second}, ref=f'S{i + 1}', props=LED_PROPS)   # S1-S3: the user's names on the PCB
    text(f"Cell {i + 1}: {first[-1]} + = red, {second[-1]} + = green", x - 22, 96.52, 1.6)

for i, net in enumerate(LINES):
    place('Connector:TestPoint', 'TP', net, 'TestPoint:TestPoint_Pad_D1.5mm', 60.96 + i * 76.2, 132.08,
          {'1': net}, ref=f'TP{13 + i}')   # same names as the charger board's pads
for i in range(2):
    place('Mechanical:MountingHole', 'H', 'M2', 'MountingHole:MountingHole_2.2mm_M2',
          228.6 + i * 15.24, 132.08, {}, ref=f'H{i + 1}')
text("H1 / H2: M2 mounting holes, one on each short side of the 20 x 10 mm board.", 210, 142, 1.6)
text("Wire pads A / B / C: thin wires to the charger board pads TP13 (A), TP14 (B), TP15 (C).\n"
     "Charger side drives PA1 = A, PA3 = B, PA0 = C through 100R each (R27-R29) and scans one line + per ms.\n"
     "Cell 1 LED between A-B, cell 2 between B-C, cell 3 between C-A (the same order as the firmware).\n"
     "LED: Kingbright KPHBM-2012SURKCGKC (LCSC C598341), 2.0 x 1.25 mm, red 1.95 V / yellow-green 2.1 V.\n"
     "Datasheet pinout: red = anode 1, cathode 2; green = anode 4, cathode 3 -> pins 1+3 to the first line,\n"
     "pins 2+4 to the second line. Two LEDs in series need ~4 V > 3.3 V, so no LED lights by accident.",
     20, 150, 1.6)

OUT = sys.argv[1]
exec(compile(emit, 'gen.py:emit', 'exec'))
