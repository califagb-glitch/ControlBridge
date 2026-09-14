package com.controlbridge.tv

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.widget.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import kotlin.concurrent.thread

class MainActivity: Activity() {
    private lateinit var status: TextView
    private lateinit var log: TextView
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(60,50,60,50)}
        TextView(this).also{it.text="CONTROLBRIDGE TV";it.textSize=30f;box.addView(it)}
        status=TextView(this).apply{ text="🟢 Aguardando celular na porta UDP 47600";textSize=20f;setTextColor(Color.GREEN)}
        box.addView(status)
        log=TextView(this).apply{text="Nenhum comando recebido.";textSize=18f}
        box.addView(log)
        setContentView(box)
        thread { listen() }
    }
    private fun listen() {
        try {
            val s=DatagramSocket(47600)
            val buf=ByteArray(1024)
            while(true) {
                val p=DatagramPacket(buf,buf.size);s.receive(p)
                val msg=String(p.data,0,p.length)
                runOnUiThread { status.text="🟢 Celular conectado: ${p.address.hostAddress}";log.text=msg }
            }
        } catch(e:Exception) { runOnUiThread{status.text="🔴 Erro: ${e.message}"} }
    }
}
