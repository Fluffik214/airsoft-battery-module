"""Copy value + user fields (MPN, Note) from a netlist into the matching PCB footprints.
kicad-python fields.py in.kicad_pcb netlist.net out.kicad_pcb"""
import sys, re, pcbnew
TOK = re.compile(r'"(?:[^"\\]|\\.)*"|[()]|[^\s()"]+')
def unq(t):
    return re.sub(r'\\(.)', r'\1', t[1:-1])
def parse(s):
    toks = TOK.findall(s); pos = 0
    def rd():
        nonlocal pos
        t = toks[pos]; pos += 1
        if t == '(':
            l = []
            while toks[pos] != ')': l.append(rd())
            pos += 1; return l
        return unq(t) if t.startswith('"') else t
    return rd()
def child(e, k): return next((x for x in e if isinstance(x, list) and x[0] == k), None)
b = pcbnew.LoadBoard(sys.argv[1])
n = parse(open(sys.argv[2], encoding='utf8').read())
fps = {f.GetReference(): f for f in b.Footprints()}
changed = []
for c in child(n, 'components')[1:]:
    ref = child(c, 'ref')[1]; f = fps.get(ref)
    if f is None: continue
    val = child(c, 'value')[1]
    if f.GetValue() != val: f.SetValue(val); changed.append(ref + ' value')
    fl = child(c, 'fields')
    for fe in (fl[1:] if fl else []):
        name = child(fe, 'name')[1]
        v = fe[2] if len(fe) > 2 and not isinstance(fe[2], list) else ''
        if name in ('Footprint', 'Datasheet', 'Description', 'Reference', 'Value'): continue
        if not f.HasField(name) or f.GetFieldText(name) != v:
            new = not f.HasField(name)
            f.SetField(name, v); changed.append(f'{ref} {name}')
            if new:                                   # a new field must not show up on the silkscreen
                fld = f.GetField(name); fld.SetVisible(False)
                fld.SetLayer(pcbnew.B_Fab if f.IsFlipped() else pcbnew.F_Fab)
b.Save(sys.argv[3])
print('changed', changed)
