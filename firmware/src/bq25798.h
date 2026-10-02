// TI BQ25798 buck-boost charger (I2C 0x6B). Register map: datasheet SLUSDV2C section 7.5.
#pragma once
#include <stdint.h>
#include <stdbool.h>

typedef struct {
    bool     present;
    uint16_t vbus_mv, vbat_mv, vsys_mv;
    int16_t  ibus_ma, ibat_ma;       // ibat > 0 = charging
    int16_t  tdie_dc;                // die temperature, 0.1 degC
    uint8_t  chg_stat;               // REG1C[7:5]: 0 none,1 trickle,2 pre,3 CC,4 CV,6 top-off,7 done
    uint8_t  vbus_stat;              // REG1C[4:1]
    uint8_t  fault0, fault1;         // REG20 / REG21
    uint8_t  ts_stat;                // REG1F temperature status bits
} bq25798_t;

bool bq25798_init(void);                                   // probe + base config (no D+/D- detection, ADC on)
bool bq25798_configure(uint16_t vreg_mv, uint16_t ichg_ma, uint16_t iindpm_ma);
bool bq25798_enable(bool on);                              // EN_CHG bit (the /CE pin is driven separately)
bool bq25798_kick(void);                                   // watchdog reset
bool bq25798_read(bq25798_t *s);
bool bq25798_shutdown(void);                               // SDRV_CTRL = shutdown (only accepted without VBUS)
bool bq25798_hiz(bool on);
