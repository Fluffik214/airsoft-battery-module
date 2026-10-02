#include "bq25798.h"
#include "hw.h"
#include "board.h"

#define A I2C_ADDR_BQ25798
enum {
    REG_VSYSMIN = 0x00, REG_VREG = 0x01, REG_ICHG = 0x03, REG_VINDPM = 0x05, REG_IINDPM = 0x06,
    REG_CTRL0 = 0x0F, REG_CTRL1 = 0x10, REG_CTRL2 = 0x11, REG_STAT1 = 0x1C, REG_STAT4 = 0x1F,
    REG_FAULT0 = 0x20, REG_FAULT1 = 0x21, REG_ADC_CTRL = 0x2E, REG_IBUS = 0x31, REG_IBAT = 0x33,
    REG_VBUS = 0x35, REG_VBAT = 0x3B, REG_VSYS = 0x3D, REG_TDIE = 0x41, REG_PART = 0x48,
};
// REG0F bits
#define EN_CHG   (1u << 5)
#define EN_HIZ   (1u << 2)
// REG10 bits
#define WD_RST   (1u << 3)
#define VAC_OVP_MASK (3u << 4)
#define VAC_OVP_18V  (1u << 4)          // 0 = 26 V (default), 1 = 18 V, 2 = 12 V, 3 = 7 V
// REG11 bits
#define AUTO_INDET_EN (1u << 6)
#define HVDCP_EN      (1u << 3)
#define SDRV_SHUTDOWN (1u << 1)

static bool wr8(uint8_t reg, uint8_t v) { uint8_t b[2] = {reg, v}; return i2c_xfer(A, b, 2, 0, 0); }
static bool rd8(uint8_t reg, uint8_t *v) { return i2c_xfer(A, &reg, 1, v, 1); }
static bool wr16(uint8_t reg, uint16_t v) { uint8_t b[3] = {reg, (uint8_t)(v >> 8), (uint8_t)v}; return i2c_xfer(A, b, 3, 0, 0); }
static bool rd16(uint8_t reg, uint16_t *v) {
    uint8_t b[2];
    if (!i2c_xfer(A, &reg, 1, b, 2)) return false;
    *v = (uint16_t)((b[0] << 8) | b[1]);
    return true;
}
static bool modify(uint8_t reg, uint8_t clr, uint8_t set) {
    uint8_t v;
    if (!rd8(reg, &v)) return false;
    return wr8(reg, (uint8_t)((v & ~clr) | set));
}

bool bq25798_init(void) {
    uint8_t part;
    if (!rd8(REG_PART, &part)) return false;
    if (((part >> 3) & 7) != 3) return false;                 // PN = 3 -> BQ25798
    // D+/D- are not connected on this board: no BC1.2 / HVDCP detection, ship FET not fitted
    modify(REG_CTRL2, AUTO_INDET_EN | HVDCP_EN | (3u << 1), 0);
    wr8(REG_ADC_CTRL, 0x80 | (2u << 4));                       // ADC on, continuous, 13-bit
    modify(REG_CTRL1, VAC_OVP_MASK, VAC_OVP_18V);              // input OVP 18 V: backstop for the 17 V firmware cut
    bq25798_enable(false);
    return true;
}

bool bq25798_configure(uint16_t vreg_mv, uint16_t ichg_ma, uint16_t iindpm_ma) {
    if (vreg_mv < 10000) vreg_mv = 10000;                      // 3S window per datasheet (10.0..13.99 V)
    if (vreg_mv > 13000) vreg_mv = 13000;
    if (ichg_ma < 50) ichg_ma = 50;
    if (ichg_ma > 3000) ichg_ma = 3000;
    if (iindpm_ma < 100) iindpm_ma = 100;
    if (iindpm_ma > 3300) iindpm_ma = 3300;
    bool ok = wr16(REG_VREG, vreg_mv / 10);
    ok &= wr16(REG_ICHG, ichg_ma / 10);
    ok &= wr16(REG_IINDPM, iindpm_ma / 10);
    ok &= modify(REG_CTRL1, VAC_OVP_MASK, VAC_OVP_18V);        // re-applied: a watchdog/register reset restores 26 V
    return ok;
}

bool bq25798_enable(bool on) { return modify(REG_CTRL0, EN_CHG | EN_HIZ, on ? EN_CHG : 0); }
bool bq25798_hiz(bool on) { return modify(REG_CTRL0, EN_HIZ, on ? EN_HIZ : 0); }
bool bq25798_kick(void) { return modify(REG_CTRL1, 0, WD_RST); }
bool bq25798_shutdown(void) { return modify(REG_CTRL2, 3u << 1, SDRV_SHUTDOWN); }

bool bq25798_read(bq25798_t *s) {
    uint8_t st1, st4, f0, f1;
    uint16_t v;
    s->present = rd8(REG_STAT1, &st1);
    if (!s->present) return false;
    rd8(REG_STAT4, &st4); rd8(REG_FAULT0, &f0); rd8(REG_FAULT1, &f1);
    s->chg_stat = st1 >> 5; s->vbus_stat = (st1 >> 1) & 0xF;
    s->ts_stat = st4; s->fault0 = f0; s->fault1 = f1;
    if (rd16(REG_VBUS, &v)) s->vbus_mv = v;
    if (rd16(REG_VBAT, &v)) s->vbat_mv = v;
    if (rd16(REG_VSYS, &v)) s->vsys_mv = v;
    if (rd16(REG_IBUS, &v)) s->ibus_ma = (int16_t)v;           // 2's complement, 1 mA/LSB
    if (rd16(REG_IBAT, &v)) s->ibat_ma = (int16_t)v;
    if (rd16(REG_TDIE, &v)) s->tdie_dc = (int16_t)(((int16_t)v) * 5);   // 0.5 degC/LSB -> 0.1 degC
    return true;
}
