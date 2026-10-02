"""Swap H1/H3 (USB-end holes) to M1.6 in place: same position, same schematic link, locked."""
import sys, pcbnew
PCB = sys.argv[1]
LIB = r"C:/Users/duzik/Desktop/usbc pd airsoft module/airsoft_module.pretty"
board = pcbnew.LoadBoard(PCB)
before = {f.GetReference(): f for f in board.GetFootprints()}
n_before = len(before)
for ref in ('H1', 'H3'):
    old = before[ref]
    new = pcbnew.FootprintLoad(LIB, 'MountingHole_1.7mm_M1.6')
    new.SetFPID(pcbnew.LIB_ID('airsoft_module', 'MountingHole_1.7mm_M1.6'))
    new.SetReference(ref); new.SetValue('M1.6')
    new.SetPath(old.GetPath())
    new.SetPosition(old.GetPosition()); new.SetOrientation(old.GetOrientation())
    new.Reference().SetVisible(False); new.Value().SetVisible(False)
    new.SetLocked(True)
    print(ref, 'at', pcbnew.ToMM(old.GetPosition().x), pcbnew.ToMM(old.GetPosition().y))
    board.Remove(old)
    board.Add(new)
assert len(list(board.GetFootprints())) == n_before
board.Save(PCB)
print('saved, footprints', n_before)
