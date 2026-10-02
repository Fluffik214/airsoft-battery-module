#include <string.h>
#include "log.h"
#include "hw.h"

extern const uint8_t _log_page[];         // linker: 1 KiB page before the settings page
#define LOG_MAX  (1024 / sizeof(log_entry_t))
#define KEEP     (LOG_MAX / 2)            // entries kept when the page is full

static const log_entry_t *const page = (const log_entry_t *)(const void *)_log_page;
static uint16_t n;                        // entries in use
static uint16_t boot;
static uint32_t once_mask[2];             // codes 0..63 already logged this power-up

static bool empty(const log_entry_t *e) { return e->boot == 0xFFFF; }

void log_init(void) {
    n = 0;
    uint16_t last = 0;
    while (n < LOG_MAX && !empty(&page[n])) { if (page[n].boot > last) last = page[n].boot; n++; }
    boot = (uint16_t)(last + 1);
    if (boot == 0xFFFF) boot = 1;
}

void log_event(uint8_t code, uint16_t val) {
    if (n >= LOG_MAX) {
        // full: keep the newest half, rewrite the page
        static log_entry_t keep[KEEP];
        memcpy(keep, &page[LOG_MAX - KEEP], sizeof keep);
        flash_write_page((uint32_t)_log_page, keep, sizeof keep);
        n = KEEP;
    }
    uint32_t s = millis() / 1000;
    log_entry_t e = {boot, (uint16_t)(s > 0xFFFE ? 0xFFFE : s), code, 0, val};
    if (flash_program((uint32_t)&page[n], &e, sizeof e)) n++;
}

void log_once(uint8_t code, uint16_t val) {
    uint32_t bit = 1u << (code & 31);
    uint32_t *m = &once_mask[(code >> 5) & 1];
    if (*m & bit) return;
    *m |= bit;
    log_event(code, val);
}

uint16_t log_count(void) { return n; }
const log_entry_t *log_get(uint16_t i) { return i < n ? &page[i] : 0; }
uint16_t log_boot(void) { return boot; }

void log_clear(void) {
    flash_erase_page((uint32_t)_log_page);
    n = 0;
    log_event(EV_LOG_CLEARED, 0);
}
