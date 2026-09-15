package com.controlbridge.mobile

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Build
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.Locale

class ControllerAccessibilityService : AccessibilityService() {
    private var socket: DatagramSocket? = null
    private var tvIp: String = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        tvIp = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_TV_IP, "") ?: ""
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        if (Build.VERSION.SDK_INT >= 34) {
            info.setMotionEventSources(InputDevice.SOURCE_JOYSTICK or InputDevice.SOURCE_GAMEPAD)
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent) = Unit

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!isControllerEvent(event)) return false
        send("KEY|${event.action}|${event.keyCode}|${event.repeatCount}|${event.deviceId}")
        return false
    }

    override fun onMotionEvent(event: MotionEvent) {
        if (Build.VERSION.SDK_INT < 34) return
        val source = event.source
        if ((source and InputDevice.SOURCE_JOYSTICK) != 0 || (source and InputDevice.SOURCE_GAMEPAD) != 0) {
            val payload = String.format(Locale.US, "JOY|%.3f|%.3f|%.3f|%.3f|%.3f|%.3f|%d",
                axis(event, MotionEvent.AXIS_X), axis(event, MotionEvent.AXIS_Y),
                axis(event, MotionEvent.AXIS_Z), axis(event, MotionEvent.AXIS_RZ),
                axis(event, MotionEvent.AXIS_LTRIGGER), axis(event, MotionEvent.AXIS_RTRIGGER), event.deviceId)
            send(payload)
        }
    }

    private fun axis(event: MotionEvent, axis: Int): Float = try { event.getAxisValue(axis) } catch (_: Exception) { 0f }

    private fun isControllerEvent(event: KeyEvent): Boolean {
        val source = event.source
        return (source and InputDevice.SOURCE_GAMEPAD) != 0 ||
            (source and InputDevice.SOURCE_JOYSTICK) != 0 ||
            event.device?.name?.contains("controller", true) == true ||
            event.device?.name?.contains("gamepad", true) == true
    }

    private fun send(message: String) {
        if (tvIp.isBlank()) return
        Thread {
            try {
                if (socket == null || socket?.isClosed == true) socket = DatagramSocket()
                val data = message.toByteArray(Charsets.UTF_8)
                socket?.send(DatagramPacket(data, data.size, InetAddress.getByName(tvIp), PORT))
            } catch (_: Exception) { }
        }.start()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        socket?.close()
        super.onDestroy()
    }

    companion object {
        const val PORT = 47600
        const val PREFS = "controlbridge"
        const val KEY_TV_IP = "tv_ip"
    }
}
