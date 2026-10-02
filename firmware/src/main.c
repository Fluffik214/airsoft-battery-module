// Airsoft PD charger firmware - STM32F042F6P6
#include <string.h>
#include "stm32f0xx.h"
#include "tusb.h"
#include "hw.h"
#include "config.h"
#include "app.h"

void USB_IRQHandler(void) { tud_int_handler(0); }
uint32_t tusb_time_millis_api(void) { return millis(); }

void usb_send(const char *s) {
    if (!tud_cdc_connected()) return;
    uint32_t n = (uint32_t)strlen(s), t = millis();
    while (n && millis() - t < 50) {
        uint32_t k = tud_cdc_write(s, n);
        s += k; n -= k;
        tud_cdc_write_flush();
        tud_task();
    }
}

// keep USB alive during the few blocking waits (cell monitor boot, reboot replies)
static bool in_delay;
void delay_hook(void) {
    if (in_delay) return;
    in_delay = true;
    tud_task();
    wdg_feed();
    in_delay = false;
}

static char rx[96];
static uint8_t rx_len;

static void usb_poll_rx(void) {
    while (tud_cdc_available()) {
        char c = (char)tud_cdc_read_char();
        if (c == '\r') continue;
        if (c == '\n') { rx[rx_len] = 0; app_command(rx); rx_len = 0; }
        else if (rx_len < sizeof rx - 1) rx[rx_len++] = c;
    }
}

int main(void) {
    hw_init();
    config_load();
    app_init();
    tusb_rhport_init_t dev = {.role = TUSB_ROLE_DEVICE, .speed = TUSB_SPEED_AUTO};
    tusb_init(0, &dev);
    wdg_init();

    uint32_t t_tick = 0;
    char status[360];
    while (1) {
        wdg_feed();
        app_fast();
        tud_task();
        usb_poll_rx();
        if (tud_mounted()) app_host_seen();     // a PD wall charger never enumerates us; a phone/PC does
        if (millis() - t_tick >= 250) { t_tick = millis(); app_tick(); }
        app_led();
        if (app_report_due() && tud_cdc_connected()) {
            app_status_json(status, sizeof status);
            usb_send(status);
        }
    }
}
