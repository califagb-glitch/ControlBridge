package com.controlbridge.mobile

import android.util.Base64
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors

class BridgeServer {
    companion object {
        const val HTTP_PORT = 8080
        const val WS_PORT = 47600
    }

    private val pool = Executors.newCachedThreadPool()
    private val clients = CopyOnWriteArraySet<Socket>()
    @Volatile private var running = false
    private var http: ServerSocket? = null
    private var ws: ServerSocket? = null

    fun start() {
        if (running) return
        running = true
        pool.execute { serveHttp() }
        pool.execute { serveWs() }
    }

    fun stop() {
        running = false
        clients.forEach { socket ->
            try { socket.close() } catch (_: Exception) {}
        }
        clients.clear()
        try { http?.close() } catch (_: Exception) {}
        try { ws?.close() } catch (_: Exception) {}
    }

    fun url(): String = "http://${localIp()}:$HTTP_PORT"

    fun broadcast(json: String) {
        val frame = frame(json)
        clients.forEach { socket ->
            try {
                socket.getOutputStream().apply {
                    write(frame)
                    flush()
                }
            } catch (_: Exception) {
                clients.remove(socket)
                try { socket.close() } catch (_: Exception) {}
            }
        }
    }

    private fun serveHttp() {
        try {
            http = ServerSocket(HTTP_PORT)
            while (running) {
                val socket = http?.accept() ?: break
                pool.execute { handleHttp(socket) }
            }
        } catch (_: Exception) {}
    }

    private fun handleHttp(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))
            reader.readLine()
            while (reader.readLine()?.isNotEmpty() == true) {}

            val body = html().toByteArray(StandardCharsets.UTF_8)
            val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Cache-Control: no-store\r\n" +
                "Connection: close\r\n\r\n"

            socket.getOutputStream().apply {
                write(header.toByteArray(StandardCharsets.US_ASCII))
                write(body)
                flush()
            }
        } catch (_: Exception) {
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun serveWs() {
        try {
            ws = ServerSocket(WS_PORT)
            while (running) {
                val socket = ws?.accept() ?: break
                pool.execute { handleWs(socket) }
            }
        } catch (_: Exception) {}
    }

    private fun handleWs(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))
            var key: String? = null
            var line: String?
            do {
                line = reader.readLine()
                if (line?.startsWith("Sec-WebSocket-Key:", ignoreCase = true) == true) {
                    key = line.substringAfter(":").trim()
                }
            } while (!line.isNullOrEmpty())

            if (key == null) {
                socket.close()
                return
            }

            val digest = MessageDigest.getInstance("SHA-1").digest(
                (key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray(StandardCharsets.US_ASCII)
            )
            val accept = Base64.encodeToString(digest, Base64.NO_WRAP)
            val response = "HTTP/1.1 101 Switching Protocols\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Accept: $accept\r\n\r\n"

            socket.getOutputStream().apply {
                write(response.toByteArray(StandardCharsets.US_ASCII))
                flush()
            }

            clients += socket
            while (running && socket.getInputStream().read() >= 0) {}
        } catch (_: Exception) {
        } finally {
            clients.remove(socket)
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun frame(text: String): ByteArray {
        val payload = text.toByteArray(StandardCharsets.UTF_8)
        val out = ByteArrayOutputStream(payload.size + 10)
        out.write(0x81)
        when {
            payload.size < 126 -> out.write(payload.size)
            payload.size <= 65535 -> {
                out.write(126)
                out.write(payload.size ushr 8)
                out.write(payload.size)
            }
            else -> {
                out.write(127)
                for (i in 7 downTo 0) out.write((payload.size.toLong() ushr (i * 8)).toInt())
            }
        }
        out.write(payload)
        return out.toByteArray()
    }

    private fun localIp(): String {
        return try {
            for (network in Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (address in Collections.list(network.inetAddresses)) {
                    if (!address.isLoopbackAddress && address is Inet4Address && address.isSiteLocalAddress) {
                        return address.hostAddress ?: "127.0.0.1"
                    }
                }
            }
            "127.0.0.1"
        } catch (_: Exception) {
            "127.0.0.1"
        }
    }

    private fun html(): String = """
        <!doctype html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width,initial-scale=1">
          <title>ControlBridge</title>
          <style>
            body{margin:0;background:#07090f;color:#fff;font:20px Arial;display:grid;place-items:center;min-height:100vh}
            .box{width:min(850px,90vw);padding:40px;background:#101522;border:1px solid #273047;border-radius:28px}
            .small{letter-spacing:4px;color:#8ca9d8}.title{font-size:46px;font-weight:800;margin:8px 0}.ok{color:#70e3a2}
            pre{color:#aeb8c8;font-size:15px}
          </style>
        </head>
        <body>
          <main class="box">
            <div class="small">CONTROLBRIDGE</div>
            <div class="title">TV Receiver</div>
            <div id="status" class="ok">● conectando...</div>
            <pre id="data">Aguardando controle...</pre>
          </main>
          <script>
            let socket;
            function connect(){
              socket=new WebSocket('ws://'+location.hostname+':47600');
              socket.onopen=()=>document.getElementById('status').textContent='● CONTROLE CONECTADO';
              socket.onclose=()=>{document.getElementById('status').textContent='● aguardando celular';setTimeout(connect,1000)};
              socket.onmessage=e=>{document.getElementById('data').textContent=JSON.stringify(JSON.parse(e.data),null,2)};
            }
            connect();
          </script>
        </body>
        </html>
    """.trimIndent()
}
