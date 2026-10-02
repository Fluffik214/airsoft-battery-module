// Event log in its own 1 KiB flash page: survives power-off, read by the app with "log?"
#pragma once
#include <stdint.h>
#include <stdbool.h>

// event codes 0..15 = fault bit numbers (see app.h), the rest are events
enum {
    EV_CHARGE_START = 32,   // val = pack mV
    EV_CHARGE_DONE  = 33,   // val = pack mV (target reached / charger terminated)
    EV_RESET_WDG    = 34,   // MCU was reset by the watchdog (firmware hang)
    EV_BMS_WAKE_FAIL= 35,   // cell monitor did not boot
    EV_CHARGE_STOP  = 36,   // charging stopped by a fault, val = fault bits
    EV_LOG_CLEARED  = 37,
    EV_CHARGE_UNPLUG= 38,   // unplugged before the target was reached, val = pack mV
    EV_CHARGE_MAH   = 39,   // charge report: mAh put into the pack
    EV_CHARGE_MIN   = 40,   // charge report: minutes from start to unplug
    EV_CHARGE_PEAKT = 41,   // charge report: peak pack temperature, 0.1 degC
    EV_CHARGE_FROM  = 42,   // charge report: pack mV when charging started
};

typedef struct {
    uint16_t boot;          // power-up number (1, 2, 3 ...)
    uint16_t t_s;           // seconds since that power-up
    uint8_t  code;
    uint8_t  pad;
    uint16_t val;
} log_entry_t;              // 8 bytes, 128 per page

void     log_init(void);
void     log_event(uint8_t code, uint16_t val);       // always written
void     log_once(uint8_t code, uint16_t val);        // at most once per power-up (saves flash wear)
uint16_t log_count(void);
const log_entry_t *log_get(uint16_t i);               // oldest first
void     log_clear(void);
uint16_t log_boot(void);
