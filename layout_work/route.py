"""Deterministic placement + hand-routing. kicad-python route.py base.kicad_pcb out.kicad_pcb out.json"""
import sys
import pcbnew
import os
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from kb import Board, F, B, chamfer_corners, smooth_tees
from place import PLACE, BACK

OVR = {
    'U1': (116.5, 108.0, 0), 'R1': (111.3, 107.7, 90), 'C1': (112.1, 104.3, 90), 'R2': (121.7, 107.4, -90),
    'U2': (112.4, 102.1, 180), 'D1': (110.3, 117.4, 90),
    'U6': (128.0, 106.6, 90), }
OVR.update({
    'C31': (127.675, 101.6, 90),
    'R3': (122.6, 104.6, -90), 'R21': (124.4, 114.2, -90), 'JP1': (122.3, 114.6, 90),
    'C34': (127.025, 111.5, -90), 'C32': (127.675, 114.1, -90),
    'R22': (133.0, 104.6, 90), 'R23': (134.6, 106.3, 0), 'C35': (134.6, 107.3, 0),    # R23/C35 moved down to make room for the LED row
    'R26': (135.0, 102.75, 0), 'D4': (137.6, 102.75, 180),     # LED row below the PWR_HOLD lane (strap notch)
    'R20': (139.6, 104.1, -90), 'Q3': (142.0, 103.9, 0), 'Q2': (147.0, 103.9, 0), 'R19': (144.3, 106.2, 180),
    'U5': (145.5, 109.2, -90), 'D3': (149.0, 107.4, 180), 'C29': (148.4, 111.2, -90),
    'C30': (142.6, 109.6, -90), 'C33': (141.3, 109.6, -90),
    })
CHARGER = ['U3', 'L1', 'C7', 'C8', 'C9', 'C10', 'C2', 'C3', 'C4', 'C11', 'C12', 'C13', 'C14', 'C15', 'C16', 'C17', 'C18',
           'C5', 'C6', 'C19', 'C20', 'R4', 'R5', 'R6', 'R7', 'TH1', 'R8', 'D2', 'R9', 'R10', 'Q1', 'R11']
CHG = {
    'U3': (163.0, 112.4, 0), 'L1': (163.0, 104.9, 0), 'C10': (162.1, 108.9, 90), 'C16': (163.9, 108.9, 90),
    'C7': (154.4, 107.4, 90), 'C8': (156.7, 107.4, 90), 'C9': (159.0, 107.4, 90),
    'C11': (166.6, 107.4, 90), 'C12': (168.9, 107.4, 90), 'C13': (171.2, 107.4, 90), 'C14': (173.5, 107.4, 90),
    'C15': (175.8, 107.4, 90),
    'C17': (179.0, 103.4, 0), 'C18': (179.0, 105.8, 0),
    'C20': (166.4, 110.55, 0), 'C4': (159.5, 111.3, 180),
    'C2': (156.15, 113.0, 180), 'C3': (156.15, 115.05, 180),
    'D2': (153.6, 111.2, 0), 'R8': (153.0, 112.9, 0),
    'C5': (160.9, 111.85, 0), 'C6': (165.4, 112.9, 180), 'C19': (160.6, 114.95, 180), 'R5': (168.2, 114.3, 0),
    'R4': (167.7, 115.4, -90), 'R6': (165.3, 115.95, 180), 'R7': (164.82, 117.4, -90), 'TH1': (166.6, 117.4, -90),
    'R9': (172.0, 115.2, 90), 'R10': (162.4, 115.55, 0), 'Q1': (161.4, 118.2, 0), 'R11': (128.975, 112.6, 90),
}
PL = dict(PLACE)
for r in CHARGER:
    x, y, a = PL[r]; PL[r] = (x + 6.0, y, a)
BMS = {
    'U4': (192.0, 110.6, 0), 'R18': (193.0, 105.9, 180),
    'C26': (197.2, 107.15, 90), 'C25': (198.4, 108.85, 90), 'C24': (197.2, 110.55, 90), 'C23': (198.4, 112.25, 90),
    'R16': (201.5, 108.0, 180), 'R15': (201.5, 109.7, 180), 'R14': (201.5, 111.4, 180), 'R13': (201.5, 113.1, 180),
    'R12': (193.3, 115.0, 180), 'C22': (188.9, 116.4, -90), 'C21': (190.7, 116.9, -90),
    'C28': (187.4, 115.5, -90), 'C27': (186.4, 117.0, -90), 'TH2': (185.4, 118.6, -90), 'R17': (183.9, 117.9, 0),
    'R24': (183.6, 113.0, 180), 'R25': (180.8, 113.0, 0), 'F1': (205.0, 102.3, 180),
}
PL.update(BMS)
PL.update({
    'J2': (218.4, 110.6, 90),                      # opening faces the board end; plug body clears the M2 screws
    'TP13': (113.0, 114.0, 0), 'TP14': (115.5, 114.0, 0), 'TP15': (118.0, 114.0, 0),     # cell LED wire pads
    'R27': (113.0, 116.4, 90), 'R28': (115.5, 116.4, 90), 'R29': (118.0, 116.4, 90),
    'JP2': (131.6, 105.2, 0),                     # fast-charge jumper (back, under U6) - placed by the user
    'TP5': (125.9, 102.6, 0), 'TP6': (123.9, 102.6, 0), 'TP7': (127.9, 102.6, 0), 'TP8': (129.9, 102.6, 0),
    'TP9': (114.5, 119.3, 0), 'TP10': (190.0, 101.3, 0), 'TP11': (181.2, 114.6, 0), 'TP12': (183.3, 114.7, 0),
})
PL.update(CHG)
PL.update(OVR)

b = Board(sys.argv[1])
b.clear_routing()
for ref, (x, y, r) in PL.items():
    if ref not in b.fp: continue           # e.g. TP1-TP4 (Deans pads, removed)
    b.place(ref, x, y, r, back=ref in (BACK | {'C5', 'C6', 'C19', 'R5', 'TP5', 'TP6', 'TP7', 'TP8', 'JP2'}))
pad = b.pad
# silkscreen: reference designators off the silk (kept on the Fab layer for assembly)
for f in b.b.GetFootprints():
    f.Reference().SetVisible(False)
    f.Value().SetVisible(False)


# widths
W_SIG, W_USB, W_PWR, W_VB = 0.2, 0.2, 0.8, 1.0
VIA_S = dict(d=0.5, drill=0.25)

# =====================================================================================
# REGION A : USB-C, ESD, CH224K, VBUS entry
# =====================================================================================
# ---- GND pins of J1 to the shell tabs (THT -> both layers)
b.track('GND', [(106.0, 107.25), (106.0, 106.4)], 0.5)
b.track('GND', [(106.0, 113.75), (106.0, 114.6)], 0.5)

# ---- VBUS: top pad (A4/B9) -> via -> bottom jumper -> via at lower pad (A9/B4)
b.track('VBUS', [(106.6, 108.05), (107.95, 108.05)], 0.6)
b.via('VBUS', 107.95, 108.05)
b.track('VBUS', [(107.95, 108.05), (107.15, 108.05), (106.9, 108.3), (106.9, 112.7), (107.15, 112.95), (107.95, 112.95)], 0.6, B)
b.via('VBUS', 107.95, 112.95)
# lower pad -> VBUS lane along the bottom edge (top layer)
b.track('VBUS', [(106.6, 112.95), (107.95, 112.95)], 0.6)
b.track('VBUS', [(107.95, 112.95), (108.7, 113.7)], 0.6)
b.track('VBUS', [(108.7, 113.7), (108.7, 118.5), (109.5, 119.3), (133.6, 119.3), (134.3, 118.6), (139.9, 118.6), (140.6, 119.3),
                 (154.0, 119.3)], W_VB)     # steps up past the strap notch at x 137.1
# bottom spur to R1 (CH224K VDD feed)
b.track('VBUS', [(107.95, 108.05), (111.3, 108.05)], 0.4, B)
b.via('VBUS', 111.3, 108.05)
x, y = pad('R1', 1)
b.track('VBUS', [(111.3, 108.05), (x, y)], 0.4)
# TVS on the lane
x, y = pad('D1', 1); b.track('VBUS', [(x, y), (x, 119.3)], W_PWR)

# ---- USB D+/D-  (DM continues from B7, DP from A6+B6)
# DM: B7 pad -> via (A7 joins it on the bottom) -> right -> up -> top lane y 101.6
b.track('USB_DM', [(106.6, 109.75), (108.9, 109.75), (108.9, 101.95), (109.4, 101.45), (126.525, 101.45), (127.025, 101.95), (127.025, 103.2)], W_USB)
b.via('USB_DM', 107.75, 109.75, **VIA_S)
b.track('USB_DM', [(106.6, 110.75), (107.75, 110.75)], W_USB)
b.via('USB_DM', 107.75, 110.75, **VIA_S)
b.track('USB_DM', [(107.75, 109.75), (107.75, 110.75)], W_USB, B)
# DP: A6 and B6 joined on top, then right -> up -> lane y 102.9
b.track('USB_DP', [(106.6, 110.25), (108.5, 110.25)], W_USB)
b.track('USB_DP', [(106.6, 111.25), (108.5, 111.25), (108.5, 110.25), (109.5, 110.25), (109.5, 103.25), (110.0, 102.75), (126.375, 102.75), (126.375, 103.2)], W_USB)
# ESD (U2) GND pin -> via between the lanes
x, y = pad('U2', 3); b.track('GND', [(x, y), (110.6, y)], 0.3); b.via('GND', 110.6, y)

# ---- CC1: pad -> via -> bottom -> up at U1 right side (pin 7)
b.track('CC1', [(106.6, 109.25), (107.8, 109.25), (108.15, 108.9)], W_SIG)
b.via('CC1', 108.15, 108.9, **VIA_S)
x7, y7 = pad('U1', 7)
b.track('CC1', [(108.15, 108.9), (108.6, 109.35), (108.6, 111.4), (109.0, 111.8), (121.0, 111.8), (121.5, 111.3), (121.5, y7)], W_SIG, B)
b.via('CC1', 121.5, y7, **VIA_S)
b.track('CC1', [(121.5, y7), (x7 + 0.3, y7)], W_SIG)
# ---- CC2: pad -> right -> up to U1 pin 6 (right side, bottom)
x6, y6 = pad('U1', 6)
b.track('CC2', [(106.6, 112.25), (120.4, 112.25), (120.6, 112.05), (120.6, y6 + 0.4), (120.2, y6), (x6, y6)], W_SIG)

# ---- CH224K support: VDD (R1, C1), DP/DM short, CFG1 (R2), EP GND
x1, y1 = pad('U1', 1); xr, yr = pad('R1', 2); xc, yc = pad('C1', 1)
b.track('PD_VDD', [(xr, yr), (xr + 0.6, y1), (x1, y1)], 0.3)
b.track('PD_VDD', [(xc, yc), (xc, y1)], 0.3)
xg, yg = pad('C1', 2); b.track('GND', [(xg, yg), (xg - 0.9, yg)], 0.3); b.via('GND', xg - 0.9, yg)
x4, y4 = pad('U1', 4); x5, y5 = pad('U1', 5)
b.track('PD_DPDM', [(x4 - 0.5, y4), (x4 - 0.5, y5)], 0.3)
x9, y9 = pad('U1', 9); xr2, yr2 = pad('R2', 1)
b.track('PD_CFG1', [(x9, y9), (xr2 - 0.3, y9), (xr2, yr2)], W_SIG)
xg, yg = pad('R2', 2); b.track('GND', [(xg, yg), (120.85, 108.4)], 0.3); b.via('GND', 120.85, 108.4, **VIA_S)
xe, ye = pad('U1', 11)
for dy in (-1.0, 0.0, 1.0):
    b.via('GND', xe, ye + dy)
# TVS GND
x, y = pad('D1', 2); b.via('GND', x, y - 1.0); b.track('GND', [(x, y), (x, y - 1.0)], 0.5)

exec(open(os.path.join(HERE, 'regionB.py')).read())
exec(open(os.path.join(HERE, 'regionC.py')).read())
exec(open(os.path.join(HERE, 'regionD.py')).read())
exec(open(os.path.join(HERE, 'regionF.py')).read())
exec(open(os.path.join(HERE, 'regionG.py')).read())
exec(open(os.path.join(HERE, 'regionH.py')).read())
for _ in range(3):
    print('chamfered', chamfer_corners(b))
print('tees smoothed', smooth_tees(b))
exec(open(os.path.join(HERE, 'regionE.py')).read())
b.fill()
b.save(sys.argv[2])
b.dump(sys.argv[3])
print('ok')
