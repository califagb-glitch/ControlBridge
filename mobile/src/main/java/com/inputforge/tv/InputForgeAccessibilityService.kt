package com.inputforge.tv

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import kotlin.math.max
import kotlin.math.min

class InputForgeAccessibilityService : AccessibilityService() {
    companion object { var instance: InputForgeAccessibilityService? = null }

    private lateinit var wm: WindowManager
    private var cursor: CursorView? = null
    private var x = 0f
    private var y = 0f
    private var screenW = 1920
    private var screenH = 1080
    private var lastMove = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val dm = resources.displayMetrics
        screenW = dm.widthPixels
        screenH = dm.heightPixels
        x = screenW * 0.5f
        y = screenH * 0.5f
        showCursor()
    }

    override fun onDestroy() {
        hideCursor()
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) return false
        val cursorMode = getSharedPreferences("inputforge", MODE_PRIVATE).getBoolean("cursor_mode", true)
        if (!cursorMode) return false

        if (event.action == KeyEvent.ACTION_UP) return when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER -> true
            KeyEvent.KEYCODE_BUTTON_B -> true
            KeyEvent.KEYCODE_BUTTON_X -> true
            KeyEvent.KEYCODE_BUTTON_Y -> true
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> true
            else -> false
        }

        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> { move(0f, -1f); true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { move(0f, 1f); true }
            KeyEvent.KEYCODE_DPAD_LEFT -> { move(-1f, 0f); true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { move(1f, 0f); true }
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER -> { tap(); true }
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> { performGlobalAction(GLOBAL_ACTION_BACK); true }
            KeyEvent.KEYCODE_BUTTON_X -> { performGlobalAction(GLOBAL_ACTION_HOME); true }
            KeyEvent.KEYCODE_BUTTON_Y -> { performGlobalAction(GLOBAL_ACTION_RECENTS); true }
            else -> false
        }
    }

    private fun move(dx: Float, dy: Float) {
        val prefs = getSharedPreferences("inputforge", MODE_PRIVATE)
        val speed = prefs.getInt("speed", 12)
        val now = SystemClock.uptimeMillis()
        if (now - lastMove < 5) return
        lastMove = now
        val nx = min(screenW.toFloat() - 2f, max(2f, x + dx * speed))
        val ny = min(screenH.toFloat() - 2f, max(2f, y + dy * speed))
        dispatchSwipe(x, y, nx, ny)
        x = nx
        y = ny
        cursor?.setPosition(x, y)
    }

    private fun tap() {
        val path = Path().apply { moveTo(x, y); lineTo(x + 0.1f, y + 0.1f) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 35)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    private fun dispatchSwipe(fromX: Float, fromY: Float, toX: Float, toY: Float) {
        val path = Path().apply { moveTo(fromX, fromY); lineTo(toX, toY) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 16)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    private fun showCursor() {
        if (cursor != null) return
        cursor = CursorView(this).also { view ->
            val lp = WindowManager.LayoutParams(
                28, 28,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            )
            lp.gravity = Gravity.TOP or Gravity.START
            lp.x = x.toInt()
            lp.y = y.toInt()
            wm.addView(view, lp)
        }
    }

    private fun hideCursor() {
        cursor?.let { runCatching { wm.removeView(it) } }
        cursor = null
    }

    private class CursorView(context: android.content.Context) : View(context) {
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = android.graphics.Paint.Style.FILL
        }
        private var px = 14f
        private var py = 14f
        override fun onDraw(canvas: android.graphics.Canvas) {
            canvas.drawCircle(px, py, 7f, paint)
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = android.graphics.Color.BLACK
            canvas.drawCircle(px, py, 9f, paint)
            paint.style = android.graphics.Paint.Style.FILL
            paint.color = android.graphics.Color.WHITE
        }
        fun setPosition(x: Float, y: Float) {
            val lp = layoutParams as? WindowManager.LayoutParams ?: return
            lp.x = x.toInt() - 14
            lp.y = y.toInt() - 14
            (context.getSystemService(WINDOW_SERVICE) as WindowManager).updateViewLayout(this, lp)
        }
    }
}
