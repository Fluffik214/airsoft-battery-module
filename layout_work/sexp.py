import re, sys
LIB = r"C:/Program Files/KiCad/10.0/share/kicad/symbols/"
TOK = re.compile(r'"(?:[^"\\]|\\.)*"|[()]|[^\s()"]+')

def parse(s):
    toks = TOK.findall(s); pos = 0
    def rd():
        nonlocal pos
        t = toks[pos]; pos += 1
        if t == '(':
            l = []
            while toks[pos] != ')':
                l.append(rd())
            pos += 1
            return l
        return t
    return rd()

def dump(x, ind=0):
    if not isinstance(x, list):
        return x
    if all(not isinstance(i, list) for i in x) or len(x) < 3:
        return '(' + ' '.join(dump(i) for i in x) + ')'
    pad = '\t' * (ind + 1)
    s = '(' + ' '.join(dump(i) for i in x if not isinstance(i, list))
    for i in x:
        if isinstance(i, list):
            s += '\n' + pad + dump(i, ind + 1)
    return s + '\n' + '\t' * ind + ')'

_cache = {}
def lib(name):
    if name not in _cache:
        _cache[name] = parse(open(LIB + name + '.kicad_sym', encoding='utf8').read())
    return _cache[name]

def find_sym(libname, sym):
    for e in lib(libname):
        if isinstance(e, list) and e[0] == 'symbol' and e[1].strip('"') == sym:
            return e

def pins(symexpr):
    out = []
    def walk(e, unit=None):
        if isinstance(e, list):
            if e[0] == 'symbol' and e is not symexpr:
                m = re.search(r'_(\d+)_(\d+)"$', e[1])
                unit = int(m.group(1)) if m else unit
            if e[0] == 'pin':
                d = {'type': e[1], 'unit': unit}
                for s in e[2:]:
                    if isinstance(s, list):
                        if s[0] == 'at': d['at'] = tuple(float(v) for v in s[1:])
                        if s[0] == 'length': d['len'] = float(s[1])
                        if s[0] == 'name': d['name'] = s[1].strip('"')
                        if s[0] == 'number': d['num'] = s[1].strip('"')
                out.append(d)
            for s in e:
                walk(s, unit)
    walk(symexpr)
    return out

if __name__ == '__main__':
    s = find_sym(sys.argv[1], sys.argv[2])
    for e in s:
        if isinstance(e, list) and e[0] == 'extends': print('EXTENDS', e)
        if isinstance(e, list) and e[0] == 'property' and e[1] in ('"Footprint"', '"Datasheet"', '"Description"'):
            print(e[1], e[2])
    for p in sorted(pins(s), key=lambda p: (p['unit'] or 0, p['num'].zfill(3))):
        print(p['unit'], p['num'], p['name'], p['type'], p['at'], p.get('len'))
