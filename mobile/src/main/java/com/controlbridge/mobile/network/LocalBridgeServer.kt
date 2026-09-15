package com.controlbridge.mobile.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Base64
import com.controlbridge.core.WsProtocol
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.*
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors

class LocalBridgeServer(private val context: Context) {
    companion object { const val PORT = 47600; const val SERVICE = "_controlbridge._tcp." }
    var onClientCount: (Int) -> Unit = {}
    private val pool = Executors.newCachedThreadPool(); private val clients = CopyOnWriteArraySet<Socket>()
    @Volatile private var running = false; private var server: ServerSocket? = null; private var registration: NsdManager.RegistrationListener? = null

    fun start() { if (running) return; running=true; pool.execute { acceptLoop() }; advertise() }
    fun stop() { running=false; clients.forEach { runCatching { it.close() } }; clients.clear(); runCatching { server?.close() }; registration?.let { runCatching { (context.getSystemService(Context.NSD_SERVICE) as NsdManager).unregisterService(it) } }; pool.shutdownNow() }
    fun broadcast(text: String) { val frame=WsProtocol.textFrame(text); clients.forEach { s -> runCatching { s.getOutputStream().apply { write(frame); flush() } }.onFailure { clients.remove(s); runCatching { s.close() } } } }
    fun localIp(): String = try { Collections.list(NetworkInterface.getNetworkInterfaces()).flatMap { Collections.list(it.inetAddresses) }.firstOrNull { !it.isLoopbackAddress && it is Inet4Address && it.isSiteLocalAddress }?.hostAddress ?: "127.0.0.1" } catch (_: Exception) { "127.0.0.1" }

    private fun acceptLoop() { try { server=ServerSocket(PORT); while(running) { val s=server!!.accept(); pool.execute { handshake(s) } } } catch (_:Exception){} }
    private fun handshake(socket: Socket) { try {
        val r=BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII)); var key:String?=null; var line:String?
        do { line=r.readLine(); if(line?.startsWith("Sec-WebSocket-Key:",true)==true) key=line.substringAfter(':').trim() } while(!line.isNullOrEmpty())
        if(key==null){socket.close();return}
        val response="HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: ${WsProtocol.acceptKey(key!!)}\r\n\r\n"
        socket.getOutputStream().apply { write(response.toByteArray(StandardCharsets.US_ASCII)); flush() }
        clients += socket; onClientCount(clients.size)
        while(running && WsProtocol.readTextFrame(socket.getInputStream()) != null) {}
    } catch(_:Exception){} finally { clients.remove(socket); onClientCount(clients.size); runCatching { socket.close() } }
    }
    private fun advertise() { val nsd=context.getSystemService(Context.NSD_SERVICE) as NsdManager; val info=NsdServiceInfo().apply { serviceName="ControlBridge-${android.os.Build.MODEL}"; serviceType=SERVICE; port=PORT }; val listener=object:NsdManager.RegistrationListener { override fun onServiceRegistered(i:NsdServiceInfo){ } override fun onRegistrationFailed(s:NsdServiceInfo,e:Int){} override fun onServiceUnregistered(s:NsdServiceInfo){} override fun onUnregistrationFailed(s:NsdServiceInfo,e:Int){} }; registration=listener; nsd.registerService(info,NsdManager.PROTOCOL_DNS_SD,listener) }
}
