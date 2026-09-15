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
    companion object { const val HTTP_PORT = 8080; const val WS_PORT = 47600 }
    private val pool = Executors.newCachedThreadPool()
    private val clients = CopyOnWriteArraySet<Socket>()
    @Volatile private var running = false
    private var http: ServerSocket? = null
    private var ws: ServerSocket? = null

    fun start() { if(running)return; running=true; pool.execute{serveHttp()}; pool.execute{serveWs()} }
    fun stop(){ running=false; clients.forEach{try{it.close()}catch(_:Exception){}}; clients.clear(); try{http?.close()}catch(_:Exception){}; try{ws?.close()}catch(_:Exception){} }
    fun url()="http://${localIp()}:$HTTP_PORT"
    fun broadcast(json:String){ val f=frame(json); clients.forEach{s->try{s.getOutputStream().apply{write(f);flush()}}catch(_:Exception){clients.remove(s);try{s.close()}catch(_:Exception){}}} }

    private fun serveHttp(){try{http=ServerSocket(HTTP_PORT);while(running)pool.execute{handleHttp(http!!.accept())}}catch(_:Exception){}}
    private fun handleHttp(s:Socket){try{val r=BufferedReader(InputStreamReader(s.getInputStream(),StandardCharsets.US_ASCII));r.readLine();while(r.readLine()?.isNotEmpty()==true){};val b=HTML.toByteArray(StandardCharsets.UTF_8);val h="HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${b.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n";s.getOutputStream().apply{write(h.toByteArray(StandardCharsets.US_ASCII));write(b);flush()}}catch(_:Exception){}finally{try{s.close()}catch(_:Exception){}}}
    private fun serveWs(){try{ws=ServerSocket(WS_PORT);while(running)pool.execute{handleWs(ws!!.accept())}}catch(_:Exception){}}
    private fun handleWs(s:Socket){try{val r=BufferedReader(InputStreamReader(s.getInputStream(),StandardCharsets.US_ASCII));var key:String?=null;var line:String?;do{line=r.readLine();if(line?.startsWith("Sec-WebSocket-Key:",true)==true)key=line!!.substringAfter(":").trim()}while(!line.isNullOrEmpty());if(key==null){s.close();return};val d=MessageDigest.getInstance("SHA-1").digest((key+"258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray(StandardCharsets.US_ASCII));val a=Base64.encodeToString(d,Base64.NO_WRAP);val response="HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: $a\r\n\r\n";s.getOutputStream().apply{write(response.toByteArray(StandardCharsets.US_ASCII));flush()};clients+=s;while(running&&s.getInputStream().read()>=0){}}catch(_:Exception){}finally{clients.remove(s);try{s.close()}catch(_:Exception){}}}
    private fun frame(text:String):ByteArray{val p=text.toByteArray(StandardCharsets.UTF_8);val o=ByteArrayOutputStream(p.size+10);o.write(0x81);when{p.size<126->o.write(p.size);p.size<=65535->{o.write(126);o.write(p.size ushr 8);o.write(p.size)};else->{o.write(127);for(i in 7 downTo 0)o.write((p.size.toLong() ushr(i*8)).toInt())}};o.write(p);return o.toByteArray()}
    private fun localIp():String{try{for(n in Collections.list(NetworkInterface.getNetworkInterfaces()))for(a in Collections.list(n.inetAddresses))if(!a.isLoopbackAddress&&a is Inet4Address&&a.isSiteLocalAddress)return a.hostAddress?:"127.0.0.1"}catch(_:Exception){};return"127.0.0.1"}
    private val HTML="""<!doctype html><html><head><meta name=viewport content='width=device-width,initial-scale=1'><title>ControlBridge</title><style>body{margin:0;background:#07090f;color:#fff;font:20px Arial;display:grid;place-items:center;min-height:100vh}.box{width:min(850px,90vw);padding:40px;background:#101522;border:1px solid #273047;border-radius:28px}.small{letter-spacing:4px;color:#8ca9d8}.title{font-size:46px;font-weight:800;margin:8px 0}.ok{color:#70e3a2}pre{color:#aeb8c8;font-size:15px}</style></head><body><main class=box><div class=small>CONTROLBRIDGE</div><div class=title>TV Receiver</div><div id=s class=ok>● conectando…</div><pre id=d>Aguardando controle…</pre></main><script>let w;function c(){w=new WebSocket('ws://'+location.hostname+':47600');w.onopen=()=>s.textContent='● CONTROLE CONECTADO';w.onclose=()=>{s.textContent='● aguardando celular';setTimeout(c,1000)};w.onmessage=e=>d.textContent=JSON.stringify(JSON.parse(e.data),null,2)}c()</script></body></html>"""
}
