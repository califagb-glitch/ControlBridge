package com.controlbridge.tv

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Collections
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var log: TextView
    @Volatile private var running = true
    private var socket: Socket? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 50, 60, 50)
            setBackgroundColor(Color.rgb(7, 9, 14))
        }
        TextView(this).also {
            it.text = "CONTROLBRIDGE TV"
            it.textSize = 30f
            it.setTextColor(Color.WHITE)
            box.addView(it)
        }
        status = TextView(this).apply {
            text = "🟡 Aguardando celular..."
            textSize = 20f
            setTextColor(Color.rgb(255, 190, 90))
        }
        box.addView(status)
        log = TextView(this).apply {
            text = "Conecte o celular à mesma rede Wi-Fi."
            textSize = 18f
            setTextColor(Color.LTGRAY)
        }
        box.addView(log)
        setContentView(box)

        thread(isDaemon = true, name = "ControlBridge-TV") { connectLoop() }
    }

    override fun onDestroy() {
        running = false
        try { socket?.close() } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun connectLoop() {
        while (running) {
            try {
                val bridgeIp = waitForBridgeIp() ?: run {
                    updateStatus("🟡 Procurando celular na rede...", Color.rgb(255, 190, 90))
                    Thread.sleep(1500)
                    return@run null
                }

                updateStatus("🟡 Conectando ao celular: $bridgeIp", Color.rgb(255, 190, 90))
                val s = Socket(bridgeIp, 47600)
                socket = s
                sendWebSocketHandshake(s, bridgeIp)
                readWebSocket(s)
            } catch (_: Exception) {
                try { socket?.close() } catch (_: Exception) {}
                socket = null
                updateStatus("🟡 Aguardando/reconectando...", Color.rgb(255, 190, 90))
                Thread.sleep(1000)
            }
        }
    }

    private fun waitForBridgeIp(): String? {
        // The phone's HTTP endpoint is intentionally used as the discovery
        // mechanism. Android TV can discover the phone with mDNS/NSD later;
        // for now, scan the local /24 for port 8080 with short timeouts.
        val local = localIpv4() ?: return null
        val prefix = local.substringBeforeLast('.')
        for (last in 1..254) {
            if (!running) return null
            val host = "$prefix.$last"
            try {
                Socket().use { probe ->
                    probe.connect(java.net.InetSocketAddress(host, 8080), 35)
                }
                return host
            } catch (_: Exception) { }
        }
        return null
    }

    private fun sendWebSocketHandshake(s: Socket, host: String) {
        val key = android.util.Base64.encodeToString(
            "controlbridge-tv".toByteArray(StandardCharsets.US_ASCII),
            android.util.Base64.NO_WRAP
        ).take(16).padEnd(16, 'A')
        val request = "GET / HTTP/1.1\r\n" +
            "Host: $host:47600\r\n" +
            "Upgrade: websocket\r\n" +
            "Connection: Upgrade\r\n" +
            "Sec-WebSocket-Key: $key\r\n" +
            "Sec-WebSocket-Version: 13\r\n\r\n"
        s.getOutputStream().write(request.toByteArray(StandardCharsets.US_ASCII))
        s.getOutputStream().flush()
    }

    private fun readWebSocket(s: Socket) {
        val input = s.getInputStream()
        val header = BufferedReader(InputStreamReader(input, StandardCharsets.US_ASCII))
        var line: String?
        var accepted = false
        do {
            line = header.readLine()
            if (line?.startsWith("HTTP/1.1 101") == true) accepted = true
        } while (!line.isNullOrEmpty())
        if (!accepted) throw IllegalStateException("WebSocket handshake recusado")

        updateStatus("🟢 CELULAR CONECTADO", Color.rgb(91, 231, 160))
        while (running && !s.isClosed) {
            val first = input.read()
            if (first < 0) break
            val second = input.read()
            if (second < 0) break
            val opcode = first and 0x0F
            if (opcode == 0x8) break
            val masked = (second and 0x80) != 0
            var length = second and 0x7F
            if (length == 126) {
                length = (input.read() shl 8) or input.read()
            } else if (length == 127) {
                repeat(8) { input.read() }
                throw IllegalStateException("Frame WebSocket grande demais")
            }
            val mask = if (masked) ByteArray(4) { input.read().toByte() } else null
            val payload = ByteArray(length)
            var offset = 0
            while (offset < length) {
                val n = input.read(payload, offset, length - offset)
                if (n < 0) break
                offset += n
            }
            if (mask != null) for (i in payload.indices) {
                payload[i] = (payload[i].toInt() xor (mask[i % 4].toInt() and 0xFF)).toByte()
            }
            if (opcode == 0x1) handleMessage(String(payload, StandardCharsets.UTF_8))
        }
    }

    private fun handleMessage(raw: String) {
        try {
            val json = JSONObject(raw)
            val type = json.optString("type")
            val summary = when (type) {
                "key" -> "KEY ${json.optInt("key")} • ${if (json.optInt("action") == 0) "DOWN" else "UP"}"
                "motion" -> "LX ${"%.2f".format(json.optDouble("lx"))}  LY ${"%.2f".format(json.optDouble("ly"))}  " +
                    "RX ${"%.2f".format(json.optDouble("rx"))}  RY ${"%.2f".format(json.optDouble("ry"))}"
                else -> raw
            }
            runOnUiThread { log.text = summary }
        } catch (_: Exception) {
            runOnUiThread { log.text = raw }
        }
    }

    private fun updateStatus(text: String, color: Int) {
        runOnUiThread {
            status.text = text
            status.setTextColor(color)
        }
    }

    private fun localIpv4(): String? {
        return try {
            for (network in Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (address in Collections.list(network.inetAddresses)) {
                    if (!address.isLoopbackAddress && address is Inet4Address && address.isSiteLocalAddress) {
                        return address.hostAddress
                    }
                }
            }
            null
        } catch (_: Exception) { null }
    }
}
