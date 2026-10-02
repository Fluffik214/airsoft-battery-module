# =====================================================================================
# REGION C : BQ25798 charger, U3 rot 0 @ (163.0, 112.4)
# TI layout guideline: 0.1uF PMID/SYS caps on the pins, SW1/SW2 via under the IC to the
# inductor on the bottom layer, BTST caps on the bottom at those vias.
# Bottom-side parts: C5, C6 (BTST), C19 (REGN), R5 (BATP).
# Bottom bus lanes occupy y 116.65..120.2 -> no vias there except lane taps.
# =====================================================================================
P = lambda r, n: pad(r, n)
def via_s(net, x, y): b.via(net, x, y, **VIA_S)

# ---------------- top-row power pins: C10 (PMID 100n) / C16 (SYS 100n), GND bar to pin 27
x29, y29 = P('U3', 29); x27, y27 = P('U3', 27); x25, y25 = P('U3', 25)
x28, y28 = P('U3', 28); x26, y26 = P('U3', 26)
c10p, c10g = P('C10', 1), P('C10', 2)
c16p, c16g = P('C16', 1), P('C16', 2)
b.track('PMID', [(x29, y29 - 0.3), (x29, c10p[1])], 0.2)
b.track('SYS', [(x25, y25 - 0.3), (x25, c16p[1])], 0.2)
b.track('GND', [(x27, y27 - 0.3), (x27, c10g[1])], 0.2)
b.track('GND', [c10g, c16g], 0.4)
b.track('GND', [(x27, c10g[1]), (x27, 106.75)], 0.4)
for vy in (107.6, 106.75):
    b.via('GND', x27, vy)

# ---------------- SW1 / SW2: pins -> vias under the IC -> bottom -> vias in the inductor pads
l1a, l1b = P('L1', 1), P('L1', 2)
SW1V = [(162.25, 111.95), (162.25, 112.75)]
SW2V = [(163.75, 111.95), (163.75, 112.75)]
b.track('SW1', [(x28, y28 + 0.3), (x28, 111.5), SW1V[0], SW1V[1]], 0.25)
b.track('SW2', [(x26, y26 + 0.3), (x26, 111.5), SW2V[0], SW2V[1]], 0.25)
for v in SW1V: b.via('SW1', *v)
for v in SW2V: b.via('SW2', *v)
# inductor vias sit just ABOVE the pads (not in-pad) so they can't wick solder away from the joint
VY = 102.55
L1V1 = [(l1a[0] - 0.4, VY), (l1a[0] + 0.4, VY)]
L1V2 = [(l1b[0] - 0.4, VY), (l1b[0] + 0.4, VY)]
for v in L1V1: b.via('SW1', *v)
for v in L1V2: b.via('SW2', *v)
for (va, vb), lp, net in ((L1V1, l1a, 'SW1'), (L1V2, l1b, 'SW2')):
    b.track(net, [va, vb], 0.6)                                  # top: via pair joined
    b.track(net, [(lp[0], VY), (lp[0], lp[1] - 1.2)], 0.8)       # top: into the pad
    b.track(net, [va, vb], 0.6, B)
b.track('SW1', [SW1V[1], SW1V[0], (162.25, 110.0), (l1a[0], 109.4), (l1a[0], VY)], 0.8, B)
b.track('SW2', [SW2V[1], SW2V[0], (163.75, 110.0), (l1b[0], 109.4), (l1b[0], VY)], 0.8, B)

# ---------------- left edge: VBUS sense pins, BTST1, REGN, STAT
x4, y4 = P('U3', 4); x5, y5 = P('U3', 5); x8, y8 = P('U3', 8); x9, y9 = P('U3', 9); x1, y1 = P('U3', 1)
bt1 = (159.85, 112.2); rg1 = (160.15, 113.05); st1 = (160.2, 110.5)
b.track('BTST1', [(x4 - 0.2, y4), (160.1, y4), bt1], W_SIG); via_s('BTST1', *bt1)
b.track('REGN', [(x5 - 0.2, y5), (160.4, y5), rg1], W_SIG); via_s('REGN', *rg1)
b.track('CHG_STAT', [(161.0, 110.8), (160.65, 110.8), (160.4, 110.55), st1], W_SIG); via_s('CHG_STAT', *st1)
b.track('VBUS', [(x8 - 0.2, y8), (160.4, y8 + 0.2), (158.4, y8 + 0.2)], 0.2)
b.track('VBUS', [(160.95, 114.0), (159.9, 114.0), (159.7, y8 + 0.2)], 0.2)
c4a, c4b = P('C4', 1), P('C4', 2)
b.track('GND', [c4b, (c4b[0], 110.6)], 0.3); b.via('GND', c4b[0], 110.6, **VIA_S)
c5a, c5b = P('C5', 1), P('C5', 2)            # bottom: BTST1 cap
b.track('BTST1', [bt1, c5a], 0.25, B); b.track('SW1', [c5b, SW1V[0]], 0.4, B)

# ---------------- REGN network on the bottom: rg1 -> C19 -> R10 via, -> right side, -> LED (left)
c19a = P('C19', 1)
r10v = (161.0, 115.55)
b.track('REGN', [rg1, (rg1[0], 113.6), c19a], 0.3, B)
b.track('REGN', [c19a, (c19a[0], r10v[1] - 0.4), r10v], 0.3, B); via_s('REGN', *r10v)
r10a, r10b = P('R10', 1), P('R10', 2)
b.track('REGN', [r10v, r10a], W_SIG)
rg2 = (165.95, 114.05)
b.track('REGN', [c19a, (165.95, c19a[1]), rg2], 0.3, B); via_s('REGN', *rg2)
rg3 = (151.65, 112.7)
b.track('REGN', [rg1, (rg1[0] - 0.4, 113.4), (152.0, 113.4), rg3], 0.25, B); via_s('REGN', *rg3)

# ---------------- STAT LED (D2 + R8) left of the charger
d2k, d2a = P('D2', 1), P('D2', 2); r8a, r8b = P('R8', 1), P('R8', 2)
st2 = (151.9, d2k[1])
b.track('CHG_STAT', [st1, (159.8, 109.9), (152.2, 109.9), (st2[0], 110.2), st2], 0.25, B); via_s('CHG_STAT', *st2)
b.track('CHG_STAT', [st2, d2k], W_SIG)
b.track('CHG_LED_A', [d2a, (d2a[0], r8b[1] - 0.5), r8b], W_SIG)
b.track('REGN', [rg3, r8a], W_SIG)

# ---------------- right edge stack: INT (far right), PROG, BTST2, BATP, REGN(ILIM), TS
x21, y21 = P('U3', 21); x20, y20 = P('U3', 20); x19, y19 = P('U3', 19)
x18, y18 = P('U3', 18); x17, y17 = P('U3', 17); x16, y16 = P('U3', 16)
INT_X = 171.2
b.track('CHG_INT_N', [(x21 + 0.2, y21), (INT_X, y21), (INT_X, LANE['CHG_INT_N'])], W_SIG)
via_s('CHG_INT_N', INT_X, LANE['CHG_INT_N'])
r9a, r9b = P('R9', 1), P('R9', 2)          # INT pull-up to +3V3, +3V3 tapped from its bus lane
b.track('CHG_INT_N', [(INT_X, r9b[1]), r9b], W_SIG)
b.track('+3V3', [r9a, (r9a[0], LANE['+3V3'])], 0.25); via_s('+3V3', r9a[0], LANE['+3V3'])
r4a = P('R4', 1)
b.track('CHG_PROG', [(x20 + 0.2, y20), (r4a[0], y20), r4a], W_SIG)
bt2 = (167.2, 113.25)
b.track('BTST2', [(x19 + 0.2, y19), (166.75, y19), bt2], W_SIG); via_s('BTST2', *bt2)
c6a, c6b = P('C6', 1), P('C6', 2)          # bottom: BTST2 cap
b.track('BTST2', [bt2, c6a], 0.25, B); b.track('SW2', [c6b, SW2V[1]], 0.4, B)
bpv = (166.65, 113.9)
b.track('CHG_BATP', [(x18 + 0.2, y18), (166.4, y18), bpv], W_SIG); via_s('CHG_BATP', *bpv)
r5a, r5b = P('R5', 1), P('R5', 2)          # bottom: BATP 100R, Kelvin into the BAT copper
b.track('CHG_BATP', [bpv, r5a], 0.25, B)
b.track('BAT_P', [r5b, (169.6, r5b[1]), (169.6, 111.4)], 0.3, B); via_s('BAT_P', 169.6, 111.4)
b.track('REGN', [(x17 + 0.2, y17), (165.5, y17), rg2], W_SIG)
r6a, r6b = P('R6', 1), P('R6', 2)          # TS bias: REGN -> R6 -> TS ; R7 + NTC TH1 to GND
b.track('REGN', [rg2, (rg2[0], r6a[1] - 0.5), r6a], W_SIG)
b.track('CHG_TS', [(164.4, 114.45), (164.4, r6b[1] - 0.4), r6b], W_SIG)
r7a = P('R7', 1); th1a = P('TH1', 1)
b.track('CHG_TS', [r6b, r7a], W_SIG)
b.track('CHG_TS', [r7a, th1a], W_SIG)

# ---------------- bottom edge: GND10/11 via under the IC, CE -> Q1 + R10, SCL/SDA down to taps
x10, y10 = P('U3', 10); x11, y11 = P('U3', 11); x13, y13 = P('U3', 13)
x14, y14 = P('U3', 14); x15, y15 = P('U3', 15)
gv = (162.3, 113.62)
b.track('GND', [(x10, y10 - 0.3), gv, (x11, y11 - 0.3)], 0.2); via_s('GND', *gv)
b.track('I2C_SCL', [(x14, y14 + 0.3), (x14, LANE['I2C_SCL'])], W_SIG); via_s('I2C_SCL', x14, LANE['I2C_SCL'])
b.track('I2C_SDA', [(x15, y15 + 0.3), (x15, 118.8), (x15 + 0.5, 119.3), (x15 + 0.5, LANE['I2C_SDA'])], W_SIG); via_s('I2C_SDA', x15 + 0.5, LANE['I2C_SDA'])
qd, qg = P('Q1', 3), P('Q1', 1)
b.track('CHG_CE_N', [(x13, y13 + 0.3), (x13, qd[1] - 0.5), (x13 - 0.5, qd[1]), qd], W_SIG)
b.track('CHG_CE_N', [r10b, (x13, r10b[1])], W_SIG)
cg = (159.05, LANE['CHG_EN'])
b.track('CHG_EN', [qg, (cg[0], qg[1]), cg], W_SIG); via_s('CHG_EN', *cg)
r11a, r11b = P('R11', 1), P('R11', 2)     # CHG_EN pull-down sits at the MCU pin
x8m, y8m = P('U6', 8)
b.track('CHG_EN', [r11a, (x8m, r11a[1])], W_SIG)
b.track('GND', [r11b, (r11b[0], 111.3)], 0.3); via_s('GND', r11b[0], 111.3)

# ---------------- SDRV cap
x24, y24 = P('U3', 24); c20a, c20b = P('C20', 1), P('C20', 2)
b.track('CHG_SDRV', [(x24 + 0.3, y24), c20a], W_SIG)
b.track('GND', [c20b, (c20b[0] + 0.75, c20b[1])], 0.3); via_s('GND', c20b[0] + 0.75, c20b[1])

# ---------------- bulk cap GND vias (PMID/SYS/VBUS/BAT)
for c in ('C7', 'C8', 'C9', 'C11', 'C12', 'C13', 'C14', 'C15'):
    xg, yg = P(c, 2); b.track('GND', [(xg, yg), (xg, yg - 0.95)], 0.5); b.via('GND', xg, yg - 0.95)
xg, yg = P('C2', 2); b.track('GND', [(xg, yg), (xg, yg - 0.8)], 0.5); b.via('GND', xg, yg - 0.8)
xg, yg = P('C3', 2); b.track('GND', [(xg, yg), (xg - 0.9, yg)], 0.5); b.via('GND', xg - 0.9, yg)
for c in ('C17', 'C18'):
    xg, yg = P(c, 2); b.track('GND', [(xg, yg), (xg + 0.95, yg)], 0.5); b.via('GND', xg + 0.95, yg)

# VBUS lane from the USB end into the VBUS input copper
b.track('VBUS', [(154.0, 119.3), (156.6, 119.3), (157.8, 118.1)], 1.0)
# ---------------- power copper (top)
b.zone('PMID', F, [(152.6, 108.55), (161.6, 108.55), (161.6, 109.05), (162.45, 109.05), (162.45, 110.0),
                   (161.85, 110.0), (161.85, 109.85), (152.6, 109.85)], priority=5, name='PMID')
b.zone('SYS', F, [(163.55, 109.05), (164.4, 109.05), (164.4, 108.55), (176.3, 108.55), (176.3, 109.85),
                  (164.15, 109.85), (164.15, 110.0), (163.55, 110.0)], priority=5, name='SYS')
b.zone('VBUS', F, [(157.0, 111.0), (161.1, 111.0), (161.1, 111.75), (160.1, 111.75), (158.6, 113.25),
                   (158.6, 118.8), (152.0, 118.8), (152.0, 119.8), (157.0, 119.8)], priority=5, name='VBUS_IN')
b.zone('BAT_P', F, [(165.0, 111.05), (176.6, 111.05), (176.6, 100.6), (178.1, 100.6), (178.1, 111.7),
                    (165.0, 111.7)], priority=5, name='BAT_OUT')
