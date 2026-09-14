package com.controlbridge.mobile

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private var tvIp = ""
    private var socket: DatagramSocket? = null
    private lateinit var status: TextView
    private lateinit var ip: EditText

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val box = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(40,50,40,40) }
        TextView(this).also { it.text="CONTROLBRIDGE"; it.textSize=28f; box.addView(it) }
        TextView(this).also { it.text="Controle Bluetooth → Wi‑Fi → Android TV"; it.textSize=16f; box.addView(it) }
        ip = EditText(this).apply { hint="IP da TV (ex.: 192.168.1.20)"; inputType=1 }
        box.addView(ip)
        val connect=Button(this).apply { text="CONECTAR À TV" }
        box.addView(connect)
        status=TextView(this).apply { text="🔴 Desconectado"; textSize=18f }
        box.addView(status)
        val hint=TextView(this).apply { text="Conecte seu controle ao celular nas configurações Bluetooth. Depois pressione qualquer botão para enviar o estado."; textSize=14f }
        box.addView(hint)
        setContentView(box)
        connect.setOnClickListener {
            tvIp=ip.text.toString().trim()
            if(tvIp.isNotEmpty()) { socket=DatagramSocket(); status.text="🟢 Pronto para enviar para $tvIp:47600" }
        }
        requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_SCAN),100)
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        send("KEY|${e.action}|${e.keyCode}|${e.repeatCount}")
        return super.dispatchKeyEvent(e)
    }
    override fun dispatchGenericMotionEvent(e: MotionEvent): Boolean {
        if((e.source and InputDevice.SOURCE_JOYSTICK)==InputDevice.SOURCE_JOYSTICK) {
            val d=e.device
            val payload=buildString {
                append("JOY|")
                append("%.3f|".format(e.getAxisValue(MotionEvent.AXIS_X)))
                append("%.3f|".format(e.getAxisValue(MotionEvent.AXIS_Y)))
                append("%.3f|".format(e.getAxisValue(MotionEvent.AXIS_Z)))
                append("%.3f".format(e.getAxisValue(MotionEvent.AXIS_RZ)))
            }
            send(payload)
        }
        return super.dispatchGenericMotionEvent(e)
    }
    private fun send(s:String) {
        if(tvIp.isEmpty()) return
        Thread {
            try {
                val data=s.toByteArray()
                socket?.send(DatagramPacket(data,data.size,InetAddress.getByName(tvIp),47600))
            } catch(_:Exception){}
        }.start()
    }
}
