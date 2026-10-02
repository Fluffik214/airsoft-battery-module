"""Render the dumped board geometry: python render.py dump.json out.png x1 y1 x2 y2 scale [F|B|FB] [labels]"""
import json, sys, hashlib
from PIL import Image, ImageDraw, ImageFont

d = json.load(open(sys.argv[1]))
out = sys.argv[2]
x1, y1, x2, y2, S = map(float, sys.argv[3:8])
layers = sys.argv[8] if len(sys.argv) > 8 else 'FB'
labels = (sys.argv[9] == '1') if len(sys.argv) > 9 else True
W, H = int((x2 - x1) * S), int((y2 - y1) * S)
img = Image.new('RGB', (W, H), (18, 20, 26))
ov = Image.new('RGBA', (W, H), (0, 0, 0, 0))
dr = ImageDraw.Draw(ov)
def T(p): return ((p[0] - x1) * S, (p[1] - y1) * S)
try:
    font = ImageFont.truetype('arial.ttf', max(9, int(S * 0.28)))
    fsmall = ImageFont.truetype('arial.ttf', max(8, int(S * 0.22)))
except Exception:
    font = fsmall = ImageFont.load_default()

def netcol(n, a=255):
    if n in ('/GND',): return (60, 120, 255, a)
    if not n: return (150, 150, 150, a)
    h = hashlib.md5(n.encode()).digest()
    return (90 + h[0] % 166, 90 + h[1] % 166, 60 + h[2] % 120, a)

FCOL, BCOL = (200, 60, 60), (60, 160, 90)
if 'B' in layers:
    for z in d['zones']:
        if z['layer'] == 'B': dr.polygon([T(p) for p in z['pts']], fill=BCOL + (70,), outline=BCOL + (160,))
    for t in d['tracks']:
        if t['layer'] == 'B': dr.line([T(t['a']), T(t['b'])], fill=BCOL + (200,), width=max(1, int(t['w'] * S)))
if 'F' in layers:
    for z in d['zones']:
        if z['layer'] == 'F':
            c = netcol(z['net'], 90)
            dr.polygon([T(p) for p in z['pts']], fill=c, outline=c[:3] + (200,))
    for t in d['tracks']:
        if t['layer'] == 'F':
            w = max(1, int(t['w'] * S))
            dr.line([T(t['a']), T(t['b'])], fill=FCOL + (220,), width=w)
            for p in (t['a'], t['b']):
                q = T(p); r = w / 2
                dr.ellipse([q[0] - r, q[1] - r, q[0] + r, q[1] + r], fill=FCOL + (220,))
for p in d['pads']:
    if not any(l in layers for l in p['layers']) and not p['th']: continue
    c = netcol(p['net'], 235)
    for poly in p['poly']:
        if len(poly) > 2: dr.polygon([T(q) for q in poly], fill=c, outline=(255, 255, 255, 120))
for v in d['vias']:
    q = T((v['x'], v['y'])); r = v['d'] / 2 * S
    dr.ellipse([q[0] - r, q[1] - r, q[0] + r, q[1] + r], fill=(220, 200, 80, 255), outline=(0, 0, 0, 255))
for f in d['fps']:
    cy = f['cy']
    dr.rectangle([T(cy[:2]), T(cy[2:])], outline=(230, 230, 230, 90) if not f['back'] else (120, 230, 120, 90))
for e in d['edge']:
    dr.line([T(p) for p in e], fill=(255, 220, 0, 255), width=2)
img = Image.alpha_composite(img.convert('RGBA'), ov)
dt = ImageDraw.Draw(img)
if labels:
    for p in d['pads']:
        if not any(l in layers for l in p['layers']) and not p['th']: continue
        if not p['poly'] or not p['poly'][0]: continue
        xs = [q[0] for q in p['poly'][0]]; ys = [q[1] for q in p['poly'][0]]
        c = T(((min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2))
        n = p['net'].lstrip('/')
        if n.startswith('unconnected'): n = 'nc'
        dt.text(c, n, fill=(255, 255, 255), font=fsmall, anchor='mm')
    for f in d['fps']:
        cy = f['cy']
        dt.text(T((cy[0], cy[1])), f['ref'], fill=(255, 255, 120), font=font, anchor='lb')
img.convert('RGB').save(out)
print(out, img.size)
