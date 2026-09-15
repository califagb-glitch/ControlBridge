package com.controlbridge.mobile

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Toast
import com.controlbridge.core.InputPacket
import com.controlbridge.mobile.audio.UiSoundManager
import com.controlbridge.mobile.service.BridgeService
import com.controlbridge.mobile.ui.DashboardView

class MainActivity : Activity() {
    private lateinit var view: DashboardView
    private lateinit var sound: UiSoundManager

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.setFlags(1024, 1024)
        sound = UiSoundManager(this)
        view = DashboardView(this)
        setContentView(view)
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 40)
        } else {
            startBridge()
        }
        view.onAction = { id ->
            sound.click()
            when (id) {
                "start" -> startBridge()
                "hud" -> { view.page = DashboardView.Page.HUD; sound.open() }
                "settings" -> { view.page = DashboardView.Page.SETTINGS; sound.open() }
                "home" -> { view.page = DashboardView.Page.HOME; sound.close() }
                "connect" -> chooseTv()
            }
            view.invalidate()
        }
    }

    private fun startBridge() {
        if (BridgeService.instance == null) {
            val intent = Intent(this, BridgeService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        }
        window.decorView.postDelayed({ sync() }, 700)
    }

    private fun sync() {
        BridgeService.instance?.let { service ->
            view.ip = service.ip()
            view.tv = service.connected()
            service.onStatus = { _, connected ->
                runOnUiThread { view.tv = connected; view.invalidate() }
            }
            view.invalidate()
        }
    }

    private fun chooseTv() {
        val service = BridgeService.instance ?: return
        val devices = service.devices()
        if (devices.isEmpty()) {
            Toast.makeText(this, "Pareie a TV com o celular no Bluetooth primeiro.", Toast.LENGTH_LONG).show()
            return
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Conectar à TV")
            .setItems(devices.map { it.name ?: it.address }.toTypedArray()) { _, index -> service.connect(devices[index]) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val service = BridgeService.instance
        val device = event.device
        val gamepad = device != null && ((device.sources and InputDevice.SOURCE_GAMEPAD) != 0 || (device.sources and InputDevice.SOURCE_JOYSTICK) != 0)
        if (gamepad && service != null) {
            service.input(InputPacket("key", key = event.keyCode, down = event.action == KeyEvent.ACTION_DOWN))
            view.controller = true
            view.invalidate()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        val service = BridgeService.instance ?: return super.onGenericMotionEvent(event)
        val device = event.device ?: return super.onGenericMotionEvent(event)
        if ((device.sources and InputDevice.SOURCE_JOYSTICK) == 0) return super.onGenericMotionEvent(event)
        if (event.actionMasked != MotionEvent.ACTION_MOVE) return super.onGenericMotionEvent(event)

        val rx = if (device.getMotionRange(MotionEvent.AXIS_RX) != null) event.getAxisValue(MotionEvent.AXIS_RX) else event.getAxisValue(MotionEvent.AXIS_Z)
        val ry = if (device.getMotionRange(MotionEvent.AXIS_RY) != null) event.getAxisValue(MotionEvent.AXIS_RY) else event.getAxisValue(MotionEvent.AXIS_RZ)
        service.input(
            InputPacket(
                "motion",
                lx = event.getAxisValue(MotionEvent.AXIS_X),
                ly = event.getAxisValue(MotionEvent.AXIS_Y),
                rx = rx,
                ry = ry,
                lt = event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
                rt = event.getAxisValue(MotionEvent.AXIS_RTRIGGER)
            )
        )
        view.controller = true
        view.invalidate()
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grants: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grants)
        if (requestCode == 40 && grants.firstOrNull() == PackageManager.PERMISSION_GRANTED) startBridge()
        else if (requestCode == 40) Toast.makeText(this, "Bluetooth é necessário para a ponte.", Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        sound.release()
        super.onDestroy()
    }
}
