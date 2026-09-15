package com.controlbridge.mobile

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
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
    private lateinit var deviceSpinner: Spinner
    private lateinit var status: TextView
    private lateinit var serviceStatus: TextView
    private val devices = mutableListOf<BluetoothDevice>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        requestBluetoothPermissions()
        startHidService()
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
        refreshDevices()
    }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissions = mutableListOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
            if (Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
            requestPermissions(permissions.toTypedArray(), 100)
        }
    }

    private fun startHidService() {
        val intent = Intent(this, HidGamepadService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(42, 50, 42, 36)
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
            text = "🎮 Controle Bluetooth → celular → TV"
            textSize = 17f
            setTextColor(Color.LTGRAY)
            setPadding(0, 8, 0, 24)
        }
        root.addView(subtitle)

        val explanation = TextView(this).apply {
            text = "O celular agora funciona como um gamepad Bluetooth real. A TV não precisa instalar nenhum aplicativo: ela recebe o ControlBridge como se fosse uma manete comum."
            textSize = 15f
            setTextColor(Color.LTGRAY)
            setPadding(0, 0, 0, 22)
        }
        root.addView(explanation)

        val hidTitle = TextView(this).apply {
            text = "📡  1. ATIVAR GAMEPAD DO CELULAR"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }
        root.addView(hidTitle)

        serviceStatus = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.LTGRAY)
            setPadding(0, 10, 0, 10)
        }
        root.addView(serviceStatus)

        val refresh = Button(this).apply {
            text = "ATUALIZAR DISPOSITIVOS PAREADOS"
            setOnClickListener { refreshDevices() }
        }
        root.addView(refresh)

        val pairHelp = TextView(this).apply {
            text = "Primeiro, deixe esta tela aberta. Na TV, abra Bluetooth/Dispositivos e procure por “ControlBridge Gamepad”. Faça o pareamento com o celular. Depois volte aqui e atualize."
            textSize = 14f
            setTextColor(Color.GRAY)
            setPadding(0, 8, 0, 14)
        }
        root.addView(pairHelp)

        val tvTitle = TextView(this).apply {
            text = "📺  2. ESCOLHER A TV"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }
        root.addView(tvTitle)

        deviceSpinner = Spinner(this)
        root.addView(deviceSpinner, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 10 })

        val connect = Button(this).apply {
            text = "CONECTAR COMO GAMEPAD"
            setOnClickListener { connectSelected() }
        }
        root.addView(connect, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 10 })

        val disconnect = Button(this).apply {
            text = "DESCONECTAR DA TV"
            setOnClickListener { sendServiceAction(HidGamepadService.ACTION_DISCONNECT) }
        }
        root.addView(disconnect)

        status = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.LTGRAY)
            setPadding(0, 14, 0, 18)
        }
        root.addView(status)

        val divider = View(this).apply { setBackgroundColor(Color.DKGRAY) }
        root.addView(divider, LinearLayout.LayoutParams(-1, 1))

        val accessibilityTitle = TextView(this).apply {
            text = "🎮  3. CAPTURAR A MANETE DO CELULAR"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, 22, 0, 6)
        }
        root.addView(accessibilityTitle)

        val accessibilityStatus = TextView(this).apply {
            text = "O serviço de acessibilidade captura os botões e analógicos globalmente e envia os dados para a TV."
            textSize = 14f
            setTextColor(Color.GRAY)
        }
        root.addView(accessibilityStatus)

        val accessibility = Button(this).apply {
            text = "ATIVAR CAPTURA DA MANETE"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        root.addView(accessibility, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 10 })

        val footer = TextView(this).apply {
            text = "\n⚡ Bluetooth HID • sem IP • sem APK na TV • baixa latência"
            textSize = 14f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
        }
        root.addView(footer)

        setContentView(root)
    }

    private fun refreshDevices() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            status.text = "🔴 Bluetooth não disponível"
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            status.text = "🟡 Conceda a permissão de dispositivos próximos"
            return
        }
        devices.clear()
        devices.addAll(adapter.bondedDevices.sortedBy { it.name ?: it.address })
        val labels = if (devices.isEmpty()) {
            listOf("Nenhum dispositivo pareado — pareie a TV primeiro")
        } else {
            devices.map { "${it.name ?: "Dispositivo Bluetooth"}\n${it.address}" }
        }
        deviceSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        status.text = if (devices.isEmpty()) "🟡 Nenhuma TV pareada ainda" else "🟢 ${devices.size} dispositivo(s) pareado(s)"
    }

    private fun connectSelected() {
        if (devices.isEmpty()) {
            status.text = "🔴 Pareie o ControlBridge Gamepad com a TV primeiro"
            return
        }
        val device = devices[deviceSpinner.selectedItemPosition]
        sendServiceAction(HidGamepadService.ACTION_CONNECT, device.address)
        status.text = "🟡 Conectando a ${device.name ?: "TV"}…"
    }

    private fun sendServiceAction(action: String, address: String? = null) {
        val intent = Intent(this, HidGamepadService::class.java).setAction(action)
        if (address != null) intent.putExtra(HidGamepadService.EXTRA_ADDRESS, address)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
    }

    private fun updateServiceStatus() {
        serviceStatus.text = "🟢 Serviço HID iniciado • mantenha o Bluetooth ligado"
    }
}
