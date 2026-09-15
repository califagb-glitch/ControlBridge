package com.controlbridge.mobile

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var hud: ControllerHud
    private val bridge = BridgeServer()
    private var controllerName = "Nenhuma manete detectada"
    private var connected = false
    private var lastSend = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hud = ControllerHud()
        setContentView(hud)
        bridge.start()
        hud.postDelayed({ refreshController() }, 250)
    }

    override fun onDestroy() {
        bridge.stop()
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!isGamepad(event.device)) return super.dispatchKeyEvent(event)
        connected = true
        controllerName = event.device?.name ?: "Gamepad"
        hud.invalidate()
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
        val now = System.currentTimeMillis()

        if (now - lastSend >= 8) {
            lastSend = now
            bridge.broadcast(
                JSONObject().apply {
                    put("type", "motion")
                    put("lx", axis(event, MotionEvent.AXIS_X))
                    put("ly", axis(event, MotionEvent.AXIS_Y))
                    put("rx", axis(event, MotionEvent.AXIS_Z))
                    put("ry", axis(event, MotionEvent.AXIS_RZ))
                    put("lt", axis01(event, MotionEvent.AXIS_LTRIGGER))
                    put("rt", axis01(event, MotionEvent.AXIS_RTRIGGER))
                    put("connected", true)
                }.toString()
            )
        }

        hud.invalidate()
        return true
    }

    private fun axis(event: MotionEvent, axis: Int): Float =
        event.getAxisValue(axis).coerceIn(-1f, 1f)

    private fun axis01(event: MotionEvent, axis: Int): Float =
        event.getAxisValue(axis).coerceIn(0f, 1f)

    private fun isGamepad(device: InputDevice?): Boolean =
        device != null && (
            (device.sources and InputDevice.SOURCE_GAMEPAD) != 0 ||
                (device.sources and InputDevice.SOURCE_JOYSTICK) != 0
            )

    private fun refreshController() {
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

    private inner class ControllerHud : View(this@MainActivity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val a = RectF()
        private val b = RectF()
        private val x = RectF()
        private val y = RectF()

        init {
            setBackgroundColor(Color.rgb(7, 9, 15))
            isFocusable = true
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            paint.color = Color.WHITE
            paint.textSize = 25f
            canvas.drawText("CONTROLBRIDGE", 28f, 48f, paint)
            paint.typeface = android.graphics.Typeface.DEFAULT
            paint.color = Color.rgb(135, 151, 180)
            paint.textSize = 11f
            canvas.drawText("PHONE CONTROLLER  •  LAN", 29f, 69f, paint)

            paint.color = Color.rgb(17, 22, 33)
            canvas.drawRoundRect(18f, 92f, w - 18f, 178f, 22f, 22f, paint)
            paint.color = Color.rgb(115, 150, 255)
            paint.textSize = 12f
            canvas.drawText("●  CONEXÃO", 34f, 117f, paint)
            paint.color = Color.WHITE
            paint.textSize = 17f
            canvas.drawText(controllerName, 34f, 144f, paint)
            paint.color = Color.rgb(150, 160, 178)
            paint.textSize = 11f
            canvas.drawText(bridge.url(), 34f, 165f, paint)
            paint.color = if (connected) Color.rgb(105, 225, 155) else Color.rgb(255, 190, 90)
            paint.textSize = 11f
            canvas.drawText(
                if (connected) "MANETE DETECTADA" else "CONECTE A MANETE PELO BLUETOOTH DO ANDROID",
                34f,
                192f,
                paint
            )

            paint.color = Color.rgb(12, 16, 24)
            canvas.drawRoundRect(18f, 210f, w - 18f, h - 24f, 22f, 22f, paint)
            drawPad(canvas, w, h)
        }

        private fun drawPad(canvas: Canvas, w: Float, h: Float) {
            val cy = h - 150f
            paint.color = Color.rgb(30, 39, 56)
            canvas.drawCircle(125f, cy, 62f, paint)
            canvas.drawCircle(w - 125f, cy, 62f, paint)
            paint.color = Color.rgb(80, 95, 120)
            canvas.drawCircle(125f, cy, 26f, paint)
            canvas.drawCircle(w - 125f, cy, 26f, paint)

            a.set(w - 185f, cy - 75f, w - 125f, cy - 15f)
            b.set(w - 115f, cy - 115f, w - 55f, cy - 55f)
            x.set(w - 255f, cy - 115f, w - 195f, cy - 55f)
            y.set(w - 185f, cy - 155f, w - 125f, cy - 95f)
            drawBtn(canvas, a, "A")
            drawBtn(canvas, b, "B")
            drawBtn(canvas, x, "X")
            drawBtn(canvas, y, "Y")

            paint.color = Color.rgb(120, 132, 155)
            paint.textSize = 12f
            canvas.drawText("TOQUE PARA CONTROLAR", w / 2 - 70f, 230f, paint)
        }

        private fun drawBtn(canvas: Canvas, rect: RectF, label: String) {
            paint.color = Color.rgb(28, 36, 53)
            canvas.drawRoundRect(rect, 18f, 18f, paint)
            paint.color = Color.WHITE
            paint.textSize = 18f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(label, rect.centerX(), rect.centerY() + 6f, paint)
            paint.textAlign = Paint.Align.LEFT
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            connected = true
            invalidate()
            return true
        }
    }
}
