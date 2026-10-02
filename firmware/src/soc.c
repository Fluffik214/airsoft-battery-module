#include "soc.h"

// typical LiPo open-circuit voltage curve (mV -> %), interpolated
static const uint16_t ocv_mv[] = {3300, 3500, 3600, 3700, 3750, 3790, 3830, 3870, 3920, 3980, 4060, 4110, 4150, 4200};
static const uint8_t  ocv_pc[] = {   0,    3,    6,   15,   25,   35,   45,   55,   65,   75,   85,   92,   96,  100};
#define N (sizeof ocv_mv / sizeof ocv_mv[0])

uint8_t soc_from_mv(uint16_t mv) {
    if (mv <= ocv_mv[0]) return 0;
    if (mv >= ocv_mv[N - 1]) return 100;
    for (unsigned i = 1; i < N; i++) {
        if (mv <= ocv_mv[i]) {
            uint32_t span = ocv_mv[i] - ocv_mv[i - 1];
            return (uint8_t)(ocv_pc[i - 1] + (uint32_t)(ocv_pc[i] - ocv_pc[i - 1]) * (mv - ocv_mv[i - 1]) / span);
        }
    }
    return 100;
}

// ~25 mOhm per cell internal + wiring resistance: subtract the charge-current IR rise
uint16_t soc_rest_mv(uint16_t mv, int16_t ibat_ma) {
    if (ibat_ma <= 0) return mv;
    uint32_t drop = (uint32_t)ibat_ma * 25u / 1000u;
    return (uint16_t)(mv > drop ? mv - drop : 0);
}

// inverse of soc_from_mv: resting cell voltage for a given charge %
uint16_t soc_to_mv(uint8_t pct) {
    if (pct <= ocv_pc[0]) return ocv_mv[0];
    if (pct >= ocv_pc[N - 1]) return ocv_mv[N - 1];
    for (unsigned i = 1; i < N; i++) {
        if (pct <= ocv_pc[i]) {
            uint32_t span = ocv_pc[i] - ocv_pc[i - 1];
            return (uint16_t)(ocv_mv[i - 1] + (uint32_t)(ocv_mv[i] - ocv_mv[i - 1]) * (pct - ocv_pc[i - 1]) / span);
        }
    }
    return ocv_mv[N - 1];
}
