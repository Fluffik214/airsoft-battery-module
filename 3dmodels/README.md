# 3D models

`JST_XH_S4B-XH-SM4-TB.step`: J2 balance plug (JST S4B-XH-SM4-TB, LCSC C161861). KiCad's library has no model for this side-entry SMD part.

Source: JLCEDA/EasyEDA Official Library (https://lceda.cn/, https://easyeda.com), model "CONN-SMD_XH2.50-WS-4P".

Placed on J2 with offset (0, -0.704, 0) mm by `layout_work/setmodel.py`.

`LED_Kingbright_KPHBM-2012.wrl`: cell LEDs D1-D3 on the LED board (Kingbright KPHBM-2012SURKCGKC). Neither KiCad nor EasyEDA has a model for it, so this is a simple hand-made one (white body, 4 terminals, red chip on the pad 1/2 side, green chip on the pad 3/4 side, dark mark at the pad 2/3 end, clear lens).

`USB_C_HRO_TYPE-C-31-M-12.step`: J1 USB-C (HRO TYPE-C-31-M-12, LCSC C165948), and `TI_BQ25798_VQFN-29_4x4mm.step`: U3 charger (TI BQ25798RQMR, LCSC C2876593). KiCad 10 ships neither file. Source: JLCEDA/EasyEDA Official Library (https://lceda.cn/, https://easyeda.com). Placed by `layout_work/set3d.py`: J1 offset (0, 0.471, 0) mm rotated 180 deg, U3 offset (0.014, -0.038, 0) mm; offsets found by matching the EasyEDA and KiCad footprint pads.
