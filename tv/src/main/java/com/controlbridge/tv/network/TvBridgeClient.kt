package com.controlbridge.tv.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.controlbridge.core.InputPacket
import com.controlbridge.core.WsProtocol
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import java.util.concurrent.Executors

class TvBridgeClient(private val context:Context){
    companion object{const val SERVICE="_controlbridge._tcp."}
    var onState:(String)->Unit={}; var onPacket:(InputPacket)->Unit={}; private var socket:Socket?=null; private var listener:NsdManager.DiscoveryListener?=null; private val pool=Executors.newCachedThreadPool()
    fun start(){val nsd=context.getSystemService(Context.NSD_SERVICE) as NsdManager;val l=object:NsdManager.DiscoveryListener{override fun onDiscoveryStarted(s:String){};override fun onServiceFound(info:NsdServiceInfo){if(info.serviceType==SERVICE||info.serviceType.trimEnd('.')==SERVICE.trimEnd('.')) nsd.resolveService(info,object:NsdManager.ResolveListener{override fun onResolveFailed(i:NsdServiceInfo,e:Int){};override fun onServiceResolved(i:NsdServiceInfo){connect(i.host.hostAddress?:return,i.port)}})};override fun onServiceLost(i:NsdServiceInfo){};override fun onDiscoveryStopped(s:String){};override fun onStartDiscoveryFailed(s:String,e:Int){};override fun onStopDiscoveryFailed(s:String,e:Int){}};listener=l;nsd.discoverServices(SERVICE,NsdManager.PROTOCOL_DNS_SD,l)}
    fun stop(){listener?.let{runCatching{(context.getSystemService(Context.NSD_SERVICE) as NsdManager).stopServiceDiscovery(it)}};runCatching{socket?.close()};pool.shutdownNow()}
    private fun connect(host:String,port:Int){if(socket?.isConnected==true)return;pool.execute{try{onState("Conectando…");val s=Socket(host,port);socket=s;val key=Base64.getEncoder().encodeToString(UUID.randomUUID().toString().toByteArray());val req="GET / HTTP/1.1\r\nHost: $host:$port\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Key: $key\r\n\r\n";s.getOutputStream().apply{write(req.toByteArray(StandardCharsets.US_ASCII));flush()};val r=BufferedReader(InputStreamReader(s.getInputStream(),StandardCharsets.US_ASCII));var line:String?;do{line=r.readLine()}while(!line.isNullOrEmpty());onState("TV sincronizada");while(true){val raw=WsProtocol.readTextFrame(s.getInputStream())?:break;InputPacket.parse(raw)?.let(onPacket)}}catch(_:Exception){onState("Aguardando celular")}}}
}
