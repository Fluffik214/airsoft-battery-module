# =====================================================================================
# REGION H : strap notches - U-shaped cut-outs in both long edges for a ~3 mm strap that holds the pack.
#            The pack is as wide as the board (21 mm), so the strap runs around the board edges; notch pairs sit
#            straight across from each other (centre to centre = board width, 21.0 mm). x positions chosen by the user.
# =====================================================================================
NOTCH_X = (137.1, 173.5)
NOTCH_W, NOTCH_D, NOTCH_R = 3.8, 1.5, 0.5     # opening along the edge, depth into the board, entry corner radius
Y_TOP, Y_BOT = 100.0, 121.0
EW = pcbnew.FromMM(0.05)

def _seg(a, c):
    s = pcbnew.PCB_SHAPE(b.b, pcbnew.SHAPE_T_SEGMENT)
    s.SetStart(P(*a)); s.SetEnd(P(*c)); s.SetLayer(pcbnew.Edge_Cuts); s.SetWidth(EW); b.b.Add(s)
def _arc(a, m, c):
    s = pcbnew.PCB_SHAPE(b.b, pcbnew.SHAPE_T_ARC)
    s.SetArcGeometry(P(*a), P(*m), P(*c)); s.SetLayer(pcbnew.Edge_Cuts); s.SetWidth(EW); b.b.Add(s)

def notch(cx, ey, sgn):
    """U notch at x cx in the edge y = ey; sgn = +1 cuts downward (top edge), -1 upward (bottom edge)"""
    x1, x2, r, rb = cx - NOTCH_W / 2, cx + NOTCH_W / 2, NOTCH_R, NOTCH_W / 2 * 0 + 0.75
    k = 0.29289                                   # 1 - cos(45)
    y = lambda d: ey + sgn * d
    # rounded entry corners (convex, radius r) on both sides
    _arc((x1 - r, y(0)), (x1 - r * k, y(r * k)), (x1, y(r)))
    _arc((x2, y(r)), (x2 + r * k, y(r * k)), (x2 + r, y(0)))
    # side walls down to the rounded bottom (concave corners, radius rb)
    _seg((x1, y(r)), (x1, y(NOTCH_D - rb)))
    _seg((x2, y(r)), (x2, y(NOTCH_D - rb)))
    _arc((x1, y(NOTCH_D - rb)), (x1 + rb * k, y(NOTCH_D - rb * k)), (x1 + rb, y(NOTCH_D)))
    _arc((x2 - rb, y(NOTCH_D)), (x2 - rb * k, y(NOTCH_D - rb * k)), (x2, y(NOTCH_D - rb)))
    _seg((x1 + rb, y(NOTCH_D)), (x2 - rb, y(NOTCH_D)))
    return x1 - r, x2 + r

from kb import P
# split the straight long edges around the notches
for d in list(b.b.GetDrawings()):
    if d.GetLayer() != pcbnew.Edge_Cuts or d.GetShape() != pcbnew.SHAPE_T_SEGMENT: continue
    s, e = d.GetStart(), d.GetEnd()
    for ey, sgn in ((Y_TOP, 1), (Y_BOT, -1)):
        if s.y == e.y == pcbnew.FromMM(ey):
            xa, xb = sorted((pcbnew.ToMM(s.x), pcbnew.ToMM(e.x)))
            b.b.Remove(d)
            cuts = [notch(cx, ey, sgn) for cx in NOTCH_X]
            pos = xa
            for l, rr in cuts:
                _seg((pos, ey), (l, ey)); pos = rr
            _seg((pos, ey), (xb, ey))
