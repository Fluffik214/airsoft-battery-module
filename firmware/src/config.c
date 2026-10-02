#include <string.h>
#include "config.h"
#include "hw.h"

#define CFG_MAGIC 0xA160
extern const uint8_t _config_page[];      // linker: last flash page
config_t cfg;

static uint16_t crc16(const uint8_t *p, uint32_t n) {
    uint16_t c = 0xFFFF;
    while (n--) { c ^= *p++; for (int i = 0; i < 8; i++) c = (c & 1) ? (uint16_t)((c >> 1) ^ 0xA001) : (uint16_t)(c >> 1); }
    return c;
}

void config_defaults(void) {
    memset(&cfg, 0, sizeof cfg);
    cfg.magic = CFG_MAGIC;
    cfg.vcell_mv = 4200; cfg.ichg_ma = 1400; cfg.iin_ma = 1500; cfg.storage_mv = 3800;
    cfg.bal_min_mv = 3900; cfg.minvin_mv = 12000; cfg.imb_max_mv = 300; cfg.capacity_mah = 1450;
    cfg.rate_ms = 500; cfg.bal_th_mv = 15; cfg.tmax_c = 45; cfg.led_pct = 40; cfg.bal_en = 1;
    cfg.mode = MODE_CHARGE; cfg.bal_unplug_min = 0;
    cfg.tgt_pct = 100; cfg.wait_s = 5; cfg.ichg_slow_ma = 700;
}

void config_load(void) {
    const config_t *f = (const config_t *)(const void *)_config_page;
    if (f->magic == CFG_MAGIC && f->crc == crc16((const uint8_t *)f, offsetof(config_t, crc))) cfg = *f;
    else config_defaults();
}

bool config_save(void) {
    cfg.magic = CFG_MAGIC;
    cfg.crc = crc16((const uint8_t *)&cfg, offsetof(config_t, crc));
    return flash_write_page((uint32_t)_config_page, &cfg, sizeof cfg);
}

typedef struct { const char *key; void *ptr; uint8_t size; int32_t lo, hi; } field_t;
static const field_t fields[] = {
    {"vcell", &cfg.vcell_mv, 2, 4000, 4200},   {"ichg", &cfg.ichg_ma, 2, 100, 3000},
    {"iin", &cfg.iin_ma, 2, 500, 3000},        {"stor", &cfg.storage_mv, 2, 3700, 3900},
    {"balmin", &cfg.bal_min_mv, 2, 3500, 4150}, {"minvin", &cfg.minvin_mv, 2, 9000, 15000},
    {"imb", &cfg.imb_max_mv, 2, 50, 500},      {"cap", &cfg.capacity_mah, 2, 200, 10000},
    {"rate", &cfg.rate_ms, 2, 200, 5000},      {"balth", &cfg.bal_th_mv, 1, 5, 60},
    {"tmax", &cfg.tmax_c, 1, 35, 60},          {"led", &cfg.led_pct, 1, 0, 100},
    {"bal", &cfg.bal_en, 1, 0, 1},             {"mode", &cfg.mode, 1, 0, 2},
    {"bun", &cfg.bal_unplug_min, 1, 0, 60},    {"tgt", &cfg.tgt_pct, 1, 50, 100},
    {"wait", &cfg.wait_s, 2, 1, 600},       {"ichs", &cfg.ichg_slow_ma, 2, 100, 3000},
};

bool config_set(const char *key, int32_t val) {
    for (unsigned i = 0; i < sizeof fields / sizeof fields[0]; i++) {
        if (strcmp(key, fields[i].key) == 0) {
            if (val < fields[i].lo || val > fields[i].hi) return false;
            if (fields[i].size == 2) *(uint16_t *)fields[i].ptr = (uint16_t)val;
            else *(uint8_t *)fields[i].ptr = (uint8_t)val;
            return true;
        }
    }
    return false;
}
