// Minimal startup for STM32F042x6 (Cortex-M0): vector table + reset handler, no assembler needed
#include <stdint.h>

extern uint32_t _sidata, _sdata, _edata, _sbss, _ebss, _estack;
extern int main(void);
extern void boot_check(void);

void Reset_Handler(void);
void Default_Handler(void) { while (1) {} }

#define WEAK __attribute__((weak, alias("Default_Handler")))
void NMI_Handler(void) WEAK;
void HardFault_Handler(void) WEAK;
void SVC_Handler(void) WEAK;
void PendSV_Handler(void) WEAK;
void SysTick_Handler(void) WEAK;
void WWDG_IRQHandler(void) WEAK;
void PVD_VDDIO2_IRQHandler(void) WEAK;
void RTC_IRQHandler(void) WEAK;
void FLASH_IRQHandler(void) WEAK;
void RCC_CRS_IRQHandler(void) WEAK;
void EXTI0_1_IRQHandler(void) WEAK;
void EXTI2_3_IRQHandler(void) WEAK;
void EXTI4_15_IRQHandler(void) WEAK;
void TSC_IRQHandler(void) WEAK;
void DMA1_Channel1_IRQHandler(void) WEAK;
void DMA1_Channel2_3_IRQHandler(void) WEAK;
void DMA1_Channel4_5_IRQHandler(void) WEAK;
void ADC1_IRQHandler(void) WEAK;
void TIM1_BRK_UP_TRG_COM_IRQHandler(void) WEAK;
void TIM1_CC_IRQHandler(void) WEAK;
void TIM2_IRQHandler(void) WEAK;
void TIM3_IRQHandler(void) WEAK;
void TIM14_IRQHandler(void) WEAK;
void TIM16_IRQHandler(void) WEAK;
void TIM17_IRQHandler(void) WEAK;
void I2C1_IRQHandler(void) WEAK;
void SPI1_IRQHandler(void) WEAK;
void SPI2_IRQHandler(void) WEAK;
void USART1_IRQHandler(void) WEAK;
void USART2_IRQHandler(void) WEAK;
void CEC_CAN_IRQHandler(void) WEAK;
void USB_IRQHandler(void) WEAK;

__attribute__((section(".isr_vector"), used))
void (*const g_vectors[])(void) = {
    (void (*)(void))&_estack, Reset_Handler, NMI_Handler, HardFault_Handler,
    0, 0, 0, 0, 0, 0, 0, SVC_Handler, 0, 0, PendSV_Handler, SysTick_Handler,
    WWDG_IRQHandler, PVD_VDDIO2_IRQHandler, RTC_IRQHandler, FLASH_IRQHandler,
    RCC_CRS_IRQHandler, EXTI0_1_IRQHandler, EXTI2_3_IRQHandler, EXTI4_15_IRQHandler,
    TSC_IRQHandler, DMA1_Channel1_IRQHandler, DMA1_Channel2_3_IRQHandler, DMA1_Channel4_5_IRQHandler,
    ADC1_IRQHandler, TIM1_BRK_UP_TRG_COM_IRQHandler, TIM1_CC_IRQHandler, TIM2_IRQHandler,
    TIM3_IRQHandler, 0, 0, TIM14_IRQHandler,
    0, TIM16_IRQHandler, TIM17_IRQHandler, I2C1_IRQHandler,
    0, SPI1_IRQHandler, SPI2_IRQHandler, USART1_IRQHandler,
    USART2_IRQHandler, 0, CEC_CAN_IRQHandler, USB_IRQHandler,
};

void Reset_Handler(void) {
    boot_check();                       // jumps to the ROM DFU bootloader if requested before reset
    uint32_t *src = &_sidata, *dst = &_sdata;
    while (dst < &_edata) *dst++ = *src++;
    for (dst = &_sbss; dst < &_ebss;) *dst++ = 0;
    main();
    while (1) {}
}
