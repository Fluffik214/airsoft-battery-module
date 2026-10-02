#!/usr/bin/env python3
"""Build the charger firmware without make.

    python build.py                 -> build/airsoft_charger.{elf,bin,hex}
    python build.py --gcc <bin dir> -> use a specific arm-none-eabi toolchain
    python build.py clean
"""
import os, shutil, subprocess, sys

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "build")
NAME = "airsoft_charger"

SRC = [
    "src/startup.c", "src/main.c", "src/hw.c", "src/app.c", "src/config.c", "src/soc.c", "src/log.c",
    "src/bq25798.c", "src/bq76920.c", "src/usb_descriptors.c",
    "lib/tinyusb/src/tusb.c", "lib/tinyusb/src/common/tusb_fifo.c",
    "lib/tinyusb/src/device/usbd.c",
    "lib/tinyusb/src/class/cdc/cdc_device.c",
    "lib/tinyusb/src/portable/st/stm32_fsdev/dcd_stm32_fsdev.c",
    "lib/tinyusb/src/portable/st/stm32_fsdev/fsdev_common.c",
]
INC = ["src", "lib/cmsis", "lib/tinyusb/src"]
CFLAGS = ["-mcpu=cortex-m0", "-mthumb", "-Os", "-g", "-std=gnu11", "-ffunction-sections", "-fdata-sections",
          "-fno-common", "-Wall", "-Wextra", "-Wno-unused-parameter", "-DSTM32F042x6"]
LDFLAGS = ["-mcpu=cortex-m0", "-mthumb", "-nostartfiles", "-Wl,--gc-sections", "-Wl,--print-memory-usage",
           "--specs=nano.specs", "--specs=nosys.specs", "-T", "stm32f042f6.ld", f"-Wl,-Map={OUT}/{NAME}.map"]


def tool(prefix, name):
    exe = "arm-none-eabi-" + name + (".exe" if os.name == "nt" else "")
    if prefix:
        return os.path.join(prefix, exe)
    found = shutil.which(exe)
    if not found:
        sys.exit("arm-none-eabi-gcc not found: install it or pass --gcc <toolchain bin folder>")
    return found


def run(cmd):
    r = subprocess.run(cmd, cwd=HERE)
    if r.returncode:
        sys.exit(r.returncode)


def main():
    args = sys.argv[1:]
    if args[:1] == ["clean"]:
        shutil.rmtree(OUT, ignore_errors=True)
        return
    prefix = args[args.index("--gcc") + 1] if "--gcc" in args else os.environ.get("ARM_GCC_BIN", "")
    gcc, objcopy, size = tool(prefix, "gcc"), tool(prefix, "objcopy"), tool(prefix, "size")
    os.makedirs(OUT, exist_ok=True)
    objs = []
    for s in SRC:
        o = os.path.join(OUT, s.replace("/", "_").replace(".c", ".o"))
        print("CC", s)
        run([gcc, *CFLAGS, *[f"-I{i}" for i in INC], "-c", s, "-o", o])
        objs.append(o)
    elf = os.path.join(OUT, NAME + ".elf")
    run([gcc, *LDFLAGS, *objs, "-o", elf])
    run([objcopy, "-O", "binary", elf, os.path.join(OUT, NAME + ".bin")])
    run([objcopy, "-O", "ihex", elf, os.path.join(OUT, NAME + ".hex")])
    run([size, elf])


if __name__ == "__main__":
    main()
