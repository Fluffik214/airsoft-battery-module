// USB descriptors: one CDC-ACM (virtual serial) interface
#include <string.h>
#include "tusb.h"
#include "hw.h"

// pid.codes test VID/PID (free for prototypes) - change before any production run
#define USB_VID 0x1209
#define USB_PID 0x0001

static const tusb_desc_device_t desc_device = {
    .bLength = sizeof(tusb_desc_device_t), .bDescriptorType = TUSB_DESC_DEVICE, .bcdUSB = 0x0200,
    .bDeviceClass = TUSB_CLASS_MISC, .bDeviceSubClass = MISC_SUBCLASS_COMMON, .bDeviceProtocol = MISC_PROTOCOL_IAD,
    .bMaxPacketSize0 = CFG_TUD_ENDPOINT0_SIZE, .idVendor = USB_VID, .idProduct = USB_PID, .bcdDevice = 0x0100,
    .iManufacturer = 1, .iProduct = 2, .iSerialNumber = 3, .bNumConfigurations = 1,
};
const uint8_t *tud_descriptor_device_cb(void) { return (const uint8_t *)&desc_device; }

enum { ITF_CDC = 0, ITF_CDC_DATA, ITF_TOTAL };
#define CONFIG_LEN (TUD_CONFIG_DESC_LEN + TUD_CDC_DESC_LEN)
static const uint8_t desc_config[] = {
    TUD_CONFIG_DESCRIPTOR(1, ITF_TOTAL, 0, CONFIG_LEN, 0x00, 100),
    TUD_CDC_DESCRIPTOR(ITF_CDC, 4, 0x81, 8, 0x02, 0x82, 64),
};
const uint8_t *tud_descriptor_configuration_cb(uint8_t index) { (void)index; return desc_config; }

static const char *const strings[] = {"", "Airsoft Module", "PD 3S Charger", "", "Charger Serial"};
static uint16_t desc_str[33];

const uint16_t *tud_descriptor_string_cb(uint8_t index, uint16_t langid) {
    (void)langid;
    uint8_t n = 0;
    if (index == 0) { desc_str[1] = 0x0409; n = 1; }
    else if (index == 3) {                                    // serial number from the chip UID
        static const char hex[] = "0123456789ABCDEF";
        uint32_t h = chip_uid_hash();
        for (n = 0; n < 8; n++) desc_str[1 + n] = (uint16_t)hex[(h >> (28 - 4 * n)) & 0xF];
    } else {
        if (index >= sizeof strings / sizeof strings[0]) return 0;
        const char *s = strings[index];
        n = (uint8_t)strlen(s); if (n > 32) n = 32;
        for (uint8_t i = 0; i < n; i++) desc_str[1 + i] = (uint16_t)s[i];
    }
    desc_str[0] = (uint16_t)((TUSB_DESC_STRING << 8) | (2 * n + 2));
    return desc_str;
}
