"""Create led_board.kicad_pcb: placeholder outline + every footprint (with nets) parked below it, unrouted.
kicad-python led_board/make_led_pcb.py      (run once; afterwards the user places parts, then routing is added)"""
import os, sys, subprocess
import pcbnew

HERE = os.path.dirname(os.path.abspath(__file__))
PCB = os.path.join(HERE, 'led_board.kicad_pcb')
NET = os.path.join(HERE, 'led_board.net')
mm = pcbnew.FromMM

b = pcbnew.CreateEmptyBoard()
# outline 20 x 10 mm, 1 mm corner radius; M2 holes H1/H2 centred on the two short sides
X0, Y0, W, H, R = 100.0, 100.0, 20.0, 10.0, 1.0
HOLE_IN = 2.6                                     # hole centre from the short edge (2.2 mm hole, ~0.85 mm web to the edge)
def seg(a, c):
    s = pcbnew.PCB_SHAPE(b); s.SetShape(pcbnew.SHAPE_T_SEGMENT); s.SetLayer(pcbnew.Edge_Cuts); s.SetWidth(mm(0.05))
    s.SetStart(pcbnew.VECTOR2I(mm(a[0]), mm(a[1]))); s.SetEnd(pcbnew.VECTOR2I(mm(c[0]), mm(c[1]))); b.Add(s)
def arc(cx, cy, sx, sy, ang):
    s = pcbnew.PCB_SHAPE(b); s.SetShape(pcbnew.SHAPE_T_ARC); s.SetLayer(pcbnew.Edge_Cuts); s.SetWidth(mm(0.05))
    s.SetCenter(pcbnew.VECTOR2I(mm(cx), mm(cy))); s.SetStart(pcbnew.VECTOR2I(mm(sx), mm(sy)))
    s.SetArcAngleAndEnd(pcbnew.EDA_ANGLE(ang, pcbnew.DEGREES_T)); b.Add(s)
x1, y1, x2, y2 = X0, Y0, X0 + W, Y0 + H
seg((x1 + R, y1), (x2 - R, y1)); seg((x2, y1 + R), (x2, y2 - R)); seg((x2 - R, y2), (x1 + R, y2)); seg((x1, y2 - R), (x1, y1 + R))
arc(x2 - R, y1 + R, x2 - R, y1, 90); arc(x2 - R, y2 - R, x2, y2 - R, 90)
arc(x1 + R, y2 - R, x1 + R, y2, 90); arc(x1 + R, y1 + R, x1, y1 + R, 90)
t = pcbnew.PCB_TEXT(b); t.SetLayer(pcbnew.Cmts_User); t.SetTextSize(pcbnew.VECTOR2I(mm(1), mm(1)))
t.SetText('Cell LED board 20 x 10 mm, M2 holes on the short sides')
t.SetPosition(pcbnew.VECTOR2I(mm(X0), mm(Y0 - 3))); t.SetHorizJustify(pcbnew.GR_TEXT_H_ALIGN_LEFT); b.Add(t)
t2 = pcbnew.PCB_TEXT(b); t2.SetLayer(pcbnew.Cmts_User); t2.SetTextSize(pcbnew.VECTOR2I(mm(1), mm(1)))
t2.SetText('Parts parked below: S1-S3 = cell 1-3 LEDs, TP13-TP15 = wire pads A, B, C')
t2.SetPosition(pcbnew.VECTOR2I(mm(X0), mm(Y0 + H + 4))); t2.SetHorizJustify(pcbnew.GR_TEXT_H_ALIGN_LEFT); b.Add(t2)
b.Save(PCB)

# footprints + nets from the netlist (same tool as the main board), then park them in a tidy row
subprocess.run([sys.executable, os.path.join(HERE, '..', 'layout_work', 'sync.py'), PCB, NET, PCB], check=True)
subprocess.run([sys.executable, os.path.join(HERE, '..', 'layout_work', 'fields.py'), PCB, NET, PCB], check=True)
b = pcbnew.LoadBoard(PCB)
park = {'S1': (100, 117), 'S2': (104, 117), 'S3': (108, 117), 'TP13': (113, 117), 'TP14': (117, 117), 'TP15': (121, 117),
        'H1': (X0 + HOLE_IN, Y0 + H / 2), 'H2': (X0 + W - HOLE_IN, Y0 + H / 2)}
for f in b.Footprints():
    x, y = park[f.GetReference()]
    f.SetPosition(pcbnew.VECTOR2I(mm(x), mm(y)))
    f.Reference().SetVisible(not f.GetReference().startswith('H'))
    if f.GetReference().startswith('H'): f.SetLocked(True)
    if f.GetReference().startswith('S'):            # KiCad has no model for this LED: use ours
        m = pcbnew.FP_3DMODEL(); m.m_Filename = '${KIPRJMOD}/../3dmodels/LED_Kingbright_KPHBM-2012.wrl'
        ms = f.Models(); ms.clear(); ms.append(m)
b.Save(PCB)
print('written', PCB)
