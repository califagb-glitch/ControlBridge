package com.controlbridge.mobile

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import org.json.JSONObject
import kotlin.math.hypot

class MainActivity : Activity() {
    private lateinit var hud: ControllerHud
    private val bridge = BridgeServer()
    private var controllerName = "Nenhum controle conectado"
    private var connected = false
    private var lastSend = 0L
    private var lx = 0f
    private var ly = 0f
    private var rx = 0f
    private var ry = 0f
    private var lt = 0f
    private var rt = 0f
    private var lastInputAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(7, 9, 14)
        window.navigationBarColor = Color.rgb(7, 9, 14)
        hud = ControllerHud()
        setContentView(hud)
        bridge.start()
        hud.postDelayed({ refreshController() }, 250)
        hud.post(object : Runnable {
            override fun run() {
                hud.invalidate()
                hud.postDelayed(this, 50)
            }
        })
    }

    override fun onDestroy() {
        bridge.stop()
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!isGamepad(event.device)) return super.dispatchKeyEvent(event)
        connected = true
        controllerName = event.device?.name ?: "Gamepad"
        lastInputAt = SystemClock.elapsedRealtime()
        hud.setButtonState(event.keyCode, event.action == KeyEvent.ACTION_DOWN)
        bridge.broadcast(
            JSONObject().apply {
                put("type", "key")
                put("key", event.keyCode)
                put("action", event.action)
                put("connected", true)
            }.toString()
        )
        return true
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (!isGamepad(event.device) || event.action != MotionEvent.ACTION_MOVE) {
            return super.onGenericMotionEvent(event)
        }

        connected = true
        controllerName = event.device?.name ?: "Gamepad"
        lastInputAt = SystemClock.elapsedRealtime()
        lx = axis(event, MotionEvent.AXIS_X)
        ly = axis(event, MotionEvent.AXIS_Y)
        rx = axis(event, MotionEvent.AXIS_Z)
        ry = axis(event, MotionEvent.AXIS_RZ)
        lt = axis01(event, MotionEvent.AXIS_LTRIGGER)
        rt = axis01(event, MotionEvent.AXIS_RTRIGGER)

        val now = SystemClock.elapsedRealtime()
        if (now - lastSend >= 8) {
            lastSend = now
            bridge.broadcast(
                JSONObject().apply {
                    put("type", "motion")
                    put("lx", lx)
                    put("ly", ly)
                    put("rx", rx)
                    put("ry", ry)
                    put("lt", lt)
                    put("rt", rt)
                    put("connected", true)
                }.toString()
            )
        }
        return true
    }

    private fun axis(event: MotionEvent, axis: Int): Float = event.getAxisValue(axis).coerceIn(-1f, 1f)
    private fun axis01(event: MotionEvent, axis: Int): Float = event.getAxisValue(axis).coerceIn(0f, 1f)

    private fun isGamepad(device: InputDevice?): Boolean =
        device != null && ((device.sources and InputDevice.SOURCE_GAMEPAD) != 0 ||
            (device.sources and InputDevice.SOURCE_JOYSTICK) != 0)

    private fun refreshController() {
        connected = false
        for (id in InputDevice.getDeviceIds()) {
            val device = InputDevice.getDevice(id)
            if (isGamepad(device)) {
                connected = true
                controllerName = device?.name ?: "Gamepad"
                break
            }
        }
        hud.invalidate()
    }

    private fun sendTouchKey(key: Int, down: Boolean) {
        connected = true
        lastInputAt = SystemClock.elapsedRealtime()
        hud.setButtonState(key, down)
        bridge.broadcast(
            JSONObject().apply {
                put("type", "key")
                put("key", key)
                put("action", if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP)
                put("connected", true)
                put("source", "touch")
            }.toString()
        )
    }

    private inner class ControllerHud : View(this@MainActivity) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val buttonRects = linkedMapOf<Int, RectF>()
        private val pressed = HashSet<Int>()
        private var activeTouchKey: Int? = null
        private var touchStartX = 0f
        private var touchStartY = 0f

        init {
            setBackgroundColor(Color.rgb(7, 9, 14))
            isFocusable = true
        }

        fun setButtonState(key: Int, down: Boolean) {
            if (down) pressed.add(key) else pressed.remove(key)
            invalidate()
        }

        override fun onDraw(c: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val compact = w < 650f
            val pad = if (compact) 18f else 26f

            p.style = Paint.Style.FILL
            p.shader = null
            p.color = Color.rgb(7, 9, 14)
            c.drawRect(0f, 0f, w, h, p)

            // Soft animated accent glow.
            val pulse = (SystemClock.uptimeMillis() % 1800L) / 1800f
            p.color = Color.argb((18 + 10 * pulse).toInt(), 90, 120, 255)
            c.drawCircle(w * 0.82f, 54f, 90f + pulse * 20f, p)

            // Header
            p.typeface = android.graphics.Typeface.DEFAULT_BOLD
            p.color = Color.WHITE
            p.textSize = if (compact) 22f else 26f
            c.drawText("ControlBridge", pad, 38f, p)
            p.typeface = android.graphics.Typeface.DEFAULT
            p.color = Color.rgb(125, 139, 166)
            p.textSize = 10f
            c.drawText("PHONE CONTROLLER  •  LOCAL NETWORK", pad, 58f, p)

            val statusColor = if (connected) Color.rgb(91, 231, 160) else Color.rgb(255, 181, 89)
            p.color = Color.argb(38, statusColor.red(), statusColor.green(), statusColor.blue())
            c.drawRoundRect(w - 150f, 18f, w - pad, 48f, 15f, 15f, p)
            p.color = statusColor
            p.textSize = 10f
            c.drawText(if (connected) "●  CONECTADO" else "●  AGUARDANDO", w - 138f, 37f, p)

            // Connection card
            p.color = Color.rgb(15, 20, 30)
            c.drawRoundRect(pad, 78f, w - pad, 145f, 20f, 20f, p)
            p.color = Color.rgb(30, 38, 54)
            c.drawRoundRect(pad + 12f, 91f, pad + 45f, 124f, 11f, 11f, p)
            p.color = statusColor
            c.drawCircle(pad + 28.5f, 107.5f, 5f, p)
            p.color = Color.WHITE
            p.textSize = 13f
            c.drawText(controllerName, pad + 55f, 107f, p)
            p.color = Color.rgb(121, 134, 157)
            p.textSize = 9f
            c.drawText(bridge.url(), pad + 55f, 124f, p)

            // Controller surface
            val top = 164f
            p.color = Color.rgb(11, 15, 23)
            c.drawRoundRect(pad, top, w - pad, h - 18f, 26f, 26f, p)
            p.color = Color.rgb(23, 30, 44)
            c.drawRoundRect(pad + 1f, top + 1f, w - pad - 1f, h - 19f, 25f, 25f, p)

            val centerY = h - if (compact) 116f else 132f
            val leftX = w * 0.25f
            val rightX = w * 0.75f
            drawStick(c, leftX, centerY, lx, ly)
            drawStick(c, rightX, centerY, rx, ry)

            // Face buttons
            val base = minOf(w * 0.75f, w - 70f)
            drawFaceButton(c, base, centerY - 55f, 52f, KeyEvent.KEYCODE_BUTTON_Y, "Y", Color.rgb(255, 205, 92))
            drawFaceButton(c, base + 58f, centerY, 52f, KeyEvent.KEYCODE_BUTTON_B, "B", Color.rgb(255, 103, 117))
            drawFaceButton(c, base - 58f, centerY, 52f, KeyEvent.KEYCODE_BUTTON_X, "X", Color.rgb(100, 170, 255))
            drawFaceButton(c, base, centerY + 55f, 52f, KeyEvent.KEYCODE_BUTTON_A, "A", Color.rgb(103, 230, 160))

            // D-pad
            drawDpad(c, w * 0.14f, centerY)

            // Triggers / live telemetry
            p.color = Color.rgb(116, 130, 154)
            p.textSize = 8f
            c.drawText("L2", pad + 18f, top + 24f, p)
            c.drawText("R2", w - pad - 34f, top + 24f, p)
            drawBar(c, pad + 38f, top + 18f, 75f, lt)
            drawBar(c, w - pad - 113f, top + 18f, 75f, rt)

            p.color = Color.rgb(103, 116, 141)
            p.textSize = 9f
            c.drawText("TOQUE NOS BOTÕES • MOVA OS STICKS", w / 2f - 105f, top + 48f, p)

            // Input activity indicator.
            val age = SystemClock.elapsedRealtime() - lastInputAt
            p.color = if (age < 500) Color.rgb(92, 226, 160) else Color.rgb(79, 91, 112)
            c.drawCircle(w / 2f, h - 29f, 4f, p)
        }

        private fun drawStick(c: Canvas, cx: Float, cy: Float, sx: Float, sy: Float) {
            p.color = Color.rgb(31, 40, 57)
            c.drawCircle(cx, cy, 55f, p)
            p.color = Color.rgb(17, 22, 33)
            c.drawCircle(cx, cy, 42f, p)
            val len = hypot(sx.toDouble(), sy.toDouble()).coerceAtMost(1.0).toFloat()
            val nx = if (len > 0.03f) sx / maxOf(1f, len) else 0f
            val ny = if (len > 0.03f) sy / maxOf(1f, len) else 0f
            val px = cx + sx * 27f
            val py = cy + sy * 27f
            p.color = Color.rgb(74, 88, 112)
            c.drawCircle(px, py, 20f + len * 2f, p)
            p.color = Color.rgb(108, 126, 160)
            c.drawCircle(px + nx * 1.5f, py + ny * 1.5f, 13f, p)
        }

        private fun drawFaceButton(c: Canvas, cx: Float, cy: Float, size: Float, key: Int, label: String, accent: Int) {
            val r = size / 2f
            val isPressed = pressed.contains(key)
            p.color = Color.argb(if (isPressed) 70 else 30, Color.red(accent), Color.green(accent), Color.blue(accent))
            c.drawCircle(cx, cy, r + 5f, p)
            p.color = if (isPressed) accent else Color.rgb(35, 44, 61)
            c.drawCircle(cx, cy, r, p)
            p.color = if (isPressed) Color.WHITE else Color.rgb(196, 204, 219)
            p.textSize = 16f
            p.textAlign = Paint.Align.CENTER
            p.typeface = android.graphics.Typeface.DEFAULT_BOLD
            c.drawText(label, cx, cy + 6f, p)
            p.textAlign = Paint.Align.LEFT
        }

        private fun drawDpad(c: Canvas, cx: Float, cy: Float) {
            val s = 22f
            p.color = Color.rgb(35, 44, 61)
            c.drawRoundRect(cx - s, cy - s * 2.2f, cx + s, cy + s * 2.2f, 8f, 8f, p)
            c.drawRoundRect(cx - s * 2.2f, cy - s, cx + s * 2.2f, cy + s, 8f, 8f, p)
            p.color = Color.rgb(118, 132, 157)
            p.textSize = 14f
            p.textAlign = Paint.Align.CENTER
            c.drawText("↑", cx, cy - 25f, p)
            c.drawText("↓", cx, cy + 30f, p)
            c.drawText("←", cx - 27f, cy + 5f, p)
            c.drawText("→", cx + 27f, cy + 5f, p)
            p.textAlign = Paint.Align.LEFT
        }

        private fun drawBar(c: Canvas, x: Float, y: Float, width: Float, value: Float) {
            p.color = Color.rgb(29, 37, 52)
            c.drawRoundRect(x, y, x + width, y + 6f, 3f, 3f, p)
            p.color = Color.rgb(102, 132, 255)
            c.drawRoundRect(x, y, x + width * value, y + 6f, 3f, 3f, p)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = event.x
                    touchStartY = event.y
                    activeTouchKey = buttonAt(event.x, event.y)
                    activeTouchKey?.let { sendTouchKey(it, true) }
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    activeTouchKey?.let { sendTouchKey(it, false) }
                    activeTouchKey = null
                    invalidate()
                    return true
                }
            }
            return true
        }

        private fun buttonAt(x: Float, y: Float): Int? {
            val w = width.toFloat()
            val h = height.toFloat()
            val cy = h - if (w < 650f) 116f else 132f
            val base = minOf(w * 0.75f, w - 70f)
            val faces = listOf(
                Triple(base, cy - 55f, KeyEvent.KEYCODE_BUTTON_Y),
                Triple(base + 58f, cy, KeyEvent.KEYCODE_BUTTON_B),
                Triple(base - 58f, cy, KeyEvent.KEYCODE_BUTTON_X),
                Triple(base, cy + 55f, KeyEvent.KEYCODE_BUTTON_A)
            )
            for ((bx, by, key) in faces) if (hypot((x - bx).toDouble(), (y - by).toDouble()) <= 30.0) return key
            return null
        }
    }
}
