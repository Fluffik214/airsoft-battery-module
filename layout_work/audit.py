import sys, math, pcbnew
b = pcbnew.LoadBoard(sys.argv[1])
segs = [t for t in b.GetTracks() if not isinstance(t, pcbnew.PCB_VIA)]
vias = [t for t in b.GetTracks() if isinstance(t, pcbnew.PCB_VIA)]
pads = [p for f in b.GetFootprints() for p in f.Pads()]
ends = {}
for s in segs:
    for p, q in ((s.GetStart(), s.GetEnd()), (s.GetEnd(), s.GetStart())):
        k = (s.GetNetname(), s.GetLayer(), round(pcbnew.ToMM(p.x), 3), round(pcbnew.ToMM(p.y), 3))
        ends.setdefault(k, []).append((pcbnew.ToMM(q.x), pcbnew.ToMM(q.y)))
cnt = {'corner_free': 0, 'on_pad_or_via': 0, 'tee': 0}
for (net, layer, x, y), others in ends.items():
    P0 = pcbnew.VECTOR2I(pcbnew.FromMM(x), pcbnew.FromMM(y))
    onpad = any(v.HitTest(P0) for v in vias) or any(p.IsOnLayer(layer) and p.HitTest(P0) for p in pads)
    dirs = [math.atan2(oy - y, ox - x) for ox, oy in others]
    sharp = False
    for i in range(len(dirs)):
        for j in range(i + 1, len(dirs)):
            ang = abs((math.degrees(dirs[i] - dirs[j]) + 180) % 360 - 180)   # angle between the two legs
            if 60 < ang < 130: sharp = True
    if not sharp: continue
    kind = 'on_pad_or_via' if onpad else ('tee' if len(others) > 2 else 'corner_free')
    cnt[kind] += 1
    if kind != 'on_pad_or_via': print(kind, net, 'F' if layer == pcbnew.F_Cu else 'B', x, y, len(others))
print(cnt)
