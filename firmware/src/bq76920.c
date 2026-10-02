#include "bq76920.h"
#include "hw.h"
#include "board.h"

#define A I2C_ADDR_BQ76920
enum { SYS_STAT = 0x00, CELLBAL1 = 0x01, SYS_CTRL1 = 0x04, SYS_CTRL2 = 0x05, CC_CFG = 0x0B,
       VC1_HI = 0x0C, VC2_HI = 0x0E, VC5_HI = 0x14, BAT_HI = 0x2A, TS1_HI = 0x2C,
       ADCGAIN1 = 0x50, ADCOFFSET = 0x51, ADCGAIN2 = 0x59 };
#define ADC_EN   (1u << 4)
#define TEMP_SEL (1u << 3)

static uint16_t g_gain_uv = 380;   // uV/LSB, read from the part
static int8_t   g_offset_mv = 0;

// CRC-8, polynomial x^8 + x^2 + x + 1, init 0 (datasheet 8.3.3)
static uint8_t crc8(uint8_t crc, uint8_t d) {
    crc ^= d;
    for (int i = 0; i < 8; i++) crc = (crc & 0x80) ? (uint8_t)((crc << 1) ^ 0x07) : (uint8_t)(crc << 1);
    return crc;
}
static bool wr8(uint8_t reg, uint8_t v) {
    uint8_t c = crc8(crc8(crc8(0, A << 1), reg), v);
    uint8_t b[3] = {reg, v, c};
    return i2c_xfer(A, b, 3, 0, 0);
}
// block read: data bytes are each followed by a CRC; first CRC covers the read address + data
static bool rdn(uint8_t reg, uint8_t *out, uint8_t n) {
    uint8_t buf[8];
    if (n > 4 || !i2c_xfer(A, &reg, 1, buf, (uint8_t)(n * 2))) return false;
    for (uint8_t i = 0; i < n; i++) {
        uint8_t c = (i == 0) ? crc8(crc8(0, (uint8_t)((A << 1) | 1)), buf[0]) : crc8(0, buf[i * 2]);
        if (c != buf[i * 2 + 1]) return false;
        out[i] = buf[i * 2];
    }
    return true;
}
static bool rd8(uint8_t reg, uint8_t *v) { return rdn(reg, v, 1); }
static bool rd14(uint8_t reg, uint16_t *v) {
    uint8_t b[2];
    if (!rdn(reg, b, 2)) return false;
    *v = (uint16_t)(((b[0] & 0x3F) << 8) | b[1]);
    return true;
}

bool bq76920_wake(void) {
    bms_boot_pulse();
    delay_ms(300);                                   // datasheet: 250 ms before the first cell data
    uint8_t g1, g2, off;
    if (!wr8(CC_CFG, 0x19)) {                        // retry once (boot can take a little longer)
        delay_ms(100);
        if (!wr8(CC_CFG, 0x19)) return false;
    }
    wr8(SYS_CTRL1, ADC_EN | TEMP_SEL);
    wr8(SYS_CTRL2, 0x00);                            // coulomb counter off, CHG/DSG drivers unused
    wr8(SYS_STAT, 0xFF);                             // clear latched flags
    if (rd8(ADCGAIN1, &g1) && rd8(ADCGAIN2, &g2) && rd8(ADCOFFSET, &off)) {
        g_gain_uv = (uint16_t)(365 + (((g1 & 0x0C) << 1) | (g2 >> 5)));
        g_offset_mv = (int8_t)off;
    }
    return true;
}

static uint16_t to_mv(uint16_t raw) {
    int32_t mv = ((int32_t)raw * g_gain_uv) / 1000 + g_offset_mv;
    return (uint16_t)(mv < 0 ? 0 : mv);
}

// 10k NTC (B3435, 103AT) on TS1, internal 10k pull-up to 3.3 V: datasheet eq. (4)/(5)
static int16_t ntc_dc(uint16_t raw) {
    int32_t v_uv = (int32_t)raw * 382;                       // uV
    if (v_uv <= 0 || v_uv >= 3300000) return -999;
    float r = 10000.0f * (float)v_uv / (3300000.0f - (float)v_uv);
    // 1/T = 1/T0 + ln(R/R0)/B, with a cheap ln() approximation good to ~0.1 degC in 0..60 degC
    float x = r / 10000.0f, y = (x - 1.0f) / (x + 1.0f), y2 = y * y;
    float ln = 2.0f * y * (1.0f + y2 / 3.0f + y2 * y2 / 5.0f + y2 * y2 * y2 / 7.0f);
    float t = 1.0f / (1.0f / 298.15f + ln / 3435.0f) - 273.15f;
    return (int16_t)(t * 10.0f);
}

bool bq76920_read(bq76920_t *s) {
    uint16_t r1, r2, r5, ts;
    uint8_t st, bal;
    s->present = rd8(SYS_STAT, &st);
    if (!s->present) return false;
    s->sys_stat = st;
    if (st & 0x20) wr8(SYS_STAT, 0x20);              // DEVICE_XREADY: clear, data still usable next cycle
    if (!(rd14(VC1_HI, &r1) && rd14(VC2_HI, &r2) && rd14(VC5_HI, &r5))) { s->present = false; return false; }
    s->cell_mv[0] = to_mv(r1);
    s->cell_mv[1] = to_mv(r2);
    s->cell_mv[2] = to_mv(r5);
    s->pack_mv = (uint16_t)(s->cell_mv[0] + s->cell_mv[1] + s->cell_mv[2]);
    if (rd14(TS1_HI, &ts)) s->temp_dc = ntc_dc(ts);
    if (rd8(CELLBAL1, &bal)) s->bal_mask = (uint8_t)((bal & 1) | ((bal >> 1) & 1) << 1 | ((bal >> 4) & 1) << 2);
    return true;
}

bool bq76920_balance(uint8_t mask) {
    if ((mask & 3) == 3) mask &= (uint8_t)~2;        // cells 1+2 adjacent
    if ((mask & 6) == 6) mask &= (uint8_t)~2;        // cells 2+3 share VC2/VC4 node
    uint8_t reg = (uint8_t)((mask & 1) | ((mask >> 1) & 1) << 1 | ((mask >> 2) & 1) << 4);  // CB1, CB2, CB5
    return wr8(CELLBAL1, reg);
}

bool bq76920_ship(void) {
    wr8(CELLBAL1, 0);
    bool ok = wr8(SYS_CTRL1, 0x01);                  // SHUT_A=0, SHUT_B=1
    ok &= wr8(SYS_CTRL1, 0x02);                      // SHUT_A=1, SHUT_B=0 -> SHIP
    return ok;
}
