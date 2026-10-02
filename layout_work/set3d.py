"""Attach a 3D model to one footprint: kicad-python set3d.py board.kicad_pcb REF model_path ox oy oz rx ry rz
(offset mm, KiCad model convention: +y = up on screen; rotation degrees). Replaces the footprint's models."""
import sys, pcbnew
path, ref, model = sys.argv[1], sys.argv[2], sys.argv[3]
ox, oy, oz, rx, ry, rz = map(float, sys.argv[4:10])
b = pcbnew.LoadBoard(path)
n = 0
for f in b.Footprints():
    if f.GetReference() == ref:
        m = pcbnew.FP_3DMODEL(); m.m_Filename = model
        m.m_Offset = pcbnew.VECTOR3D(ox, oy, oz); m.m_Rotation = pcbnew.VECTOR3D(rx, ry, rz); m.m_Scale = pcbnew.VECTOR3D(1, 1, 1)
        ms = f.Models(); ms.clear(); ms.append(m); n += 1
assert n == 1, (ref, n)
b.Save(path)
print(ref, '->', model, (ox, oy, oz), (rx, ry, rz))
