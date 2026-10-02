package com.airsoftmodule.charger.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import kotlin.concurrent.thread

/**
 * Minimal USB CDC-ACM (virtual serial port) driver on top of the Android USB host API.
 * The charger firmware only sends status lines while DTR is set, so we always raise DTR.
 */
class CdcConnection(
    private val manager: UsbManager,
    private val onLine: (String) -> Unit,
    private val onClosed: (error: String?) -> Unit,
) {
    private var conn: UsbDeviceConnection? = null
    private var epIn: UsbEndpoint? = null
    private var epOut: UsbEndpoint? = null
    private val claimed = mutableListOf<UsbInterface>()
    @Volatile private var running = false

    fun open(device: UsbDevice): String? {
        var comm: UsbInterface? = null
        var data: UsbInterface? = null
        for (i in 0 until device.interfaceCount) {
            val itf = device.getInterface(i)
            when (itf.interfaceClass) {
                UsbConstants.USB_CLASS_COMM -> if (comm == null) comm = itf
                UsbConstants.USB_CLASS_CDC_DATA -> if (data == null) data = itf
            }
        }
        if (data == null) return "No serial interface on this USB device"
        val c = manager.openDevice(device) ?: return "USB permission missing"
        for (itf in listOfNotNull(comm, data)) {
            if (!c.claimInterface(itf, true)) { c.close(); return "Could not claim USB interface" }
            claimed += itf
        }
        for (i in 0 until data.endpointCount) {
            val ep = data.getEndpoint(i)
            if (ep.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
            if (ep.direction == UsbConstants.USB_DIR_IN) epIn = ep else epOut = ep
        }
        if (epIn == null || epOut == null) { c.close(); return "Serial endpoints not found" }
        val commIdx = comm?.id ?: 0
        // SET_LINE_CODING 115200 8N1 (ignored by the firmware but expected by the class), then DTR|RTS
        val coding = byteArrayOf(0x00, 0xC2.toByte(), 0x01, 0x00, 0, 0, 8)
        c.controlTransfer(0x21, 0x20, 0, commIdx, coding, coding.size, 500)
        c.controlTransfer(0x21, 0x22, 0x03, commIdx, null, 0, 500)
        conn = c
        running = true
        thread(name = "cdc-rx", isDaemon = true) { readLoop(c) }
        return null
    }

    private fun readLoop(c: UsbDeviceConnection) {
        val buf = ByteArray(64)
        val line = StringBuilder()
        var failures = 0
        while (running) {
            val n = c.bulkTransfer(epIn, buf, buf.size, 250)
            if (n > 0) {
                failures = 0
                for (i in 0 until n) {
                    val ch = buf[i].toInt().toChar()
                    when (ch) {
                        '\n' -> { if (line.isNotEmpty()) onLine(line.toString()); line.setLength(0) }
                        '\r' -> {}
                        else -> if (line.length < 1024) line.append(ch)
                    }
                }
            } else if (n < 0) {
                // a timeout also returns -1; only treat many in a row (with the device gone) as fatal
                if (++failures > 40 && !running) break
            }
        }
    }

    @Synchronized
    fun write(text: String): Boolean {
        val c = conn ?: return false
        val bytes = text.toByteArray()
        var off = 0
        while (off < bytes.size) {
            val len = minOf(64, bytes.size - off)
            val n = c.bulkTransfer(epOut, bytes, off, len, 500)
            if (n <= 0) return false
            off += n
        }
        return true
    }

    fun close(error: String? = null) {
        if (!running && conn == null) return
        running = false
        conn?.let { c ->
            claimed.forEach { runCatching { c.releaseInterface(it) } }
            runCatching { c.close() }
        }
        claimed.clear()
        conn = null
        onClosed(error)
    }

    companion object {
        const val VID = 0x1209
        const val PID = 0x0001
        fun isCharger(d: UsbDevice) = d.vendorId == VID && d.productId == PID
    }
}
