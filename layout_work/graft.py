import sys, pcbnew
inst = pcbnew.LoadBoard(sys.argv[2])
logos = [f.Duplicate(False) for f in inst.Footprints() if f.GetReference().startswith(('G', 'LOGO'))]
texts = [d.Duplicate().Cast() for d in inst.GetDrawings() if isinstance(d, pcbnew.PCB_TEXT) and d.GetLayer() == pcbnew.B_SilkS]
b = pcbnew.LoadBoard(sys.argv[1])
for f in [f for f in b.Footprints() if f.GetReference() in ('TP1', 'TP2', 'TP3', 'TP4')]:
    print('removed', f.GetReference()); b.Remove(f)
for f in logos: b.Add(f); print('logo')
for t in texts: b.Add(t); print('text')
b.Save(sys.argv[3])
