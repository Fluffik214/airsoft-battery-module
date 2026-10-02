"""Sync a PCB with a netlist: add missing footprints (parked off-board), refresh every pad's net.
kicad-python sync.py in.kicad_pcb netlist.net out.kicad_pcb"""
import sys, re
import pcbnew
FPLIB = {None: r"C:/Program Files/KiCad/10.0/share/kicad/footprints/",
         'airsoft_module': r"C:/Users/duzik/Desktop/usbc pd airsoft module/"}
TOK = re.compile(r'"(?:[^"\\]|\\.)*"|[()]|[^\s()"]+')
def parse(s):
    toks = TOK.findall(s); pos = 0
    def rd():
        nonlocal pos
        t = toks[pos]; pos += 1
        if t == '(':
            l = []
            while toks[pos] != ')': l.append(rd())
            pos += 1; return l
        return t.strip('"') if t.startswith('"') else t
    return rd()
def child(e, k): return next((x for x in e if isinstance(x, list) and x[0] == k), None)

b = pcbnew.LoadBoard(sys.argv[1])
n = parse(open(sys.argv[2], encoding='utf8').read())
pin_net = {}
for net in child(n, 'nets')[1:]:
    name = child(net, 'name')[1]
    for nd in net:
        if isinstance(nd, list) and nd[0] == 'node':
            pin_net[(child(nd, 'ref')[1], child(nd, 'pin')[1])] = name
nets = {}
for name in sorted(set(pin_net.values())):
    ni = b.FindNet(name)
    if ni is None:
        ni = pcbnew.NETINFO_ITEM(b, name); b.Add(ni)
    nets[name] = ni
have = {f.GetReference(): f for f in b.GetFootprints()}
added = []
px = 100.0
for c in child(n, 'components')[1:]:
    ref = child(c, 'ref')[1]
    if ref in have: continue
    lib, name = child(c, 'footprint')[1].split(':')
    path = (FPLIB['airsoft_module'] + lib + '.pretty') if lib == 'airsoft_module' else (FPLIB[None] + lib + '.pretty')
    fp = pcbnew.FootprintLoad(path, name)
    fp.SetFPID(pcbnew.LIB_ID(lib, name))
    fp.SetReference(ref); fp.SetValue(child(c, 'value')[1])
    fp.SetPath(pcbnew.KIID_PATH('/' + child(c, 'tstamps')[1]))
    fp.SetPosition(pcbnew.VECTOR2I(pcbnew.FromMM(px), pcbnew.FromMM(130.0))); px += 3
    fp.Reference().SetVisible(False); fp.Value().SetVisible(False)
    b.Add(fp); have[ref] = fp; added.append(ref)
changed = 0
for ref, f in have.items():
    for p in f.Pads():
        k = (ref, p.GetNumber())
        if k in pin_net and p.GetNetname() != pin_net[k]:
            p.SetNet(nets[pin_net[k]]); changed += 1
b.Save(sys.argv[3])
print('added', added, 'pad nets changed', changed)
