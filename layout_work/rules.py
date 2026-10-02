"""Write design rules + net classes into a .kicad_pro (python rules.py file.kicad_pro)."""
import json, sys, copy
p = sys.argv[1]
d = json.load(open(p, encoding='utf8'))
ds = d['board']['design_settings']
ds['rules'].update({
    'min_clearance': 0.15, 'min_track_width': 0.15, 'min_copper_edge_clearance': 0.3,
    'min_via_diameter': 0.5, 'min_through_hole_diameter': 0.25, 'min_via_annular_width': 0.1,
    'min_hole_clearance': 0.2, 'min_hole_to_hole': 0.25, 'min_connection': 0.15,
})
ds['track_widths'] = [0.0, 0.2, 0.25, 0.3, 0.5, 0.8, 1.0, 1.5]
ds['via_dimensions'] = [{'diameter': 0.0, 'drill': 0.0}, {'diameter': 0.5, 'drill': 0.25}, {'diameter': 0.6, 'drill': 0.3}]
base = d['net_settings']['classes'][0]
base.update({'clearance': 0.15, 'track_width': 0.2, 'via_diameter': 0.6, 'via_drill': 0.3})
def cls(name, **kw):
    c = copy.deepcopy(base); c['name'] = name; c['priority'] = 0; c.update(kw); return c
d['net_settings']['classes'] = [base,
    cls('POWER', clearance=0.2, track_width=0.8, via_diameter=0.6, via_drill=0.3),
    cls('USB', clearance=0.15, track_width=0.2, diff_pair_width=0.2, diff_pair_gap=0.3)]
d['net_settings']['netclass_patterns'] = (
    [{'netclass': 'POWER', 'pattern': '/' + n} for n in ('VBUS', 'PMID', 'SYS', 'BAT_P', 'MAIN_P', 'SW1', 'SW2', 'GND')] +
    [{'netclass': 'USB', 'pattern': '/USB_D*'}])
json.dump(d, open(p, 'w', encoding='utf8'), indent=2)
print('rules written', p)
