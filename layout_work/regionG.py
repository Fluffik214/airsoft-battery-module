# =====================================================================================
# REGION G : cell-LED wire pads TP13 LED_A, TP14 LED_B, TP15 LED_C (near the USB-C) with their
#            100R series resistors R27..R29 ; JP2 fast-charge jumper (back, under U6).
# Lanes run west along the free top-layer strip between the MCU via row and the VBUS lane.
# =====================================================================================
P = lambda r, n: pad(r, n)
def via_s(net, x, y): b.via(net, x, y, **VIA_S)
# resistors (rot 90): pad 1 (MCU side) at the bottom, pad 2 (wire pad side) at the top
for r, t, n in (('R27', 'TP13', 'LED_A'), ('R28', 'TP14', 'LED_B'), ('R29', 'TP15', 'LED_C')):
    b.track(n, [P(r, 2), P(t, 1)], 0.25)
# CLED_C: pin 6 (PA0) straight down, west on the upper lane into R29
x6, y6 = P('U6', 6); r29 = P('R29', 1)
b.track('CLED_C', [(x6, y6 + 0.6), (x6, r29[1] - 0.0), r29], W_SIG)
# CLED_B: pin 9 (PA3) down past the lane above, west on the lower lane, up into R28
x9, y9 = P('U6', 9); r28 = P('R28', 1)
LB = 117.6
b.track('CLED_B', [(x9, y9 + 0.6), (x9, LB), (r28[0], LB), r28], W_SIG)
# CLED_A: from the via under the MCU (regionB) down the bottom layer, west under the lanes, up at R27
r27 = P('R27', 1); LA = 118.1
b.track('CLED_A', [CLA_V, (CLA_V[0], LA - 0.5), (CLA_V[0] - 0.5, LA), (r27[0], LA)], W_SIG, B)
via_s('CLED_A', r27[0], LA)
b.track('CLED_A', [(r27[0], LA), r27], W_SIG)
# silk letters above the wire pads
for t, s in (('TP13', 'A'), ('TP14', 'B'), ('TP15', 'C')):
    x, y = P(t, 1); b.text(s, x, y - 1.85, pcbnew.F_SilkS, 0.8)

# JP2 (FAST CHARGE, back, under U6): pin 14 (PB1) -> via -> bottom -> JP2 pad 1 ; pad 2 sits in the GND pour
x14, y14 = P('U6', 14); j1 = P('JP2', 1)
b.track('FAST_CHG_N', [(x14, y14 + 0.6), (x14, 105.2)], W_SIG)
b.via('FAST_CHG_N', x14, 105.2, **VIA_S)
b.track('FAST_CHG_N', [(x14, 105.2), j1], W_SIG, B)
