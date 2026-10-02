// Airsoft PD charger - board pin map (STM32F042F6P6, TSSOP-20), matches schematic Rev A
#pragma once

// GPIOA
#define PIN_LED_C       0   // PA0  led : cell LED line C (charlieplexed, 100R R29 -> pad TP15)
#define PIN_LED_A       1   // PA1  led : cell LED line A (100R R27 -> pad TP13)
#define PIN_CHG_EN      2   // PA2  out : high -> Q1 pulls BQ25798 /CE low -> charging allowed
#define PIN_LED_B       3   // PA3  led : cell LED line B (100R R28 -> pad TP14)
#define PIN_BMS_BOOT    4   // PA4  out : pulse high ~5 ms to boot BQ76920 from SHIP (via 1k into TS1)
#define PIN_VBUS_SENSE  5   // PA5  adc : VBUS / 11 (100k / 10k divider) -> ADC_IN5
#define PIN_LED         6   // PA6  pwm : green status LED (TIM3_CH1, AF1)
#define PIN_PWR_HOLD    7   // PA7  out : high keeps the MCU powered from the pack after USB is unplugged
// PA11/PA12 = USB DM/DP (pins 17/18, remapped), PA13/PA14 = SWD
// GPIOB
#define PIN_FAST_N      1   // PB1  in  : JP2 solder jumper to GND (internal pull-up). open = slow charge, bridged = fast
// GPIOF
#define PIN_I2C_SDA     0   // PF0 I2C1_SDA (AF1)
#define PIN_I2C_SCL     1   // PF1 I2C1_SCL (AF1)

#define VBUS_DIV        11u      // (100k + 10k) / 10k
#define VBUS_MAX_MV     17000u   // above this USB voltage charging is cut immediately (CH224K should give 15 V)
#define I2C_ADDR_BQ25798 0x6B
#define I2C_ADDR_BQ76920 0x08    // BQ7692003: 3.3 V REGOUT, address 0x08, CRC enabled

#define FW_VERSION      "1.0.0"
#define HW_VERSION      "RevB"   // RevB: pack on the balance plug only (no Deans pads), cell LED pads
