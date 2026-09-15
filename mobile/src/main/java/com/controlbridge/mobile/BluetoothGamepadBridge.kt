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
import java.util.concurrent.Executors

/**
 * Turns the phone into a real Bluetooth Classic HID gamepad.
 * The TV sees a normal gamepad instead of an app-specific network stream.
 */
class BluetoothGamepadBridge(private val context: Context) {
    companion object {
        private const val REPORT_ID = 1
        private const val REPORT_SIZE = 9
        private const val NAME = "ControlBridge Gamepad"

        private val DESCRIPTOR = byteArrayOf(
            0x05, 0x01,             // Generic Desktop
            0x09, 0x05,             // Game Pad
            0xA1.toByte(), 0x01,    // Application
            0x85.toByte(), REPORT_ID.toByte(),

            // LX, LY, RX, RY
            0x09, 0x30, 0x09, 0x31, 0x09, 0x32, 0x09, 0x35,
            0x15, 0x00, 0x26, 0xFF.toByte(), 0x00,
            0x75, 0x08, 0x95, 0x04, 0x81.toByte(), 0x02,

            // LT, RT
            0x05, 0x02, 0x09, 0xC5.toByte(), 0x09, 0xC4.toByte(),
            0x15, 0x00, 0x26, 0xFF.toByte(), 0x00,
            0x75, 0x08, 0x95, 0x02, 0x81.toByte(), 0x02,

            // D-pad hat
            0x05, 0x01, 0x09, 0x39, 0x15, 0x00, 0x25, 0x07,
            0x35, 0x00, 0x46, 0x3B, 0x01, 0x65, 0x14,
            0x75, 0x04, 0x95, 0x01, 0x81.toByte(), 0x42,
            0x65, 0x00, 0x75, 0x04, 0x95, 0x01, 0x81.toByte(), 0x03,

            // 16 buttons
            0x05, 0x09, 0x19, 0x01, 0x29, 0x10,
            0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95, 0x10,
            0x81.toByte(), 0x02,
            0xC0.toByte()
        )

        private const val A = 1 shl 0
        private const val B = 1 shl 1
        private const val X = 1 shl 2
        private const val Y = 1 shl 3
        private const val LB = 1 shl 4
        private const val RB = 1 shl 5
        private const val LT_BUTTON = 1 shl 6
        private const val RT_BUTTON = 1 shl 7
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
    private var lx = 128
    private var ly = 128
    private var rx = 128
    private var ry = 128
    private var lt = 0
    private var rt = 0
    private var hat = 8
    private val dpad = BooleanArray(4)

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, isRegistered: Boolean) {
            registered = isRegistered
            if (!isRegistered) {
                host = null
                onStatus?.invoke("HID indisponível", false)
            } else {
                host = pluggedDevice
                onStatus?.invoke("HID pronto — pareie a TV", pluggedDevice != null)
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            if (state == BluetoothProfile.STATE_CONNECTED) {
                host = device
                onStatus?.invoke("TV conectada", true)
                sendReport()
            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                if (host == device) host = null
                onStatus?.invoke("TV desconectada", false)
            }
        }

        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            if (type.toInt() == BluetoothHidDevice.REPORT_TYPE_INPUT.toInt()) {
                hid?.replyReport(device, type, id, buildReport())
            }
        }

        override fun onSetProtocol(device: BluetoothDevice, protocol: Byte) = Unit
        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) = Unit
        override fun onVirtualCableUnplug(device: BluetoothDevice) {
            if (host == device) host = null
            onStatus?.invoke("TV desconectada", false)
        }
    }

    fun start() {
        if (Build.VERSION.SDK_INT < 28 || !hasConnectPermission()) {
            onStatus?.invoke("Permissão Bluetooth necessária", false)
            return
        }
        if (adapter == null || !adapter.isEnabled) {
            onStatus?.invoke("Ative o Bluetooth", false)
            return
        }
        adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile != BluetoothProfile.HID_DEVICE) return
                hid = proxy as BluetoothHidDevice
                val sdp = BluetoothHidDeviceAppSdpSettings(
                    NAME,
                    "Controle Bluetooth para Android TV",
                    "ControlBridge",
                    0x08,
                    DESCRIPTOR
                )
                hid?.registerApp(sdp, null, null, executor, callback)
            }

            override fun onServiceDisconnected(profile: Int) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    hid = null
                    host = null
                    onStatus?.invoke("Bluetooth HID encerrado", false)
                }
            }
        }, BluetoothProfile.HID_DEVICE)
    }

    fun stop() {
        try {
            if (hasConnectPermission()) hid?.unregisterApp()
        } catch (_: Exception) { }
        hid = null
        host = null
        executor.shutdownNow()
    }

    fun bondedDevices(): List<BluetoothDevice> {
        if (!hasConnectPermission()) return emptyList()
        return try { adapter?.bondedDevices?.toList().orEmpty() } catch (_: SecurityException) { emptyList() }
    }

    fun connect(device: BluetoothDevice): Boolean {
        if (!hasConnectPermission() || !registered) return false
        host = device
        onStatus?.invoke("Conectando à ${device.name ?: "TV"}…", false)
        return try { hid?.connect(device) == true } catch (_: Exception) { false }
    }

    fun updateAxis(leftX: Float, leftY: Float, rightX: Float, rightY: Float) {
        lx = axisByte(leftX)
        ly = axisByte(leftY)
        rx = axisByte(rightX)
        ry = axisByte(rightY)
        sendReport()
    }

    fun updateTriggers(left: Float, right: Float) {
        lt = triggerByte(left)
        rt = triggerByte(right)
        if (lt >= 32) buttons = buttons or LT_BUTTON else buttons = buttons and LT_BUTTON.inv()
        if (rt >= 32) buttons = buttons or RT_BUTTON else buttons = buttons and RT_BUTTON.inv()
        sendReport()
    }

    fun setButton(button: Int, down: Boolean) {
        val mask = when (button) {
            android.view.KeyEvent.KEYCODE_BUTTON_A -> A
            android.view.KeyEvent.KEYCODE_BUTTON_B -> B
            android.view.KeyEvent.KEYCODE_BUTTON_X -> X
            android.view.KeyEvent.KEYCODE_BUTTON_Y -> Y
            android.view.KeyEvent.KEYCODE_BUTTON_L1 -> LB
            android.view.KeyEvent.KEYCODE_BUTTON_R1 -> RB
            android.view.KeyEvent.KEYCODE_BUTTON_L2 -> LT_BUTTON
            android.view.KeyEvent.KEYCODE_BUTTON_R2 -> RT_BUTTON
            android.view.KeyEvent.KEYCODE_BUTTON_SELECT -> SELECT
            android.view.KeyEvent.KEYCODE_BUTTON_START -> START
            android.view.KeyEvent.KEYCODE_BUTTON_THUMBL -> L3
            android.view.KeyEvent.KEYCODE_BUTTON_THUMBR -> R3
            else -> 0
        }
        if (mask != 0) {
            buttons = if (down) buttons or mask else buttons and mask.inv()
            sendReport()
        }
    }

    fun setDpad(direction: Int, down: Boolean) {
        when (direction) {
            android.view.KeyEvent.KEYCODE_DPAD_UP -> dpad[0] = down
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> dpad[1] = down
            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> dpad[2] = down
            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> dpad[3] = down
        }
        hat = when {
            dpad[0] && dpad[1] -> 1
            dpad[1] && dpad[2] -> 3
            dpad[2] && dpad[3] -> 5
            dpad[3] && dpad[0] -> 7
            dpad[0] -> 0
            dpad[1] -> 2
            dpad[2] -> 4
            dpad[3] -> 6
            else -> 8
        }
        sendReport()
    }

    private fun sendReport() {
        val h = hid ?: return
        val device = host ?: return
        if (!registered) return
        try { h.sendReport(device, REPORT_ID, buildReport()) } catch (_: Exception) { }
    }

    private fun buildReport(): ByteArray = byteArrayOf(
        lx.toByte(), ly.toByte(), rx.toByte(), ry.toByte(),
        lt.toByte(), rt.toByte(), hat.toByte(),
        (buttons and 0xFF).toByte(), ((buttons ushr 8) and 0xFF).toByte()
    )

    private fun axisByte(value: Float): Int = ((value.coerceIn(-1f, 1f) + 1f) * 127.5f).toInt().coerceIn(0, 255)
    private fun triggerByte(value: Float): Int = (value.coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)

    private fun hasConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < 31 || context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
}
