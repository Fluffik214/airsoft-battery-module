import sys, pcbnew
b = pcbnew.LoadBoard(sys.argv[1]); oy = float(sys.argv[3])
for f in b.Footprints():
    if f.GetReference() == 'J2':
        ms = f.Models()
        m = ms[0] if len(ms) else pcbnew.FP_3DMODEL()
        m.m_Filename = '${KIPRJMOD}/3dmodels/JST_XH_S4B-XH-SM4-TB.step'
        m.m_Offset = pcbnew.VECTOR3D(0, oy, 0); m.m_Rotation = pcbnew.VECTOR3D(0, 0, 0); m.m_Scale = pcbnew.VECTOR3D(1, 1, 1)
        if not len(ms): f.Add3DModel(m)
        else: ms[0] = m
b.Save(sys.argv[2])
