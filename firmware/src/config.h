// User settings, stored in the last flash page (CRC protected)
#pragma once
#include <stdint.h>
#include <stdbool.h>

// MODE_CHARGE: charge to tgt_pct, MODE_STORAGE: charge/bleed to storage_mv, MODE_MONITOR: never charge
enum { MODE_CHARGE = 0, MODE_STORAGE = 1, MODE_MONITOR = 2 };

typedef struct {
    uint16_t magic;
    uint16_t vcell_mv;      // full-charge target per cell        (4000..4200)
    uint16_t ichg_ma;       // FAST charge current, JP2 bridged   (100..3000)
    uint16_t iin_ma;        // input current limit                (500..3000)
    uint16_t storage_mv;    // storage voltage per cell           (3700..3900)
    uint16_t bal_min_mv;    // only balance above this cell voltage (3500..4150)
    uint16_t minvin_mv;     // lowest USB voltage allowed to charge (9000..15000; max is VBUS_MAX_MV)
    uint16_t imb_max_mv;    // refuse to charge above this cell spread (50..500)
    uint16_t capacity_mah;  // pack capacity, for time-to-full estimate (200..10000)
    uint16_t rate_ms;       // status report interval              (200..5000)
    uint8_t  bal_th_mv;     // balance when spread exceeds this    (5..60)
    uint8_t  tmax_c;        // max temperature while charging      (35..60)
    uint8_t  led_pct;       // LED brightness                      (0..100)
    uint8_t  bal_en;        // balancing on/off
    uint8_t  mode;          // MODE_*
    uint8_t  bal_unplug_min;// keep balancing on battery after unplug (0..60 min)
    uint8_t  tgt_pct;       // charge target in MODE_CHARGE, % (50..100; 100 = vcell_mv)
    uint8_t  pad;
    uint16_t ichg_slow_ma;  // SLOW charge current, JP2 open (default) (100..3000)
    uint16_t wait_s;        // USB must stay inside minvin..VBUS_MAX_MV this long before charging starts (1..600 s)
    uint16_t crc;
} config_t;

extern config_t cfg;

void config_load(void);
bool config_save(void);
void config_defaults(void);
// set by name (used by the USB protocol). Returns false if the key is unknown or out of range.
bool config_set(const char *key, int32_t val);
