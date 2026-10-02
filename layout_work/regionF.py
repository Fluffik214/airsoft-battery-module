# =====================================================================================
# REGION F : test pads. Back-side programming row TP5 SWDIO, TP6 SWCLK, TP7 3V3, TP8 GND
#            (under the MCU top edge); top probe pads TP9 VBUS, TP10 BAT, TP11 SYS on their copper.
# =====================================================================================
P = lambda r, n: pad(r, n)
def via_s(net, x, y): b.via(net, x, y, **VIA_S)
x19, y19 = P('U6', 19); x20, y20 = P('U6', 20)
sdv = (x19, 105.15); sck = (x20, 105.85)
b.track('SWDIO', [(x19, y19 + 0.6), sdv], W_SIG); via_s('SWDIO', *sdv)
b.track('SWCLK', [(x20, y20 + 0.6), sck], W_SIG); via_s('SWCLK', *sck)
t5, t6, t7 = P('TP5', 1), P('TP6', 1), P('TP7', 1)
b.track('SWDIO', [sdv, (t5[0], sdv[1] - (t5[0] - sdv[0])), t5], W_SIG, B)
b.track('SWCLK', [sck, (sck[0] - 0.65, sck[1] - 0.65), (t6[0], sck[1] - 0.65 - (sck[0] - 0.65 - t6[0])), t6], W_SIG, B)
xv16, yv16 = P('U6', 16)
b.track('+3V3', [(xv16, 105.2), (t7[0], 105.2 - (t7[0] - xv16)), t7], 0.3, B)

# I2C probe pads next to the SDA/SCL drops at the cell monitor
t11, t12 = P('TP11', 1), P('TP12', 1)
b.track('I2C_SDA', [t11, (SDA_X, t11[1])], W_SIG)
b.track('I2C_SCL', [t12, (SCL_X, t12[1])], W_SIG)
