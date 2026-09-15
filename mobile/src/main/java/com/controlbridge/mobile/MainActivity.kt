package com.controlbridge.mobile

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
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
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

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
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE

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

        if (Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 40)
        } else {
            hid.start()
        }

        hud.postDelayed({ refreshController() }, 250)
        hud.post(object : Runnable {
            override fun run() {
                hud.invalidate()
                hud.postDelayed(this, 50)
            }
        })
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 40 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            hid.start()
        } else if (requestCode == 40) {
            Toast.makeText(this, "O Bluetooth é necessário para usar a TV como gamepad.", Toast.LENGTH_LONG).show()
        }
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
        hud.setPhysicalPressed(event.keyCode, down)
        return true
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (!isGamepad(event.device) || event.actionMasked != MotionEvent.ACTION_MOVE) return super.onGenericMotionEvent(event)
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
            Toast.makeText(this, "Pareie o celular com a TV pelo Bluetooth primeiro.", Toast.LENGTH_LONG).show()
            openBluetoothSettings()
            return
        }
        val names = devices.map { it.name ?: it.address }.toTypedArray()
        android.app.AlertDialog.Builder(this)
            .setTitle("Conectar à TV")
            .setMessage("Escolha o dispositivo Bluetooth que deve receber o controle.")
            .setItems(names) { _, which -> hid.connect(devices[which]) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private inner class ControllerHud : View(this@MainActivity) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()
        private val physicalPressed = HashSet<Int>()
        private val touchPressed = HashSet<Int>()
        private val touchTargets = HashMap<Int, Int>()
        private var leftPointer = -1
        private var rightPointer = -1
        private var lx = 0f
        private var ly = 0f
        private var rx = 0f
        private var ry = 0f
        var tvStatus = "Preparando Bluetooth HID…"

        private val blue = Color.rgb(91, 126, 255)
        private val green = Color.rgb(88, 222, 159)

        init {
            isFocusable = true
            setBackgroundColor(Color.rgb(5, 7, 12))
        }

        fun setAxes(a: Float, b: Float, c: Float, d: Float) {
            lx = a; ly = b; rx = c; ry = d
            invalidate()
        }

        fun setPhysicalPressed(key: Int, down: Boolean) {
            if (down) physicalPressed.add(key) else physicalPressed.remove(key)
            invalidate()
        }

        override fun onDraw(c: Canvas) {
            super.onDraw(c)
            val w = width.toFloat()
            val h = height.toFloat()
            val unit = minOf(w, h)
            val top = unit * 0.105f
            val controlY = h * 0.70f
            val leftStickX = w * 0.235f
            val rightStickX = w * 0.765f
            val dpadX = w * 0.105f
            val faceX = w * 0.895f

            p.shader = LinearGradient(0f, 0f, w, h, Color.rgb(4, 6, 11), Color.rgb(11, 15, 25), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, p)
            p.shader = null

            // Very restrained ambient glow: the HUD stays readable without looking like a dashboard.
            p.color = Color.argb(18, 90, 120, 255)
            c.drawCircle(w * .50f, h * .48f, unit * .48f, p)

            drawHeader(c, w, top)
            drawShoulders(c, w, top)
            drawStick(c, leftStickX, controlY, lx, ly, "L3", unit)
            drawStick(c, rightStickX, controlY, rx, ry, "R3", unit)
            drawDpad(c, dpadX, controlY, unit)
            drawFaceCluster(c, faceX, controlY, unit)
            drawCenterButtons(c, w, h, unit)
            drawFooter(c, w, h)
        }

        private fun drawHeader(c: Canvas, w: Float, y: Float) {
            val height = 50f
            p.color = Color.argb(185, 9, 12, 20)
            c.drawRoundRect(20f, 12f, w - 20f, 12f + height, 24f, 24f, p)

            p.color = Color.WHITE
            p.typeface = android.graphics.Typeface.DEFAULT_BOLD
            p.textSize = 13f
            c.drawText("CONTROLBRIDGE", 40f, y + 5f, p)

            p.color = Color.rgb(106, 118, 143)
            p.typeface = android.graphics.Typeface.DEFAULT
            p.textSize = 8f
            c.drawText("PHONE GAMEPAD  •  BLUETOOTH HID", 40f, y + 19f, p)

            val statusColor = if (tvConnected) green else Color.rgb(245, 177, 82)
            p.color = Color.argb(30, Color.red(statusColor), Color.green(statusColor), Color.blue(statusColor))
            c.drawRoundRect(w - 220f, 22f, w - 116f, 50f, 14f, 14f, p)
            p.color = statusColor
            c.drawCircle(w - 204f, 36f, 4f, p)
            p.color = Color.rgb(207, 215, 231)
            p.textSize = 8f
            c.drawText(if (tvConnected) "TV CONECTADA" else "TV NÃO CONECTADA", w - 194f, 39f, p)

            p.color = Color.argb(42, Color.red(blue), Color.green(blue), Color.blue(blue))
            c.drawRoundRect(w - 104f, 22f, w - 32f, 50f, 14f, 14f, p)
            p.color = Color.rgb(151, 173, 255)
            p.textAlign = Paint.Align.CENTER
            c.drawText("CONECTAR", w - 68f, 39f, p)
            p.textAlign = Paint.Align.LEFT
        }

        private fun drawShoulders(c: Canvas, w: Float, y: Float) {
            drawPill(c, w * .25f, y + 1f, 74f, 30f, "L1", KeyEvent.KEYCODE_BUTTON_L1)
            drawPill(c, w * .35f, y + 1f, 82f, 30f, "L2", KeyEvent.KEYCODE_BUTTON_L2)
            drawPill(c, w * .65f, y + 1f, 82f, 30f, "R2", KeyEvent.KEYCODE_BUTTON_R2)
            drawPill(c, w * .75f, y + 1f, 74f, 30f, "R1", KeyEvent.KEYCODE_BUTTON_R1)
        }

        private fun drawStick(c: Canvas, cx: Float, cy: Float, sx: Float, sy: Float, label: String, unit: Float) {
            val outer = unit * .102f
            val inner = unit * .082f
            val knob = unit * .040f
            p.color = Color.argb(36, 150, 170, 220)
            c.drawCircle(cx, cy, outer, p)
            p.color = Color.argb(190, 12, 17, 28)
            c.drawCircle(cx, cy, inner, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 2f
            p.color = Color.argb(75, 125, 148, 201)
            c.drawCircle(cx, cy, inner, p)
            p.style = Paint.Style.FILL

            val magnitude = hypot(sx.toDouble(), sy.toDouble()).coerceAtMost(1.0).toFloat()
            val nx = if (magnitude > .03f) sx / magnitude else 0f
            val ny = if (magnitude > .03f) sy / magnitude else 0f
            val travel = unit * .052f
            val px = cx + nx * travel * magnitude
            val py = cy + ny * travel * magnitude

            p.color = Color.argb(45, Color.red(blue), Color.green(blue), Color.blue(blue))
            c.drawCircle(px, py, knob + 10f, p)
            p.color = Color.rgb(54, 66, 91)
            c.drawCircle(px, py, knob, p)
            p.color = Color.rgb(181, 192, 211)
            c.drawCircle(px - nx * 2f, py - ny * 2f, knob * .45f, p)

            p.color = Color.rgb(103, 116, 143)
            p.textSize = 8f
            p.textAlign = Paint.Align.CENTER
            c.drawText(label, cx, cy + outer + 17f, p)
            p.textAlign = Paint.Align.LEFT
        }

        private fun drawDpad(c: Canvas, cx: Float, cy: Float, unit: Float) {
            val arm = unit * .043f
            val gap = unit * .006f
            p.color = Color.argb(185, 16, 22, 34)
            c.drawRoundRect(cx - arm, cy - arm * 3.0f, cx + arm, cy + arm * 3.0f, 12f, 12f, p)
            c.drawRoundRect(cx - arm * 3.0f, cy - arm, cx + arm * 3.0f, cy + arm, 12f, 12f, p)
            drawDpadGlyph(c, cx, cy - arm * 1.85f, "↑", KeyEvent.KEYCODE_DPAD_UP)
            drawDpadGlyph(c, cx, cy + arm * 1.95f, "↓", KeyEvent.KEYCODE_DPAD_DOWN)
            drawDpadGlyph(c, cx - arm * 1.9f, cy + 5f, "←", KeyEvent.KEYCODE_DPAD_LEFT)
            drawDpadGlyph(c, cx + arm * 1.9f, cy + 5f, "→", KeyEvent.KEYCODE_DPAD_RIGHT)
        }

        private fun drawDpadGlyph(c: Canvas, x: Float, y: Float, text: String, key: Int) {
            p.color = if (isPressed(key)) Color.WHITE else Color.rgb(165, 178, 202)
            p.textSize = 18f
            p.typeface = android.graphics.Typeface.DEFAULT_BOLD
            p.textAlign = Paint.Align.CENTER
            c.drawText(text, x, y + 6f, p)
            p.textAlign = Paint.Align.LEFT
        }

        private fun drawFaceCluster(c: Canvas, cx: Float, cy: Float, unit: Float) {
            val d = unit * .082f
            drawFace(c, cx, cy - d, "Y", KeyEvent.KEYCODE_BUTTON_Y, Color.rgb(244, 197, 81))
            drawFace(c, cx + d, cy, "B", KeyEvent.KEYCODE_BUTTON_B, Color.rgb(238, 103, 120))
            drawFace(c, cx - d, cy, "X", KeyEvent.KEYCODE_BUTTON_X, Color.rgb(91, 155, 246))
            drawFace(c, cx, cy + d, "A", KeyEvent.KEYCODE_BUTTON_A, Color.rgb(86, 220, 154))
        }

        private fun drawFace(c: Canvas, x: Float, y: Float, label: String, key: Int, accent: Int) {
            val down = isPressed(key)
            val radius = if (down) 25f else 22f
            p.color = Color.argb(if (down) 80 else 28, Color.red(accent), Color.green(accent), Color.blue(accent))
            c.drawCircle(x, y, radius + 9f, p)
            p.color = if (down) accent else Color.rgb(28, 36, 52)
            c.drawCircle(x, y, radius, p)
            p.color = if (down) Color.WHITE else Color.rgb(190, 202, 222)
            p.textSize = 14f
            p.typeface = android.graphics.Typeface.DEFAULT_BOLD
            p.textAlign = Paint.Align.CENTER
            c.drawText(label, x, y + 5f, p)
            p.textAlign = Paint.Align.LEFT
        }

        private fun drawPill(c: Canvas, x: Float, y: Float, width: Float, height: Float, label: String, key: Int) {
            p.color = if (isPressed(key)) blue else Color.argb(175, 22, 29, 43)
            c.drawRoundRect(x - width / 2f, y - height / 2f, x + width / 2f, y + height / 2f, height / 2f, height / 2f, p)
            p.color = Color.rgb(177, 189, 210)
            p.textSize = 9f
            p.textAlign = Paint.Align.CENTER
            c.drawText(label, x, y + 3f, p)
            p.textAlign = Paint.Align.LEFT
        }

        private fun drawCenterButtons(c: Canvas, w: Float, h: Float, unit: Float) {
            val y = h * .66f
            drawCenterButton(c, w * .475f, y, "SELECT", KeyEvent.KEYCODE_BUTTON_SELECT, unit)
            drawCenterButton(c, w * .525f, y, "START", KeyEvent.KEYCODE_BUTTON_START, unit)
        }

        private fun drawCenterButton(c: Canvas, x: Float, y: Float, label: String, key: Int, unit: Float) {
            val width = unit * .085f
            val height = unit * .036f
            p.color = if (isPressed(key)) Color.rgb(79, 112, 218) else Color.argb(145, 22, 28, 41)
            c.drawRoundRect(x - width / 2f, y - height / 2f, x + width / 2f, y + height / 2f, height, height, p)
            p.color = Color.rgb(146, 159, 184)
            p.textSize = 7f
            p.textAlign = Paint.Align.CENTER
            c.drawText(label, x, y + 2.5f, p)
            p.textAlign = Paint.Align.LEFT
        }

        private fun drawFooter(c: Canvas, w: Float, h: Float) {
            val age = SystemClock.elapsedRealtime() - lastInputAt
            val active = physicalConnected && age < 2500L
            val status = if (active) "${controllerName.take(24)}  •  CONTROLE FÍSICO" else "${controllerName.take(24)}  •  TOQUE ATIVO"
            p.color = Color.rgb(93, 106, 133)
            p.textSize = 8f
            c.drawText(status, 24f, h - 17f, p)
            p.color = if (tvConnected) green else Color.rgb(103, 115, 139)
            c.drawCircle(w - 27f, h - 20f, 4f, p)
        }

        private fun isPressed(key: Int): Boolean = physicalPressed.contains(key) || touchPressed.contains(key)

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val action = event.actionMasked
            val index = event.actionIndex
            when (action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    val id = event.getPointerId(index)
                    handlePointerDown(id, event.getX(index), event.getY(index))
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    for (i in 0 until event.pointerCount) {
                        handlePointerMove(event.getPointerId(i), event.getX(i), event.getY(i))
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                    val id = if (action == MotionEvent.ACTION_CANCEL) -1 else event.getPointerId(index)
                    if (id == -1) {
                        touchTargets.keys.toList().forEach { releasePointer(it) }
                    } else {
                        releasePointer(id)
                    }
                    return true
                }
            }
            return true
        }

        private fun handlePointerDown(id: Int, x: Float, y: Float) {
            if (x > width - 125f && y < 75f) {
                chooseTv()
                return
            }

            val w = width.toFloat(); val h = height.toFloat(); val unit = minOf(w, h); val cy = h * .70f
            val leftX = w * .235f; val rightX = w * .765f
            val dpadX = w * .105f; val faceX = w * .895f
            val target = when {
                distance(x, y, leftX, cy) < unit * .13f -> -1
                distance(x, y, rightX, cy) < unit * .13f -> -2
                distance(x, y, faceX, cy - unit * .082f) < unit * .047f -> KeyEvent.KEYCODE_BUTTON_Y
                distance(x, y, faceX + unit * .082f, cy) < unit * .047f -> KeyEvent.KEYCODE_BUTTON_B
                distance(x, y, faceX - unit * .082f, cy) < unit * .047f -> KeyEvent.KEYCODE_BUTTON_X
                distance(x, y, faceX, cy + unit * .082f) < unit * .047f -> KeyEvent.KEYCODE_BUTTON_A
                distance(x, y, dpadX, cy - unit * .080f) < unit * .060f -> KeyEvent.KEYCODE_DPAD_UP
                distance(x, y, dpadX + unit * .080f, cy) < unit * .060f -> KeyEvent.KEYCODE_DPAD_RIGHT
                distance(x, y, dpadX, cy + unit * .080f) < unit * .060f -> KeyEvent.KEYCODE_DPAD_DOWN
                distance(x, y, dpadX - unit * .080f, cy) < unit * .060f -> KeyEvent.KEYCODE_DPAD_LEFT
                distance(x, y, w * .25f, unit * .105f + 1f) < 48f -> KeyEvent.KEYCODE_BUTTON_L1
                distance(x, y, w * .35f, unit * .105f + 1f) < 52f -> KeyEvent.KEYCODE_BUTTON_L2
                distance(x, y, w * .65f, unit * .105f + 1f) < 52f -> KeyEvent.KEYCODE_BUTTON_R2
                distance(x, y, w * .75f, unit * .105f + 1f) < 48f -> KeyEvent.KEYCODE_BUTTON_R1
                distance(x, y, w * .475f, h * .66f) < unit * .055f -> KeyEvent.KEYCODE_BUTTON_SELECT
                distance(x, y, w * .525f, h * .66f) < unit * .055f -> KeyEvent.KEYCODE_BUTTON_START
                else -> 0
            }
            if (target != 0) {
                touchTargets[id] = target
                if (target == -1) leftPointer = id
                else if (target == -2) rightPointer = id
                else if (target > 0) {
                    touchPressed.add(target)
                    if (isDpad(target)) hid.setDpad(target, true) else hid.setButton(target, true)
                }
                if (target == -1 || target == -2) updateStick(id, x, y)
                invalidate()
            }
        }

        private fun handlePointerMove(id: Int, x: Float, y: Float) {
            when (touchTargets[id]) {
                -1, -2 -> updateStick(id, x, y)
            }
        }

        private fun updateStick(id: Int, x: Float, y: Float) {
            val w = width.toFloat(); val h = height.toFloat(); val unit = minOf(w, h); val cy = h * .70f
            val isLeft = touchTargets[id] == -1
            val cx = if (isLeft) w * .235f else w * .765f
            val radius = unit * .078f
            var dx = (x - cx) / radius
            var dy = (y - cy) / radius
            val length = hypot(dx.toDouble(), dy.toDouble()).coerceAtMost(1.0).toFloat()
            if (length > 1f) { dx /= length; dy /= length }
            if (isLeft) { lx = dx; ly = dy } else { rx = dx; ry = dy }
            hid.updateAxis(lx, ly, rx, ry)
            bridge.broadcast("{\"type\":\"motion\",\"lx\":$lx,\"ly\":$ly,\"rx\":$rx,\"ry\":$ry,\"lt\":0,\"rt\":0,\"connected\":true}")
            invalidate()
        }

        private fun releasePointer(id: Int) {
            val target = touchTargets.remove(id) ?: return
            if (target == -1) {
                leftPointer = -1; lx = 0f; ly = 0f
            } else if (target == -2) {
                rightPointer = -1; rx = 0f; ry = 0f
            } else if (target > 0) {
                touchPressed.remove(target)
                if (isDpad(target)) hid.setDpad(target, false) else hid.setButton(target, false)
            }
            if (target == -1 || target == -2) hid.updateAxis(lx, ly, rx, ry)
            invalidate()
        }

        private fun distance(ax: Float, ay: Float, bx: Float, by: Float): Float =
            hypot((ax - bx).toDouble(), (ay - by).toDouble()).toFloat()
    }
}
