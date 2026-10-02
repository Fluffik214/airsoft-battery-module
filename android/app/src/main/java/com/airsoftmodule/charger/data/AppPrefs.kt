package com.airsoftmodule.charger.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Phone-side preferences (not stored on the module). */
class AppPrefs(context: Context) {
    private val sp = context.getSharedPreferences("app", Context.MODE_PRIVATE)

    inner class Pref<T>(private val key: String, default: T, private val read: (String, T) -> T, private val write: (String, T) -> Unit) {
        private val flow = MutableStateFlow(read(key, default))
        val value: T get() = flow.value
        val state: StateFlow<T> get() = flow
        fun set(v: T) { flow.value = v; write(key, v) }
    }

    private fun bool(k: String, d: Boolean) = Pref(k, d, { key, def -> sp.getBoolean(key, def) }, { key, v -> sp.edit().putBoolean(key, v).apply() })
    private fun int(k: String, d: Int) = Pref(k, d, { key, def -> sp.getInt(key, def) }, { key, v -> sp.edit().putInt(key, v).apply() })

    val accent = int("accent", 0)
    val fahrenheit = bool("fahrenheit", false)
    val keepScreenOn = bool("keep_screen_on", true)
    val vibrate = bool("vibrate", true)
    val demoOnStart = bool("demo_on_start", false)
    val logStatus = bool("log_status", false)
    val showTipOfDay = bool("tip_of_day", true)
    val seenIntro = bool("seen_intro", false)
}
