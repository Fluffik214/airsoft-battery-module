"""Small helper layer over pcbnew for scripted, deterministic layout (run with KiCad's python)."""
import math
import json, math
import pcbnew

mm = pcbnew.FromMM
def P(x, y): return pcbnew.VECTOR2I(mm(x), mm(y))
F, B = pcbnew.F_Cu, pcbnew.B_Cu

class Board:
    def __init__(self, path):
        self.b = pcbnew.LoadBoard(path)
        self.fp = {f.GetReference(): f for f in self.b.GetFootprints()}
        self.nets = {n: i for n, i in self.b.GetNetsByName().items()}

    def net(self, name):
        n = self.b.FindNet(name if name.startswith('/') or name.startswith('unconnected') else '/' + name)
        assert n is not None, name
        return n

    # ---------------------------------------------------------- clearing
    def clear_routing(self):
        for t in list(self.b.GetTracks()):
            self.b.Remove(t)
        for z in list(self.b.Zones()):
            self.b.Remove(z)
        for d in list(self.b.GetDrawings()):
            if d.GetLayer() in (pcbnew.Cmts_User, pcbnew.B_Fab) and isinstance(d, pcbnew.PCB_SHAPE):
                self.b.Remove(d)

    # ---------------------------------------------------------- placement
    def place(self, ref, x, y, rot=0, back=False):
        f = self.fp[ref]
        if f.IsLocked():
            return
        if f.IsFlipped() != back:
            f.Flip(f.GetPosition(), pcbnew.FLIP_DIRECTION_LEFT_RIGHT)
        f.SetOrientationDegrees(rot)
        f.SetPosition(P(x, y))

    def pad(self, ref, num):
        """absolute (x, y) of a pad centre (first match)"""
        for p in self.fp[ref].Pads():
            if p.GetNumber() == str(num):
                q = p.GetPosition()
                return (pcbnew.ToMM(q.x), pcbnew.ToMM(q.y))
        raise KeyError((ref, num))

    # ---------------------------------------------------------- routing primitives
    def track(self, net, pts, w, layer=F):
        n = self.net(net)
        for a, b in zip(pts, pts[1:]):
            if a == b: continue
            t = pcbnew.PCB_TRACK(self.b)
            t.SetStart(P(*a)); t.SetEnd(P(*b)); t.SetWidth(mm(w)); t.SetLayer(layer); t.SetNet(n)
            self.b.Add(t)

    def via(self, net, x, y, d=0.6, drill=0.3):
        v = pcbnew.PCB_VIA(self.b)
        v.SetPosition(P(x, y)); v.SetWidth(mm(d)); v.SetDrill(mm(drill)); v.SetNet(self.net(net))
        v.SetViaType(pcbnew.VIATYPE_THROUGH)
        self.b.Add(v)

    def zone(self, net, layer, poly, priority=0, thermal=False, min_w=0.2, clearance=0.2, name='', chamfer=0.4):
        z = pcbnew.ZONE(self.b)
        z.SetLayer(layer)
        z.SetNet(self.net(net))
        ol = z.Outline(); ol.NewOutline()
        for x, y in poly:
            ol.Append(mm(x), mm(y))
        z.SetAssignedPriority(priority)
        z.SetPadConnection(pcbnew.ZONE_CONNECTION_THERMAL if thermal else pcbnew.ZONE_CONNECTION_FULL)
        z.SetThermalReliefGap(mm(0.25)); z.SetThermalReliefSpokeWidth(mm(0.4))
        z.SetMinThickness(mm(min_w)); z.SetLocalClearance(mm(clearance))
        z.SetIslandRemovalMode(pcbnew.ISLAND_REMOVAL_MODE_ALWAYS)
        if name: z.SetZoneName(name)
        z.SetCornerSmoothingType(pcbnew.ZONE_SETTINGS.SMOOTHING_CHAMFER)   # no 90-degree outline corners
        z.SetCornerRadius(mm(chamfer))
        self.b.Add(z)
        return z

    def text(self, s, x, y, layer, size=1.0, mirror=False):
        t = pcbnew.PCB_TEXT(self.b)
        t.SetText(s); t.SetPosition(P(x, y)); t.SetLayer(layer)
        t.SetTextSize(P(size, size)); t.SetTextThickness(mm(size * 0.15))
        if mirror: t.SetMirrored(True)
        self.b.Add(t)

    def rect(self, x1, y1, x2, y2, layer, w=0.1):
        for a, b in [((x1, y1), (x2, y1)), ((x2, y1), (x2, y2)), ((x2, y2), (x1, y2)), ((x1, y2), (x1, y1))]:
            s = pcbnew.PCB_SHAPE(self.b, pcbnew.SHAPE_T_SEGMENT)
            s.SetStart(P(*a)); s.SetEnd(P(*b)); s.SetLayer(layer); s.SetWidth(mm(w))
            self.b.Add(s)

    def fill(self):
        pcbnew.ZONE_FILLER(self.b).Fill(self.b.Zones())

    def save(self, path):
        self.b.Save(path)

    # ---------------------------------------------------------- export for the renderer
    def dump(self, path):
        out = {'pads': [], 'tracks': [], 'vias': [], 'zones': [], 'fps': [], 'edge': []}
        for f in self.b.GetFootprints():
            cy = f.GetCourtyard(pcbnew.B_CrtYd if f.IsFlipped() else pcbnew.F_CrtYd).BBox()
            out['fps'].append({'ref': f.GetReference(), 'back': f.IsFlipped(),
                               'x': pcbnew.ToMM(f.GetPosition().x), 'y': pcbnew.ToMM(f.GetPosition().y),
                               'cy': [pcbnew.ToMM(cy.GetX()), pcbnew.ToMM(cy.GetY()),
                                      pcbnew.ToMM(cy.GetRight()), pcbnew.ToMM(cy.GetBottom())]})
            for p in f.Pads():
                lays = [l for l in (F, B) if p.IsOnLayer(l)]
                poly = p.GetEffectivePolygon(lays[0] if lays else F, pcbnew.ERROR_INSIDE)
                pts = []
                for i in range(poly.OutlineCount()):
                    ch = poly.Outline(i)
                    pts.append([(pcbnew.ToMM(ch.CPoint(j).x), pcbnew.ToMM(ch.CPoint(j).y)) for j in range(ch.PointCount())])
                out['pads'].append({'ref': f.GetReference(), 'num': p.GetNumber(), 'net': p.GetNetname(),
                                    'layers': ['F' if l == F else 'B' for l in lays], 'poly': pts,
                                    'th': p.GetAttribute() in (pcbnew.PAD_ATTRIB_PTH, pcbnew.PAD_ATTRIB_NPTH)})
        for t in self.b.GetTracks():
            if isinstance(t, pcbnew.PCB_VIA):
                out['vias'].append({'x': pcbnew.ToMM(t.GetPosition().x), 'y': pcbnew.ToMM(t.GetPosition().y),
                                    'd': pcbnew.ToMM(t.GetWidth(F)), 'net': t.GetNetname()})
            else:
                out['tracks'].append({'a': (pcbnew.ToMM(t.GetStart().x), pcbnew.ToMM(t.GetStart().y)),
                                      'b': (pcbnew.ToMM(t.GetEnd().x), pcbnew.ToMM(t.GetEnd().y)),
                                      'w': pcbnew.ToMM(t.GetWidth()), 'layer': 'F' if t.GetLayer() == F else 'B',
                                      'net': t.GetNetname()})
        for z in self.b.Zones():
            for l in (F, B):
                if not z.IsOnLayer(l): continue
                fp = z.GetFilledPolysList(l)
                fp = fp.CloneDropTriangulation() if hasattr(fp, 'CloneDropTriangulation') else fp
                fp.Fracture()
                for i in range(fp.OutlineCount()):
                    ch = fp.Outline(i)
                    out['zones'].append({'layer': 'F' if l == F else 'B', 'net': z.GetNetname(),
                                         'pts': [(pcbnew.ToMM(ch.CPoint(j).x), pcbnew.ToMM(ch.CPoint(j).y)) for j in range(ch.PointCount())]})
        for d in self.b.GetDrawings():
            if d.GetLayer() == pcbnew.Edge_Cuts and isinstance(d, pcbnew.PCB_SHAPE):
                if d.GetShape() == pcbnew.SHAPE_T_ARC:
                    c = d.GetCenter(); r = pcbnew.ToMM(d.GetRadius())
                    s, e = d.GetArcAngleStart().AsDegrees(), d.GetArcAngle().AsDegrees()
                    pts = [(pcbnew.ToMM(c.x) + r * math.cos(math.radians(s + e * k / 10)),
                            pcbnew.ToMM(c.y) + r * math.sin(math.radians(s + e * k / 10))) for k in range(11)]
                else:
                    pts = [(pcbnew.ToMM(d.GetStart().x), pcbnew.ToMM(d.GetStart().y)), (pcbnew.ToMM(d.GetEnd().x), pcbnew.ToMM(d.GetEnd().y))]
                out['edge'].append(pts)
        json.dump(out, open(path, 'w'))


def chamfer_corners(board_obj, max_c=0.6, min_turn_deg=50.0):
    """Replace sharp corners (two same-net/same-layer segments meeting at a free point) with 45-degree chamfers."""
    b = board_obj.b
    segs = [t for t in b.GetTracks() if not isinstance(t, pcbnew.PCB_VIA)]
    vias = [t for t in b.GetTracks() if isinstance(t, pcbnew.PCB_VIA)]
    def key(p): return (round(pcbnew.ToMM(p.x), 4), round(pcbnew.ToMM(p.y), 4))
    ends = {}
    for s in segs:
        for which in ('S', 'E'):
            p = s.GetStart() if which == 'S' else s.GetEnd()
            ends.setdefault((s.GetNetCode(), s.GetLayer(), key(p)), []).append((s, which))
    pads = [p for f in b.GetFootprints() for p in f.Pads()]
    done = 0
    for (net, layer, pt), lst in ends.items():
        if len(lst) != 2: continue
        P0 = pcbnew.VECTOR2I(mm(pt[0]), mm(pt[1]))
        if any(v.GetNetCode() == net and v.HitTest(P0) for v in vias): continue
        if any(p.GetNetCode() == net and p.IsOnLayer(layer) and p.HitTest(P0) for p in pads): continue
        (s1, w1), (s2, w2) = lst
        if s1 is s2: continue
        a = s1.GetEnd() if w1 == 'S' else s1.GetStart()     # far end of s1
        c = s2.GetEnd() if w2 == 'S' else s2.GetStart()     # far end of s2
        ax, ay = pcbnew.ToMM(a.x), pcbnew.ToMM(a.y); cx, cy = pcbnew.ToMM(c.x), pcbnew.ToMM(c.y)
        px, py = pt
        l1 = math.hypot(px - ax, py - ay); l2 = math.hypot(cx - px, cy - py)
        if l1 < 1e-6 or l2 < 1e-6: continue
        u = ((px - ax) / l1, (py - ay) / l1); v = ((cx - px) / l2, (cy - py) / l2)
        turn = math.degrees(math.acos(max(-1.0, min(1.0, u[0] * v[0] + u[1] * v[1]))))
        if turn < min_turn_deg: continue
        cc = min(max_c, 0.45 * l1, 0.45 * l2)
        if cc < 0.08: continue
        q1 = pcbnew.VECTOR2I(mm(px - u[0] * cc), mm(py - u[1] * cc))
        q2 = pcbnew.VECTOR2I(mm(px + v[0] * cc), mm(py + v[1] * cc))
        if w1 == 'S': s1.SetStart(q1)
        else: s1.SetEnd(q1)
        if w2 == 'S': s2.SetStart(q2)
        else: s2.SetEnd(q2)
        t = pcbnew.PCB_TRACK(b)
        t.SetStart(q1); t.SetEnd(q2); t.SetWidth(min(s1.GetWidth(), s2.GetWidth())); t.SetLayer(layer); t.SetNetCode(net)
        b.Add(t)
        done += 1
    return done


def smooth_tees(board_obj, c=0.5):
    """At T-junctions (two collinear legs + one perpendicular branch, free point), make the branch join at 45 deg."""
    b = board_obj.b
    segs = [t for t in b.GetTracks() if not isinstance(t, pcbnew.PCB_VIA)]
    vias = [t for t in b.GetTracks() if isinstance(t, pcbnew.PCB_VIA)]
    pads = [p for f in b.GetFootprints() for p in f.Pads()]
    def mmv(p): return (pcbnew.ToMM(p.x), pcbnew.ToMM(p.y))
    ends = {}
    for s in segs:
        for which in ('S', 'E'):
            p = s.GetStart() if which == 'S' else s.GetEnd()
            q = mmv(p)
            ends.setdefault((s.GetNetCode(), s.GetLayer(), round(q[0], 4), round(q[1], 4)), []).append((s, which))
    done = 0
    for (net, layer, px, py), lst in ends.items():
        if len(lst) != 3: continue
        P0 = pcbnew.VECTOR2I(mm(px), mm(py))
        if any(v.GetNetCode() == net and v.HitTest(P0) for v in vias): continue
        if any(p.GetNetCode() == net and p.IsOnLayer(layer) and p.HitTest(P0) for p in pads): continue
        legs = []
        for s, w in lst:
            far = mmv(s.GetEnd() if w == 'S' else s.GetStart())
            L = math.hypot(far[0] - px, far[1] - py)
            legs.append((s, w, ((far[0] - px) / L, (far[1] - py) / L), L))
        through = None
        for i in range(3):
            for j in range(i + 1, 3):
                if legs[i][2][0] * legs[j][2][0] + legs[i][2][1] * legs[j][2][1] < -0.99:
                    through = (i, j)
        if not through: continue
        k = 3 - through[0] - through[1]
        bs, bw, bd, bl = legs[k]
        if abs(bd[0] * legs[through[0]][2][0] + bd[1] * legs[through[0]][2][1]) > 0.05: continue   # not perpendicular
        side = max(through, key=lambda i: legs[i][3])
        td, tl = legs[side][2], legs[side][3]
        cc = min(c, 0.45 * bl, 0.45 * tl)
        if cc < 0.1: continue
        r = pcbnew.VECTOR2I(mm(px + bd[0] * cc), mm(py + bd[1] * cc))       # pull branch back
        j = pcbnew.VECTOR2I(mm(px + td[0] * cc), mm(py + td[1] * cc))       # new junction on the through line
        if bw == 'S': bs.SetStart(r)
        else: bs.SetEnd(r)
        # split the through leg at j so the junction is a real endpoint
        ts, tw = legs[side][0], legs[side][1]
        f0 = ts.GetEnd() if tw == 'S' else ts.GetStart()
        far = pcbnew.VECTOR2I(int(f0.x), int(f0.y))
        if tw == 'S': ts.SetEnd(j)
        else: ts.SetStart(j)
        t2 = pcbnew.PCB_TRACK(b); t2.SetStart(j); t2.SetEnd(far); t2.SetWidth(ts.GetWidth()); t2.SetLayer(layer); t2.SetNetCode(net); b.Add(t2)
        t = pcbnew.PCB_TRACK(b); t.SetStart(r); t.SetEnd(j); t.SetWidth(bs.GetWidth()); t.SetLayer(layer); t.SetNetCode(net); b.Add(t)
        done += 1
    return done
