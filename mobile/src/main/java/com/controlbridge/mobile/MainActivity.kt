package com.controlbridge.mobile

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    private lateinit var ip: EditText
    private lateinit var status: TextView
    private lateinit var serviceStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                ), 100
            )
        }
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 56, 48, 40)
            setBackgroundColor(Color.rgb(10, 12, 18))
        }

        val title = TextView(this).apply {
            text = "CONTROLBRIDGE"
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }
        root.addView(title)

        val subtitle = TextView(this).apply {
            text = "Seu controle Bluetooth → celular → TV"
            textSize = 16f
            setTextColor(Color.LTGRAY)
            setPadding(0, 8, 0, 28)
        }
        root.addView(subtitle)

        val cardTitle = TextView(this).apply {
            text = "📺  CONEXÃO COM A TV"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }
        root.addView(cardTitle)

        ip = EditText(this).apply {
            hint = "IP da TV  •  ex.: 192.168.1.20"
            text = getSharedPreferences(ControllerAccessibilityService.PREFS, MODE_PRIVATE)
                .getString(ControllerAccessibilityService.KEY_TV_IP, "")
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setPadding(24, 18, 24, 18)
        }
        root.addView(ip, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 12 })

        val connect = Button(this).apply {
            text = "CONECTAR E SALVAR"
            setOnClickListener { saveIp() }
        }
        root.addView(connect, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 12 })

        status = TextView(this).apply {
            text = "🔴 TV não configurada"
            textSize = 16f
            setTextColor(Color.LTGRAY)
            setPadding(0, 14, 0, 20)
        }
        root.addView(status)

        val divider = View(this).apply { setBackgroundColor(Color.DKGRAY) }
        root.addView(divider, LinearLayout.LayoutParams(-1, 1))

        val serviceTitle = TextView(this).apply {
            text = "🎮  CONTROLE EM SEGUNDO PLANO"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, 22, 0, 6)
        }
        root.addView(serviceTitle)

        serviceStatus = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.LTGRAY)
        }
        root.addView(serviceStatus)

        val accessibility = Button(this).apply {
            text = "ATIVAR CONTROLBRIDGE EM ACESSIBILIDADE"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        root.addView(accessibility, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 12 })

        val info = TextView(this).apply {
            text = "Depois de ativar, o ControlBridge poderá receber os botões do controle mesmo quando esta tela não estiver aberta. No Android 14+, também tentamos capturar os analógicos globalmente."
            textSize = 14f
            setTextColor(Color.GRAY)
            setPadding(0, 14, 0, 0)
        }
        root.addView(info)

        val footer = TextView(this).apply {
            text = "\n⚡ Baixa latência • Wi‑Fi local • UDP"
            textSize = 14f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
        }
        root.addView(footer)

        setContentView(root)
    }

    private fun saveIp() {
        val value = ip.text.toString().trim()
        if (value.isBlank()) {
            status.text = "🔴 Digite o IP da TV"
            return
        }
        getSharedPreferences(ControllerAccessibilityService.PREFS, MODE_PRIVATE)
            .edit()
            .putString(ControllerAccessibilityService.KEY_TV_IP, value)
            .apply()
        status.text = "🟢 TV configurada: $value:47600"
    }

    private fun updateServiceStatus() {
        serviceStatus.text = if (isAccessibilityEnabled()) {
            "🟢 Captura global ATIVA"
        } else {
            "🔴 Captura global desativada"
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val manager = getSystemService(ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        return manager.getEnabledAccessibilityServiceList(
            android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        ).any { it.resolveInfo.serviceInfo.packageName == packageName }
    }
}
