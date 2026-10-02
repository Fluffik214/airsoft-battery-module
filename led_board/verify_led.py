"""Pin-by-pin check of the LED board netlist against the datasheet + firmware wiring.
python led_board/verify_led.py led_board/led_board.net"""
import sys, os
sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'layout_work'))
from sexp import parse

n = parse(open(sys.argv[1], encoding='utf8').read())
def child(e, k): return next((x for x in e if isinstance(x, list) and x[0] == k), None)
pin, comps = {}, {}
for c in child(n, 'components')[1:]:
    comps[child(c, 'ref')[1].strip('"')] = child(c, 'footprint')[1].strip('"')
for net in child(n, 'nets')[1:]:
    name = child(net, 'name')[1].strip('"').lstrip('/')
    for nd in net:
        if isinstance(nd, list) and nd[0] == 'node':
            pin[(child(nd, 'ref')[1].strip('"'), child(nd, 'pin')[1].strip('"'))] = name

fails, oks = [], 0
def expect(ref, p, net, why):
    global oks
    got = pin.get((ref, p))
    if got != net: fails.append(f'{ref}.{p}: expected {net}, got {got}  ({why})')
    else: oks += 1

# firmware: cell i between line i and line i+1; red lights when line i is +, green when line i+1 is +
# KPHBM-2012SURKCGKC: red anode 1, red cathode 2, green cathode 3, green anode 4
L = ['LED_A', 'LED_B', 'LED_C']
for i in range(3):
    first, second = L[i], L[(i + 1) % 3]
    d = f'S{i + 1}'
    expect(d, '1', first, f'cell {i + 1} red anode on the first line')
    expect(d, '2', second, f'cell {i + 1} red cathode on the second line')
    expect(d, '3', first, f'cell {i + 1} green cathode on the first line')
    expect(d, '4', second, f'cell {i + 1} green anode on the second line')
    if comps.get(d) != 'LED_SMD:LED_Kingbright_APHBM2012_2x1.25mm': fails.append(f'{d}: footprint {comps.get(d)}')
for i, net in enumerate(L):
    expect(f'TP{13 + i}', '1', net, 'wire pad (same name as the charger board pad)')
extra = set(comps) - {'S1', 'S2', 'S3', 'TP13', 'TP14', 'TP15', 'H1', 'H2'}
for h in ('H1', 'H2'):
    if comps.get(h) != 'MountingHole:MountingHole_2.2mm_M2': fails.append(f'{h}: footprint {comps.get(h)}')
if extra: fails.append(f'unexpected parts: {sorted(extra)}')

# every lit path, simulated: + line -> anode ... cathode -> 0 V line, per firmware scan step
def lit(ph, want):
    out = []
    for i in range(3):
        a = {p: pin[(f'S{i + 1}', p)] for p in '1234'}
        hi = L[ph]
        if a['1'] == hi and want[i] == 'red': out.append((i, 'red', a['2']))
        if a['4'] == hi and want[i] == 'green': out.append((i, 'green', a['3']))
    return out
for want in (['red'] * 3, ['green'] * 3, ['red', 'green', 'red']):
    seen = {}
    for ph in range(3):
        for i, col, lo in lit(ph, want):
            seen[i] = col
            # firmware pulls exactly this line low for this LED
            fw_lo = L[(ph + 1) % 3] if col == 'red' else L[(ph + 2) % 3]
            if lo != fw_lo: fails.append(f'scan {ph}: cell {i + 1} {col} needs {lo} low, firmware pulls {fw_lo}')
    if [seen.get(i) for i in range(3)] != want: fails.append(f'scan for {want} lights {seen}')
    else: oks += 1

print(f'checks passed: {oks}   failed: {len(fails)}')
for f in fails: print('  FAIL', f)
sys.exit(1 if fails else 0)
