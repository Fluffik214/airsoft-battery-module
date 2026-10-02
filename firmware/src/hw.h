// Low-level hardware: clock, systick, GPIO, ADC, I2C, LED PWM, watchdog, flash, bootloader
#pragma once
#include <stdint.h>
#include <stdbool.h>

void     hw_init(void);
uint32_t millis(void);
void     delay_ms(uint32_t ms);

void     pin_write(uint8_t pin, bool on);          // GPIOA outputs
bool     pin_read(uint8_t pin);                    // GPIOA inputs
bool     fast_jumper(void);                        // JP2 bridged -> true (fast charge)
void     bms_boot_pulse(void);                     // TS1 boot pulse, then leaves PA4 as analog (hi-Z)

uint32_t vbus_mv(void);                            // VBUS from the ADC divider (uses VREFINT for VDDA)
uint32_t vdda_mv(void);

bool     i2c_xfer(uint8_t addr7, const uint8_t *w, uint8_t wl, uint8_t *r, uint8_t rl);

void     led_set(uint8_t percent);                 // 0..100 duty

void     wdg_init(void);
void     wdg_feed(void);

bool     flash_write_page(uint32_t addr, const void *data, uint32_t len);  // erases one 1 KiB page first
void     flash_erase_page(uint32_t addr);
bool     flash_program(uint32_t addr, const void *data, uint32_t len);     // into erased flash only

void     enter_bootloader(void);                   // reboot into the ST ROM USB DFU bootloader
void     system_reset(void);
enum { RESET_POWER, RESET_PIN, RESET_SOFTWARE, RESET_WATCHDOG };
uint8_t  reset_cause(void);                        // call once at boot
uint32_t chip_uid_hash(void);
