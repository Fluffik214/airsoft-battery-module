# =====================================================================================
# REGION B : MCU (U6 rot 90), decoupling/straps, MCU power path (U5, D3, Q2, Q3)
# (exec'd from route.py: b, pad, VIA_S, W_SIG in scope)
# =====================================================================================
P = lambda r, n: pad(r, n)

# ---- top edge of U6: VDD16 + C31, GND15
xv, yv = P('U6', 16); xg, yg = P('U6', 15)
xc1, yc1 = P('C31', 1); xc2, yc2 = P('C31', 2)
b.track('+3V3', [(xc1, yc1), (xv, yv - 0.6)], 0.25)
b.track('GND', [(xg, yg - 0.6), (xg, 101.4)], 0.25)
b.via('GND', xg, 101.4, **VIA_S)
b.track('GND', [(xg, 101.4), (xc2 + 0.25, yc2), (xc2, yc2)], 0.25)

# ---- +3V3 / VDDA hop under the IC body (bottom layer)
b.track('+3V3', [(xv, yv + 0.6), (xv, 105.2)], 0.25)
b.via('+3V3', xv, 105.2, **VIA_S)
x5, y5 = P('U6', 5)
b.track('+3V3', [(x5, y5 - 0.6), (x5, 108.0)], 0.25)
b.via('+3V3', x5, 108.0, **VIA_S)
b.track('+3V3', [(xv, 105.2), (x5, 108.0)], 0.3, B)

# ---- PD_PG: CH224K PG -> R3 pull-up only (no longer wired to the MCU)
x10, y10 = P('U1', 10); x7, y7 = P('U6', 7)
xr, yr = P('R3', 2); b.track('PD_PG', [(x10, y10), (xr - 0.5, y10), (xr, y10 - 0.5), (xr, yr)], W_SIG)
# ---- CLED_A (pin 7, PA1): under the MCU body, west, down to a via, bottom layer to the LED pads (regionG)
CLA_V = (123.2, 108.8)
b.track('CLED_A', [(x7, y7 - 0.6), (x7, 107.0), (x7 - 0.5, 106.5), (CLA_V[0] + 0.5, 106.5), (CLA_V[0], 107.0), CLA_V], W_SIG)
b.via('CLED_A', *CLA_V, **VIA_S)
xr, yr = P('R3', 1); b.track('+3V3', [(xr, yr), (123.4, yr)], 0.25); b.via('+3V3', 123.4, yr, **VIA_S)
b.track('+3V3', [(123.4, yr), (123.4, 108.0)], 0.3, B)        # joins the +3V3 bottom run at y 108

# ---- bottom-row fan-out
x, y = P('U6', 1)          # BOOT0 -> R21 (pull-down) + JP1 (DFU bridge)
xj, yj = P('JP1', 2); xr21, yr21 = P('R21', 1)
b.track('MCU_BOOT0', [(x, y + 0.6), (x, 112.8), (x - 0.5, 113.3), (xj, 113.3), (xj, yj)], W_SIG)
b.track('MCU_BOOT0', [(xr21, 113.3), (xr21, yr21)], W_SIG)
xg2, yg2 = P('R21', 2); b.track('GND', [(xg2, yg2), (xg2, yg2 + 0.9)], 0.3); b.via('GND', xg2, yg2 + 0.9, **VIA_S)
xa, ya = P('JP1', 1); b.track('+3V3', [(xa, ya), (xa, ya + 1.0)], 0.3); b.via('+3V3', xa, ya + 1.0, **VIA_S)
b.track('+3V3', [(x5, 108.0), (122.9, 108.0), (122.4, 108.5), (122.4, ya + 0.5), (xa, ya + 1.0)], 0.3, B)

x, y = P('U6', 4)          # NRST -> C34 directly below
xc, yc = P('C34', 1); b.track('MCU_NRST', [(x, y + 0.6), (xc, yc)], W_SIG)
xc, yc = P('C34', 2); b.track('GND', [(xc, yc), (xc, 112.75)], 0.3); b.via('GND', xc, 112.75, **VIA_S)
x, y = P('U6', 5)          # VDDA -> C32
xc, yc = P('C32', 1); b.track('+3V3', [(x, y + 0.6), (xc, yc)], 0.25)
xc, yc = P('C32', 2); b.track('GND', [(xc, yc), (127.35, 115.45)], 0.3); b.via('GND', 127.35, 115.45, **VIA_S)

# bus: top verticals down to a staggered via row, then bottom-layer lanes east along the bottom edge
LANE = {'+3V3': 116.8, 'BMS_BOOT': 117.35, 'BMS_ALERT': 117.9, 'CHG_EN': 118.45, 'CHG_INT_N': 119.0,
        'I2C_SCL': 119.55, 'I2C_SDA': 120.1}
FAN = [('I2C_SDA', 2, 115.6), ('I2C_SCL', 3, 114.9),
       ('CHG_EN', 8, 114.9), ('BMS_BOOT', 10, 114.9)]
LEND = {'I2C_SDA': 182.0, 'I2C_SCL': 182.6, 'CHG_INT_N': 171.2, 'CHG_EN': 159.05, 'BMS_ALERT': 184.5,
        'BMS_BOOT': 183.3, '+3V3': 184.28}
for net, pin, vy in FAN:
    x, y = P('U6', pin)
    b.track(net, [(x, y + 0.6), (x, vy)], W_SIG)
    b.via(net, x, vy, **VIA_S)
    ly = LANE[net]
    b.track(net, [(x, vy), (x, ly - 0.4), (x + 0.4, ly), (LEND[net], ly)], W_SIG, B)

# ---- top lanes from the top-right pins: PWR_HOLD 101.1, LED 101.85, VBUS_SENSE 102.55
x13, y13 = P('U6', 13); x12, y12 = P('U6', 12); x11, y11 = P('U6', 11)
xr22, yr22 = P('R22', 2)
b.track('VBUS_SENSE', [(x11, y11 - 0.6), (x11, 102.55), (xr22 - 0.45, 102.55), (xr22, 103.0), (xr22, yr22)], W_SIG)
xa, ya = P('R23', 1); xb, yb = P('C35', 1)
b.track('VBUS_SENSE', [(xr22, 103.25), (xr22 + 0.15, ya), (xa, ya)], W_SIG)
b.track('VBUS_SENSE', [(xa, ya), (xb, yb)], W_SIG)
xa, ya = P('R23', 2); xb, yb = P('C35', 2)
b.track('GND', [(xa, ya), (xb, yb)], 0.3); b.track('GND', [(xb, yb), (xb + 0.85, yb)], 0.3); b.via('GND', xb + 0.85, yb, **VIA_S)
xr, yr = P('R22', 1)
b.track('VBUS', [(xr, 118.9), (xr, yr)], 0.3)       # VBUS up from the lane to the divider
# LED
xr, yr = P('R26', 1)
b.track('LED_STATUS', [(x12, y12 - 0.6), (x12, 101.85), (xr, yr)], W_SIG)
xa, ya = P('R26', 2); xb, yb = P('D4', 2); b.track('LED_G_A', [(xa, ya), (xb, yb)], W_SIG)
xb, yb = P('D4', 1); b.track('GND', [(xb, yb), (xb, yb + 0.95)], 0.3); b.via('GND', xb, yb + 0.95, **VIA_S)
# PWR_HOLD -> Q3 gate, R20 pull-down
xg, yg = P('Q3', 1); xr, yr = P('R20', 1)
b.track('PWR_HOLD', [(x13, y13 - 0.6), (x13, 100.95), (xg - 0.6, 100.95), (xg, 101.55), (xg, yg)], W_SIG)
b.track('PWR_HOLD', [(xr, yr), (xr, yg), (xg, yg)], W_SIG)
xr, yr = P('R20', 2); b.track('GND', [(xr, yr), (xr, yr + 0.8)], 0.3); b.via('GND', xr, yr + 0.8, **VIA_S)
xs, ys = P('Q3', 2); b.track('GND', [(xs, ys), (xs, ys + 0.9)], 0.3); b.via('GND', xs, ys + 0.9, **VIA_S)
# WAKE_G: Q3 drain -> short bottom hop -> Q2 gate ; R19 (1M to BAT_P)
xd, yd = P('Q3', 3); xq, yq = P('Q2', 1); xr, yr = P('R19', 2)
w1 = (xd + 1.0, yd)                      # just outside the drain pad (no via-in-pad)
b.track('WAKE_G', [(xd, yd), w1], W_SIG); b.via('WAKE_G', *w1, **VIA_S)
b.track('WAKE_G', [w1, (w1[0], yr), (xr, yr)], W_SIG)
w2 = (xq - 0.65, 101.95)
b.track('WAKE_G', [w1, (w1[0] + 0.6, w1[1] - 0.6), (w2[0], 102.3 - 0.0), w2], W_SIG, B)
b.via('WAKE_G', *w2, **VIA_S)
b.track('WAKE_G', [w2, (xq, yq)], W_SIG)
# BAT_P thin feed along the top edge from the charger -> Q2 source + R19
xs, ys = P('Q2', 2); xr, yr = P('R19', 1)
bx = (w1[0] + w2[0]) / 2
b.track('BAT_P', [(177.3, 101.1), (bx + 0.4, 101.1), (bx, 101.5), (bx, ys), (xs, ys)], 0.3)
b.track('BAT_P', [(bx, ys), (bx, yr), (xr, yr)], 0.3)
# WAKE_RAIL: Q2 drain -> D3 pin 2
xd, yd = P('Q2', 3); x2, y2 = P('D3', 2)
b.track('WAKE_RAIL', [(xd, yd), (x2, yd), (x2, y2)], 0.3)
# VIN_LDO: D3 pin 3 -> U5 VI, C29
x3, y3 = P('D3', 3); xi, yi = P('U5', 1); xc, yc = P('C29', 1)
b.track('VIN_LDO', [(x3, y3), (xi + 0.5, y3), (xi, yi)], 0.4)
b.track('VIN_LDO', [(x3, y3), (xc, y3), (xc, yc)], 0.4)
xc, yc = P('C29', 2); b.track('GND', [(xc, yc), (xc, yc + 0.9)], 0.4); b.via('GND', xc, yc + 0.9)
# VBUS into D3 pin 1 from the lane
x1, y1 = P('D3', 1)
b.track('VBUS', [(x1 + 0.7, 118.8), (x1 + 0.7, y1 + 0.5), (x1 + 0.2, y1), (x1, y1)], 0.5)
# LDO output -> C30/C33 -> via -> bottom: west to the MCU, east as the +3V3 bus lane
xo, yo = P('U5', 2); xa1, ya1 = P('C30', 1); xa2, ya2 = P('C33', 1)
vA = (143.6, 107.5)
b.track('+3V3', [(xo, yo), (xa1, yo), (xa1, ya1)], 0.4)
b.track('+3V3', [(vA[0], yo), vA], 0.4)
b.track('+3V3', [(xa1, ya1), (xa2, ya1), (xa2, ya2)], 0.4)
b.via('+3V3', *vA)
b.track('+3V3', [vA, (vA[0] - 0.5, 108.0), (x5, 108.0)], 0.4, B)
b.track('+3V3', [vA, (vA[0], LANE['+3V3'] - 0.4), (vA[0] + 0.4, LANE['+3V3']), (LEND['+3V3'], LANE['+3V3'])], 0.3, B)
for c in ('C30', 'C33'):
    xc, yc = P(c, 2); b.track('GND', [(xc, yc), (xc, yc + 0.9)], 0.3); b.via('GND', xc, yc + 0.9, **VIA_S)
xg, yg = P('U5', 3); b.track('GND', [(xg, yg), (xg, yg + 0.9)], 0.4); b.via('GND', xg, yg + 0.9)
