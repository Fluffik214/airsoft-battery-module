"""Route the cell-LED board on top of the user's placement (footprints are not moved).
kicad-python led_board/route_led.py led_board/led_board.kicad_pcb [out.kicad_pcb]

Placement (by the user): S1-S3 (cell LEDs) in a row on top (y ~102.5), wire pads TP15 (C) / TP14 (B) / TP13 (A) on the back
at y 108.5, M2 holes at the short ends.
  - top row: S1.4-S2.1 (B) and S2.4-S3.1 (C) link straight across the gaps, each with a via in the gap
  - A: S1.1 / S3.4 run out to the ends, all A pins meet on one top-layer line under the LEDs, one via to TP13
  - B, C: a via under each lower B/C pad, back-layer wiring to TP14 / TP15; C crosses B on a short top strip
"""
import os, sys
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(os.path.dirname(HERE), 'layout_work'))
import pcbnew
from kb import Board, F, B, chamfer_corners, smooth_tees

src = sys.argv[1]; dst = sys.argv[2] if len(sys.argv) > 2 else src
b = Board(src)
P = b.pad
W = 0.25                                   # ~6 mA LED lines
V = dict(d=0.6, drill=0.3)                 # standard vias (no small-drill surcharge)

d1 = {n: P('S1', n) for n in '1234'}; d2 = {n: P('S2', n) for n in '1234'}; d3 = {n: P('S3', n) for n in '1234'}
tpA, tpB, tpC = P('TP13', 1), P('TP14', 1), P('TP15', 1)
Y_ROW = 104.9                              # vias under the lower B / C pads
Y_A = 106.2                                # A line (top)
Y_BUS = 103.8                              # B / C links on the back, under the LEDs
Y_C = 107.0                                # C strip (top) under the A line
XL, XR = d1['1'][0] - 1.025, d3['4'][0] + 1.075   # A runs out past both ends (104.85 / 114.55)

# clear the old routing (after reading the pads). Delete(), not Remove(): a removed track's python wrapper gets
# garbage-collected and corrupts the board in memory (KiCad 10 SWIG)
for t in list(b.b.GetTracks()):
    b.b.Delete(t)

# ---- top row links across the LED gaps, each with a via down to the back
vb1 = ((d1['4'][0] + d2['1'][0]) / 2, d2['1'][1])
vc1 = ((d2['4'][0] + d3['1'][0]) / 2, d2['4'][1])
b.track('LED_B', [d1['4'], (d1['4'][0] + 0.3, d2['1'][1]), d2['1']], W); b.via('LED_B', *vb1, **V)
b.track('LED_C', [d2['4'], (d3['1'][0] - 0.3, d2['4'][1]), d3['1']], W); b.via('LED_C', *vc1, **V)

# ---- A: entirely on top, then one via next to TP13
b.track('LED_A', [d1['1'], (XL, d1['1'][1]), (XL, Y_A), (XR, Y_A)], W)
b.track('LED_A', [d3['4'], (XR, d3['4'][1]), (XR, 107.15)], W)
b.track('LED_A', [d1['3'], (d1['3'][0], Y_A)], W)
b.track('LED_A', [d3['2'], (d3['2'][0], Y_A)], W)
va = (XR, 107.15); b.via('LED_A', *va, **V)          # via position as moved by the user
b.track('LED_A', [va, (XR, 108.05), tpA], W, B)

# ---- B: vias under S1.2 and S2.3, back-layer link at Y_BUS (also picks up the top-row via), down to TP14
b1 = (d1['2'][0], Y_ROW); b2 = (d2['3'][0], Y_ROW)
b.track('LED_B', [d1['2'], b1], W); b.via('LED_B', *b1, **V)
b.track('LED_B', [d2['3'], b2], W); b.via('LED_B', *b2, **V)
b.track('LED_B', [b1, (b1[0], Y_BUS), (b2[0], Y_BUS), b2, tpB], W, B)
b.track('LED_B', [vb1, (vb1[0], Y_BUS)], W, B)

# ---- C: vias under S2.2 and S3.3; east group joined on the back, then a top strip under A to TP15
c1 = (d2['2'][0], Y_ROW); c2 = (d3['3'][0], Y_ROW)
b.track('LED_C', [d2['2'], c1], W); b.via('LED_C', *c1, **V)
b.track('LED_C', [d3['3'], c2], W); b.via('LED_C', *c2, **V)
b.track('LED_C', [vc1, (vc1[0], Y_BUS), (c2[0], Y_BUS), c2, (c2[0], Y_C)], W, B)
b.track('LED_C', [c1, (c1[0], Y_C)], W, B)
for x in (c2[0], c1[0], tpC[0]):
    b.via('LED_C', x, Y_C, **V)
b.track('LED_C', [(c2[0], Y_C), (tpC[0], Y_C)], W)
b.track('LED_C', [(tpC[0], Y_C), tpC], W, B)

for _ in range(3):
    chamfer_corners(b)
smooth_tees(b)
b.save(dst)
print('routed ->', dst)
