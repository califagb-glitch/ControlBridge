package com.controlbridge.mobile

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import org.json.JSONObject

class MainActivityFixed : Activity() {
    private lateinit var hud: Hud
    private val bridge = BridgeServer()
    private var controllerName = "Nenhuma manete detectada"
    private var connected = false
    private var lastSend = 0L

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); hud=Hud(); setContentView(hud); bridge.start(); hud.postDelayed({refresh()},250) }
    override fun onDestroy(){bridge.stop();super.onDestroy()}
    override fun dispatchKeyEvent(e:KeyEvent):Boolean { if(!gamepad(e.device))return super.dispatchKeyEvent(e);connected=true;controllerName=e.device?.name?:"Gamepad";hud.invalidate();bridge.broadcast(JSONObject().apply{put("type","key");put("key",e.keyCode);put("action",e.action);put("connected",true)}.toString());return true }
    override fun onGenericMotionEvent(e:MotionEvent):Boolean { if(!gamepad(e.device)||e.action!=MotionEvent.ACTION_MOVE)return super.onGenericMotionEvent(e);connected=true;controllerName=e.device?.name?:"Gamepad";val n=System.currentTimeMillis();if(n-lastSend>=8){lastSend=n;bridge.broadcast(JSONObject().apply{put("type","motion");put("lx",axis(e,MotionEvent.AXIS_X));put("ly",axis(e,MotionEvent.AXIS_Y));put("rx",axis(e,MotionEvent.AXIS_Z));put("ry",axis(e,MotionEvent.AXIS_RZ));put("lt",axis01(e,MotionEvent.AXIS_LTRIGGER));put("rt",axis01(e,MotionEvent.AXIS_RTRIGGER);put("connected",true)}.toString())};hud.invalidate();return true }
    private fun axis(e:MotionEvent,a:Int)=e.getAxisValue(a).coerceIn(-1f,1f)
    private fun axis01(e:MotionEvent,a:Int)=e.getAxisValue(a).coerceIn(0f,1f)
    private fun gamepad(d:InputDevice?)=d!=null&&((d.sources and InputDevice.SOURCE_GAMEPAD)!=0||(d.sources and InputDevice.SOURCE_JOYSTICK)!=0)
    private fun refresh(){for(id in InputDevice.getDeviceIds()){val d=InputDevice.getDevice(id);if(gamepad(d)){connected=true;controllerName=d.name?:"Gamepad";break}};hud.invalidate()}

    private inner class Hud:View(this@MainActivityFixed){
        private val p=Paint(Paint.ANTI_ALIAS_FLAG);private val a=RectF();private val b=RectF();private val x=RectF();private val y=RectF()
        init{setBackgroundColor(Color.rgb(7,9,15));isFocusable=true}
        override fun onDraw(c:Canvas){val w=width.toFloat();val h=height.toFloat();p.typeface=android.graphics.Typeface.DEFAULT_BOLD;p.color=Color.WHITE;p.textSize=25f;c.drawText("CONTROLBRIDGE",28f,48f,p);p.typeface=android.graphics.Typeface.DEFAULT;p.color=Color.rgb(135,151,180);p.textSize=11f;c.drawText("PHONE CONTROLLER  •  LAN",29f,69f,p);p.color=Color.rgb(17,22,33);c.drawRoundRect(18f,92f,w-18f,178f,22f,22f,p);p.color=Color.rgb(115,150,255);p.textSize=12f;c.drawText("●  CONEXÃO",34f,117f,p);p.color=Color.WHITE;p.textSize=17f;c.drawText(controllerName,34f,144f,p);p.color=Color.rgb(150,160,178);p.textSize=11f;c.drawText(bridge.url(),34f,165f,p);p.color=if(connected)Color.rgb(105,225,155)else Color.rgb(255,190,90);p.textSize=11f;c.drawText(if(connected)"MANETE DETECTADA" else "CONECTE A MANETE PELO BLUETOOTH DO ANDROID",34f,192f,p);p.color=Color.rgb(12,16,24);c.drawRoundRect(18f,210f,w-18f,h-24f,22f,22f,p);drawPad(c,w,h)}
        private fun drawPad(c:Canvas,w:Float,h:Float){val cy=h-150f;p.color=Color.rgb(30,39,56);c.drawCircle(125f,cy,62f,p);c.drawCircle(w-125f,cy,62f,p);p.color=Color.rgb(80,95,120);c.drawCircle(125f,cy,26f,p);c.drawCircle(w-125f,cy,26f,p);a.set(w-185f,cy-75f,w-125f,cy-15f);b.set(w-115f,cy-115f,w-55f,cy-55f);x.set(w-255f,cy-115f,w-195f,cy-55f);y.set(w-185f,cy-155f,w-125f,cy-95f);btn(c,a,"A");btn(c,b,"B");btn(c,x,"X");btn(c,y,"Y");p.color=Color.rgb(120,132,155);p.textSize=12f;c.drawText("TOQUE PARA CONTROLAR",w/2-70f,230f,p)}
        private fun btn(c:Canvas,r:RectF,s:String){p.color=Color.rgb(28,36,53);c.drawRoundRect(r,18f,18f,p);p.color=Color.WHITE;p.textSize=18f;p.textAlign=Paint.Align.CENTER;c.drawText(s,r.centerX(),r.centerY()+6f,p);p.textAlign=Paint.Align.LEFT}
        override fun onTouchEvent(e:MotionEvent):Boolean{connected=true;invalidate();return true}
    }
}
