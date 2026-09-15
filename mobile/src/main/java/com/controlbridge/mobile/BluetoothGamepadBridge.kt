package com.controlbridge.mobile

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.view.KeyEvent
import java.util.concurrent.Executors

/** Makes the phone present itself to the TV as a normal Bluetooth HID gamepad. */
class BluetoothGamepadBridge(private val context: Context) {
    companion object {
        private const val REPORT_ID = 1
        private const val NAME = "ControlBridge Gamepad"

        private fun descriptor(vararg values: Int): ByteArray = values.map { it.toByte() }.toByteArray()

        private val DESCRIPTOR = descriptor(
            0x05, 0x01, 0x09, 0x05, 0xA1, 0x01, 0x85, REPORT_ID,
            // Left/right sticks: X Y Z Rz, unsigned 8-bit, 0..255.
            0x09, 0x30, 0x09, 0x31, 0x09, 0x32, 0x09, 0x35,
            0x15, 0x00, 0x26, 0xFF, 0x00, 0x75, 0x08, 0x95, 0x04, 0x81, 0x02,
            // Triggers: Brake and Accelerator.
            0x05, 0x02, 0x09, 0xC5, 0x09, 0xC4,
            0x15, 0x00, 0x26, 0xFF, 0x00, 0x75, 0x08, 0x95, 0x02, 0x81, 0x02,
            // Eight-way hat switch plus padding.
            0x05, 0x01, 0x09, 0x39, 0x15, 0x00, 0x25, 0x07,
            0x35, 0x00, 0x46, 0x3B, 0x01, 0x65, 0x14, 0x75, 0x04, 0x95, 0x01, 0x81, 0x42,
            0x65, 0x00, 0x75, 0x04, 0x95, 0x01, 0x81, 0x03,
            // 16 digital buttons.
            0x05, 0x09, 0x19, 0x01, 0x29, 0x10, 0x15, 0x00, 0x25, 0x01,
            0x75, 0x01, 0x95, 0x10, 0x81, 0x02, 0xC0
        )

        private const val A = 1 shl 0
        private const val B = 1 shl 1
        private const val X = 1 shl 2
        private const val Y = 1 shl 3
        private const val LB = 1 shl 4
        private const val RB = 1 shl 5
        private const val LT = 1 shl 6
        private const val RT = 1 shl 7
        private const val SELECT = 1 shl 8
        private const val START = 1 shl 9
        private const val L3 = 1 shl 10
        private const val R3 = 1 shl 11
    }

    var onStatus: ((String, Boolean) -> Unit)? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter
    private var hid: BluetoothHidDevice? = null
    private var host: BluetoothDevice? = null
    private var registered = false
    private var buttons = 0
    private var lx = 128; private var ly = 128; private var rx = 128; private var ry = 128
    private var lt = 0; private var rt = 0; private var hat = 8
    private val dpad = BooleanArray(4)

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, isRegistered: Boolean) {
            registered = isRegistered
            if (!isRegistered) {
                host = null
                onStatus?.invoke("HID indisponível", false)
            } else {
                host = pluggedDevice
                onStatus?.invoke(if (pluggedDevice == null) "HID pronto — pareie a TV" else "TV conectada", pluggedDevice != null)
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    host = device; onStatus?.invoke("TV conectada", true); sendReport()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (host == device) host = null
                    onStatus?.invoke("TV desconectada", false)
                }
            }
        }

        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            if (type.toInt() == BluetoothHidDevice.REPORT_TYPE_INPUT.toInt()) hid?.replyReport(device, type, id, buildReport())
        }
        override fun onSetProtocol(device: BluetoothDevice, protocol: Byte) = Unit
        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) = Unit
        override fun onVirtualCableUnplug(device: BluetoothDevice) {
            if (host == device) host = null
            onStatus?.invoke("TV desconectada", false)
        }
    }

    fun start() {
        if (Build.VERSION.SDK_INT < 28 || !hasPermission()) { onStatus?.invoke("Permissão Bluetooth necessária", false); return }
        if (adapter == null || !adapter.isEnabled) { onStatus?.invoke("Ative o Bluetooth", false); return }
        adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile != BluetoothProfile.HID_DEVICE) return
                hid = proxy as BluetoothHidDevice
                val sdp = BluetoothHidDeviceAppSdpSettings(NAME, "Controle Bluetooth para Android TV", "ControlBridge", 0x08, DESCRIPTOR)
                hid?.registerApp(sdp, null, null, executor, callback)
            }
            override fun onServiceDisconnected(profile: Int) {
                if (profile == BluetoothProfile.HID_DEVICE) { hid = null; host = null; onStatus?.invoke("Bluetooth HID encerrado", false) }
            }
        }, BluetoothProfile.HID_DEVICE)
    }

    fun stop() {
        try { if (hasPermission()) hid?.unregisterApp() } catch (_: Exception) { }
        hid = null; host = null; executor.shutdownNow()
    }

    fun bondedDevices(): List<BluetoothDevice> = if (!hasPermission()) emptyList() else try { adapter?.bondedDevices?.toList().orEmpty() } catch (_: SecurityException) { emptyList() }

    fun connect(device: BluetoothDevice): Boolean {
        if (!hasPermission() || !registered) return false
        host = device
        onStatus?.invoke("Conectando à ${device.name ?: "TV"}…", false)
        return try { hid?.connect(device) == true } catch (_: Exception) { false }
    }

    fun updateAxis(leftX: Float, leftY: Float, rightX: Float, rightY: Float) {
        lx = axis(leftX); ly = axis(leftY); rx = axis(rightX); ry = axis(rightY); sendReport()
    }

    fun updateTriggers(left: Float, right: Float) {
        lt = trigger(left); rt = trigger(right)
        buttons = if (lt >= 32) buttons or LT else buttons and LT.inv()
        buttons = if (rt >= 32) buttons or RT else buttons and RT.inv()
        sendReport()
    }

    fun setButton(key: Int, down: Boolean) {
        val mask = when (key) {
            KeyEvent.KEYCODE_BUTTON_A -> A; KeyEvent.KEYCODE_BUTTON_B -> B
            KeyEvent.KEYCODE_BUTTON_X -> X; KeyEvent.KEYCODE_BUTTON_Y -> Y
            KeyEvent.KEYCODE_BUTTON_L1 -> LB; KeyEvent.KEYCODE_BUTTON_R1 -> RB
            KeyEvent.KEYCODE_BUTTON_L2 -> LT; KeyEvent.KEYCODE_BUTTON_R2 -> RT
            KeyEvent.KEYCODE_BUTTON_SELECT -> SELECT; KeyEvent.KEYCODE_BUTTON_START -> START
            KeyEvent.KEYCODE_BUTTON_THUMBL -> L3; KeyEvent.KEYCODE_BUTTON_THUMBR -> R3
            else -> 0
        }
        if (mask != 0) { buttons = if (down) buttons or mask else buttons and mask.inv(); sendReport() }
    }

    fun setDpad(direction: Int, down: Boolean) {
        when (direction) {
            KeyEvent.KEYCODE_DPAD_UP -> dpad[0] = down
            KeyEvent.KEYCODE_DPAD_RIGHT -> dpad[1] = down
            KeyEvent.KEYCODE_DPAD_DOWN -> dpad[2] = down
            KeyEvent.KEYCODE_DPAD_LEFT -> dpad[3] = down
        }
        hat = when {
            dpad[0] && dpad[1] -> 1; dpad[1] && dpad[2] -> 3; dpad[2] && dpad[3] -> 5; dpad[3] && dpad[0] -> 7
            dpad[0] -> 0; dpad[1] -> 2; dpad[2] -> 4; dpad[3] -> 6; else -> 8
        }
        sendReport()
    }

    private fun sendReport() {
        val h = hid ?: return; val device = host ?: return
        if (!registered) return
        try { h.sendReport(device, REPORT_ID, buildReport()) } catch (_: Exception) { }
    }

    private fun buildReport() = byteArrayOf(
        lx.toByte(), ly.toByte(), rx.toByte(), ry.toByte(), lt.toByte(), rt.toByte(), hat.toByte(),
        (buttons and 0xFF).toByte(), ((buttons ushr 8) and 0xFF).toByte()
    )

    private fun axis(v: Float) = ((v.coerceIn(-1f, 1f) + 1f) * 127.5f).toInt().coerceIn(0, 255)
    private fun trigger(v: Float) = (v.coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
    private fun hasPermission() = Build.VERSION.SDK_INT < 31 || context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
}
