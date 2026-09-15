package com.controlbridge.mobile

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Toast
import com.controlbridge.core.InputPacket
import com.controlbridge.mobile.audio.UiSoundManager
import com.controlbridge.mobile.service.BridgeService
import com.controlbridge.mobile.ui.DashboardView

class MainActivity:Activity(){
 private lateinit var view:DashboardView;private lateinit var sound:UiSoundManager
 override fun onCreate(state:Bundle?){super.onCreate(state);window.setFlags(1024,1024);sound=UiSoundManager(this);view=DashboardView(this);setContentView(view);if(Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT),40)else startBridge();view.onAction={id->sound.click();when(id){"start"->startBridge();"hud"->{view.page=DashboardView.Page.HUD;sound.open()};"settings"->{view.page=DashboardView.Page.SETTINGS;sound.open()};"home"->{view.page=DashboardView.Page.HOME;sound.close()};"connect"->chooseTv()};view.invalidate()}}
 private fun startBridge(){if(BridgeService.instance==null){val i=Intent(this,BridgeService::class.java);if(Build.VERSION.SDK_INT>=26)startForegroundService(i)else startService(i)};window.decorView.postDelayed({sync()},700)}
 private fun sync(){BridgeService.instance?.let{s->view.ip=s.ip();view.tv=s.connected();s.onStatus={_,c->runOnUiThread{view.tv=c;view.invalidate()}};view.invalidate()}}
 private fun chooseTv(){val s=BridgeService.instance?:return;val d=s.devices();if(d.isEmpty()){Toast.makeText(this,"Pareie a TV com o celular no Bluetooth primeiro.",Toast.LENGTH_LONG).show();return};android.app.AlertDialog.Builder(this).setTitle("Conectar à TV").setItems(d.map{it.name?:it.address}.toTypedArray()){_,i->s.connect(d[i])}.setNegativeButton("Cancelar",null).show()}
 override fun dispatchKeyEvent(e:KeyEvent):Boolean{val s=BridgeService.instance;val game=e.device?.let{(it.sources and android.view.InputDevice.SOURCE_GAMEPAD)!=0||(it.sources and android.view.InputDevice.SOURCE_JOYSTICK)!=0}==true;if(game&&s!=null){s.input(InputPacket("key",key=e.keyCode,down=e.action==KeyEvent.ACTION_DOWN));view.controller=true;view.invalidate();return true};return super.dispatchKeyEvent(e)}
 override fun onGenericMotionEvent(e:MotionEvent):Boolean{val s=BridgeService.instance?:return super.onGenericMotionEvent(e);val d=e.device?:return super.onGenericMotionEvent(e);if((d.sources and android.view.InputDevice.SOURCE_JOYSTICK)==0)return super.onGenericMotionEvent(e);if(e.actionMasked==MotionEvent.ACTION_MOVE){val rx=if(d.hasMotionRange(MotionEvent.AXIS_RX))e.getAxisValue(MotionEvent.AXIS_RX)else e.getAxisValue(MotionEvent.AXIS_Z);val ry=if(d.hasMotionRange(MotionEvent.AXIS_RY))e.getAxisValue(MotionEvent.AXIS_RY)else e.getAxisValue(MotionEvent.AXIS_RZ);s.input(InputPacket("motion",lx=e.getAxisValue(MotionEvent.AXIS_X),ly=e.getAxisValue(MotionEvent.AXIS_Y),rx=rx,ry=ry,lt=e.getAxisValue(MotionEvent.AXIS_LTRIGGER),rt=e.getAxisValue(MotionEvent.AXIS_RTRIGGER)));view.controller=true;view.invalidate();return true};return super.onGenericMotionEvent(e)}
 override fun onRequestPermissionsResult(r:Int,p:Array<out String>,g:IntArray){super.onRequestPermissionsResult(r,p,g);if(r==40&&g.firstOrNull()==PackageManager.PERMISSION_GRANTED)startBridge()else if(r==40)Toast.makeText(this,"Bluetooth é necessário para a ponte.",Toast.LENGTH_LONG).show()}
 override fun onDestroy(){sound.release();super.onDestroy()}
}
