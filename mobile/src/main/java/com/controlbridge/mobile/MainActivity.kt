package com.controlbridge.mobile

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import kotlin.math.hypot

class MainActivity : Activity() {
    private lateinit var hud: ControllerHud
    private val hid by lazy { BluetoothGamepadBridge(this) }
    private val bridge = BridgeServer()
    private var controllerName = "Nenhum controle"
    private var physicalConnected = false
    private var tvConnected = false
    private var lastInputAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        hud = ControllerHud()
        setContentView(hud)
        bridge.start()

        hid.onStatus = { text, connected ->
            runOnUiThread {
                tvConnected = connected
                hud.tvStatus = text
                hud.invalidate()
            }
        }
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 40)
        } else {
            hid.start()
        }

        hud.postDelayed({ refreshController() }, 300)
        hud.post(object : Runnable {
            override fun run() {
                hud.invalidate()
                hud.postDelayed(this, 32)
            }
        })
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 40 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) hid.start()
        else Toast.makeText(this, "Bluetooth é necessário para conectar a TV como gamepad.", Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        hid.stop()
        bridge.stop()
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!isGamepad(event.device)) return super.dispatchKeyEvent(event)
        physicalConnected = true
        controllerName = event.device?.name ?: "Gamepad"
        lastInputAt = SystemClock.elapsedRealtime()
        val down = event.action == KeyEvent.ACTION_DOWN
        if (isDpad(event.keyCode)) hid.setDpad(event.keyCode, down) else hid.setButton(event.keyCode, down)
        bridge.broadcast("{\"type\":\"key\",\"key\":${event.keyCode},\"action\":${event.action},\"connected\":true}")
        hud.setPressed(event.keyCode, down)
        return true
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (!isGamepad(event.device) || event.action != MotionEvent.ACTION_MOVE) return super.onGenericMotionEvent(event)
        physicalConnected = true
        controllerName = event.device?.name ?: "Gamepad"
        lastInputAt = SystemClock.elapsedRealtime()
        val lx = event.getAxisValue(MotionEvent.AXIS_X).coerceIn(-1f, 1f)
        val ly = event.getAxisValue(MotionEvent.AXIS_Y).coerceIn(-1f, 1f)
        val rx = event.getAxisValue(MotionEvent.AXIS_Z).coerceIn(-1f, 1f)
        val ry = event.getAxisValue(MotionEvent.AXIS_RZ).coerceIn(-1f, 1f)
        val lt = event.getAxisValue(MotionEvent.AXIS_LTRIGGER).coerceIn(0f, 1f)
        val rt = event.getAxisValue(MotionEvent.AXIS_RTRIGGER).coerceIn(0f, 1f)
        hid.updateAxis(lx, ly, rx, ry)
        hid.updateTriggers(lt, rt)
        bridge.broadcast("{\"type\":\"motion\",\"lx\":$lx,\"ly\":$ly,\"rx\":$rx,\"ry\":$ry,\"lt\":$lt,\"rt\":$rt,\"connected\":true}")
        hud.setAxes(lx, ly, rx, ry)
        return true
    }

    private fun refreshController() {
        physicalConnected = false
        for (id in InputDevice.getDeviceIds()) {
            val device = InputDevice.getDevice(id)
            if (isGamepad(device)) {
                physicalConnected = true
                controllerName = device?.name ?: "Gamepad"
                break
            }
        }
        hud.invalidate()
    }

    private fun isGamepad(device: InputDevice?): Boolean = device != null &&
        ((device.sources and InputDevice.SOURCE_GAMEPAD) != 0 || (device.sources and InputDevice.SOURCE_JOYSTICK) != 0)

    private fun isDpad(key: Int): Boolean = key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_RIGHT ||
        key == KeyEvent.KEYCODE_DPAD_DOWN || key == KeyEvent.KEYCODE_DPAD_LEFT

    private fun openBluetoothSettings() {
        startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    }

    private fun chooseTv() {
        val devices = hid.bondedDevices()
        if (devices.isEmpty()) {
            Toast.makeText(this, "Primeiro pareie o celular com a TV pelo Bluetooth.", Toast.LENGTH_LONG).show()
            openBluetoothSettings()
            return
        }
        val names = devices.map { it.name ?: it.address }.toTypedArray()
        android.app.AlertDialog.Builder(this)
            .setTitle("Escolher TV / host")
            .setItems(names) { _, which -> hid.connect(devices[which]) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private inner class ControllerHud : View(this@MainActivity) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private var lx = 0f
        private var ly = 0f
        private var rx = 0f
        private var ry = 0f
        private val pressed = HashSet<Int>()
        private val pointers = HashMap<Int, Int>()
        private var leftPointer = -1
        private var rightPointer = -1
        private var tvStatus = "Preparando Bluetooth HID…"

        init { isFocusable = true; setBackgroundColor(Color.rgb(5, 7, 12)) }

        fun setAxes(a: Float, b: Float, c: Float, d: Float) { lx = a; ly = b; rx = c; ry = d; invalidate() }
        fun setPressed(key: Int, down: Boolean) { if (down) pressed.add(key) else pressed.remove(key); invalidate() }

        override fun onDraw(c: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            val t = SystemClock.uptimeMillis() / 1000f

            p.shader = LinearGradient(0f, 0f, w, h, Color.rgb(5, 7, 12), Color.rgb(10, 14, 23), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, p); p.shader = null
            p.shader = RadialGradient(w * .5f, h * .45f, h * .7f, Color.argb(35, 82, 108, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, p); p.shader = null

            // Minimal top bar — no giant card, only the information that matters while playing.
            p.color = Color.argb(190, 8, 11, 18)
            c.drawRoundRect(22f, 16f, w - 22f, 66f, 25f, 25f, p)
            p.color = Color.WHITE; p.textSize = 13f; p.typeface = android.graphics.Typeface.DEFAULT_BOLD
            c.drawText("CONTROLBRIDGE", 42f, 47f, p)
            p.color = Color.rgb(115, 127, 151); p.textSize = 9f; p.typeface = android.graphics.Typeface.DEFAULT
            c.drawText("BLUETOOTH HID GAMEPAD", 42f, 59f, p)

            val tvColor = if (tvConnected) Color.rgb(96, 236, 164) else Color.rgb(255, 183, 88)
            p.color = Color.argb(35, Color.red(tvColor), Color.green(tvColor), Color.blue(tvColor)); c.drawRoundRect(w - 285f, 26f, w - 155f, 56f, 15f, 15f, p)
            p.color = tvColor; c.drawCircle(w - 268f, 41f, 4f, p); p.color = Color.rgb(205, 214, 229); p.textSize = 9f
            c.drawText(if (tvConnected) "TV CONECTADA" else "TV OFFLINE", w - 258f, 45f, p)
            p.color = Color.argb(42, 120, 145, 255); c.drawRoundRect(w - 145f, 26f, w - 38f, 56f, 15f, 15f, p)
            p.color = Color.rgb(151, 174, 255); c.drawText("CONECTAR", w - 128f, 45f, p)

            val leftX = w * .20f; val rightX = w * .80f; val cy = h * .70f
            drawStick(c, leftX, cy, lx, ly, "L3")
            drawStick(c, rightX, cy, rx, ry, "R3")
            drawDpad(c, w * .09f, cy)
            drawFaceCluster(c, w * .88f, cy)

            // Shoulder controls sit high and stay out of the game area.
            drawPillButton(c, w * .27f, 96f, 92f, 32f, "L1", KeyEvent.KEYCODE_BUTTON_L1)
            drawPillButton(c, w * .34f, 96f, 112f, 32f, "L2", KeyEvent.KEYCODE_BUTTON_L2)
            drawPillButton(c, w * .73f, 96f, 112f, 32f, "R2", KeyEvent.KEYCODE_BUTTON_R2)
            drawPillButton(c, w * .80f, 96f, 92f, 32f, "R1", KeyEvent.KEYCODE_BUTTON_R1)

            // Center controls.
            drawSmallButton(c, w * .47f, h * .68f, 58f, "SELECT", KeyEvent.KEYCODE_BUTTON_SELECT)
            drawSmallButton(c, w * .53f, h * .68f, 58f, "START", KeyEvent.KEYCODE_BUTTON_START)

            p.color = Color.rgb(104, 116, 139); p.textSize = 8f
            c.drawText("${controllerName.take(24)}  •  $tvStatus", 30f, h - 20f, p)
            val pulse = (0.5f + 0.5f * kotlin.math.sin(t * 4f)).toFloat()
            p.color = Color.argb((20 + pulse * 25).toInt(), 105, 137, 255)
            c.drawCircle(w / 2f, h - 23f, 5f, p)
        }

        private fun drawStick(c: Canvas, cx: Float, cy: Float, sx: Float, sy: Float, label: String) {
            p.color = Color.argb(45, 150, 170, 220); c.drawCircle(cx, cy, 72f, p)
            p.color = Color.argb(155, 17, 22, 34); c.drawCircle(cx, cy, 59f, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = Color.argb(90, 119, 144, 190); c.drawCircle(cx, cy, 59f, p); p.style = Paint.Style.FILL
            val px = cx + sx * 38f; val py = cy + sy * 38f
            p.color = Color.argb(55, 112, 144, 255); c.drawCircle(px, py, 30f, p)
            p.color = Color.rgb(73, 88, 117); c.drawCircle(px, py, 23f, p)
            p.color = Color.rgb(178, 188, 208); c.drawCircle(px - sx * 2f, py - sy * 2f, 11f, p)
            p.color = Color.rgb(94, 108, 135); p.textSize = 8f; c.drawText(label, cx - 8f, cy + 91f, p)
        }

        private fun drawDpad(c: Canvas, cx: Float, cy: Float) {
            val s = 30f
            p.color = Color.argb(175, 17, 22, 34)
            c.drawRoundRect(cx - s, cy - s * 2.1f, cx + s, cy + s * 2.1f, 12f, 12f, p)
            c.drawRoundRect(cx - s * 2.1f, cy - s, cx + s * 2.1f, cy + s, 12f, 12f, p)
            p.color = Color.rgb(170, 181, 202); p.textSize = 18f; p.textAlign = Paint.Align.CENTER
            c.drawText("↑", cx, cy - 31f, p); c.drawText("↓", cx, cy + 39f, p); c.drawText("←", cx - 36f, cy + 6f, p); c.drawText("→", cx + 36f, cy + 6f, p); p.textAlign = Paint.Align.LEFT
        }

        private fun drawFaceCluster(c: Canvas, cx: Float, cy: Float) {
            drawRoundFace(c, cx, cy - 65f, "Y", KeyEvent.KEYCODE_BUTTON_Y, Color.rgb(245, 197, 89))
            drawRoundFace(c, cx + 65f, cy, "B", KeyEvent.KEYCODE_BUTTON_B, Color.rgb(240, 101, 118))
            drawRoundFace(c, cx - 65f, cy, "X", KeyEvent.KEYCODE_BUTTON_X, Color.rgb(91, 155, 246))
            drawRoundFace(c, cx, cy + 65f, "A", KeyEvent.KEYCODE_BUTTON_A, Color.rgb(88, 220, 154))
        }

        private fun drawRoundFace(c: Canvas, x: Float, y: Float, label: String, key: Int, accent: Int) {
            val down = pressed.contains(key); val r = if (down) 25f else 23f
            p.color = Color.argb(if (down) 100 else 35, Color.red(accent), Color.green(accent), Color.blue(accent)); c.drawCircle(x, y, r + 8f, p)
            p.color = if (down) accent else Color.argb(215, 28, 36, 52); c.drawCircle(x, y, r, p)
            p.color = if (down) Color.WHITE else Color.rgb(190, 201, 220); p.textSize = 14f; p.typeface = android.graphics.Typeface.DEFAULT_BOLD; p.textAlign = Paint.Align.CENTER
            c.drawText(label, x, y + 5f, p); p.textAlign = Paint.Align.LEFT
        }

        private fun drawPillButton(c: Canvas, x: Float, y: Float, width: Float, height: Float, label: String, key: Int) {
            val down = pressed.contains(key); p.color = if (down) Color.rgb(98, 133, 242) else Color.argb(180, 24, 31, 46)
            c.drawRoundRect(x - width / 2f, y - height / 2f, x + width / 2f, y + height / 2f, 16f, 16f, p)
            p.color = Color.rgb(172, 184, 205); p.textSize = 9f; p.textAlign = Paint.Align.CENTER; c.drawText(label, x, y + 3f, p); p.textAlign = Paint.Align.LEFT
        }

        private fun drawSmallButton(c: Canvas, x: Float, y: Float, width: Float, label: String, key: Int) {
            p.color = if (pressed.contains(key)) Color.rgb(86, 120, 224) else Color.argb(155, 22, 28, 42)
            c.drawRoundRect(x - width / 2f, y - 15f, x + width / 2f, y + 15f, 15f, 15f, p)
            p.color = Color.rgb(142, 153, 174); p.textSize = 7f; p.textAlign = Paint.Align.CENTER; c.drawText(label, x, y + 3f, p); p.textAlign = Paint.Align.LEFT
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    val index = e.actionIndex; val id = e.getPointerId(index); val x = e.getX(index); val y = e.getY(index)
                    if (x > width - 160f && y < 75f) { chooseTv(); return true }
                    val zone = zoneAt(x, y)
                    if (zone == ZONE_LEFT_STICK && leftPointer == -1) { leftPointer = id; pointers[id] = zone; updateStick(true, x, y) }
                    else if (zone == ZONE_RIGHT_STICK && rightPointer == -1) { rightPointer = id; pointers[id] = zone; updateStick(false, x, y) }
                    else if (zone != 0) { pointers[id] = zone; sendZone(zone, true) }
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    for (i in 0 until e.pointerCount) {
                        val id = e.getPointerId(i); when (pointers[id]) {
                            ZONE_LEFT_STICK -> updateStick(true, e.getX(i), e.getY(i))
                            ZONE_RIGHT_STICK -> updateStick(false, e.getX(i), e.getY(i))
                        }
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                    val index = e.actionIndex; val id = e.getPointerId(index); val zone = pointers.remove(id)
                    when (zone) {
                        ZONE_LEFT_STICK -> { if (leftPointer == id) leftPointer = -1; updateStick(true, -1f, -1f) }
                        ZONE_RIGHT_STICK -> { if (rightPointer == id) rightPointer = -1; updateStick(false, -1f, -1f) }
                        null -> Unit
                        else -> sendZone(zone, false)
                    }
                    return true
                }
            }
            return true
        }

        private fun updateStick(left: Boolean, x: Float, y: Float) {
            val cx = if (left) width * .20f else width * .80f; val cy = height * .70f
            if (x < 0f) { if (left) { lx = 0f; ly = 0f } else { rx = 0f; ry = 0f } }
            else {
                val dx = ((x - cx) / 65f).coerceIn(-1f, 1f); val dy = ((y - cy) / 65f).coerceIn(-1f, 1f)
                if (left) { lx = dx; ly = dy } else { rx = dx; ry = dy }
            }
            hid.updateAxis(lx, ly, rx, ry); invalidate()
        }

        private fun zoneAt(x: Float, y: Float): Int {
            val cy = height * .70f
            if (hypot((x - width * .20f).toDouble(), (y - cy).toDouble()) < 85) return ZONE_LEFT_STICK
            if (hypot((x - width * .80f).toDouble(), (y - cy).toDouble()) < 85) return ZONE_RIGHT_STICK
            val face = arrayOf(
                floatArrayOf(width * .88f, cy - 65f, KeyEvent.KEYCODE_BUTTON_Y.toFloat()),
                floatArrayOf(width * .88f + 65f, cy, KeyEvent.KEYCODE_BUTTON_B.toFloat()),
                floatArrayOf(width * .88f - 65f, cy, KeyEvent.KEYCODE_BUTTON_X.toFloat()),
                floatArrayOf(width * .88f, cy + 65f, KeyEvent.KEYCODE_BUTTON_A.toFloat())
            )
            for (f in face) if (hypot((x - f[0]).toDouble(), (y - f[1]).toDouble()) < 34) return f[2].toInt()
            val dCx = width * .09f
            if (hypot((x - dCx).toDouble(), (y - cy).toDouble()) < 78) {
                return when {
                    y < cy - 22 -> KeyEvent.KEYCODE_DPAD_UP
                    y > cy + 22 -> KeyEvent.KEYCODE_DPAD_DOWN
                    x < dCx - 22 -> KeyEvent.KEYCODE_DPAD_LEFT
                    else -> KeyEvent.KEYCODE_DPAD_RIGHT
                }
            }
            if (y in 75f..115f) return when {
                x < width * .31f -> KeyEvent.KEYCODE_BUTTON_L1
                x < width * .40f -> KeyEvent.KEYCODE_BUTTON_L2
                x > width * .69f && x < width * .77f -> KeyEvent.KEYCODE_BUTTON_R2
                x >= width * .77f -> KeyEvent.KEYCODE_BUTTON_R1
                else -> 0
            }
            return when {
                hypot((x - width * .47f).toDouble(), (y - height * .68f).toDouble()) < 35 -> KeyEvent.KEYCODE_BUTTON_SELECT
                hypot((x - width * .53f).toDouble(), (y - height * .68f).toDouble()) < 35 -> KeyEvent.KEYCODE_BUTTON_START
                else -> 0
            }
        }

        private fun sendZone(zone: Int, down: Boolean) {
            if (zone == 0 || zone == ZONE_LEFT_STICK || zone == ZONE_RIGHT_STICK) return
            lastInputAt = SystemClock.elapsedRealtime()
            if (isDpad(zone)) hid.setDpad(zone, down) else hid.setButton(zone, down)
            setPressed(zone, down)
        }

        companion object {
            const val ZONE_LEFT_STICK = -100
            const val ZONE_RIGHT_STICK = -101
        }
    }
}
