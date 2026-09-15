package com.controlbridge.mobile

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.*
import java.util.concurrent.Executors
import java.util.regex.Pattern

class MainActivity : Activity() {
    private lateinit var controllerStatus: TextView
    private lateinit var tvStatus: TextView
    private lateinit var logText: TextView
    private lateinit var companionManager: CompanionDeviceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        companionManager = getSystemService(COMPANION_DEVICE_SERVICE) as CompanionDeviceManager
        buildHud()
        requestBluetoothPermissions()
        startBridgeService()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
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

    private fun startBridgeService() {
        val intent = Intent(this, HidGamepadService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
    }

    private fun buildHud() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 34, 28, 30)
            setBackgroundColor(Color.rgb(7, 9, 15))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val logo = TextView(this).apply {
            text = "CB"; textSize = 25f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(35, 95, 180))
        }
        header.addView(logo, LinearLayout.LayoutParams(58, 58))
        val titleBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 0, 0, 0) }
        titleBox.addView(TextView(this).apply { text = "CONTROLBRIDGE"; textSize = 25f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE) })
        titleBox.addView(TextView(this).apply { text = "GAMEPAD BRIDGE  •  BLUETOOTH"; textSize = 11f; setTextColor(Color.rgb(130, 160, 205)) })
        header.addView(titleBox, LinearLayout.LayoutParams(0, -2, 1f)); root.addView(header)

        val pulse = TextView(this).apply { text = "●  PRONTO PARA CONECTAR"; textSize = 12f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.rgb(90, 220, 150)); setPadding(0, 18, 0, 20) }
        root.addView(pulse)
        pulse.startAnimation(AlphaAnimation(0.45f, 1f).apply { duration = 1100; repeatCount = Animation.INFINITE; repeatMode = Animation.REVERSE })

        root.addView(card("🎮  MANETE", "Conecte a manete ao celular primeiro. O app não ativa o HID da TV enquanto você faz isso."))
        controllerStatus = statusText("⚪ Nenhuma manete selecionada"); root.addView(controllerStatus)
        val pair = actionButton("CONECTAR MANETE", Color.rgb(28, 92, 180)); pair.setOnClickListener { pairController() }; root.addView(pair)
        val accessibility = actionButton("ATIVAR CAPTURA DOS BOTÕES + ANALÓGICOS", Color.rgb(38, 45, 58)); accessibility.setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }; root.addView(accessibility)

        root.addView(card("📺  TV SAMSUNG", "Quando a manete já estiver funcionando no celular, ative o modo TV e pareie o ControlBridge Gamepad com a TV."))
        tvStatus = statusText("⚪ Modo TV desligado"); root.addView(tvStatus)
        val tvButton = actionButton("ATIVAR MODO TV", Color.rgb(28, 92, 180)); tvButton.setOnClickListener { startTvMode() }; root.addView(tvButton)
        val tvSettings = actionButton("ABRIR CONFIGURAÇÕES BLUETOOTH", Color.rgb(38, 45, 58)); tvSettings.setOnClickListener { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }; root.addView(tvSettings)

        logText = TextView(this).apply { textSize = 12f; setTextColor(Color.rgb(125, 135, 150)); setPadding(4, 22, 4, 0); gravity = Gravity.CENTER; text = "ControlBridge • sem APK na TV • baixa latência" }
        root.addView(logText)
        setContentView(root); animateIn(root)
    }

    private fun animateIn(root: View) {
        root.alpha = 0f; root.translationY = 24f
        root.animate().alpha(1f).translationY(0f).setDuration(420).start()
    }

    private fun card(title: String, body: String): LinearLayout {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 16, 18, 16); setBackgroundColor(Color.rgb(17, 21, 30)) }
        box.addView(TextView(this).apply { text = title; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE) })
        box.addView(TextView(this).apply { text = body; textSize = 12f; setTextColor(Color.rgb(165, 172, 185)); setPadding(0, 6, 0, 0) })
        box.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 12 }; return box
    }

    private fun statusText(text: String): TextView = TextView(this).apply { this.text = text; textSize = 13f; setTextColor(Color.rgb(180, 188, 200)); setPadding(18, 12, 18, 10) }

    private fun actionButton(text: String, color: Int): Button = Button(this).apply {
        this.text = text; textSize = 12f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); setBackgroundColor(color); isAllCaps = false
        layoutParams = LinearLayout.LayoutParams(-1, 54).apply { topMargin = 8 }
    }

    private fun pairController() {
        if (Build.VERSION.SDK_INT < 26) { logText.text = "Android 8+ é necessário para o pareamento assistido."; return }
        try {
            val filter = BluetoothDeviceFilter.Builder().setNamePattern(Pattern.compile(".*", Pattern.CASE_INSENSITIVE)).build()
            val request = AssociationRequest.Builder().addDeviceFilter(filter).setSingleDevice(false).build()
            companionManager.associate(request, Executors.newSingleThreadExecutor(), object : CompanionDeviceManager.Callback() {
                override fun onAssociationPending(chooserLauncher: android.content.IntentSender) {
                    runOnUiThread { try { startIntentSenderForResult(chooserLauncher, REQUEST_SELECT_DEVICE, null, 0, 0, 0) } catch (_: Exception) { logText.text = "Não foi possível abrir a seleção Bluetooth." } }
                }
                override fun onDeviceFound(chooserLauncher: android.content.IntentSender) { onAssociationPending(chooserLauncher) }
                override fun onAssociationCreated(associationInfo: android.companion.AssociationInfo) { runOnUiThread { logText.text = "Pareamento autorizado pelo Android. Conectando…" } }
                override fun onFailure(error: CharSequence?) { runOnUiThread { logText.text = "Falha ao procurar manete: ${error ?: "nenhum dispositivo encontrado"}" } }
            })
            controllerStatus.text = "🟡 Procurando manetes Bluetooth próximas…"
            logText.text = "Coloque a manete em modo de pareamento e escolha-a na janela do Android."
        } catch (e: Exception) { logText.text = "Não foi possível iniciar o pareamento: ${e.message ?: "erro"}" }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_SELECT_DEVICE || resultCode != RESULT_OK || data == null) return
        val device = data.getParcelableExtra<BluetoothDevice>(CompanionDeviceManager.EXTRA_DEVICE) ?: run { logText.text = "O Android não retornou a manete selecionada."; return }
        try { if (device.bondState != BluetoothDevice.BOND_BONDED) device.createBond() } catch (_: SecurityException) { }
        controllerStatus.text = "🟢 Manete: ${device.name ?: "Wireless Controller"} • pareamento iniciado"
        logText.text = "Agora ative a captura de acessibilidade. O modo TV só será ativado quando você mandar."
    }

    private fun startTvMode() {
        val intent = Intent(this, HidGamepadService::class.java).setAction(HidGamepadService.ACTION_START_HID)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        tvStatus.text = "🟡 Ativando ControlBridge Gamepad…"
        logText.text = "Depois, na TV, procure e pareie o dispositivo ‘ControlBridge Gamepad’."
        tvStatus.animate().translationX(8f).setDuration(120).withEndAction { tvStatus.animate().translationX(0f).setDuration(120).start() }.start()
    }

    private fun refreshStatus() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) { logText.text = "Este celular não possui Bluetooth."; return }
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) logText.text = "Conceda a permissão ‘Dispositivos próximos’."
    }

    companion object { private const val REQUEST_SELECT_DEVICE = 401 }
}
