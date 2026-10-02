# =====================================================================================
# REGION D : BQ7692003 cell monitor (U4 @ 192.0, 110.6), balance plug J2, fuse F1,
#            Deans wire pads TP1..TP4 (bottom), BAT_P out, back-side text area.
# Back-side TEXT AREA (kept free of vias/bottom copper except the GND plane): x 198..209.8, y 101.5..116.2
# =====================================================================================
P = lambda r, n: pad(r, n)
def via_s(net, x, y): b.via(net, x, y, **VIA_S)

# ---------------- right side of U4: ALERT hop, SRP/SRN -> VSS under the body, VC lanes
x20, y20 = P('U4', 20); x19, y19 = P('U4', 19); x18, y18 = P('U4', 18); x3, y3 = P('U4', 3)
av = (195.95, 106.85)
b.track('BMS_ALERT', [(x20 + 0.5, y20), (av[0], y20 - 0.4), av], W_SIG); via_s('BMS_ALERT', *av)
r18a, r18b = P('R18', 1), P('R18', 2)
b.track('BMS_ALERT', [av, (av[0], r18a[1] + 0.4), (r18a[0] + 0.4, r18a[1]), r18a], W_SIG)
b.track('GND', [r18b, (r18b[0] - 0.8, r18b[1])], 0.3); via_s('GND', r18b[0] - 0.8, r18b[1])
b.track('BMS_ALERT', [av, (184.9, av[1]), (184.9, LANE['BMS_ALERT'] - 0.4), (184.5, LANE['BMS_ALERT'])], W_SIG, B)
# SRN/SRP tied to VSS straight under the IC body, VSS -> GND via on the left
b.track('GND', [(x19 - 0.5, y19), (x18 - 0.5, y18)], 0.25)
b.track('GND', [(x18 - 0.5, y18), (x3 + 0.5, y3)], 0.25)
gvl = (187.6, 108.65)
b.track('GND', [(x3 - 0.5, y3), (188.0, y3), gvl], 0.25); via_s('GND', *gvl)

x17, y17 = P('U4', 17); x16, y16 = P('U4', 16); x15, y15 = P('U4', 15)
x14, y14 = P('U4', 14); x13, y13 = P('U4', 13); x12, y12 = P('U4', 12)
LV = {'BMS_VC0': 108.0, 'BMS_VC1': 109.7, 'BMS_VC2': 111.4, 'BMS_VC5': 113.1}
XB = 195.95                                  # VC2/3/4 bar
b.track('BMS_VC2', [(x15 + 0.5, y15), (XB, y15), (XB, y13), (x13 + 0.5, y13)], 0.25)
b.track('BMS_VC2', [(x14 + 0.5, y14), (XB, y14)], 0.25)
b.track('BMS_VC0', [(x17 + 0.5, y17), (196.4, y17), (197.2 - 0.0, LV['BMS_VC0'] + 0.8), (197.2, LV['BMS_VC0'])], W_SIG)
b.track('BMS_VC1', [(x16 + 0.5, y16), (196.6, y16), (197.2, LV['BMS_VC1'])], W_SIG)
b.track('BMS_VC2', [(XB, y14), (196.4, y14), (196.6, LV['BMS_VC2'])], 0.25)
b.track('BMS_VC5', [(x12 + 0.5, y12), (196.6, y12), (196.8, LV['BMS_VC5'])], W_SIG)
RS = {'BMS_VC0': 'R16', 'BMS_VC1': 'R15', 'BMS_VC2': 'R14', 'BMS_VC5': 'R13'}
SRC = {'R16': ('GND', 1), 'R15': ('CELL1_P', 2), 'R14': ('CELL2_P', 3), 'R13': ('PACK_P', 4)}
starts = {'BMS_VC0': (197.2, LV['BMS_VC0']), 'BMS_VC1': (197.2, LV['BMS_VC1']),
          'BMS_VC2': (196.6, LV['BMS_VC2']), 'BMS_VC5': (196.8, LV['BMS_VC5'])}
for net, y in LV.items():
    rp = P(RS[net], 2)
    b.track(net, [starts[net], (rp[0], y)], W_SIG)
# differential cell-input caps, staggered in two columns
for c, top_net, bot_net in (('C26', 'GND', 'BMS_VC0'), ('C25', 'BMS_VC0', 'BMS_VC1'),
                            ('C24', 'BMS_VC1', 'BMS_VC2'), ('C23', 'BMS_VC2', 'BMS_VC5')):
    p1, p2 = P(c, 1), P(c, 2)              # rot 90: pad1 bottom, pad2 top
    if bot_net in LV:
        dy = LV[bot_net] - p1[1]
        b.track(bot_net, [p1, (p1[0] + abs(dy), LV[bot_net])], W_SIG)
    if top_net in LV:
        dy = p2[1] - LV[top_net]
        b.track(top_net, [p2, (p2[0] + abs(dy), LV[top_net])], W_SIG)   # 45-degree join onto the lane
gt = P('C26', 2); b.track('GND', [gt, (gt[0], gt[1] - 0.8)], 0.3); via_s('GND', gt[0], gt[1] - 0.8)

# balance plug lanes -> filter resistors. J2 is rotated so its pads run PACK_P, CELL2, CELL1, GND
# top->bottom (reverse of the U4 VC order): CELL2 stays on top, CELL1 and PACK_P hop under it
# through the gaps between J2's pads (all hops at x >= 216.6, clear of the back-side text area).
jP, j3, j2c, jG = P('J2', 4), P('J2', 3), P('J2', 2), P('J2', 1)
JX = P('J2', 1)[0] - 2.25                       # left edge of the J2 pads
r15, r14, r13 = P('R15', 1), P('R14', 1), P('R13', 1)
# CELL2: top, straight out then 45 deg down to its lane
b.track('CELL2_P', [(JX + 0.5, j3[1]), (JX - 0.5, j3[1]), (JX - 0.5 - (LV['BMS_VC2'] - j3[1]), LV['BMS_VC2']),
                    (r14[0] + 0.0, LV['BMS_VC2'])], 0.3)
# CELL1: pad -> via above it -> bottom -> via -> lane at y(VC1)
c1v1 = (JX + 0.7, 110.6); c1v2 = (JX - 2.3, LV['BMS_VC1'])
b.track('CELL1_P', [(c1v1[0], j2c[1]), c1v1], 0.3); via_s('CELL1_P', *c1v1)
b.track('CELL1_P', [c1v1, (c1v1[0] - (c1v1[1] - c1v2[1]), c1v2[1]), c1v2], 0.3, B); via_s('CELL1_P', *c1v2)
b.track('CELL1_P', [c1v2, (r15[0], c1v2[1])], 0.3)
# PACK_P: pad -> via below it -> bottom straight down -> via between CELL1 and GND pads -> lane at y(VC5)
pv1 = (JX + 1.7, 108.1); pv2 = (JX + 1.7, LV['BMS_VC5'])
b.track('PACK_P', [(pv1[0], jP[1]), pv1], 0.3); via_s('PACK_P', *pv1)
b.track('PACK_P', [pv1, pv2], 0.3, B); via_s('PACK_P', *pv2)
b.track('PACK_P', [pv2, (r13[0], pv2[1])], 0.3)
# PACK_P also feeds the BMS supply filter R12 (45 deg branch off the lane)
r12a, r12b = P('R12', 1), P('R12', 2)
bx0 = r13[0] + 1.6
b.track('PACK_P', [(bx0, pv2[1]), (bx0 - (r12a[1] - pv2[1]), r12a[1]), r12a], 0.3)
# J2 GND pin -> GND via in the gap below it ; R16 (VC0 filter) GND side -> C26 GND via
gv = (JX + 1.1, 115.65)
b.track('GND', [(gv[0], jG[1]), gv], 0.4); b.via('GND', *gv)
r16g = P('R16', 1); c26g = P('C26', 2)
b.track('GND', [r16g, (r16g[0], 106.75), (r16g[0] - 0.45, 106.3), (c26g[0] + 0.45, 106.3), c26g], 0.3)

# ---------------- left side of U4
x4, y4 = P('U4', 4); x5, y5 = P('U4', 5); x6, y6 = P('U4', 6); x7, y7 = P('U4', 7)
x8, y8 = P('U4', 8); x9, y9 = P('U4', 9); x10, y10 = P('U4', 10)
SDA_X, SCL_X = 182.0, 182.6
b.track('I2C_SDA', [(x4 - 0.5, y4), (SDA_X, y4), (SDA_X, LANE['I2C_SDA'])], W_SIG); via_s('I2C_SDA', SDA_X, LANE['I2C_SDA'])
b.track('I2C_SCL', [(x5 - 0.5, y5), (SCL_X, y5), (SCL_X, LANE['I2C_SCL'])], W_SIG); via_s('I2C_SCL', SCL_X, LANE['I2C_SCL'])
r24a, r24b = P('R24', 1), P('R24', 2); r25a, r25b = P('R25', 1), P('R25', 2)
b.track('I2C_SCL', [r24b, (SCL_X, r24b[1])], W_SIG)
b.track('I2C_SDA', [r25b, (SDA_X, r25b[1])], W_SIG)
pv1 = (r24a[0] + 0.2, LANE['+3V3']); pv2 = (r25a[0], LANE['+3V3'])
b.track('+3V3', [r24a, (pv1[0], r24a[1]), pv1], 0.25); via_s('+3V3', *pv1)
b.track('+3V3', [r25a, pv2], 0.25); via_s('+3V3', *pv2)
# TS1: TH2 to GND + R17 from BMS_BOOT
TS_X = 185.4
th2a = P('TH2', 1); r17a, r17b = P('R17', 1), P('R17', 2)
b.track('BMS_TS1', [(x6 - 0.5, y6), (TS_X, y6), (TS_X, th2a[1])], W_SIG)
b.track('BMS_TS1', [(TS_X, th2a[1]), th2a], W_SIG)
b.track('BMS_TS1', [r17b, (TS_X, r17b[1])], W_SIG)
bv = (183.3, LANE['BMS_BOOT'])
b.track('BMS_BOOT', [bv, r17a], W_SIG); via_s('BMS_BOOT', *bv)
# CAP1 / REGOUT caps
c27a = P('C27', 1); c28a = P('C28', 1)
b.track('BMS_CAP1', [(x7 - 0.5, y7), (c27a[0], y7), c27a], W_SIG)
b.track('BMS_REGOUT', [(x8 - 0.5, y8), (c28a[0], y8), c28a], W_SIG)
# BAT/REGSRC -> C21, C22, R12 (PACK_P filter)
c21a = P('C21', 1); c22a = P('C22', 1)
BX = 188.1
b.track('BMS_BAT', [(x9 - 0.5, y9), (BX, y9), (BX, 115.0), (r12b[0], 115.0), r12b], 0.3)
b.track('BMS_BAT', [(x10 - 0.5, y10), (BX, y10)], 0.3)
b.track('BMS_BAT', [c22a, (c22a[0], 115.0)], 0.3)
b.track('BMS_BAT', [c21a, (c21a[0], 115.0)], 0.3)

# ---------------- BAT_P out along the top edge to F1, fuse -> MAIN_P -> vias -> Deans pads (bottom)
f2 = P('F1', 2); f1 = P('F1', 1)
b.track('BAT_P', [(177.35, 101.3), (f2[0] - 0.6, 101.3), (f2[0], f2[1])], 1.0)
MV = [(217.4, 101.6), (217.4, 102.6), (216.5, 101.6), (216.5, 102.6)]
b.track('MAIN_P', [f1, (217.4, f1[1])], 1.0)
for v in MV: b.via('MAIN_P', *v)
b.zone('MAIN_P', B, [(215.9, 100.6), (228.6, 100.6), (228.6, 108.6), (221.15, 108.6), (221.15, 104.6), (215.9, 104.6)],
       priority=5, name='MAIN_P_PADS')

# ---------------- text-area marker (back) for the user's custom text
b.rect(198.0, 101.5, 209.8, 116.2, pcbnew.B_Fab, 0.12)
b.text('CUSTOM TEXT AREA', 203.9, 108.85, pcbnew.B_Fab, 0.9, mirror=True)
