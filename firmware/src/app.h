#pragma once
#include <stdint.h>
#include <stdbool.h>

// fault bits (reported as "flt")
enum {
    F_NOBATT  = 1u << 0,   // pack not detected
    F_CELL_OV = 1u << 1,   // a cell is above the safe limit
    F_CELL_UV = 1u << 2,   // a cell is deeply discharged (< 2.5 V): charging refused
    F_IMBAL   = 1u << 3,   // cell spread above the configured limit
    F_HOT     = 1u << 4,   // pack or board too hot
    F_COLD    = 1u << 5,   // too cold to charge (< 0 degC)
    F_CHG     = 1u << 6,   // charger IC reported a fault
    F_BMS     = 1u << 7,   // cell monitor not responding
    F_LOWSRC  = 1u << 8,   // USB source below the minimum charging voltage (phone / 5 V charger)
    F_CHGCOMM = 1u << 9,   // charger IC not responding
    F_OVERVOLT= 1u << 10,  // USB voltage above VBUS_MAX_MV (17 V): charging cut immediately
};

void app_init(void);
void app_tick(void);                    // call every 250 ms
void app_led(void);                     // call often (LED animation)
void app_command(char *line);           // one text command from USB
void app_status_json(char *buf, int n); // status line for USB
bool app_report_due(void);
void app_fast(void);                     // call every loop: instant over-voltage cut
void app_host_seen(void);                // USB enumerated by a phone/PC: lock out charging
