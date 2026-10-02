// Airsoft PD charger - board pin map (STM32F042F6P6, TSSOP-20), matches schematic Rev A
#pragma once

// GPIOA
#define PIN_CHG_INT_N   0   // PA0  in  : BQ25798 /INT (open drain, 10k pull-up)
#define PIN_PD_PG       1   // PA1  in  : CH224K PG (low = PD contract ok)
#define PIN_CHG_EN      2   // PA2  out : high -> Q1 pulls BQ25798 /CE low -> charging allowed
#define PIN_BMS_ALERT   3   // PA3  in  : BQ76920 ALERT (1M pull-down)
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
#define HW_VERSION      "RevA"
