package com.controlbridge.mobile

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Build
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent

/** Captures the physical controller globally and forwards it to the phone's HID device. */
class ControllerAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
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
        HidGamepadServiceHolder.service?.sendKey(event)
        return false
    }

    override fun onMotionEvent(event: MotionEvent) {
        if (Build.VERSION.SDK_INT < 34) return
        val source = event.source
        if ((source and InputDevice.SOURCE_JOYSTICK) != 0 || (source and InputDevice.SOURCE_GAMEPAD) != 0) {
            HidGamepadServiceHolder.service?.sendMotion(event)
        }
    }

    private fun isControllerEvent(event: KeyEvent): Boolean {
        val source = event.source
        return (source and InputDevice.SOURCE_GAMEPAD) != 0 ||
            (source and InputDevice.SOURCE_JOYSTICK) != 0 ||
            event.device?.name?.contains("controller", true) == true ||
            event.device?.name?.contains("gamepad", true) == true
    }

    override fun onInterrupt() = Unit
}

/** Process-local bridge between the accessibility input capture and HID service. */
object HidGamepadServiceHolder {
    @Volatile
    var service: HidGamepadService? = null
}
