"""Build the PCB: 135 x 21 mm outline, rounded corners, 4x M2 holes, all footprints
loaded with nets + schematic links, parked beside the board for manual placement.
Run with KiCad's bundled python."""
import sys, re
import pcbnew

PCB = sys.argv[1]
NET = sys.argv[2]
FPLIB = r"C:/Program Files/KiCad/10.0/share/kicad/footprints/"

# ------------------------------------------------------------ tiny s-expr parser
TOK = re.compile(r'"(?:[^"\\]|\\.)*"|[()]|[^\s()"]+')
def parse(s):
    toks = TOK.findall(s); pos = 0
    def rd():
        nonlocal pos
        t = toks[pos]; pos += 1
        if t == '(':
            l = []
            while toks[pos] != ')':
                l.append(rd())
            pos += 1
            return l
        return t.strip('"') if t.startswith('"') else t
    return rd()
def child(e, name):
    return next((x for x in e if isinstance(x, list) and x[0] == name), None)

n = parse(open(NET, encoding='utf8').read())
comps = child(n, 'components')[1:]
nets = child(n, 'nets')[1:]
pin_net = {}
for net in nets:
    name = child(net, 'name')[1]
    for nd in net:
        if isinstance(nd, list) and nd[0] == 'node':
            pin_net[(child(nd, 'ref')[1], child(nd, 'pin')[1])] = name

mm = pcbnew.FromMM
def P(x, y): return pcbnew.VECTOR2I(mm(x), mm(y))

board = pcbnew.NewBoard(PCB)
board.SetCopperLayerCount(2)

# ------------------------------------------------------------ outline: 135 x 21, R3 corners
X0, Y0, L, W, R = 100.0, 100.0, 135.0, 21.0, 3.0
def seg(a, b):
    s = pcbnew.PCB_SHAPE(board, pcbnew.SHAPE_T_SEGMENT)
    s.SetStart(P(*a)); s.SetEnd(P(*b)); s.SetLayer(pcbnew.Edge_Cuts); s.SetWidth(mm(0.1)); board.Add(s)
def arc(start, mid, end):
    s = pcbnew.PCB_SHAPE(board, pcbnew.SHAPE_T_ARC)
    s.SetArcGeometry(P(*start), P(*mid), P(*end)); s.SetLayer(pcbnew.Edge_Cuts); s.SetWidth(mm(0.1)); board.Add(s)
k = R * (1 - 0.7071067811865476)
x1, y1, x2, y2 = X0, Y0, X0 + L, Y0 + W
seg((x1 + R, y1), (x2 - R, y1)); seg((x2, y1 + R), (x2, y2 - R))
seg((x2 - R, y2), (x1 + R, y2)); seg((x1, y2 - R), (x1, y1 + R))
arc((x2 - R, y1), (x2 - k, y1 + k), (x2, y1 + R))
arc((x2, y2 - R), (x2 - k, y2 - k), (x2 - R, y2))
arc((x1 + R, y2), (x1 + k, y2 - k), (x1, y2 - R))
arc((x1, y1 + R), (x1 + k, y1 + k), (x1 + R, y1))

# ------------------------------------------------------------ M2 holes, concentric with the corner arcs
HOLES = {'H1': (x1 + R, y1 + R), 'H2': (x2 - R, y1 + R), 'H3': (x1 + R, y2 - R), 'H4': (x2 - R, y2 - R)}

# ------------------------------------------------------------ nets
netinfo = {}
for name in sorted(set(pin_net.values())):
    ni = pcbnew.NETINFO_ITEM(board, name)
    board.Add(ni)
    netinfo[name] = ni

# ------------------------------------------------------------ footprints, grouped, parked below the board
GROUPS = [
    ('CONNECTORS (place these first)', lambda r: r in ('J1', 'J2', 'TP1', 'TP2', 'TP3', 'TP4')),
    ('USB-C / PD', lambda r: r in ('U1', 'U2', 'D1', 'R1', 'C1', 'R2', 'R3', 'C2', 'C3', 'C4')),
    ('CHARGER BQ25798', lambda r: r in ('U3', 'L1', 'Q1', 'TH1', 'D2', 'F1') or
        (r[0] in 'RC' and r[1:].isdigit() and ((r[0] == 'C' and 5 <= int(r[1:]) <= 20) or (r[0] == 'R' and 4 <= int(r[1:]) <= 11)))),
    ('CELL MONITOR BQ76920', lambda r: r in ('U4', 'TH2') or
        (r[0] in 'RC' and r[1:].isdigit() and ((r[0] == 'C' and 21 <= int(r[1:]) <= 28) or (r[0] == 'R' and 12 <= int(r[1:]) <= 18)))),
    ('MCU POWER', lambda r: r in ('U5', 'Q2', 'Q3', 'D3', 'R19', 'R20', 'C29', 'C30')),
    ('MCU', lambda r: True),
]
def natkey(r):
    m = re.match(r'([A-Z]+)(\d+)', r)
    return (m.group(1), int(m.group(2))) if m else (r, 0)

placed = set()
gy = y2 + 12
missing_nets = []
for title, pred in GROUPS:
    members = sorted([c for c in comps if child(c, 'ref')[1] not in placed and pred(child(c, 'ref')[1])],
                     key=lambda c: natkey(child(c, 'ref')[1]))
    if not members: continue
    t = pcbnew.PCB_TEXT(board)
    t.SetText(title); t.SetPosition(P(X0, gy)); t.SetLayer(pcbnew.Cmts_User)
    t.SetTextSize(P(1.5, 1.5)); t.SetHorizJustify(pcbnew.GR_TEXT_H_ALIGN_LEFT); board.Add(t)
    cx, cy, rowh = X0, gy + 5, 0
    for c in members:
        ref = child(c, 'ref')[1]; placed.add(ref)
        lib, name = child(c, 'footprint')[1].split(':')
        fp = pcbnew.FootprintLoad(FPLIB + lib + '.pretty', name)
        assert fp, (ref, lib, name)
        fp.SetReference(ref); fp.SetValue(child(c, 'value')[1])
        fp.SetFPID(pcbnew.LIB_ID(lib, name))
        for prop in c:
            if isinstance(prop, list) and prop[0] == 'property':
                pn = child(prop, 'name')[1]; pv = (child(prop, 'value') or [None, ''])[1]
                if pn in ('MPN', 'Note'):
                    fp.SetField(pn, pv)
                    fp.GetField(pn).SetVisible(False)
        fp.SetExcludedFromBOM(bool(child(c, 'property') and any(
            isinstance(p, list) and p[0] == 'property' and child(p, 'name')[1] == 'exclude_from_bom' for p in c)))
        fp.SetPath(pcbnew.KIID_PATH('/' + child(c, 'tstamps')[1]))
        try:
            fp.SetSheetname(''); fp.SetSheetfile('usbc pd airsoft module.kicad_sch')
        except AttributeError:
            pass
        for pad in fp.Pads():
            num = pad.GetNumber()
            if not num or num == 'MP':
                continue
            net = pin_net.get((ref, num))
            if net is None:
                missing_nets.append((ref, num))
                continue
            pad.SetNet(netinfo[net])
        bb = fp.GetBoundingBox(False)
        w, h = pcbnew.ToMM(bb.GetWidth()), pcbnew.ToMM(bb.GetHeight())
        if cx + w > X0 + L + 40:
            cx, cy, rowh = X0, cy + rowh + 2, 0
        fp.SetPosition(P(0, 0))
        bb = fp.GetBoundingBox(False)
        ox, oy = pcbnew.ToMM(bb.GetX()), pcbnew.ToMM(bb.GetY())
        fp.SetPosition(P(cx - ox, cy - oy))
        if ref in HOLES:
            fp.SetPosition(P(*HOLES[ref])); fp.SetLocked(True)
            fp.Reference().SetVisible(False); fp.Value().SetVisible(False)
            board.Add(fp)
            continue
        board.Add(fp)
        cx += w + 2; rowh = max(rowh, h)
    gy = cy + rowh + 8

# ------------------------------------------------------------ note on the board
t = pcbnew.PCB_TEXT(board)
t.SetText('135 x 21 mm, R3 corners, 4x M2 (2.2 mm) holes. USB-C on one short end, Deans pads + balance plug on the other.')
t.SetPosition(P(X0, Y0 - 5)); t.SetLayer(pcbnew.Cmts_User); t.SetTextSize(P(1.2, 1.2))
t.SetHorizJustify(pcbnew.GR_TEXT_H_ALIGN_LEFT); board.Add(t)

board.Save(PCB)
print('footprints', len(placed), 'nets', len(netinfo), 'pads without netlist entry', missing_nets)
