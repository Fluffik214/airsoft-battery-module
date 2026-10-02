"""Print absolute pad positions/sizes for given refs: python pads.py dump.json REF..."""
import json, sys
d = json.load(open(sys.argv[1]))
want = set(sys.argv[2:])
for p in d['pads']:
    if p['ref'] in want and p['poly'] and p['poly'][0]:
        xs = [q[0] for q in p['poly'][0]]; ys = [q[1] for q in p['poly'][0]]
        print(f"{p['ref']:4s} {p['num']:4s} {p['net'].lstrip('/')[:14]:14s} c=({(min(xs)+max(xs))/2:7.3f},{(min(ys)+max(ys))/2:7.3f})"
              f"  x[{min(xs):7.3f},{max(xs):7.3f}] y[{min(ys):7.3f},{max(ys):7.3f}]")
