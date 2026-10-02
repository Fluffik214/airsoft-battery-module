// TI BQ7692003 3-5S monitor (I2C 0x08 with CRC). 3S wiring: cell1 = VC1-VC0, cell2 = VC2-VC1, cell3 = VC5-VC4.
#pragma once
#include <stdint.h>
#include <stdbool.h>

typedef struct {
    bool     present;
    uint16_t cell_mv[3];
    uint16_t pack_mv;
    int16_t  temp_dc;        // pack-side NTC (TH2), 0.1 degC
    uint8_t  sys_stat;
    uint8_t  bal_mask;       // bit0 cell1, bit1 cell2, bit2 cell3
} bq76920_t;

bool bq76920_wake(void);                     // boot pulse + init (ADC on, thermistor mode, read calibration)
bool bq76920_read(bq76920_t *s);
bool bq76920_balance(uint8_t mask);          // mask bit0..2 = cells 1..3 (adjacent cells are never enabled together)
bool bq76920_ship(void);
