package com.airsoftmodule.charger.data

import android.annotation.SuppressLint
import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.airsoftmodule.charger.usb.CdcConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Session statistics integrated from the status stream. */
data class Session(val startMs: Long = 0, val mAh: Double = 0.0, val wh: Double = 0.0, val peakTempDc: Int = 0)

class ChargerViewModel(app: Application) : AndroidViewModel(app) {
    private val usb = app.getSystemService(UsbManager::class.java)
    val prefs = AppPrefs(app)

    private val _link = MutableStateFlow<Link>(Link.Disconnected)
    val link: StateFlow<Link> = _link.asStateFlow()
    private val _status = MutableStateFlow<Status?>(null)
    val status: StateFlow<Status?> = _status.asStateFlow()
    private val _config = MutableStateFlow<DevConfig?>(null)
    val config: StateFlow<DevConfig?> = _config.asStateFlow()
    private val _info = MutableStateFlow<DevInfo?>(null)
    val info: StateFlow<DevInfo?> = _info.asStateFlow()
    private val _history = MutableStateFlow<List<Sample>>(emptyList())
    val history: StateFlow<List<Sample>> = _history.asStateFlow()
    private val _console = MutableStateFlow<List<ConsoleLine>>(emptyList())
    val console: StateFlow<List<ConsoleLine>> = _console.asStateFlow()
    private val _session = MutableStateFlow(Session())
    val session: StateFlow<Session> = _session.asStateFlow()
    private val _log = MutableStateFlow<List<LogEntry>>(emptyList())
    val log: StateFlow<List<LogEntry>> = _log.asStateFlow()
    private val _logLoading = MutableStateFlow(false)
    val logLoading: StateFlow<Boolean> = _logLoading.asStateFlow()
    private val logBuf = mutableListOf<LogEntry>()
    private var logBoot = 0
    val currentBoot get() = logBoot
    private var lastLogCount = -1
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    private var cdc: CdcConnection? = null
    private var demo: DemoDevice? = null
    private var demoJob: Job? = null
    private val outbox = Channel<String>(Channel.UNLIMITED)
    private var lastSampleMs = 0L
    private var lastStatusMs = 0L
    private var lastState = ""
    private var expectingSave = false

    private val permissionAction = "${app.packageName}.USB_PERMISSION"
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                permissionAction -> {
                    val dev = intent.usbDevice() ?: return
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) openDevice(dev)
                    else _link.value = Link.Error("USB access was denied")
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> if (_link.value !is Link.Demo) connect()
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val dev = intent.usbDevice()
                    val c = cdc
                    if (c != null && (dev == null || dev.deviceName == c.deviceName)) c.close()
                }
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(permissionAction)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        // single writer: commands go out one by one with a small gap so the module's line buffer never overflows
        // USB writes block (bulkTransfer), so they run on the IO dispatcher, never on the UI thread
        viewModelScope.launch(Dispatchers.IO) {
            for (cmd in outbox) {
                log(true, cmd)
                val d = demo
                if (d != null) withContext(Dispatchers.Main) { d.command(cmd) }   // demo state lives on Main
                else if (cdc?.write(cmd + "\n") != true) _messages.tryEmit("Not connected")
                delay(40)
            }
        }
        // watchdog: a connected device that stops talking gets re-polled
        viewModelScope.launch {
            while (isActive) {
                delay(3000)
                if (_link.value is Link.Connected && System.currentTimeMillis() - lastStatusMs > 4000) send("s?")
            }
        }
        if (prefs.demoOnStart.value) startDemo() else connect()
    }

    @Suppress("DEPRECATION")
    private fun Intent.usbDevice(): UsbDevice? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        else getParcelableExtra(UsbManager.EXTRA_DEVICE)

    // ------------------------------------------------------------------ connection
    fun connect() {
        if (_link.value is Link.Connected) return
        stopDemo()
        val devices = usb.deviceList.values
        val dev = devices.firstOrNull { CdcConnection.isCharger(it) }
            ?: devices.firstOrNull { d -> (0 until d.interfaceCount).any { d.getInterface(it).interfaceClass == 0x0A } }
        if (dev == null) { _link.value = Link.Disconnected; return }
        if (usb.hasPermission(dev)) openDevice(dev)
        else {
            _link.value = Link.Searching
            val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val pi = PendingIntent.getBroadcast(getApplication(), 0, Intent(permissionAction).setPackage(getApplication<Application>().packageName), flags)
            usb.requestPermission(dev, pi)
        }
    }

    fun onUsbIntent(intent: Intent?) {
        if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            val dev = intent.usbDevice() ?: return
            stopDemo()
            if (usb.hasPermission(dev)) openDevice(dev) else connect()
        }
    }

    private fun openDevice(dev: UsbDevice) {
        cdc?.close()
        val c = CdcConnection(usb, ::onLine) { err ->
            cdc = null
            if (_link.value !is Link.Demo) _link.value = if (err != null) Link.Error(err) else Link.Disconnected
            _status.value = null
        }
        val err = c.open(dev)
        if (err != null) { _link.value = Link.Error(err); return }
        cdc = c
        resetSession()
        _link.value = Link.Connected(dev.productName ?: "PD Charger")
        send("i?"); send("c?"); send("s?"); refreshLog()
    }

    fun disconnect() {
        stopDemo()
        cdc?.close()
        _link.value = Link.Disconnected
    }

    fun startDemo() {
        cdc?.close()
        demoJob?.cancel()
        val d = DemoDevice { line -> onLine(line) }
        d.phone = demoPhone.value
        demo = d
        resetSession()
        _link.value = Link.Demo
        send("i?"); send("c?"); refreshLog()
        demoJob = viewModelScope.launch {
            var last = System.nanoTime()
            while (isActive) {
                delay((_config.value?.get("rate") ?: 500).toLong().coerceAtLeast(200))
                val now = System.nanoTime()
                d.tick((now - last) / 1e9)
                last = now
            }
        }
    }

    val demoPhone = MutableStateFlow(true)
    fun setDemoScenario(phone: Boolean) {
        demoPhone.value = phone
        demo?.phone = phone
        resetSession()
    }

    fun stopDemo() {
        if (demo == null) return
        demoJob?.cancel()
        demo = null
        _status.value = null
        _config.value = null
        _info.value = null
        _link.value = Link.Disconnected
    }

    fun refreshLog() {
        synchronized(logBuf) { logBuf.clear() }
        _logLoading.value = true
        send("log?")
    }

    fun clearLog() {
        send("logclr")
        refreshLog()
    }

    private fun resetSession() {
        _history.value = emptyList()
        _session.value = Session(startMs = System.currentTimeMillis())
        lastState = ""
    }

    // ------------------------------------------------------------------ protocol
    fun send(cmd: String) { outbox.trySend(cmd.trim()) }

    /** Charge plan for the next time the module sits on a charger: mode 0 = to tgt %, 1 = storage, 2 = never. */
    fun setPlan(mode: Int, targetPct: Int? = null) {
        send("set mode $mode")
        if (targetPct != null) send("set tgt $targetPct")
        expectingSave = true
        send("save")
    }

    /** Push a batch of changed settings, then store them in flash. */
    fun applyConfig(changes: Map<String, Int>) {
        if (changes.isEmpty()) return
        changes.forEach { (k, v) -> send("set $k $v") }
        expectingSave = true
        send("save")
    }

    fun factoryDefaults() { expectingSave = true; send("defaults"); send("save") }

    private fun onLine(line: String) {
        val o = runCatching { JSONObject(line) }.getOrNull()
        if (o == null) { log(false, line); return }
        when (o.optString("t")) {
            "s" -> onStatus(Status.parse(o))
            "cfg" -> { _config.value = DevConfig.parse(o); log(false, line) }
            "i" -> { _info.value = DevInfo.parse(o); log(false, line) }
            "ok" -> {
                log(false, line)
                if (o.optString("m") == "save" && expectingSave) { expectingSave = false; _messages.tryEmit("Settings saved to the module") }
            }
            "err" -> { log(false, line); _messages.tryEmit("Module: " + o.optString("m")) }
            "log" -> {
                val e = LogEntry(o.optInt("b"), o.optInt("s"), o.optInt("c"), o.optInt("v"))
                // skip entries half-written by a power cut (erased flash reads back as 0xFF..)
                synchronized(logBuf) { if (e.boot != 0xFFFF && e.code != 0xFF) logBuf += e.copy(idx = logBuf.size) }
            }
            "logend" -> {
                logBoot = o.optInt("boot")
                _log.value = synchronized(logBuf) { logBuf.toList() }
                lastLogCount = o.optInt("n", _log.value.size)
                _logLoading.value = false
            }
            else -> log(false, line)
        }
    }

    private fun onStatus(s: Status) {
        val now = System.currentTimeMillis()
        val dt = if (lastStatusMs == 0L) 0.0 else ((now - lastStatusMs) / 1000.0).coerceAtMost(5.0)
        lastStatusMs = now
        _status.value = s
        // a new event was written on the module: fetch the log again (and buzz if it is an error)
        if (lastLogCount >= 0 && s.logCount != lastLogCount && !_logLoading.value) {
            lastLogCount = s.logCount
            refreshLog()
        }
        if (prefs.logStatus.value) log(false, "status ${s.state} ${s.packMv} mV ${s.ibatMa} mA")
        // session energy (demo runs time-accelerated, so its numbers are scaled up too)
        val scale = if (demo != null) 24.0 else 1.0
        if (s.ibatMa > 0) _session.update {
            it.copy(
                mAh = it.mAh + s.ibatMa * dt * scale / 3600.0,
                wh = it.wh + s.chargeW * dt * scale / 3600.0,
                peakTempDc = maxOf(it.peakTempDc, s.packTempDc),
            )
        } else _session.update { it.copy(peakTempDc = maxOf(it.peakTempDc, s.packTempDc)) }
        if (now - lastSampleMs >= 1000) {
            lastSampleMs = now
            val smp = Sample(now, s.cellMv, s.packMv, s.ibatMa, s.ibusMa, s.packTempDc, s.chipTempDc, s.soc)
            _history.update { (if (it.size >= MAX_SAMPLES) it.drop(it.size - MAX_SAMPLES + 1) else it) + smp }
        }
        if (s.state != lastState) {
            if (s.state == "DONE" && lastState.isNotEmpty()) { buzz(longArrayOf(0, 120, 90, 120, 90, 300)); _messages.tryEmit("Pack is fully charged") }
            if (s.state == "FAULT" && lastState.isNotEmpty()) buzz(longArrayOf(0, 400))
            lastState = s.state
        }
    }

    @SuppressLint("MissingPermission")
    private fun buzz(pattern: LongArray) {
        if (!prefs.vibrate.value) return
        val v = getApplication<Application>().getSystemService(Vibrator::class.java) ?: return
        v.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    private fun log(out: Boolean, text: String) {
        _console.update { (if (it.size > 400) it.drop(100) else it) + ConsoleLine(System.currentTimeMillis(), out, text) }
    }

    fun clearConsole() { _console.value = emptyList() }
    fun clearHistory() { _history.value = emptyList(); _session.value = Session(startMs = System.currentTimeMillis()) }

    override fun onCleared() {
        runCatching { getApplication<Application>().unregisterReceiver(receiver) }
        cdc?.close()
        demoJob?.cancel()
    }

    companion object { const val MAX_SAMPLES = 4 * 3600 }
}
