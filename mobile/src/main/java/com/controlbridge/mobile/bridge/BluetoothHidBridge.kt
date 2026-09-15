package com.controlbridge.mobile.bridge

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class BluetoothHidBridge(private val context:Context){
    companion object{private fun d(vararg v:Int)=v.map{it.toByte()}.toByteArray();private val DESCRIPTOR=d(0x05,0x01,0x09,0x05,0xA1,0x01,0x85,0x01,0x09,0x01,0xA1,0x00,0x05,0x01,0x09,0x30,0x09,0x31,0x09,0x33,0x09,0x34,0x15,0x00,0x26,0xFF,0x00,0x75,0x08,0x95,0x04,0x81,0x02,0xC0,0x05,0x02,0x09,0xC5,0x09,0xC4,0x15,0x00,0x26,0xFF,0x00,0x75,0x08,0x95,0x02,0x81,0x02,0x05,0x01,0x09,0x39,0x15,0x00,0x25,0x07,0x35,0x00,0x46,0x3B,0x01,0x65,0x14,0x75,0x04,0x95,0x01,0x81,0x42,0x05,0x09,0x19,0x01,0x29,0x10,0x15,0x00,0x25,0x01,0x75,0x01,0x95,0x10,0x81,0x02,0xC0)}
    var onStatus:(String,Boolean)->Unit={_,_->};private var hid:BluetoothHidDevice?=null;private var host:BluetoothDevice?=null;private var registered=false;private var buttons=0;private var hat=8;private val axes=intArrayOf(128,128,128,128,0,0);private val executor=Executors.newSingleThreadExecutor()
    private val callback=object:BluetoothHidDevice.Callback(){override fun onAppStatusChanged(d:BluetoothDevice?,r:Boolean){registered=r;onStatus(if(r)"HID pronto"else"HID desligado",isConnected())};override fun onConnectionStateChanged(d:BluetoothDevice,s:Int){when(s){BluetoothProfile.STATE_CONNECTED->{host=d;onStatus("TV conectada",true);send()};BluetoothProfile.STATE_DISCONNECTED->{if(host?.address==d.address)host=null;onStatus("TV desconectada",false)}}}}
    @SuppressLint("MissingPermission") fun start(){if(Build.VERSION.SDK_INT<28){onStatus("Android sem HID Device",false);return};val a=(context.getSystemService(Context.BLUETOOTH_SERVICE)as BluetoothManager).adapter?:return;a.getProfileProxy(context,object:BluetoothProfile.ServiceListener{override fun onServiceConnected(p:Int,proxy:BluetoothProfile){hid=proxy as BluetoothHidDevice;hid?.registerApp(BluetoothHidDeviceAppSdpSettings("ControlBridge Gamepad","Gamepad remoto para Android TV","ControlBridge",0.toByte(),DESCRIPTOR),null,null,executor,callback)};override fun onServiceDisconnected(p:Int){hid=null}},BluetoothProfile.HID_DEVICE)}
    @SuppressLint("MissingPermission") fun bondedDevices():List<BluetoothDevice>=(context.getSystemService(Context.BLUETOOTH_SERVICE)as BluetoothManager).adapter?.bondedDevices?.toList().orEmpty()
    @SuppressLint("MissingPermission") fun connect(d:BluetoothDevice){host=d;hid?.connect(d);onStatus("Conectando à TV…",false)}
    fun isConnected()=host!=null
    @SuppressLint("MissingPermission") fun stop(){runCatching{hid?.unregisterApp()};executor.shutdownNow()}
    fun key(k:Int,down:Boolean){val b=when(k){19,20,21,22->-1;android.view.KeyEvent.KEYCODE_BUTTON_A->0;android.view.KeyEvent.KEYCODE_BUTTON_B->1;android.view.KeyEvent.KEYCODE_BUTTON_X->2;android.view.KeyEvent.KEYCODE_BUTTON_Y->3;android.view.KeyEvent.KEYCODE_BUTTON_L1->4;android.view.KeyEvent.KEYCODE_BUTTON_R1->5;android.view.KeyEvent.KEYCODE_BUTTON_L2->6;android.view.KeyEvent.KEYCODE_BUTTON_R2->7;android.view.KeyEvent.KEYCODE_BUTTON_SELECT->8;android.view.KeyEvent.KEYCODE_BUTTON_START->9;android.view.KeyEvent.KEYCODE_BUTTON_THUMBL->10;android.view.KeyEvent.KEYCODE_BUTTON_THUMBR->11;else->-1};if(b>=0){buttons=if(down)buttons or(1 shl b)else buttons and(1 shl b).inv();send()}}
    fun dpad(k:Int,down:Boolean){hat=if(!down)8 else when(k){android.view.KeyEvent.KEYCODE_DPAD_UP->0;android.view.KeyEvent.KEYCODE_DPAD_RIGHT->2;android.view.KeyEvent.KEYCODE_DPAD_DOWN->4;android.view.KeyEvent.KEYCODE_DPAD_LEFT->6;else->8};send()}
    fun motion(lx:Float,ly:Float,rx:Float,ry:Float,lt:Float,rt:Float){axes[0]=axis(lx);axes[1]=axis(ly);axes[2]=axis(rx);axes[3]=axis(ry);axes[4]=(lt.coerceIn(0f,1f)*255).roundToInt();axes[5]=(rt.coerceIn(0f,1f)*255).roundToInt();send()}
    private fun axis(v:Float)=((v.coerceIn(-1f,1f)+1f)*127.5f).roundToInt()
    @SuppressLint("MissingPermission") private fun send(){val d=host?:return;if(!registered)return;val r=byteArrayOf(axes[0].toByte(),axes[1].toByte(),axes[2].toByte(),axes[3].toByte(),axes[4].toByte(),axes[5].toByte(),hat.toByte(),(buttons and 255).toByte(),((buttons ushr 8)and 255).toByte());hid?.sendReport(d,1,r)}
}
