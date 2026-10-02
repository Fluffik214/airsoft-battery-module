"""Independent pin-by-pin verification of the exported netlist against the intended design."""
import sys
from sexp import parse

n = parse(open(sys.argv[1], encoding='utf8').read())
def child(e, k): return next((x for x in e if isinstance(x, list) and x[0] == k), None)
comps = {}
for c in child(n, 'components')[1:]:
    comps[child(c, 'ref')[1].strip('"')] = child(c, 'value')[1].strip('"')
pin = {}
for net in child(n, 'nets')[1:]:
    name = child(net, 'name')[1].strip('"').lstrip('/')
    for nd in net:
        if isinstance(nd, list) and nd[0] == 'node':
            pin[(child(nd, 'ref')[1].strip('"'), child(nd, 'pin')[1].strip('"'))] = name

fails, oks = [], 0
def expect(ref, p, net, why=''):
    global oks
    got = pin.get((ref, p))
    gotn = None if (got is None or got.startswith('unconnected')) else got
    if gotn != net:
        fails.append(f'{ref}.{p}: expected {net}, got {got}   ({why})')
    else:
        oks += 1
def two(ref, a, b, val=None, why=''):
    """2-pin part between nets a and b (either orientation), optional value check."""
    got = {pin.get((ref, '1')), pin.get((ref, '2'))}
    if got != {a, b}:
        fails.append(f'{ref}: expected between {a} and {b}, got {got}   ({why})')
    elif val and not comps[ref].startswith(val):
        fails.append(f'{ref}: expected value {val}, got {comps[ref]}   ({why})')
    else:
        global oks; oks += 1

# ---------------- J2 balance plug, 3S JST-XH. Order set by the pack's plug (black wire lands on header pin 4):
# 1 = pack+ (cell3+), 2 = cell2+, 3 = cell1+, 4 = pack- (black)
expect('J2', '1', 'PACK_P', 'cell3+ = pack+ (red)')
expect('J2', '2', 'CELL2_P', 'cell2+ = cell3-')
expect('J2', '3', 'CELL1_P', 'cell1+ = cell2-')
expect('J2', '4', 'GND', 'pack negative (black) / cell1-')
# ---------------- BQ76920 3S per datasheet Table 9-2
for p, net, why in [('17', 'BMS_VC0', 'VC0'), ('16', 'BMS_VC1', 'VC1'), ('15', 'BMS_VC2', 'VC2'),
                    ('14', 'BMS_VC2', 'VC3 shorted to VC2 (3S)'), ('13', 'BMS_VC2', 'VC4 shorted to VC2 (3S)'),
                    ('12', 'BMS_VC5', 'VC5 = top of cell3'), ('10', 'BMS_BAT', 'BAT via Rf'), ('9', 'BMS_BAT', 'REGSRC'),
                    ('3', 'GND', 'VSS'), ('18', 'GND', 'SRP unused->VSS'), ('19', 'GND', 'SRN unused->VSS'),
                    ('4', 'I2C_SDA', ''), ('5', 'I2C_SCL', ''), ('6', 'BMS_TS1', ''), ('7', 'BMS_CAP1', ''),
                    ('8', 'BMS_REGOUT', ''), ('20', 'BMS_ALERT', ''), ('1', None, 'DSG unused'), ('2', None, 'CHG unused'),
                    ('11', None, 'NC')]:
    expect('U4', p, net, why)
two('R16', 'GND', 'BMS_VC0', '56R', 'cell1- filter')
two('R15', 'CELL1_P', 'BMS_VC1', '56R', 'cell1+ filter')
two('R14', 'CELL2_P', 'BMS_VC2', '56R', 'cell2+ filter')
two('R13', 'PACK_P', 'BMS_VC5', '56R', 'cell3+ filter')
two('R12', 'PACK_P', 'BMS_BAT', '100R', 'Rf supply filter')
two('C26', 'BMS_VC0', 'GND', '1uF'); two('C25', 'BMS_VC1', 'BMS_VC0', '1uF', 'cell1 diff cap')
two('C24', 'BMS_VC2', 'BMS_VC1', '1uF', 'cell2 diff cap'); two('C23', 'BMS_VC5', 'BMS_VC2', '1uF', 'cell3 diff cap')
two('C21', 'BMS_BAT', 'GND', '10uF'); two('C22', 'BMS_BAT', 'GND', '1uF')
two('C27', 'BMS_CAP1', 'GND', '1uF'); two('C28', 'BMS_REGOUT', 'GND', '1uF')
two('TH2', 'BMS_TS1', 'GND', '10k'); two('R17', 'BMS_BOOT', 'BMS_TS1', '1k'); two('R18', 'BMS_ALERT', 'GND', '1M')

# ---------------- BQ25798 charger (3S via PROG)
for p, net, why in [('2', 'VBUS', ''), ('3', 'VBUS', ''), ('8', 'VBUS', 'VAC2 no ACFET'), ('9', 'VBUS', 'VAC1 no ACFET'),
                    ('10', 'GND', 'ACDRV2 no FET'), ('11', 'GND', 'ACDRV1 no FET'), ('29', 'PMID', ''), ('28', 'SW1', ''),
                    ('26', 'SW2', ''), ('4', 'BTST1', ''), ('19', 'BTST2', ''), ('5', 'REGN', ''), ('17', 'REGN', 'ILIM_HIZ'),
                    ('13', 'CHG_CE_N', ''), ('14', 'I2C_SCL', ''), ('15', 'I2C_SDA', ''), ('16', 'CHG_TS', ''),
                    ('18', 'CHG_BATP', ''), ('20', 'CHG_PROG', ''), ('21', 'CHG_INT_N', ''), ('22', 'BAT_P', ''), ('23', 'BAT_P', ''),
                    ('24', 'CHG_SDRV', ''), ('25', 'SYS', ''), ('27', 'GND', ''), ('1', 'CHG_STAT', ''),
                    ('6', None, 'D+ unused'), ('7', None, 'D- unused'), ('12', None, 'QON internal pull-up')]:
    expect('U3', p, net, why)
two('R4', 'CHG_PROG', 'GND', '10.5k', 'PROG 10.5k = 3S @1.5MHz (datasheet Table 7-1)')
two('L1', 'SW1', 'SW2', '1uH', '1uH @1.5MHz'); two('C5', 'BTST1', 'SW1', '47nF'); two('C6', 'BTST2', 'SW2', '47nF')
two('R5', 'CHG_BATP', 'BAT_P', '100R', 'BATP 100R'); two('C20', 'CHG_SDRV', 'GND', '1nF', 'no ship FET')
two('R6', 'REGN', 'CHG_TS', '5.23k'); two('R7', 'CHG_TS', 'GND', '30.1k'); two('TH1', 'CHG_TS', 'GND', '10k')
two('R10', 'REGN', 'CHG_CE_N', '100k', '/CE default high = no charge'); two('C19', 'REGN', 'GND', '4.7uF')
two('R9', '+3V3', 'CHG_INT_N', '10k'); two('R8', 'REGN', 'CHG_LED_A', '2.2k')
expect('D2', '2', 'CHG_LED_A', 'LED anode'); expect('D2', '1', 'CHG_STAT', 'LED cathode -> STAT')
expect('Q1', '1', 'CHG_EN'); expect('Q1', '2', 'GND'); expect('Q1', '3', 'CHG_CE_N'); two('R11', 'CHG_EN', 'GND', '100k')
for r in ['C7', 'C8', 'C9', 'C10']: two(r, 'PMID', 'GND')
for r in ['C11', 'C12', 'C13', 'C14', 'C15', 'C16']: two(r, 'SYS', 'GND')
for r in ['C17', 'C18']: two(r, 'BAT_P', 'GND')
# ---------------- charge path: balance plug J2 pin 1 -> fuse -> charger BAT (no main-lead pads any more)
two('F1', 'PACK_P', 'BAT_P', '2.5A', 'charge current through the balance plug, fused')
for tp in ('TP1', 'TP2', 'TP3', 'TP4'):
    if tp in comps: fails.append(f'{tp}: old Deans pad still present')

# ---------------- USB-C + CH224K
for p in ['A4', 'A9', 'B4', 'B9']: expect('J1', p, 'VBUS')
for p in ['A1', 'B1', 'A12', 'B12', 'SH']: expect('J1', p, 'GND')
expect('J1', 'A5', 'CC1'); expect('J1', 'B5', 'CC2')
expect('J1', 'A6', 'USB_DP'); expect('J1', 'B6', 'USB_DP'); expect('J1', 'A7', 'USB_DM'); expect('J1', 'B7', 'USB_DM')
expect('J1', 'A8', None); expect('J1', 'B8', None)
for p, net, why in [('1', 'PD_VDD', ''), ('7', 'CC1', ''), ('6', 'CC2', ''), ('4', 'PD_DPDM', 'DP shorted to DM, PD-only'),
                    ('5', 'PD_DPDM', ''), ('9', 'PD_CFG1', ''), ('10', 'PD_PG', ''), ('11', 'GND', ''),
                    ('2', None, 'CFG2 float'), ('3', None, 'CFG3 float'), ('8', None, 'VBUS pin NC allowed PD-only')]:
    expect('U1', p, net, why)
two('R1', 'VBUS', 'PD_VDD', '1k'); two('C1', 'PD_VDD', 'GND', '1uF'); two('R2', 'PD_CFG1', 'GND', '56k', '56k = 15V')
two('R3', '+3V3', 'PD_PG', '10k'); expect('D1', '1', 'VBUS', 'TVS cathode'); expect('D1', '2', 'GND', 'TVS anode')
expect('U2', '1', 'USB_DP'); expect('U2', '2', 'USB_DM'); expect('U2', '3', 'GND')
for r in ['C2', 'C3', 'C4']: two(r, 'VBUS', 'GND')

# ---------------- MCU power
expect('Q2', '1', 'WAKE_G'); expect('Q2', '2', 'BAT_P', 'PMOS source on battery'); expect('Q2', '3', 'WAKE_RAIL')
expect('Q3', '1', 'PWR_HOLD'); expect('Q3', '2', 'GND'); expect('Q3', '3', 'WAKE_G')
two('R19', 'BAT_P', 'WAKE_G', '1M', 'PMOS off by default'); two('R20', 'PWR_HOLD', 'GND', '100k')
expect('D3', '1', 'VBUS', 'anode'); expect('D3', '2', 'WAKE_RAIL', 'anode'); expect('D3', '3', 'VIN_LDO', 'common cathode')
expect('U5', '1', 'VIN_LDO'); expect('U5', '2', '+3V3'); expect('U5', '3', 'GND')
two('C29', 'VIN_LDO', 'GND'); two('C30', '+3V3', 'GND')

# ---------------- MCU
for p, net in [('1', 'MCU_BOOT0'), ('2', 'I2C_SDA'), ('3', 'I2C_SCL'), ('4', 'MCU_NRST'), ('5', '+3V3'), ('6', 'CLED_C'),
               ('7', 'CLED_A'), ('8', 'CHG_EN'), ('9', 'CLED_B'), ('10', 'BMS_BOOT'), ('11', 'VBUS_SENSE'),
               ('12', 'LED_STATUS'), ('13', 'PWR_HOLD'), ('14', 'FAST_CHG_N'), ('15', 'GND'), ('16', '+3V3'),
               ('17', 'USB_DM'), ('18', 'USB_DP'), ('19', 'SWDIO'), ('20', 'SWCLK')]:
    expect('U6', p, net)
two('R21', 'MCU_BOOT0', 'GND', '10k'); expect('JP1', '1', '+3V3'); expect('JP1', '2', 'MCU_BOOT0')
two('R22', 'VBUS', 'VBUS_SENSE', '100k'); two('R23', 'VBUS_SENSE', 'GND', '10k'); two('C35', 'VBUS_SENSE', 'GND')
two('R24', '+3V3', 'I2C_SCL', '4.7k'); two('R25', '+3V3', 'I2C_SDA', '4.7k'); two('C34', 'MCU_NRST', 'GND')
two('R26', 'LED_STATUS', 'LED_G_A', '1k'); expect('D4', '2', 'LED_G_A'); expect('D4', '1', 'GND')
for r in ['C31', 'C32', 'C33']: two(r, '+3V3', 'GND')
expect('JP2', '1', 'FAST_CHG_N', 'fast-charge jumper'); expect('JP2', '2', 'GND')
# cell LEDs: PA1/PA3/PA0 -> 100R -> wire pads A/B/C
for r, m, n, tp in (('R27', 'CLED_A', 'LED_A', 'TP13'), ('R28', 'CLED_B', 'LED_B', 'TP14'), ('R29', 'CLED_C', 'LED_C', 'TP15')):
    two(r, m, n, '100R', 'cell LED series resistor'); expect(tp, '1', n, 'cell LED wire pad')

# ---------------- test pads
for tp, net in (('TP5', 'SWDIO'), ('TP6', 'SWCLK'), ('TP7', '+3V3'), ('TP8', 'GND'), ('TP9', 'VBUS'),
                ('TP10', 'BAT_P'), ('TP11', 'I2C_SDA'), ('TP12', 'I2C_SCL')):
    expect(tp, '1', net, 'test pad')

# ---------------- coverage: every component pin was checked?
checked = set()
import re
for f in fails: pass
all_pins = set(pin)
print(f'checks passed: {oks}   failed: {len(fails)}')
for f in fails: print('  FAIL', f)
refs_in_netlist = set(comps)
covered_refs = {r for (r, _) in pin}
print('components in netlist:', len(comps))
import collections
seen=collections.Counter(r for (r,_) in pin)
print('refs never checked:', sorted(r for r in comps if not any(r in f for f in [open('verify.py').read()])))
