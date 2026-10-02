# =====================================================================================
# REGION E : GND pours (both layers, solid connection) + automatic GND stitching vias
# =====================================================================================
import math
OUTLINE = [(100.3, 100.3), (234.7, 100.3), (234.7, 120.7), (100.3, 120.7)]
b.zone('GND', F, OUTLINE, priority=0, thermal=False, clearance=0.2, name='GND_TOP', chamfer=1.0)
b.zone('GND', B, OUTLINE, priority=0, thermal=False, clearance=0.2, name='GND_BOT', chamfer=1.0)

NO_STITCH = [
    (198.0, 101.2, 210.1, 116.5),      # back-side custom text area
    (100.0, 116.4, 236.0, 121.0),      # bottom-layer bus band
    (100.0, 100.0, 108.0, 121.0),      # USB-C shell / holes
    (229.0, 100.0, 236.0, 121.0),      # right-end holes
]
def seg_dist(px, py, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    L = dx * dx + dy * dy
    t = 0 if L == 0 else max(0, min(1, ((px - ax) * dx + (py - ay) * dy) / L))
    return math.hypot(px - (ax + t * dx), py - (ay + t * dy))

items = []   # (kind, geometry, half-width) in mm, both layers treated as blocking
for t in b.b.GetTracks():
    if isinstance(t, pcbnew.PCB_VIA):
        items.append(('c', (pcbnew.ToMM(t.GetPosition().x), pcbnew.ToMM(t.GetPosition().y)), pcbnew.ToMM(t.GetWidth(F)) / 2))
    else:
        items.append(('s', (pcbnew.ToMM(t.GetStart().x), pcbnew.ToMM(t.GetStart().y),
                            pcbnew.ToMM(t.GetEnd().x), pcbnew.ToMM(t.GetEnd().y)), pcbnew.ToMM(t.GetWidth()) / 2))
for f in b.b.GetFootprints():
    for p in f.Pads():
        bb = p.GetBoundingBox()
        items.append(('r', (pcbnew.ToMM(bb.GetX()), pcbnew.ToMM(bb.GetY()), pcbnew.ToMM(bb.GetRight()), pcbnew.ToMM(bb.GetBottom())), 0))
    cy = f.GetCourtyard(pcbnew.B_CrtYd if f.IsFlipped() else pcbnew.F_CrtYd).BBox()
    if f.GetReference().startswith(('U', 'L', 'J')):
        items.append(('r', (pcbnew.ToMM(cy.GetX()), pcbnew.ToMM(cy.GetY()), pcbnew.ToMM(cy.GetRight()), pcbnew.ToMM(cy.GetBottom())), 0))
for z in b.b.Zones():
    if z.GetNetname() == '/GND': continue
    bb = z.GetBoundingBox()
    items.append(('r', (pcbnew.ToMM(bb.GetX()), pcbnew.ToMM(bb.GetY()), pcbnew.ToMM(bb.GetRight()), pcbnew.ToMM(bb.GetBottom())), 0))

def clear_at(x, y, need=0.3 + 0.3):
    for k, g, hw in items:
        if k == 'c':
            if math.hypot(x - g[0], y - g[1]) < need + hw: return False
        elif k == 's':
            if seg_dist(x, y, *g) < need + hw: return False
        else:
            x1, y1, x2, y2 = g
            dx = max(x1 - x, 0, x - x2); dy = max(y1 - y, 0, y - y2)
            if math.hypot(dx, dy) < need: return False
    return True

placed = 0
y = 101.4
while y < 120.0:
    x = 101.5
    while x < 234.0:
        if not any(a <= x <= c and bb <= y <= d for a, bb, c, d in NO_STITCH) and clear_at(x, y):
            b.via('GND', x, y)
            items.append(('c', (x, y), 0.3))
            placed += 1
        x += 2.5
    y += 2.4
print('stitching vias', placed)
