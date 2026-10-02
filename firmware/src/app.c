// Charging, balancing and safety logic + USB text protocol
#include <string.h>
#include <stdlib.h>
#include "app.h"
#include "hw.h"
#include "board.h"
#include "config.h"
#include "soc.h"
#include "bq25798.h"
#include "bq76920.h"
#include "log.h"

void usb_send(const char *s);           // main.c

typedef enum { SRC_NONE, SRC_LOW, SRC_HV, SRC_OVER } src_t;
typedef enum { ST_BOOT, ST_UNPLUGGED, ST_MONITOR, ST_WAIT, ST_PRE, ST_CC, ST_CV, ST_TOPOFF, ST_DONE,
               ST_BALANCE, ST_STORAGE, ST_BLEED, ST_FAULT, ST_HOST, ST_ARMING, ST_CELLCV } state_t;
static const char *const ST_NAME[] = {"BOOT", "UNPLUGGED", "MONITOR", "WAIT", "PRECHARGE", "CC", "CV",
                                      "TOPOFF", "DONE", "BALANCE", "STORAGE", "BLEED", "FAULT", "HOST", "ARMING", "CELLCV"};
static const char *const SRC_NAME[] = {"NONE", "LOW", "PD", "HIGH"};
static const char *const MODE_NAME[] = {"charge", "storage", "monitor"};

static bq25798_t chg;
static bq76920_t bms;
static src_t   src;
static state_t state = ST_BOOT;
static uint16_t faults, vbus;
static bool    charging, chg_ok, cell_hold;
// Per-cell charge control (like a hobby balance charger). The BQ25798 only sees the whole pack, so:
//  - no cell may go above target + CELL_MAX_OVER: charging pauses at once (cell_hold) while it bleeds
//  - when the highest cell is close to the target and the cells differ, the charge current is
//    stepped down (ichg_dyn) so the full cell's bleed resistor keeps it level while the others fill
//  - "full" = every cell at the target; then charging stops for good (until unplugged)
#define CELL_MAX_OVER   10      // mV above target that pauses charging
#define CELL_TAPER_AT   20      // mV below target where the current starts to drop
#define CELL_FULL_AT    25      // all cells within this of the target = full
#define ICHG_MIN_MA     50
static uint16_t ichg_dyn;       // current charge-current limit (<= ichg_base())
static bool     fast;           // JP2 bridged
// charge current for this run: JP2 bridged = fast (cfg.ichg_ma), open = slow (cfg.ichg_slow_ma)
static uint16_t ichg_base(void) { return fast ? cfg.ichg_ma : cfg.ichg_slow_ma; }
static bool     full;
static uint32_t t_taper;
// Charging is gated by the USB voltage only: it must sit inside minvin..VBUS_MAX_MV (12..17 V by default)
// for cfg.wait_s seconds (default 5 s). A phone gives 5 V, so it never charges from a phone.
// "host" (app connected) is informational only.
static bool    host, armed, report_now, was_charging;
static uint32_t t_window;               // when VBUS entered the charging window, 0 = outside
static uint16_t prev_faults;
// charge report: one per power-up (the single USB-C port means a phone can't watch a charge live,
// so the result is written to the log at unplug and read by the app later)
static bool     sess, sess_done, sess_logged;
static uint32_t sess_t0, sess_mas, t_prev_tick;     // mA*s charged
static uint16_t sess_from_mv;
static int16_t  sess_peak_dc;
static uint8_t bal_mask;
static uint32_t t_unplug, t_last_bal, t_last_cfg, t_last_wake, t_report, t_kick;
static uint16_t applied_vreg, applied_ichg, applied_iin;

void app_host_seen(void) { host = true; }

// per-cell charge target for the current mode
static uint16_t target_mv(void) {
    if (cfg.mode == MODE_STORAGE) return cfg.storage_mv;
    if (cfg.tgt_pct >= 100) return cfg.vcell_mv;
    uint16_t v = soc_to_mv(cfg.tgt_pct);
    return v < cfg.vcell_mv ? v : cfg.vcell_mv;
}

static int32_t arm_left_s(void) {
    if (armed || !t_window) return -1;
    uint32_t w = (uint32_t)cfg.wait_s * 1000u, t = millis() - t_window;
    return t >= w ? 0 : (int32_t)((w - t + 999) / 1000);
}

// called every main-loop pass: cuts charging the moment VBUS goes above the limit (17 V)
void app_fast(void) {
    static uint8_t over;
    if (!charging && !armed) { over = 0; return; }
    uint32_t v = vbus_mv();
    over = v > VBUS_MAX_MV ? (uint8_t)(over + 1) : 0;
    if (over >= 3) {                    // 3 readings in a row (< 1 ms) so one noisy ADC sample can't trip it
        over = 0;
        pin_write(PIN_CHG_EN, 0);       // hardware /CE goes high via Q1/R10: charger stops now
        armed = false;
        t_window = 0;
        log_once(10, (uint16_t)(v > 0xFFFF ? 0xFFFF : v));   // F_OVERVOLT is bit 10
    }
}

// ---------------------------------------------------------------- helpers
static uint16_t cmax(void) { uint16_t m = 0; for (int i = 0; i < 3; i++) if (bms.cell_mv[i] > m) m = bms.cell_mv[i]; return m; }
static uint16_t cmin(void) { uint16_t m = 0xFFFF; for (int i = 0; i < 3; i++) if (bms.cell_mv[i] < m) m = bms.cell_mv[i]; return m; }
static int imax(void) { int k = 0; for (int i = 1; i < 3; i++) if (bms.cell_mv[i] > bms.cell_mv[k]) k = i; return k; }
static uint8_t pack_soc(void) {
    uint32_t s = 0;
    for (int i = 0; i < 3; i++) s += soc_from_mv(soc_rest_mv(bms.cell_mv[i], charging ? chg.ibat_ma : 0));
    return (uint8_t)(s / 3);
}

static void charger_apply(uint16_t target_cell_mv) {
    uint16_t vreg = (uint16_t)(target_cell_mv * 3);
    uint16_t ichg = ichg_dyn ? ichg_dyn : ichg_base();
    if (vreg != applied_vreg || ichg != applied_ichg || cfg.iin_ma != applied_iin || millis() - t_last_cfg > 5000) {
        // re-apply periodically too: the charger falls back to defaults if its watchdog ever expires
        if (bq25798_configure(vreg, ichg, cfg.iin_ma)) {
            applied_vreg = vreg; applied_ichg = ichg; applied_iin = cfg.iin_ma;
        }
        t_last_cfg = millis();
    }
}

static void set_charging(bool on, uint16_t target_cell_mv) {
    if (on) { bq25798_hiz(false); charger_apply(target_cell_mv); }
    if (on != charging) bq25798_enable(on);
    pin_write(PIN_CHG_EN, on);
    charging = on;
}

static void shutdown_now(void) {
    pin_write(PIN_CHG_EN, 0);
    bq76920_balance(0);
    bq76920_ship();                     // ~0.6 uA
    bq25798_shutdown();                 // ~0.5 uA (only accepted with no adapter, which is the case here)
    led_set(0);
    pin_write(PIN_PWR_HOLD, 0);         // our own power goes away now
    while (1) {}                        // if power stays (USB re-plugged) the watchdog reboots us
}

// ---------------------------------------------------------------- main logic
void app_init(void) {
    pin_write(PIN_PWR_HOLD, 1);         // stay alive on the pack if USB goes away
    log_init();
    if (reset_cause() == RESET_WATCHDOG) log_event(EV_RESET_WDG, 0);
    chg_ok = bq25798_init();
    bq76920_wake();
    t_last_wake = millis();
}

void app_tick(void) {
    uint32_t now = millis();
    vbus = (uint16_t)vbus_mv();
    src = vbus < 4000 ? SRC_NONE : vbus < cfg.minvin_mv ? SRC_LOW : vbus > VBUS_MAX_MV ? SRC_OVER : SRC_HV;

    // ---- measurements
    if (!bq76920_read(&bms) && now - t_last_wake > 3000) {
        if (!bq76920_wake()) log_once(EV_BMS_WAKE_FAIL, 0);
        t_last_wake = now;
    }
    if (!chg_ok && src != SRC_NONE) chg_ok = bq25798_init();
    if (chg_ok) { if (!bq25798_read(&chg)) chg_ok = false; }
    if (now - t_kick > 1000) { bq25798_kick(); t_kick = now; }

    // ---- safety evaluation
    faults = 0;
    if (!bms.present) faults |= F_BMS;
    else {
        uint16_t hi = cmax(), lo = cmin();
        if (bms.pack_mv < 6000) faults |= F_NOBATT;
        if (hi > 4250 || hi > cfg.vcell_mv + 50) faults |= F_CELL_OV;
        if (lo < 2500 && !(faults & F_NOBATT)) faults |= F_CELL_UV;
        if (hi - lo > cfg.imb_max_mv && !(faults & F_NOBATT)) faults |= F_IMBAL;
        if (bms.temp_dc > cfg.tmax_c * 10) faults |= F_HOT;
        if (bms.temp_dc < 0 && bms.temp_dc > -400) faults |= F_COLD;
    }
    if (src != SRC_NONE && !chg_ok) faults |= F_CHGCOMM;
    if (chg_ok && (chg.fault0 || (chg.fault1 & 0xC4))) faults |= F_CHG;
    if (chg_ok && chg.tdie_dc > 1100) faults |= F_HOT;
    if (src == SRC_LOW) faults |= F_LOWSRC;
    if (src == SRC_OVER) faults |= F_OVERVOLT;
    const uint16_t blocking = F_NOBATT | F_CELL_OV | F_CELL_UV | F_IMBAL | F_HOT | F_COLD | F_CHG | F_BMS | F_CHGCOMM | F_OVERVOLT;

    // ---- log every new fault (once per power-up each; F_LOWSRC is normal on a phone, so skipped)
    uint16_t fresh = faults & ~prev_faults & ~F_LOWSRC;
    prev_faults = faults;
    for (uint8_t b = 0; b < 16; b++) {
        if (!(fresh & (1u << b))) continue;
        uint16_t val = 0;
        switch (1u << b) {
            case F_NOBATT:   val = bms.pack_mv; break;
            case F_CELL_OV:  val = cmax(); break;
            case F_CELL_UV:  val = cmin(); break;
            case F_IMBAL:    val = (uint16_t)(cmax() - cmin()); break;
            case F_HOT:      val = (uint16_t)(bms.temp_dc > cfg.tmax_c * 10 ? bms.temp_dc : chg.tdie_dc); break;
            case F_COLD:     val = (uint16_t)bms.temp_dc; break;
            case F_CHG:      val = (uint16_t)((chg.fault0 << 8) | chg.fault1); break;
            case F_OVERVOLT: val = vbus; break;
        }
        log_once(b, val);
    }

    // ---- charge decision: VBUS must stay in the window for wait_s before charging is allowed
    if (src == SRC_HV) { if (!t_window) t_window = now ? now : 1; }
    else { t_window = 0; armed = false; }
    if (t_window && !armed && now - t_window >= (uint32_t)cfg.wait_s * 1000u) armed = true;
    uint16_t target = target_mv();
    bool want = armed && (src == SRC_HV) && cfg.mode != MODE_MONITOR && !(faults & blocking);
    if (src != SRC_HV) { full = false; ichg_dyn = 0; }           // new charger connection = new charge
    fast = fast_jumper();
    if (!ichg_dyn || ichg_dyn > ichg_base()) ichg_dyn = ichg_base();
    if (want && bms.present) {
        uint16_t hi = cmax(), lo = cmin();
        // per-cell limit: never let one cell go over the target
        if (hi >= target + CELL_MAX_OVER) { cell_hold = true; ichg_dyn = ICHG_MIN_MA; }
        if (cell_hold && hi <= target + 2) cell_hold = false;   // resume in short bursts so the low cells keep filling
        // taper: step the current down near the top while the cells are uneven, back up when far
        if (now - t_taper >= 2000) {
            t_taper = now;
            if (charging && hi + CELL_TAPER_AT >= target && hi - lo > cfg.bal_th_mv) {
                uint16_t n = (uint16_t)(ichg_dyn * 3 / 4);
                ichg_dyn = n < ICHG_MIN_MA ? ICHG_MIN_MA : n;
            } else if (hi + 60 < target && ichg_dyn < ichg_base()) {
                ichg_dyn = (uint16_t)(ichg_dyn + 100 > ichg_base() ? ichg_base() : ichg_dyn + 100);
            }
        }
        // full: every cell reached the target (measured under the small final current)
        // only judged at low current: under 1.4 A the cell readings are ~35 mV high from internal resistance
        if (cfg.mode == MODE_CHARGE && lo + CELL_FULL_AT >= target && (!charging || chg.ibat_ma < 150)) full = true;
        if (full || cell_hold) want = false;
        if (cfg.mode == MODE_STORAGE && lo >= target - 20) want = false;
    }
    if (src == SRC_LOW) bq25798_hiz(true);                      // phone / 5 V: do not drain the source
    set_charging(want, target);

    // ---- balancing (decided every 2 s)
    if (now - t_last_bal > 2000) {
        t_last_bal = now;
        uint8_t m = 0;
        if (cfg.bal_en && bms.present && !(faults & (F_NOBATT | F_HOT | F_BMS))) {
            uint16_t hi = cmax(), lo = cmin();
            if (cfg.mode == MODE_STORAGE && armed && lo > cfg.storage_mv + 20) {
                m = (now / 2000) & 1 ? 0x05 : 0x02;              // bleed all cells down towards storage voltage
            } else if (hi > cfg.bal_min_mv && hi - lo > cfg.bal_th_mv) {
                int k = imax();
                m = (uint8_t)(1u << k);
                int other = (k == 0) ? 2 : (k == 2 ? 0 : -1);   // cells 1 and 3 may bleed together
                if (other >= 0 && bms.cell_mv[other] > lo + cfg.bal_th_mv) m |= (uint8_t)(1u << other);
            }
        }
        if (m != bal_mask) { bq76920_balance(m); bal_mask = m; }
    }

    // ---- charge session bookkeeping
    uint32_t dt = t_prev_tick ? now - t_prev_tick : 0;
    t_prev_tick = now;
    if (charging) {
        if (!sess) {
            sess = true; sess_t0 = now; sess_from_mv = bms.pack_mv; sess_peak_dc = bms.temp_dc;
            log_event(EV_CHARGE_START, bms.pack_mv);
        }
        if (chg.ibat_ma > 0) sess_mas += (uint32_t)chg.ibat_ma * dt / 1000u;
        if (bms.temp_dc > sess_peak_dc) sess_peak_dc = bms.temp_dc;
        if (chg.chg_stat == 7) sess_done = true;             // charger terminated at the target
    }
    if (sess && full) sess_done = true;                      // every cell reached the target
    if (sess && cfg.mode == MODE_STORAGE && !charging && !(faults & blocking) && armed) sess_done = true;
    if (!charging && was_charging && (faults & blocking)) log_once(EV_CHARGE_STOP, faults);
    was_charging = charging;

    // ---- state label
    if (src == SRC_NONE) state = ST_UNPLUGGED;
    else if (faults & blocking) state = ST_FAULT;
    else if (src == SRC_LOW) state = host ? ST_HOST : (bal_mask ? ST_BALANCE : ST_MONITOR);
    else if (!armed && cfg.mode != MODE_MONITOR) state = ST_ARMING;
    else if (src == SRC_LOW || cfg.mode == MODE_MONITOR) state = bal_mask ? ST_BALANCE : ST_MONITOR;
    else if (charging) {
        switch (chg.chg_stat) {
            case 1: case 2: state = ST_PRE; break;
            case 3: state = ST_CC; break;
            case 4: state = ST_CV; break;
            case 6: state = ST_TOPOFF; break;
            case 7: state = bal_mask ? ST_BALANCE : ST_DONE; break;
            default: state = ST_WAIT; break;
        }
        if (ichg_dyn < ichg_base() && state != ST_DONE) state = ST_CELLCV;
        if (cfg.mode == MODE_STORAGE && state != ST_DONE) state = ST_STORAGE;
    } else if (cell_hold && !full) state = ST_CELLCV;
    else if (cfg.mode == MODE_STORAGE && bal_mask) state = ST_BLEED;
    else state = bal_mask ? ST_BALANCE : ST_DONE;

    // ---- unplugged: finish up on battery power, then switch everything off
    if (src == SRC_NONE) {
        if (!t_unplug) t_unplug = now ? now : 1;
        if (sess && !sess_logged) {                          // write the charge report while we still run on the pack
            sess_logged = true;
            log_event(sess_done ? EV_CHARGE_DONE : EV_CHARGE_UNPLUG, bms.pack_mv);
            log_event(EV_CHARGE_FROM, sess_from_mv);
            log_event(EV_CHARGE_MAH, (uint16_t)(sess_mas / 3600u));
            log_event(EV_CHARGE_MIN, (uint16_t)((now - sess_t0) / 60000u));
            log_event(EV_CHARGE_PEAKT, (uint16_t)sess_peak_dc);
        }
        bool keep = cfg.bal_unplug_min && bal_mask && (now - t_unplug) < (uint32_t)cfg.bal_unplug_min * 60000u;
        if (!keep && now - t_unplug > 1500) shutdown_now();
    } else t_unplug = 0;
}

// ---------------------------------------------------------------- LED patterns
void app_led(void) {
    uint32_t t = millis();
    uint32_t b = cfg.led_pct, v = 0;
    switch (state) {
        case ST_FAULT:   v = (t / 125) & 1 ? b : 0; break;                              // fast blink
        case ST_PRE: case ST_CC: case ST_CV: case ST_TOPOFF: case ST_STORAGE: case ST_CELLCV: {        // breathing
            uint32_t p = t % 2000; p = p < 1000 ? p : 2000 - p; v = b * p / 1000; break; }
        case ST_DONE:    v = b; break;                                                  // solid
        case ST_BALANCE: case ST_BLEED: { uint32_t p = t % 1500; v = (p < 100 || (p > 250 && p < 350)) ? b : 0; break; }
        case ST_MONITOR: case ST_WAIT: case ST_HOST: v = (t % 2000) < 80 ? b : 0; break; // short flash
        case ST_ARMING:  v = (t % 1000) < 500 ? b / 3 : 0; break;                       // slow dim blink
        default: v = 0;
    }
    led_set((uint8_t)v);
}

// ---------------------------------------------------------------- tiny JSON writer
typedef struct { char *p; int left; } jw_t;
static void js(jw_t *w, const char *s) { while (*s && w->left > 1) { *w->p++ = *s++; w->left--; } *w->p = 0; }
static void ji(jw_t *w, int32_t v) {
    char b[12]; int n = 0; uint32_t u = v < 0 ? (uint32_t)(-v) : (uint32_t)v;
    do { b[n++] = (char)('0' + u % 10); u /= 10; } while (u);
    if (v < 0) b[n++] = '-';
    char o[12]; for (int i = 0; i < n; i++) o[i] = b[n - 1 - i]; o[n] = 0;
    js(w, o);
}
static void kv(jw_t *w, const char *k, int32_t v) { js(w, ",\""); js(w, k); js(w, "\":"); ji(w, v); }
static void ks(jw_t *w, const char *k, const char *v) { js(w, ",\""); js(w, k); js(w, "\":\""); js(w, v); js(w, "\""); }

bool app_report_due(void) {
    if (report_now || millis() - t_report >= cfg.rate_ms) { report_now = false; t_report = millis(); return true; }
    return false;
}

void app_status_json(char *buf, int n) {
    jw_t w = {buf, n};
    js(&w, "{\"t\":\"s\"");
    ks(&w, "st", ST_NAME[state]); ks(&w, "md", MODE_NAME[cfg.mode]); ks(&w, "src", SRC_NAME[src]);
    kv(&w, "vbus", vbus); kv(&w, "ibus", chg_ok ? chg.ibus_ma : 0);
    kv(&w, "pack", bms.present ? bms.pack_mv : (chg_ok ? chg.vbat_mv : 0));
    kv(&w, "ibat", chg_ok ? chg.ibat_ma : 0);
    js(&w, ",\"c\":["); for (int i = 0; i < 3; i++) { if (i) js(&w, ","); ji(&w, bms.cell_mv[i]); } js(&w, "]");
    js(&w, ",\"p\":["); for (int i = 0; i < 3; i++) { if (i) js(&w, ","); ji(&w, soc_from_mv(soc_rest_mv(bms.cell_mv[i], charging ? chg.ibat_ma : 0))); } js(&w, "]");
    kv(&w, "soc", pack_soc());
    kv(&w, "tp", bms.temp_dc); kv(&w, "tc", chg_ok ? chg.tdie_dc : 0);
    kv(&w, "bal", bal_mask); kv(&w, "flt", faults); kv(&w, "chg", charging);
    int32_t eta = -1;
    if (charging && chg.ibat_ma > 50) eta = (int32_t)(100 - pack_soc()) * cfg.capacity_mah / 100 * 60 / chg.ibat_ma + (state == ST_CV ? 10 : 15);
    kv(&w, "eta", eta);
    kv(&w, "host", host); kv(&w, "arm", arm_left_s()); kv(&w, "tgt", target_mv()); kv(&w, "logn", log_count());
    kv(&w, "ich", charging ? ichg_dyn : 0); kv(&w, "full", full); kv(&w, "fast", fast);
    kv(&w, "up", (int32_t)(millis() / 1000));
    js(&w, "}\n");
}

static void send_cfg(void) {
    char b[300]; jw_t w = {b, sizeof b};
    js(&w, "{\"t\":\"cfg\"");
    kv(&w, "vcell", cfg.vcell_mv); kv(&w, "ichg", cfg.ichg_ma); kv(&w, "iin", cfg.iin_ma);
    kv(&w, "stor", cfg.storage_mv); kv(&w, "balmin", cfg.bal_min_mv); kv(&w, "minvin", cfg.minvin_mv);
    kv(&w, "imb", cfg.imb_max_mv); kv(&w, "cap", cfg.capacity_mah); kv(&w, "rate", cfg.rate_ms);
    kv(&w, "balth", cfg.bal_th_mv); kv(&w, "tmax", cfg.tmax_c); kv(&w, "led", cfg.led_pct);
    kv(&w, "bal", cfg.bal_en); kv(&w, "mode", cfg.mode); kv(&w, "bun", cfg.bal_unplug_min);
    kv(&w, "tgt", cfg.tgt_pct); kv(&w, "wait", cfg.wait_s); kv(&w, "ichs", cfg.ichg_slow_ma);
    js(&w, "}\n");
    usb_send(b);
}

static void send_info(void) {
    char b[160]; jw_t w = {b, sizeof b};
    static const char hex[] = "0123456789ABCDEF";
    char uid[9]; uint32_t h = chip_uid_hash();
    for (int i = 0; i < 8; i++) uid[i] = hex[(h >> (28 - 4 * i)) & 0xF];
    uid[8] = 0;
    js(&w, "{\"t\":\"i\""); ks(&w, "fw", FW_VERSION); ks(&w, "hw", HW_VERSION); ks(&w, "uid", uid);
    kv(&w, "chg", chg_ok); kv(&w, "bms", bms.present); js(&w, "}\n");
    usb_send(b);
}

// one line per entry, oldest first, then {"t":"logend",...}
static void send_log(void) {
    char b[80];
    for (uint16_t i = 0; i < log_count(); i++) {
        const log_entry_t *e = log_get(i);
        jw_t w = {b, sizeof b};
        js(&w, "{\"t\":\"log\""); kv(&w, "b", e->boot); kv(&w, "s", e->t_s); kv(&w, "c", e->code); kv(&w, "v", e->val);
        js(&w, "}\n");
        usb_send(b);
        wdg_feed();
    }
    jw_t w = {b, sizeof b};
    js(&w, "{\"t\":\"logend\""); kv(&w, "n", log_count()); kv(&w, "boot", log_boot()); js(&w, "}\n");
    usb_send(b);
}

static void reply(bool ok, const char *what) {
    char b[96]; jw_t w = {b, sizeof b};
    js(&w, ok ? "{\"t\":\"ok\"" : "{\"t\":\"err\""); ks(&w, "m", what); js(&w, "}\n");
    usb_send(b);
}

// commands: "s?" "c?" "i?" "set <key> <int>" "save" "defaults" "mode charge|storage|monitor" "dfu" "reboot" "ship"
void app_command(char *line) {
    while (*line == ' ') line++;
    if (!*line) return;
    host = true;                        // the app is connected (information only)
    if (!strcmp(line, "s?")) report_now = true;
    else if (!strcmp(line, "c?")) send_cfg();
    else if (!strcmp(line, "i?")) send_info();
    else if (!strncmp(line, "set ", 4)) {
        char *k = line + 4, *v = strchr(k, ' ');
        if (!v) { reply(false, "usage: set key value"); return; }
        *v++ = 0;
        bool ok = config_set(k, strtol(v, 0, 10));
        reply(ok, ok ? k : "bad key/value");
        if (ok) send_cfg();
    }
    else if (!strcmp(line, "save")) reply(config_save(), "save");
    else if (!strcmp(line, "defaults")) { config_defaults(); reply(true, "defaults"); send_cfg(); }
    else if (!strncmp(line, "mode ", 5)) {
        const char *m = line + 5; int v = -1;
        for (int i = 0; i < 3; i++) if (!strcmp(m, MODE_NAME[i])) v = i;
        if (v >= 0) { cfg.mode = (uint8_t)v; reply(true, m); send_cfg(); } else reply(false, "mode?");
    }
    else if (!strcmp(line, "dfu")) { reply(true, "dfu"); delay_ms(50); pin_write(PIN_CHG_EN, 0); enter_bootloader(); }
    else if (!strcmp(line, "reboot")) { reply(true, "reboot"); delay_ms(50); system_reset(); }
    else if (!strcmp(line, "ship")) { reply(src == SRC_NONE, "ship (works only unplugged)"); }
    else if (!strcmp(line, "log?")) send_log();
    else if (!strcmp(line, "logclr")) { log_clear(); reply(true, "log cleared"); }
    else reply(false, "unknown command");
}
