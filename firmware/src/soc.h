// LiPo state-of-charge from resting cell voltage (with a simple IR correction while charging)
#pragma once
#include <stdint.h>
uint8_t soc_from_mv(uint16_t cell_mv);                       // 0..100 %
uint16_t soc_rest_mv(uint16_t cell_mv, int16_t ibat_ma);     // estimated resting voltage
uint16_t soc_to_mv(uint8_t pct);                             // resting cell voltage for a charge %
