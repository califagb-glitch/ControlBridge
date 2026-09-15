package com.controlbridge.mobile.input

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.controlbridge.core.InputPacket

class GamepadInputController(private val emit: (InputPacket) -> Unit) {
    var name = "Nenhum controle"
    var connected = false
    private var lastAxes = FloatArray(6)

    fun key(event: KeyEvent): Boolean {
        val device = event.device ?: return false
        if (!isGamepad(device)) return false
        connected = true
        name = device.name ?: "Gamepad"
        emit(InputPacket("key", key = event.keyCode, down = event.action == KeyEvent.ACTION_DOWN))
        return true
    }

    fun motion(event: MotionEvent): Boolean {
        val device = event.device ?: return false
        if (!isGamepad(device) || event.actionMasked != MotionEvent.ACTION_MOVE) return false
        connected = true
        name = device.name ?: "Gamepad"

        val rx = if (device.getMotionRange(MotionEvent.AXIS_RX) != null) event.getAxisValue(MotionEvent.AXIS_RX) else event.getAxisValue(MotionEvent.AXIS_Z)
        val ry = if (device.getMotionRange(MotionEvent.AXIS_RY) != null) event.getAxisValue(MotionEvent.AXIS_RY) else event.getAxisValue(MotionEvent.AXIS_RZ)
        val values = floatArrayOf(
            event.getAxisValue(MotionEvent.AXIS_X),
            event.getAxisValue(MotionEvent.AXIS_Y),
            rx,
            ry,
            event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
            event.getAxisValue(MotionEvent.AXIS_RTRIGGER)
        )
        if (values.indices.any { kotlin.math.abs(values[it] - lastAxes[it]) > 0.01f }) {
            lastAxes = values
            emit(InputPacket("motion", lx = values[0], ly = values[1], rx = values[2], ry = values[3], lt = values[4], rt = values[5]))
        }
        return true
    }

    private fun isGamepad(device: InputDevice): Boolean =
        (device.sources and InputDevice.SOURCE_GAMEPAD) != 0 ||
            (device.sources and InputDevice.SOURCE_JOYSTICK) != 0
}
