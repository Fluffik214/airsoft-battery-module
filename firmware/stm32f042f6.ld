/* STM32F042F6: 32 KiB flash (30 KiB code, 1 KiB event log, 1 KiB settings), 6 KiB RAM */
ENTRY(Reset_Handler)
MEMORY
{
  FLASH (rx)  : ORIGIN = 0x08000000, LENGTH = 30K
  LOG   (r)   : ORIGIN = 0x08007800, LENGTH = 1K
  CFG   (r)   : ORIGIN = 0x08007C00, LENGTH = 1K
  RAM   (rwx) : ORIGIN = 0x20000000, LENGTH = 6K
}
_estack = ORIGIN(RAM) + LENGTH(RAM);
_config_page = ORIGIN(CFG);
_log_page = ORIGIN(LOG);

SECTIONS
{
  .isr_vector : { KEEP(*(.isr_vector)) } > FLASH
  .text : {
    *(.text*) *(.rodata*)
    . = ALIGN(4);
  } > FLASH
  .ARM.exidx : { *(.ARM.exidx*) } > FLASH
  _sidata = LOADADDR(.data);
  .data : {
    _sdata = .;
    *(.data*)
    . = ALIGN(4);
    _edata = .;
  } > RAM AT > FLASH
  .bss (NOLOAD) : {
    _sbss = .;
    *(.bss*) *(COMMON)
    . = ALIGN(4);
    _ebss = .;
  } > RAM
  .noinit (NOLOAD) : { *(.noinit*) } > RAM
  ._stack_check (NOLOAD) : { . = ALIGN(8); . = . + 1024; } > RAM   /* fail the link if < 1 KiB is left for the stack */
}
