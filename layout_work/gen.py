"""Generate the full KiCad schematic for the USB-C PD 3S LiPo charger module.

Connectivity is by net labels placed exactly on pin endpoints (no hand-drawn wires),
so every connection is explicit and ERC-checkable.
"""
import uuid, copy, re, sys
from sexp import parse, dump, find_sym, pins as sym_pins, lib

OUT = sys.argv[1]
PROJECT = "usbc pd airsoft module"
ROOT_UUID = "5732c36f-2098-4755-bf77-38f4abb30fd8"   # keep existing sheet uuid

def U(): return str(uuid.uuid4())
def q(s): return '"' + s.replace('\\', '\\\\').replace('"', '\\"').replace('\n', '\\n') + '"'

# ---------------------------------------------------------------- library symbols
lib_cache = {}
def get_lib_symbol(lib_id):
    """Return flattened symbol s-expr named lib_id (handles 'extends')."""
    if lib_id in lib_cache: return lib_cache[lib_id]
    libname, name = lib_id.split(':')
    s = copy.deepcopy(find_sym(libname, name))
    ext = [e for e in s if isinstance(e, list) and e[0] == 'extends']
    if ext:
        parent_name = ext[0][1].strip('"')
        parent = copy.deepcopy(find_sym(libname, parent_name))
        child_props = {e[1]: e for e in s if isinstance(e, list) and e[0] == 'property'}
        new = []
        for e in parent:
            if isinstance(e, list) and e[0] == 'property' and e[1] in child_props:
                new.append(child_props.pop(e[1]))
            elif isinstance(e, list) and e[0] == 'symbol':
                e[1] = e[1].replace(parent_name, name)
                new.append(e)
            else:
                new.append(e)
        # insert leftover child-only props before sub-symbols
        idx = next(i for i, e in enumerate(new) if isinstance(e, list) and e[0] == 'symbol')
        for p in child_props.values(): new.insert(idx, p)
        s = new
    s[1] = q(lib_id)
    lib_cache[lib_id] = s
    return s

# ---------------------------------------------------------------- schematic items
parts, labels, ncs, texts, rects = [], [], [], [], []
refcount = {}

def nextref(prefix):
    refcount[prefix] = refcount.get(prefix, 0) + 1
    return f"{prefix}{refcount[prefix]}"

LABEL_ANG = {0: 180, 180: 0, 90: 270, 270: 90}

def place(lib_id, ref_prefix, value, fp, x, y, nets, props=None, ref=None, dnp=False):
    """nets: {pin_number: netname or None(no-connect)}. Every pin must be listed."""
    sym = get_lib_symbol(lib_id)
    ref = ref or nextref(ref_prefix)
    ps = sym_pins(sym)
    nums = {p['num'] for p in ps}
    missing = nums - set(nets)
    extra = set(nets) - nums
    assert not missing and not extra, (ref, lib_id, 'missing', missing, 'extra', extra)
    seen = {}
    for p in ps:
        px, py, pa = p['at']
        ax, ay = round(x + px, 3), round(y - py, 3)
        net = nets[p['num']]
        key = (ax, ay)
        if key in seen:
            assert seen[key] == net, (ref, p['num'], net, seen[key])
            continue
        seen[key] = net
        if net is None:
            ncs.append((ax, ay))
        else:
            labels.append((net, ax, ay, LABEL_ANG[int(pa) % 360]))
    top = max((p['at'][1] for p in ps), default=0)
    def walk(e):
        nonlocal top
        if isinstance(e, list):
            if e[0] in ('start', 'end', 'xy') and len(e) == 3:
                top = max(top, float(e[2]))
            for i in e: walk(i)
    walk(sym)
    if any(p['at'][2] == 270 and p['at'][1] >= top - 0.01 for p in ps):
        top += 11
    parts.append(dict(lib_id=lib_id, ref=ref, value=value, fp=fp, x=x, y=y, top=top,
                      pins=sorted(nums), props=props or {}, dnp=dnp))
    return ref

# shorthand for 2-pin passives (vertical, pin1 top / pin2 bottom)
FP = {
    'R0402': 'Resistor_SMD:R_0402_1005Metric', 'R0603': 'Resistor_SMD:R_0603_1608Metric',
    'R1206': 'Resistor_SMD:R_1206_3216Metric', 'C0402': 'Capacitor_SMD:C_0402_1005Metric',
    'C0603': 'Capacitor_SMD:C_0603_1608Metric', 'C0805': 'Capacitor_SMD:C_0805_2012Metric',
}
def R(val, n1, n2, x, y, fp='R0402', **kw):
    return place('Device:R_Small', 'R', val, FP[fp], x, y, {'1': n1, '2': n2}, **kw)
def C(val, n1, n2, x, y, fp='C0402', **kw):
    return place('Device:C_Small', 'C', val, FP[fp], x, y, {'1': n1, '2': n2}, **kw)

def text(s, x, y, size=1.5, bold=False):
    texts.append((s, x, y, size, bold))
def box(x1, y1, x2, y2, title):
    rects.append((x1, y1, x2, y2))
    text(title, x1 + 2, y1 + 6, 3, True)

def row(x0, y, step=10.16):
    """Generator of x positions for a row of passives."""
    x = x0
    while True:
        yield x, y
        x += step

# ================================================================= BLOCK A: USB-C + PD trigger
box(10, 10, 195, 150, "1. USB-C INPUT + PD SINK (requests 15V)")
place('Connector:USB_C_Receptacle_USB2.0_16P', 'J', 'USB_C_Receptacle_USB2.0_16P',
      'Connector_USB:USB_C_Receptacle_HRO_TYPE-C-31-M-12', 40.64, 68.58,
      {'A1': 'GND', 'B1': 'GND', 'A12': 'GND', 'B12': 'GND', 'SH': 'GND',
       'A4': 'VBUS', 'A9': 'VBUS', 'B4': 'VBUS', 'B9': 'VBUS',
       'A5': 'CC1', 'B5': 'CC2', 'A6': 'USB_DP', 'B6': 'USB_DP', 'A7': 'USB_DM', 'B7': 'USB_DM',
       'A8': None, 'B8': None},
      props={'MPN': 'HRO TYPE-C-31-M-12 (LCSC C165948)'}, ref='J1')
place('Interface_USB:CH224K', 'U', 'CH224K', 'Package_SO:SSOP-10-1EP_3.9x4.9mm_P1mm_EP2.1x3.3mm',
      134.62, 60.96,
      {'1': 'PD_VDD', '2': None, '3': None, '4': 'PD_DPDM', '5': 'PD_DPDM', '6': 'CC2', '7': 'CC1',
       '8': None, '9': 'PD_CFG1', '10': 'PD_PG', '11': 'GND'},
      props={'MPN': 'WCH CH224K (LCSC C970725)'}, ref='U1')
place('Power_Protection:TPD2E2U06DCK', 'U', 'TPD2E2U06DCK', 'Package_TO_SOT_SMD:SOT-323_SC-70',
      134.62, 104.14, {'1': 'USB_DP', '2': 'USB_DM', '3': 'GND'}, ref='U2',
      props={'MPN': 'TI TPD2E2U06DCKR'})
r = row(22.86, 132.08)
x, y = next(r); place('Diode:SMF20A', 'D', 'SMF20A', 'Diode_SMD:D_SMF', x, y - 3.81 + 3.81,
                      {'1': 'VBUS', '2': 'GND'}, ref='D1', props={'MPN': 'Littelfuse SMF20A (20V TVS)'})
x, y = next(r); x, y = next(r); R('1k', 'VBUS', 'PD_VDD', x, y, fp='R1206', props={'Note': 'CH224K VDD shunt feed, 1/4W'})
x, y = next(r); C('1uF 10V', 'PD_VDD', 'GND', x, y)
x, y = next(r); R('56k 1%', 'PD_CFG1', 'GND', x, y, props={'Note': '56k on CFG1 = request 15V'})
x, y = next(r); R('10k', '+3V3', 'PD_PG', x, y)
x, y = next(r); C('10uF 35V', 'VBUS', 'GND', x, y, fp='C0805')
x, y = next(r); C('10uF 35V', 'VBUS', 'GND', x, y, fp='C0805')
x, y = next(r); C('100nF 50V', 'VBUS', 'GND', x, y)
x, y = next(r); place('Power:PWR_FLAG' if False else 'power:PWR_FLAG', '#FLG', 'PWR_FLAG', '', x, y, {'1': 'VBUS'})
x, y = next(r); place('power:PWR_FLAG', '#FLG', 'PWR_FLAG', '', x, y, {'1': 'GND'})
x, y = next(r); place('power:PWR_FLAG', '#FLG', 'PWR_FLAG', '', x, y, {'1': 'PD_VDD'})
text("CC1/CC2 direct to CH224K (internal Rd). DP/DM of CH224K shorted together and NOT\n"
     "connected to the connector (WCH 'PD-only' mode) so USB D+/D- go to the MCU.\n"
     "CFG2/CFG3 float = single-resistor mode. CFG1 56k = 15V. PG low = contract OK.\n"
     "CH224K VBUS sense pin left NC (allowed in PD-only mode; pin is only 13.5V rated).",
     14, 18.0 + 4, 1.4)

# ================================================================= BLOCK B: BQ25798 buck-boost charger
box(200, 10, 395, 215, "2. 3S CHARGER  BQ25798 (buck-boost, 0x6B)")
place('Battery_Management:BQ25798', 'U', 'BQ25798RQMR', 'Package_DFN_QFN:Texas_RQM0029A_VQFN-29_4x4mm_P0.4mm',
      297.18, 86.36,
      {'1': 'CHG_STAT', '2': 'VBUS', '3': 'VBUS', '4': 'BTST1', '5': 'REGN', '6': None, '7': None,
       '8': 'VBUS', '9': 'VBUS', '10': 'GND', '11': 'GND', '12': None, '13': 'CHG_CE_N',
       '14': 'I2C_SCL', '15': 'I2C_SDA', '16': 'CHG_TS', '17': 'REGN', '18': 'CHG_BATP',
       '19': 'BTST2', '20': 'CHG_PROG', '21': 'CHG_INT_N', '22': 'BAT_P', '23': 'BAT_P',
       '24': 'CHG_SDRV', '25': 'SYS', '26': 'SW2', '27': 'GND', '28': 'SW1', '29': 'PMID'},
      props={'MPN': 'TI BQ25798RQMR'}, ref='U3')
r = row(205.74, 160.02)
x, y = next(r); place('Device:L_Small', 'L', '1uH', 'Inductor_SMD:L_Coilcraft_XAL4020-XXX', x, y,
                      {'1': 'SW1', '2': 'SW2'}, ref='L1', props={'MPN': 'Coilcraft XAL4020-102MEB'})
x, y = next(r); C('47nF 25V', 'BTST1', 'SW1', x, y)
x, y = next(r); C('47nF 25V', 'BTST2', 'SW2', x, y)
for _ in range(3):
    x, y = next(r); C('10uF 35V', 'PMID', 'GND', x, y, fp='C0805')
x, y = next(r); C('100nF 50V', 'PMID', 'GND', x, y)
for _ in range(5):
    x, y = next(r); C('10uF 25V', 'SYS', 'GND', x, y, fp='C0805')
x, y = next(r); C('100nF 50V', 'SYS', 'GND', x, y)
x, y = next(r); C('10uF 25V', 'BAT_P', 'GND', x, y, fp='C0805')
x, y = next(r); C('10uF 25V', 'BAT_P', 'GND', x, y, fp='C0805')
r = row(205.74, 200.66)
x, y = next(r); C('4.7uF 16V', 'REGN', 'GND', x, y, fp='C0603')
x, y = next(r); R('10.5k 1%', 'CHG_PROG', 'GND', x, y, props={'Note': 'PROG: 3S, 1.5MHz'})
x, y = next(r); R('100R', 'CHG_BATP', 'BAT_P', x, y)
x, y = next(r); C('1nF 50V', 'CHG_SDRV', 'GND', x, y, props={'Note': 'no ship FET fitted'})
x, y = next(r); R('5.23k 1%', 'REGN', 'CHG_TS', x, y)
x, y = next(r); R('30.1k 1%', 'CHG_TS', 'GND', x, y)
x, y = next(r); place('Device:Thermistor_NTC', 'TH', '10k B3435 (103AT)', 'Resistor_SMD:R_0402_1005Metric', x, y,
                      {'1': 'CHG_TS', '2': 'GND'}, ref='TH1', props={'MPN': 'Murata NCP15XH103F03RC',
                      'Note': 'Place at board edge touching the pack'})
x, y = next(r); R('2.2k', 'REGN', 'CHG_LED_A', x, y)
next(r); x, y = next(r); place('Device:LED_Small', 'D', 'RED (charging)', 'LED_SMD:LED_0603_1608Metric', x, y,
                      {'1': 'CHG_STAT', '2': 'CHG_LED_A'}, ref='D2')
next(r); x, y = next(r); R('10k', '+3V3', 'CHG_INT_N', x, y)
x, y = next(r); R('100k', 'REGN', 'CHG_CE_N', x, y, props={'Note': 'charging disabled by default'})
x, y = next(r); place('Transistor_FET:2N7002', 'Q', '2N7002', 'Package_TO_SOT_SMD:SOT-23', x + 2.54, y,
                      {'1': 'CHG_EN', '2': 'GND', '3': 'CHG_CE_N'}, ref='Q1')
next(r)
x, y = next(r); R('100k', 'CHG_EN', 'GND', x, y)
text("PROG 10.5k -> 3S / 1.5MHz: POR defaults VREG=12.6V, ICHG=1A, VSYSMIN=9V.\n"
     "No ACFETs: VAC1/VAC2 tied to VBUS, ACDRV1/2 to GND. ILIM_HIZ->REGN (limit set by I2C).\n"
     "/CE held HIGH by 100k -> NO charging until MCU drives CHG_EN high (after cell check).\n"
     "BQ25798 D+/D- left open: MCU must clear AUTO_INDET_EN and set IINDPM over I2C.\n"
     "TS: 5.23k/30.1k + 10k NTC = 0..60C window (datasheet 7.3.9.5). Shutdown mode = 0.5uA.",
     204, 22, 1.4)

# ================================================================= BLOCK C: BQ76920 cell monitor/balancer
box(400, 10, 580, 215, "3. CELL MONITOR/BALANCER  BQ7692003 (0x08)")
place('Battery_Management:BQ76920PW', 'U', 'BQ7692003PWR', 'Package_SO:TSSOP-20_4.4x6.5mm_P0.65mm',
      490.22, 86.36,
      {'1': None, '2': None, '3': 'GND', '4': 'I2C_SDA', '5': 'I2C_SCL', '6': 'BMS_TS1', '7': 'BMS_CAP1',
       '8': 'BMS_REGOUT', '9': 'BMS_BAT', '10': 'BMS_BAT', '11': None, '12': 'BMS_VC5',
       '13': 'BMS_VC2', '14': 'BMS_VC2', '15': 'BMS_VC2', '16': 'BMS_VC1', '17': 'BMS_VC0',
       '18': 'GND', '19': 'GND', '20': 'BMS_ALERT'},
      props={'MPN': 'TI BQ7692003PWR (3.3V LDO, addr 0x08, CRC)'}, ref='U4')
r = row(405.13, 160.02)
x, y = next(r); place('Connector_Generic:Conn_01x04', 'J', 'BALANCE 3S (JST-XH 4P)',
                      'Connector_JST:JST_XH_S4B-XH-SM4-TB_1x04-1MP_P2.50mm_Horizontal', x + 5.08, y,
                      {'1': 'PACK_P', '2': 'CELL2_P', '3': 'CELL1_P', '4': 'GND'}, ref='J2',
                      props={'MPN': 'JST S4B-XH-SM4-TB', 'Note': 'Pin4 = pack NEGATIVE (black), pin1 = pack + (red): set by the pack plug'})
next(r)
x, y = next(r); R('100R', 'PACK_P', 'BMS_BAT', x, y, fp='R0603', props={'Note': 'Rf supply filter'})
x, y = next(r); C('10uF 25V', 'BMS_BAT', 'GND', x, y, fp='C0805', props={'Note': 'Cf supply filter'})
x, y = next(r); C('1uF 25V', 'BMS_BAT', 'GND', x, y, fp='C0603', props={'Note': 'REGSRC cap'})
x, y = next(r); R('56R', 'PACK_P', 'BMS_VC5', x, y, fp='R0603')
x, y = next(r); R('56R', 'CELL2_P', 'BMS_VC2', x, y, fp='R0603')
x, y = next(r); R('56R', 'CELL1_P', 'BMS_VC1', x, y, fp='R0603')
x, y = next(r); R('56R', 'GND', 'BMS_VC0', x, y, fp='R0603')
x, y = next(r); C('1uF 10V', 'BMS_VC5', 'BMS_VC2', x, y)
x, y = next(r); C('1uF 10V', 'BMS_VC2', 'BMS_VC1', x, y)
x, y = next(r); C('1uF 10V', 'BMS_VC1', 'BMS_VC0', x, y)
x, y = next(r); C('1uF 10V', 'BMS_VC0', 'GND', x, y)
r = row(405.13, 200.66)
x, y = next(r); C('1uF 10V', 'BMS_CAP1', 'GND', x, y)
x, y = next(r); C('1uF 10V', 'BMS_REGOUT', 'GND', x, y)
x, y = next(r); place('Device:Thermistor_NTC', 'TH', '10k B3435 (103AT)', 'Resistor_SMD:R_0402_1005Metric', x, y,
                      {'1': 'BMS_TS1', '2': 'GND'}, ref='TH2', props={'MPN': 'Murata NCP15XH103F03RC'})
x, y = next(r); R('1k', 'BMS_BOOT', 'BMS_TS1', x, y, props={'Note': 'MCU pulses BMS_BOOT high 5ms to wake from SHIP'})
x, y = next(r); R('1M', 'BMS_ALERT', 'GND', x, y)
x, y = next(r); place('power:PWR_FLAG', '#FLG', 'PWR_FLAG', '', x, y, {'1': 'BMS_BAT'})
x, y = next(r); place('power:PWR_FLAG', '#FLG', 'PWR_FLAG', '', x, y, {'1': 'PACK_P'})
text("3S per datasheet Table 9-2: VC5-VC4 = cell3, VC4=VC3=VC2 shorted, VC2-VC1 = cell2, VC1-VC0 = cell1.\n"
     "Rc=56R / Cc=1uF cell filters -> ~37mA internal balancing (datasheet min Rc 40R, max 50mA) (never balance adjacent cells together).\n"
     "SRP/SRN to VSS (no coulomb counter), CHG/DSG unused. Sits in SHIP mode (~0.6uA) when idle.\n"
     "Balance lead also carries the charge current (pins 1 and 4): firmware pauses charging before each cell reading.",
     404, 22, 1.4)

# ================================================================= BLOCK D: MCU power path
box(10, 160, 195, 300, "4. MCU POWER (USB / self-hold)")
place('Transistor_FET:DMP3099L', 'Q', 'DMP3099L', 'Package_TO_SOT_SMD:SOT-23', 40.64, 223.52,
      {'1': 'WAKE_G', '2': 'BAT_P', '3': 'WAKE_RAIL'}, ref='Q2', props={'MPN': 'Diodes DMP3099L-7 (Vgs +-20V)'})
place('Diode:BAT54C', 'D', 'BAT54C', 'Package_TO_SOT_SMD:SOT-23', 91.44, 223.52,
      {'1': 'VBUS', '2': 'WAKE_RAIL', '3': 'VIN_LDO'}, ref='D3')
place('Regulator_Linear:AP7381-33SA-7', 'U', 'AP7381-33SA-7', 'Package_TO_SOT_SMD:SOT-23', 147.32, 223.52,
      {'1': 'VIN_LDO', '2': '+3V3', '3': 'GND'}, ref='U5', props={'MPN': 'Diodes AP7381-33SA-7 (40V in)'})
r = row(17.78, 271.78)
x, y = next(r); R('1M', 'BAT_P', 'WAKE_G', x, y)
next(r); x, y = next(r); place('Transistor_FET:2N7002', 'Q', '2N7002', 'Package_TO_SOT_SMD:SOT-23', x + 2.54, y,
                      {'1': 'PWR_HOLD', '2': 'GND', '3': 'WAKE_G'}, ref='Q3')
next(r)
x, y = next(r); R('100k', 'PWR_HOLD', 'GND', x, y)
x, y = next(r); C('1uF 50V', 'VIN_LDO', 'GND', x, y, fp='C0805')
x, y = next(r); C('4.7uF 10V', '+3V3', 'GND', x, y, fp='C0603')
x, y = next(r); place('power:PWR_FLAG', '#FLG', 'PWR_FLAG', '', x, y, {'1': 'VIN_LDO'})
text("3V3 = OR of: VBUS (charger OR a phone in the USB-C port)  |  battery via Q2 when the MCU\n"
     "asserts PWR_HOLD (lets the MCU finish balancing, put BQ25798 in shutdown + BQ76920 in\n"
     "SHIP after unplug, then release itself). Nothing else can power the MCU from the pack.\n"
     "Idle battery drain ~1-2uA total.",
     14, 172, 1.4)

# ================================================================= BLOCK E: MCU
box(200, 225, 395, 410, "5. MCU  STM32F042F6P6 (TSSOP-20, USB FS, DFU)")
place('MCU_ST_STM32F0:STM32F042F6Px', 'U', 'STM32F042F6P6', 'Package_SO:TSSOP-20_4.4x6.5mm_P0.65mm',
      297.18, 299.72,
      {'1': 'MCU_BOOT0', '2': 'I2C_SDA', '3': 'I2C_SCL', '4': 'MCU_NRST', '5': '+3V3',
       '6': 'CLED_C', '7': 'CLED_A', '8': 'CHG_EN', '9': 'CLED_B', '10': 'BMS_BOOT',
       '11': 'VBUS_SENSE', '12': 'LED_STATUS', '13': 'PWR_HOLD', '14': 'FAST_CHG_N', '15': 'GND', '16': '+3V3',
       '17': 'USB_DM', '18': 'USB_DP', '19': 'SWDIO', '20': 'SWCLK'},
      props={'MPN': 'ST STM32F042F6P6'}, ref='U6')
r = row(205.74, 358.14)
x, y = next(r); C('100nF', '+3V3', 'GND', x, y)
x, y = next(r); C('100nF', '+3V3', 'GND', x, y)
x, y = next(r); C('1uF 10V', '+3V3', 'GND', x, y)
x, y = next(r); C('100nF', 'MCU_NRST', 'GND', x, y)
x, y = next(r); R('10k', 'MCU_BOOT0', 'GND', x, y)
x, y = next(r); R('100k 1%', 'VBUS', 'VBUS_SENSE', x, y)
x, y = next(r); R('10k 1%', 'VBUS_SENSE', 'GND', x, y)
x, y = next(r); C('100nF', 'VBUS_SENSE', 'GND', x, y)
x, y = next(r); R('4.7k', '+3V3', 'I2C_SCL', x, y)
x, y = next(r); R('4.7k', '+3V3', 'I2C_SDA', x, y)
x, y = next(r); R('1k', 'LED_STATUS', 'LED_G_A', x, y)
next(r); x, y = next(r); place('Device:LED_Small', 'D', 'GREEN (status)', 'LED_SMD:LED_0603_1608Metric', x, y,
                      {'1': 'GND', '2': 'LED_G_A'}, ref='D4')
next(r); x, y = next(r); place('Jumper:SolderJumper_2_Open', 'JP', 'DFU (BOOT0)', 'Jumper:SolderJumper-2_P1.3mm_Open_RoundedPad1.0x1.5mm',
                      x + 5.08, y, {'1': '+3V3', '2': 'MCU_BOOT0'}, ref='JP1')
next(r); x, y = next(r); place('Jumper:SolderJumper_2_Open', 'JP', 'FAST CHARGE', 'Jumper:SolderJumper-2_P1.3mm_Open_RoundedPad1.0x1.5mm',
                      x + 5.08, y, {'1': 'FAST_CHG_N', '2': 'GND'}, ref='JP2',
                      props={'Note': 'open (default) = slow charge, bridged = fast charge. PB1 internal pull-up.'})
text("PF0/PF1 = I2C1 SDA/SCL (AF1). PA11/PA12 remapped onto pins 17/18 for USB (SYSCFG PA11_PA12_RMP).\n"
     "PA1/PA3/PA0 = charlieplexed cell LEDs A/B/C (pads TP13-15). Charger /INT, CH224K PG and BMS ALERT are not\n"
     "wired to the MCU (firmware polls I2C). PA2 CHG_EN, PA4 BMS boot, PA5 VBUS/11 ADC, PA6 LED, PA7 PWR_HOLD.\n"
     "PB1 = FAST_CHG_N (JP2 to GND, internal pull-up): open = slow charge (default), bridged = fast charge.\n"
     "Bridge JP1 + plug USB = ST ROM DFU bootloader. Backup: ST-Link on test pads TP5 SWDIO, TP6 SWCLK, TP7 3V3, TP8 GND.",
     204, 237, 1.4)

# ================================================================= BLOCK F: charge path (balance plug only)
box(400, 225, 580, 372, "6. CHARGE PATH (through the balance plug J2)")
refcount['TP'] = 4          # TP1-TP4 were the Deans wire pads (removed); keep the TP5.. numbering
r = row(410.21, 290.83, step=17.78)
x, y = next(r); place('Device:Fuse_Small', 'F', '2.5A 32V fast', 'Fuse:Fuse_1206_3216Metric', x, y,
                      {'1': 'PACK_P', '2': 'BAT_P'}, ref='F1',
                      props={'MPN': 'AEM F1206SB2500V032TM (LCSC C310999), 2.5A fast, 50A breaking'})
text("The pack connects ONLY through the JST-XH balance plug J2 (no Deans / main-lead pads).\n"
     "Charge current: J2 pin 1 (pack +) -> F1 2.5A fast -> BAT_P (charger), return via J2 pin 4 (GND).\n"
     "J2 pin order follows the pack's plug: its black wire lands on header pin 4 (checked on the real pack).\n"
     "F1 protects the thin balance wires: a board short would otherwise burn them inside the stock.\n"
     "Max charge 1.4 A (JP2 bridged). JST-XH is rated 3 A on AWG22; keep 26 AWG leads at <= ~2 A.\n"
     "Field check: plug a phone (USB-C OTG) into the USB-C port ->\n"
     "MCU wakes on the phone's 5V, reports V + % per cell over USB; charges only at 12-17 V.",
     404, 318, 1.4)

for i in range(4):
    usb_end = i in (0, 2)   # H1/H3 flank the USB-C: M1.6 so the screw heads clear its shell
    place('Mechanical:MountingHole', 'H', 'M1.6' if usb_end else 'M2',
          'airsoft_module:MountingHole_1.7mm_M1.6' if usb_end else 'MountingHole:MountingHole_2.2mm_M2',
          420.0 + i * 12.7, 350.52, {}, ref=f'H{i + 1}')
text("H1/H3 (USB-C end): M1.6 (1.7 mm) so heads clear the USB shell. H2/H4: M2 (2.2 mm). Concentric with R3 corners.", 404, 360, 1.4)

# ---- test pads: programming row (back) + measurement pads
r = row(410.21, 304.8, step=10.16)
for lbl, net in [('SWDIO', 'SWDIO'), ('SWCLK', 'SWCLK'), ('3V3', '+3V3'), ('GND', 'GND'),
                 ('VBUS', 'VBUS'), ('BAT', 'BAT_P'), ('SDA', 'I2C_SDA'), ('SCL', 'I2C_SCL')]:
    x, y = next(r)
    place('Connector:TestPoint', 'TP', 'TP ' + lbl, 'TestPoint:TestPoint_Pad_D1.0mm', x, y, {'1': net})
text("TP5-TP8: SWD programming / rescue (ST-Link: SWDIO, SWCLK, 3V3, GND) on the back. TP9-TP12: VBUS, BAT, I2C SDA, I2C SCL probe pads.",
     404, 312, 1.4)

# ================================================================= BLOCK G: cell LEDs (remote board)
box(400, 376, 580, 416, "7. CELL LEDs (3 wire pads near the USB-C, charlieplexed)")
r = row(410.21, 396.24, step=10.16)
for mcu_net, pad_net in (('CLED_A', 'LED_A'), ('CLED_B', 'LED_B'), ('CLED_C', 'LED_C')):
    x, y = next(r); R('100R', mcu_net, pad_net, x, y, props={'Note': 'cell LED line (charlieplexed)'})
for net in ('LED_A', 'LED_B', 'LED_C'):
    x, y = next(r)
    place('Connector:TestPoint', 'TP', net, 'TestPoint:TestPoint_Pad_D1.5mm', x, y, {'1': net})
text("Remote LED board: three 2-pin red/green bi-colour LEDs (one per cell), wired in a triangle:\n"
     "cell 1 between LED_A-LED_B, cell 2 between LED_B-LED_C, cell 3 between LED_C-LED_A.\n"
     "Red LED anode on the first pad of the pair, green anode on the second. 3 wires, no GND wire.\n"
     "Red = cell charging, green = cell full; lit only while charging. ~6 mA peak, 1/3 duty.",
     404, 400, 1.4)

# ================================================================= emit
def lib_symbols_sexpr():
    ids = sorted({p['lib_id'] for p in parts})
    return '\t(lib_symbols\n' + ''.join('\t\t' + dump(get_lib_symbol(i), 2) + '\n' for i in ids) + '\t)\n'

def eff(size=1.27, justify=None, hide=False, bold=False):
    s = f'(effects (font (size {size} {size}){" (bold yes)" if bold else ""})'
    if justify: s += f' (justify {justify})'
    if hide: s += ' (hide yes)'
    return s + ')'

out = []
out.append('(kicad_sch\n\t(version 20250114)\n\t(generator "eeschema")\n\t(generator_version "9.0")\n'
           f'\t(uuid "{ROOT_UUID}")\n\t(paper "A2")\n')
out.append('\t(title_block\n\t\t(title "USB-C PD 3S LiPo charger module")\n'
           '\t\t(date "2026-10-01")\n\t\t(rev "A")\n'
           '\t\t(comment 1 "CH224K 15V PD -> BQ25798 3S charger -> BQ76920 monitor")\n'
           '\t\t(comment 2 "STM32F042 MCU, powered from USB-C only. No BGA.")\n\t)\n')
out.append(lib_symbols_sexpr())
for (x1, y1, x2, y2) in rects:
    out.append(f'\t(rectangle (start {x1} {y1}) (end {x2} {y2}) (stroke (width 0.3) (type dash)) (fill (type none)) (uuid "{U()}"))\n')
for (s, x, y, size, bold) in texts:
    out.append(f'\t(text {q(s)} (exclude_from_sim no) (at {x} {y} 0) {eff(size, "left bottom" if False else "left top", bold=bold)} (uuid "{U()}"))\n')
for (x, y) in ncs:
    out.append(f'\t(no_connect (at {x} {y}) (uuid "{U()}"))\n')
for (net, x, y, a) in labels:
    just = 'left bottom' if a in (0, 90) else 'right bottom'
    out.append(f'\t(label {q(net)} (at {x} {y} {a}) (fields_autoplaced yes) {eff(1.0, just)} (uuid "{U()}"))\n')
for p in parts:
    is_pwr = p['lib_id'].startswith('power:')
    ref = p['ref']
    if is_pwr:
        refcount['#FLGn'] = refcount.get('#FLGn', 0) + 1
        ref = f"#FLG0{refcount['#FLGn']:02d}"
    x, y = p['x'], p['y']
    s = (f'\t(symbol (lib_id {q(p["lib_id"])}) (at {x} {y} 0) (unit 1) (exclude_from_sim no) '
         f'(in_bom {"no" if (is_pwr or p["lib_id"] in ('Connector:TestPoint', 'Jumper:SolderJumper_2_Open', 'Mechanical:MountingHole')) else "yes"}) (on_board {"no" if is_pwr else "yes"}) (dnp {"yes" if p["dnp"] else "no"}) (uuid "{uuid.uuid5(uuid.NAMESPACE_URL, 'sym/' + ref)}")\n')
    small = p['lib_id'] in ('Device:R_Small', 'Device:C_Small', 'Device:L_Small', 'Device:Fuse_Small',
                            'Device:LED_Small', 'Device:Thermistor_NTC', 'power:PWR_FLAG', 'Connector:TestPoint')
    if small:
        s += f'\t\t(property "Reference" {q(ref)} (at {x + 1.6} {y - 0.6} 0) {eff(1.0, "left", hide=is_pwr)})\n'
        s += f'\t\t(property "Value" {q(p["value"])} (at {x + 1.6} {y + 0.9} 0) {eff(0.8, "left")})\n'
    else:
        s += f'\t\t(property "Reference" {q(ref)} (at {x} {y - 36} 0) {eff(1.5, None)})\n'
        s += f'\t\t(property "Value" {q(p["value"])} (at {x} {y - 34} 0) {eff(1.27, None)})\n'
    s += f'\t\t(property "Footprint" {q(p["fp"])} (at {x} {y} 0) {eff(1.27, None, hide=True)})\n'
    s += f'\t\t(property "Datasheet" "" (at {x} {y} 0) {eff(1.27, None, hide=True)})\n'
    for k, v in p['props'].items():
        s += f'\t\t(property {q(k)} {q(v)} (at {x} {y} 0) {eff(1.27, None, hide=True)})\n'
    for n in p['pins']:
        s += f'\t\t(pin {q(n)} (uuid "{uuid.uuid5(uuid.NAMESPACE_URL, 'pin/' + ref + '/' + n)}"))\n'
    s += (f'\t\t(instances (project {q(PROJECT)} (path "/{ROOT_UUID}" (reference {q(ref)}) (unit 1))))\n\t)\n')
    out.append(s)
out.append('\t(sheet_instances (path "/" (page "1")))\n)\n')
open(OUT, 'w', encoding='utf8').write(''.join(out))
print('parts', len(parts), 'labels', len(labels), 'nc', len(ncs))
