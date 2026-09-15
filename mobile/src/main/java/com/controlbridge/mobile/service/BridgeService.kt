package com.controlbridge.mobile.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.controlbridge.core.InputPacket
import com.controlbridge.mobile.R
import com.controlbridge.mobile.bridge.BluetoothHidBridge
import com.controlbridge.mobile.network.LocalBridgeServer

class BridgeService:Service(){
    companion object{@Volatile var instance:BridgeService?=null;const val CHANNEL="controlbridge_bridge";const val ID=701}
    private lateinit var hid:BluetoothHidBridge;private lateinit var server:LocalBridgeServer;var onStatus:(String,Boolean)->Unit={_,_->}
    override fun onCreate(){super.onCreate();instance=this;createChannel();server=LocalBridgeServer(this);hid=BluetoothHidBridge(this);hid.onStatus={s,c->onStatus(s,c)};server.start();hid.start();startForegroundCompat()}
    override fun onStartCommand(i:Intent?,f:Int,id:Int)=START_STICKY
    fun input(p:InputPacket){server.broadcast(p.json());if(p.type=="key"){if(p.key in 19..22)hid.dpad(p.key,p.down)else hid.key(p.key,p.down)}else if(p.type=="motion")hid.motion(p.lx,p.ly,p.rx,p.ry,p.lt,p.rt)}
    fun devices()=hid.bondedDevices();fun connect(d:android.bluetooth.BluetoothDevice)=hid.connect(d);fun ip()=server.localIp();fun connected()=hid.isConnected()
    override fun onBind(i:Intent?):IBinder?=null
    override fun onDestroy(){server.stop();hid.stop();instance=null;super.onDestroy()}
    private fun createChannel(){if(Build.VERSION.SDK_INT>=26)getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"ControlBridge",NotificationManager.IMPORTANCE_LOW))}
    private fun startForegroundCompat(){val n=NotificationCompat.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).setContentTitle("ControlBridge ativo").setContentText("Ponte de controle pronta para a TV").setOngoing(true).build();if(Build.VERSION.SDK_INT>=29)startForeground(ID,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)else startForeground(ID,n)}
}
