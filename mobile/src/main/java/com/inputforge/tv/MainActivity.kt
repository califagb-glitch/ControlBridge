package com.inputforge.tv

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var status: TextView
    private val prefs by lazy { getSharedPreferences("inputforge", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) refreshStatus()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(56, 36, 56, 36)
            setBackgroundColor(Color.rgb(8, 10, 16))
        }

        val title = TextView(this).apply {
            text = "INPUTFORGE"
            textSize = 30f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        root.addView(title, LinearLayout.LayoutParams(-1, -2))

        val subtitle = TextView(this).apply {
            text = "TV Input Engine  •  v0.1"
            textSize = 15f
            setTextColor(Color.rgb(150, 158, 180))
            gravity = Gravity.CENTER
            setPadding(0, 6, 0, 28)
        }
        root.addView(subtitle)

        status = TextView(this).apply {
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(20, 18, 20, 18)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(18, 22, 32))
        }
        root.addView(status, LinearLayout.LayoutParams(-1, 76))

        addButton(root, "Ativar InputForge", 20) { openAccessibilitySettings() }
        addButton(root, "Modo ponteiro", 8) {
            val enabled = !prefs.getBoolean("cursor_mode", true)
            prefs.edit().putBoolean("cursor_mode", enabled).apply()
            Toast.makeText(this, if (enabled) "Ponteiro ativado" else "Ponteiro desativado", Toast.LENGTH_SHORT).show()
            refreshStatus()
        }
        addButton(root, "Velocidade do ponteiro", 8) {
            val next = when (prefs.getInt("speed", 12)) { 8 -> 12; 12 -> 18; else -> 8 }
            prefs.edit().putInt("speed", next).apply()
            Toast.makeText(this, "Velocidade: $next", Toast.LENGTH_SHORT).show()
        }
        addButton(root, "Abrir configurações de acessibilidade", 8) { openAccessibilitySettings() }

        val info = TextView(this).apply {
            text = "D-pad move o cursor • A/Center clica • B volta • X abre Home • Y abre Recentes\n\nO InputForge usa a API de acessibilidade do Android para operar como camada de input da TV."
            textSize = 14f
            setTextColor(Color.rgb(130, 138, 158))
            gravity = Gravity.CENTER
            setPadding(0, 26, 0, 0)
        }
        root.addView(info, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
        refreshStatus()
    }

    private fun addButton(root: LinearLayout, label: String, top: Int, action: () -> Unit) {
        val button = Button(this).apply {
            text = label
            textSize = 15f
            isAllCaps = false
            setOnClickListener { action() }
        }
        val lp = LinearLayout.LayoutParams(-1, 62)
        lp.topMargin = top
        root.addView(button, lp)
    }

    private fun refreshStatus() {
        val enabled = isAccessibilityEnabled()
        val cursor = prefs.getBoolean("cursor_mode", true)
        val speed = prefs.getInt("speed", 12)
        status.text = "${if (enabled) "● ACTIVE" else "○ NOT ACTIVE"}   •   Cursor ${if (cursor) "ON" else "OFF"}   •   Speed $speed"
        status.setTextColor(if (enabled) Color.rgb(120, 255, 180) else Color.rgb(255, 180, 120))
    }

    private fun isAccessibilityEnabled(): Boolean {
        val manager = getSystemService(AccessibilityManager::class.java) ?: return false
        if (!manager.isEnabled) return false
        val expected = ComponentName(this, InputForgeAccessibilityService::class.java)
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo?.let { info -> ComponentName(info.packageName, info.name) } == expected }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}
