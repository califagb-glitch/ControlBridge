package com.controlbridge.mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.core.app.NotificationCompat
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/** Real Bluetooth Classic HID gamepad: the TV sees the phone as a controller. */
class HidGamepadService : Service() {
    private var hid: BluetoothHidDevice? = null
    private var registered = false
    private var host: BluetoothDevice? = null
    private var buttons = 0
    private var hat = 8
    private var lx = 0
    private var ly = 0
    private var rx = 0
    private var ry = 0
    private var lt = 0
    private var rt = 0
    private var up = false
    private var right = false
    private var down = false
    private var left = false

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, isRegistered: Boolean) {
            registered = isRegistered
            if (isRegistered && pluggedDevice != null) {
                host = pluggedDevice
                saveHost(pluggedDevice)
            }
            notifyState()
            if (isRegistered) connectSavedHost()
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            if (state == BluetoothProfile.STATE_CONNECTED) {
                host = device
                saveHost(device)
                sendReport()
            } else if (host?.address == device.address) {
                host = null
            }
            notifyState()
        }
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            hid = proxy as BluetoothHidDevice
            registerHid()
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hid = null
                registered = false
                notifyState()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        HidGamepadServiceHolder.service = this
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Inicializando Bluetooth HID…"))
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (Build.VERSION.SDK_INT < 28 || adapter == null) {
            notifyState("Este celular não oferece Bluetooth HID Device")
            return
        }
        try {
            adapter.getProfileProxy(this, profileListener, BluetoothProfile.HID_DEVICE)
        } catch (_: Exception) {
            notifyState("Bluetooth HID Device não é suportado neste celular")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> intent.getStringExtra(EXTRA_ADDRESS)?.let { connectTo(it) }
            ACTION_DISCONNECT -> disconnectHost()
        }
        return START_STICKY
    }

    private fun registerHid() {
        val device = hid ?: return
        try { device.unregisterApp() } catch (_: Exception) { }

        val subclass = (BluetoothHidDevice.SUBCLASS1_NONE.toInt() or BluetoothHidDevice.SUBCLASS2_GAMEPAD.toInt()).toByte()
        val sdp = BluetoothHidDeviceAppSdpSettings(
            DEVICE_NAME,
            "ControlBridge Gamepad",
            "ControlBridge",
            subclass,
            REPORT_DESCRIPTOR
        )
        val ok = try {
            device.registerApp(sdp, null, null, Executors.newSingleThreadExecutor(), callback)
        } catch (_: SecurityException) {
            notifyState("Permissão Bluetooth bloqueada")
            false
        } catch (e: Exception) {
            notifyState("Falha ao registrar HID: ${e.message ?: "erro desconhecido"}")
            false
        }
        if (!ok) notifyState("HID não foi registrado neste celular")
    }

    private fun connectSavedHost() {
        val address = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_HOST, null) ?: return
        connectTo(address)
    }

    private fun connectTo(address: String): Boolean {
        if (!registered) {
            notifyState("HID ainda não está registrado")
            return false
        }
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return false
        return try {
            val device = adapter.getRemoteDevice(address)
            host = device
            saveHost(device)
            hid?.connect(device) == true
        } catch (_: Exception) {
            notifyState("Não foi possível conectar à TV")
            false
        }
    }

    private fun disconnectHost() {
        host?.let { try { hid?.disconnect(it) } catch (_: Exception) {} }
        host = null
        notifyState()
    }

    private fun saveHost(device: BluetoothDevice) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_HOST, device.address).apply()
    }

    fun sendKey(event: KeyEvent) {
        val bit = buttonBit(event.keyCode)
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> dpad(0, event.action == KeyEvent.ACTION_DOWN)
            KeyEvent.KEYCODE_DPAD_RIGHT -> dpad(1, event.action == KeyEvent.ACTION_DOWN)
            KeyEvent.KEYCODE_DPAD_DOWN -> dpad(2, event.action == KeyEvent.ACTION_DOWN)
            KeyEvent.KEYCODE_DPAD_LEFT -> dpad(3, event.action == KeyEvent.ACTION_DOWN)
            else -> if (bit >= 0) {
                if (event.action == KeyEvent.ACTION_DOWN) buttons = buttons or (1 shl bit)
                if (event.action == KeyEvent.ACTION_UP) buttons = buttons and (1 shl bit).inv()
            }
        }
        sendReport()
    }

    fun sendMotion(event: MotionEvent) {
        lx = stick(event, MotionEvent.AXIS_X)
        ly = stick(event, MotionEvent.AXIS_Y)
        rx = stick(event, MotionEvent.AXIS_Z)
        ry = stick(event, MotionEvent.AXIS_RZ)
        lt = trigger(event, MotionEvent.AXIS_LTRIGGER)
        rt = trigger(event, MotionEvent.AXIS_RTRIGGER)
        sendReport()
    }

    private fun stick(event: MotionEvent, axis: Int): Int =
        (event.getAxisValue(axis).coerceIn(-1f, 1f) * 32767f).roundToInt()

    private fun trigger(event: MotionEvent, axis: Int): Int =
        (event.getAxisValue(axis).coerceIn(0f, 1f) * 255f).roundToInt()

    private fun dpad(direction: Int, pressed: Boolean) {
        when (direction) {
            0 -> up = pressed
            1 -> right = pressed
            2 -> down = pressed
            3 -> left = pressed
        }
        hat = when {
            up && right -> 1
            right && down -> 3
            down && left -> 5
            left && up -> 7
            up -> 0
            right -> 2
            down -> 4
            left -> 6
            else -> 8
        }
    }

    private fun buttonBit(code: Int): Int = when (code) {
        KeyEvent.KEYCODE_BUTTON_A -> 0
        KeyEvent.KEYCODE_BUTTON_B -> 1
        KeyEvent.KEYCODE_BUTTON_X -> 2
        KeyEvent.KEYCODE_BUTTON_Y -> 3
        KeyEvent.KEYCODE_BUTTON_L1 -> 4
        KeyEvent.KEYCODE_BUTTON_R1 -> 5
        KeyEvent.KEYCODE_BUTTON_L2 -> 6
        KeyEvent.KEYCODE_BUTTON_R2 -> 7
        KeyEvent.KEYCODE_BUTTON_SELECT -> 8
        KeyEvent.KEYCODE_BUTTON_START -> 9
        KeyEvent.KEYCODE_BUTTON_MODE -> 10
        KeyEvent.KEYCODE_BUTTON_THUMBL -> 11
        KeyEvent.KEYCODE_BUTTON_THUMBR -> 12
        in KeyEvent.KEYCODE_BUTTON_1..KeyEvent.KEYCODE_BUTTON_16 -> 13 + (code - KeyEvent.KEYCODE_BUTTON_1)
        else -> -1
    }

    @Synchronized
    private fun sendReport() {
        val device = hid ?: return
        val target = host ?: return
        if (!registered) return
        val data = ByteArray(13)
        putShort(data, 0, lx)
        putShort(data, 2, ly)
        data[4] = lt.toByte()
        data[5] = rt.toByte()
        putShort(data, 6, rx)
        putShort(data, 8, ry)
        data[10] = (buttons and 0xFF).toByte()
        data[11] = ((buttons ushr 8) and 0xFF).toByte()
        data[12] = hat.toByte()
        try { device.sendReport(target, REPORT_ID, data) } catch (_: Exception) { }
    }

    private fun putShort(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value and 0xFF).toByte()
        data[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }

    private fun notifyState(message: String? = null) {
        val text = message ?: when {
            hid == null -> "Bluetooth HID indisponível"
            !registered -> "Pronto para registrar"
            host != null -> "TV: ${host?.name ?: "conectando"}"
            else -> "HID ativo • pronto para parear"
        }
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(DEVICE_NAME)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "ControlBridge", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onDestroy() {
        HidGamepadServiceHolder.service = null
        try { hid?.unregisterApp() } catch (_: Exception) { }
        hid?.let { try { BluetoothAdapter.getDefaultAdapter()?.closeProfileProxy(BluetoothProfile.HID_DEVICE, it) } catch (_: Exception) {} }
        hid = null
        registered = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val DEVICE_NAME = "ControlBridge Gamepad"
        const val PREFS = "controlbridge"
        const val KEY_HOST = "hid_host"
        const val REPORT_ID = 1
        const val CHANNEL_ID = "controlbridge_hid"
        const val NOTIFICATION_ID = 47600
        const val ACTION_CONNECT = "com.controlbridge.mobile.CONNECT_TV"
        const val ACTION_DISCONNECT = "com.controlbridge.mobile.DISCONNECT_TV"
        const val EXTRA_ADDRESS = "address"

        // 13-byte report: LX, LY, LT, RT, RX, RY, 16 buttons, hat switch.
        val REPORT_DESCRIPTOR = byteArrayOf(
            0x05, 0x01, 0x09, 0x05, 0xA1.toByte(), 0x01,
            0x85.toByte(), REPORT_ID.toByte(),
            0x09, 0x30, 0x09, 0x31, 0x16, 0x00, 0x80.toByte(), 0x26, 0xFF.toByte(), 0x7F,
            0x75, 0x10, 0x95, 0x02, 0x81.toByte(), 0x02,
            0x05, 0x02, 0x09, 0xC4.toByte(), 0x09, 0xC5.toByte(),
            0x15, 0x00, 0x26, 0xFF.toByte(), 0x00, 0x75, 0x08, 0x95, 0x02, 0x81.toByte(), 0x02,
            0x05, 0x01, 0x09, 0x33, 0x09, 0x34, 0x16, 0x00, 0x80.toByte(), 0x26, 0xFF.toByte(), 0x7F,
            0x75, 0x10, 0x95, 0x02, 0x81.toByte(), 0x02,
            0x05, 0x09, 0x19, 0x01, 0x29, 0x10, 0x15, 0x00, 0x25, 0x01,
            0x75, 0x01, 0x95.toByte(), 0x10, 0x81.toByte(), 0x02,
            0x05, 0x01, 0x09, 0x39, 0x15, 0x00, 0x25, 0x08, 0x35, 0x00, 0x46, 0x3B, 0x01,
            0x75, 0x04, 0x95.toByte(), 0x01, 0x81.toByte(), 0x42,
            0x75, 0x04, 0x95.toByte(), 0x01, 0x81.toByte(), 0x03,
            0xC0.toByte()
        )
    }
}
