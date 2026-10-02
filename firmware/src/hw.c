// Low-level hardware for STM32F042F6 (register level, no HAL)
#include "stm32f0xx.h"
#include "hw.h"
#include "board.h"

uint32_t SystemCoreClock = 8000000u;    // CMSIS global (no system_stm32f0xx.c here); set to 48 MHz in clock_init

static volatile uint32_t g_ms;
void SysTick_Handler(void) { g_ms++; }
uint32_t millis(void) { return g_ms; }
// called while busy-waiting so USB keeps being serviced (main.c overrides it)
__attribute__((weak)) void delay_hook(void) {}
void delay_ms(uint32_t ms) { uint32_t t = g_ms; while (g_ms - t < ms) delay_hook(); }

// magic in .noinit RAM: survives a soft reset, checked by the startup code
#define BOOT_MAGIC 0xB007B007u
__attribute__((section(".noinit"))) volatile uint32_t g_boot_magic;

// ---------------------------------------------------------------- clock: HSI48 + CRS locked to USB SOF
static void clock_init(void) {
    RCC->CR2 |= RCC_CR2_HSI48ON;
    while (!(RCC->CR2 & RCC_CR2_HSI48RDY)) {}
    FLASH->ACR = FLASH_ACR_PRFTBE | FLASH_ACR_LATENCY;            // 1 wait state for 48 MHz
    RCC->CFGR = (RCC->CFGR & ~RCC_CFGR_SW) | RCC_CFGR_SW_HSI48;
    while ((RCC->CFGR & RCC_CFGR_SWS) != RCC_CFGR_SWS_HSI48) {}
    RCC->APB1ENR |= RCC_APB1ENR_CRSEN;                              // clock recovery: trim HSI48 to USB SOF
    CRS->CFGR = (CRS->CFGR & ~CRS_CFGR_SYNCSRC) | CRS_CFGR_SYNCSRC_1;
    CRS->CR |= CRS_CR_AUTOTRIMEN | CRS_CR_CEN;
    SystemCoreClock = 48000000u;
    SysTick_Config(48000u);                                         // 1 ms tick
}

// ---------------------------------------------------------------- GPIO helpers
static void gpio_mode(GPIO_TypeDef *g, uint8_t pin, uint32_t mode) {   // 0 in, 1 out, 2 af, 3 analog
    g->MODER = (g->MODER & ~(3u << (pin * 2))) | (mode << (pin * 2));
}
static void gpio_af(GPIO_TypeDef *g, uint8_t pin, uint32_t af) {
    volatile uint32_t *r = &g->AFR[pin >> 3];
    *r = (*r & ~(0xFu << ((pin & 7) * 4))) | (af << ((pin & 7) * 4));
}
void pin_write(uint8_t pin, bool on) { GPIOA->BSRR = on ? (1u << pin) : (1u << (pin + 16)); }
bool pin_read(uint8_t pin) { return (GPIOA->IDR >> pin) & 1u; }
bool fast_jumper(void) { return !((GPIOB->IDR >> PIN_FAST_N) & 1u); }   // JP2 bridged = pulled to GND

static void gpio_init(void) {
    RCC->AHBENR |= RCC_AHBENR_GPIOAEN | RCC_AHBENR_GPIOBEN | RCC_AHBENR_GPIOFEN;
    RCC->APB2ENR |= RCC_APB2ENR_SYSCFGCOMPEN;
    SYSCFG->CFGR1 |= SYSCFG_CFGR1_PA11_PA12_RMP;                    // USB on TSSOP-20 pins 17/18
    GPIOA->BRR = (1u << PIN_CHG_EN) | (1u << PIN_BMS_BOOT);         // charging OFF by default
    gpio_mode(GPIOA, PIN_CHG_EN, 1);
    gpio_mode(GPIOA, PIN_PWR_HOLD, 1);
    gpio_mode(GPIOA, PIN_BMS_BOOT, 3);
    gpio_mode(GPIOA, PIN_CHG_INT_N, 0);
    gpio_mode(GPIOA, PIN_PD_PG, 0);
    gpio_mode(GPIOA, PIN_BMS_ALERT, 0);
    gpio_mode(GPIOA, PIN_VBUS_SENSE, 3);
    gpio_mode(GPIOB, PIN_FAST_N, 0);                                 // JP2 fast-charge jumper
    GPIOB->PUPDR = (GPIOB->PUPDR & ~(3u << (PIN_FAST_N * 2))) | (1u << (PIN_FAST_N * 2));   // pull-up
    // LED: TIM3_CH1 on PA6 (AF1), 1 kHz PWM
    gpio_af(GPIOA, PIN_LED, 1); gpio_mode(GPIOA, PIN_LED, 2);
    RCC->APB1ENR |= RCC_APB1ENR_TIM3EN;
    TIM3->PSC = 47; TIM3->ARR = 999; TIM3->CCR1 = 0;
    TIM3->CCMR1 = (6u << TIM_CCMR1_OC1M_Pos) | TIM_CCMR1_OC1PE;
    TIM3->CCER = TIM_CCER_CC1E; TIM3->CR1 = TIM_CR1_ARPE | TIM_CR1_CEN;
    // I2C1 on PF0/PF1, open drain, AF1
    for (uint8_t p = 0; p < 2; p++) {
        GPIOF->OTYPER |= (1u << p);
        GPIOF->OSPEEDR |= (3u << (p * 2));
        gpio_af(GPIOF, p, 1); gpio_mode(GPIOF, p, 2);
    }
}
void led_set(uint8_t percent) { if (percent > 100) percent = 100; TIM3->CCR1 = percent * 10u; }

void bms_boot_pulse(void) {
    gpio_mode(GPIOA, PIN_BMS_BOOT, 1);
    pin_write(PIN_BMS_BOOT, 1);
    delay_ms(5);
    pin_write(PIN_BMS_BOOT, 0);
    gpio_mode(GPIOA, PIN_BMS_BOOT, 3);      // release TS1 so the thermistor reading is not disturbed
}

// ---------------------------------------------------------------- ADC (VBUS divider + VREFINT)
#define VREFINT_CAL (*(const uint16_t *)0x1FFFF7BAu)   // raw VREFINT at VDDA = 3.3 V
static void adc_init(void) {
    RCC->APB2ENR |= RCC_APB2ENR_ADCEN;
    ADC1->CFGR2 = ADC_CFGR2_CKMODE_1;          // PCLK/4 = 12 MHz
    ADC1->CR |= ADC_CR_ADCAL; while (ADC1->CR & ADC_CR_ADCAL) {}
    ADC1->SMPR = 7;                            // 239.5 cycles (high-impedance divider)
    ADC->CCR |= ADC_CCR_VREFEN;
    ADC1->CR |= ADC_CR_ADEN; while (!(ADC1->ISR & ADC_ISR_ADRDY)) {}
}
static uint32_t adc_read(uint32_t ch) {
    ADC1->CHSELR = 1u << ch;
    uint32_t acc = 0;
    for (int i = 0; i < 8; i++) {
        ADC1->CR |= ADC_CR_ADSTART;
        while (!(ADC1->ISR & ADC_ISR_EOC)) {}
        acc += ADC1->DR;
    }
    return acc / 8;
}
uint32_t vdda_mv(void) {
    uint32_t raw = adc_read(17);
    return raw ? (3300u * VREFINT_CAL) / raw : 3300u;
}
uint32_t vbus_mv(void) {
    uint32_t vdda = vdda_mv();
    return (adc_read(PIN_VBUS_SENSE) * vdda / 4095u) * VBUS_DIV;
}

// ---------------------------------------------------------------- I2C1 (100 kHz from 8 MHz HSI), polling with timeouts
static void i2c_init(void) {
    RCC->APB1ENR |= RCC_APB1ENR_I2C1EN;
    I2C1->CR1 = 0;
    I2C1->TIMINGR = 0x2000090Eu;
    I2C1->CR1 = I2C_CR1_PE;
}
static void i2c_recover(void) {
    I2C1->CR1 &= ~I2C_CR1_PE;
    for (volatile int i = 0; i < 50; i++) {}
    I2C1->CR1 |= I2C_CR1_PE;
}
static bool i2c_wait(uint32_t flag) {
    uint32_t t = g_ms;
    while (!(I2C1->ISR & flag)) {
        if (I2C1->ISR & I2C_ISR_NACKF) { I2C1->ICR = I2C_ICR_NACKCF | I2C_ICR_STOPCF; return false; }
        if (g_ms - t > 10) return false;
    }
    return true;
}
bool i2c_xfer(uint8_t addr7, const uint8_t *w, uint8_t wl, uint8_t *r, uint8_t rl) {
    if (wl) {
        I2C1->CR2 = ((uint32_t)addr7 << 1) | ((uint32_t)wl << I2C_CR2_NBYTES_Pos) |
                    (rl ? 0 : I2C_CR2_AUTOEND) | I2C_CR2_START;
        for (uint8_t i = 0; i < wl; i++) {
            if (!i2c_wait(I2C_ISR_TXIS)) goto fail;
            I2C1->TXDR = w[i];
        }
        if (rl) { if (!i2c_wait(I2C_ISR_TC)) goto fail; }
        else { if (!i2c_wait(I2C_ISR_STOPF)) goto fail; I2C1->ICR = I2C_ICR_STOPCF; return true; }
    }
    if (rl) {
        I2C1->CR2 = ((uint32_t)addr7 << 1) | I2C_CR2_RD_WRN | ((uint32_t)rl << I2C_CR2_NBYTES_Pos) |
                    I2C_CR2_AUTOEND | I2C_CR2_START;
        for (uint8_t i = 0; i < rl; i++) {
            if (!i2c_wait(I2C_ISR_RXNE)) goto fail;
            r[i] = (uint8_t)I2C1->RXDR;
        }
        if (!i2c_wait(I2C_ISR_STOPF)) goto fail;
        I2C1->ICR = I2C_ICR_STOPCF;
    }
    return true;
fail:
    i2c_recover();
    return false;
}

// ---------------------------------------------------------------- independent watchdog (~2 s)
void wdg_init(void) {
    IWDG->KR = 0xCCCC; IWDG->KR = 0x5555;
    IWDG->PR = 4;                 // /64 -> ~625 Hz
    IWDG->RLR = 1250;             // ~2 s
    while (IWDG->SR) {}
    IWDG->KR = 0xAAAA;
}
void wdg_feed(void) { IWDG->KR = 0xAAAA; }

// ---------------------------------------------------------------- flash (one page = 1 KiB)
static void flash_unlock(void) {
    if (FLASH->CR & FLASH_CR_LOCK) { FLASH->KEYR = 0x45670123u; FLASH->KEYR = 0xCDEF89ABu; }
    while (FLASH->SR & FLASH_SR_BSY) {}
}

void flash_erase_page(uint32_t addr) {
    flash_unlock();
    FLASH->CR |= FLASH_CR_PER; FLASH->AR = addr; FLASH->CR |= FLASH_CR_STRT;
    while (FLASH->SR & FLASH_SR_BSY) {}
    FLASH->CR &= ~FLASH_CR_PER;
    FLASH->SR = FLASH_SR_EOP | FLASH_SR_PGERR | FLASH_SR_WRPERR;
    FLASH->CR |= FLASH_CR_LOCK;
}

bool flash_write_page(uint32_t addr, const void *data, uint32_t len) {
    flash_erase_page(addr);
    return flash_program(addr, data, len);
}

// program already-erased flash (half-word steps), no erase
bool flash_program(uint32_t addr, const void *data, uint32_t len) {
    flash_unlock();
    const uint16_t *s = (const uint16_t *)data;
    bool ok = true;
    for (uint32_t i = 0; i < (len + 1) / 2; i++) {
        FLASH->CR |= FLASH_CR_PG;
        *(volatile uint16_t *)(addr + i * 2) = s[i];
        while (FLASH->SR & FLASH_SR_BSY) {}
        FLASH->CR &= ~FLASH_CR_PG;
        if (*(volatile uint16_t *)(addr + i * 2) != s[i]) ok = false;
    }
    FLASH->SR = FLASH_SR_EOP | FLASH_SR_PGERR | FLASH_SR_WRPERR;
    FLASH->CR |= FLASH_CR_LOCK;
    return ok;
}

// ---------------------------------------------------------------- reset / bootloader
void system_reset(void) { NVIC_SystemReset(); }

// why did we reset? read once at boot (flags are cleared afterwards)
uint8_t reset_cause(void) {
    uint32_t csr = RCC->CSR;
    RCC->CSR |= RCC_CSR_RMVF;
    if (csr & RCC_CSR_IWDGRSTF) return RESET_WATCHDOG;
    if (csr & RCC_CSR_SFTRSTF) return RESET_SOFTWARE;
    if (csr & RCC_CSR_PORRSTF) return RESET_POWER;
    return RESET_PIN;
}
void enter_bootloader(void) { g_boot_magic = BOOT_MAGIC; NVIC_SystemReset(); }

// called from the reset handler before .data/.bss init
void boot_check(void) {
    if (g_boot_magic != BOOT_MAGIC) return;
    g_boot_magic = 0;
    RCC->APB2ENR |= RCC_APB2ENR_SYSCFGCOMPEN;
    SYSCFG->CFGR1 = (SYSCFG->CFGR1 & ~SYSCFG_CFGR1_MEM_MODE) | SYSCFG_CFGR1_MEM_MODE_0;  // system flash at 0x0
    const uint32_t *sysmem = (const uint32_t *)0x1FFFC400u;                              // STM32F04x ROM bootloader
    __set_MSP(sysmem[0]);
    ((void (*)(void))sysmem[1])();
    while (1) {}
}

uint32_t chip_uid_hash(void) {
    const uint32_t *uid = (const uint32_t *)UID_BASE;
    return uid[0] ^ (uid[1] * 0x9E3779B1u) ^ (uid[2] * 0x85EBCA77u);
}

void hw_init(void) {
    clock_init();
    gpio_init();
    adc_init();
    i2c_init();
}
