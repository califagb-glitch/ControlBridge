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

class ControlBridgeActivity : Activity() {
    private lateinit var hud: Hud
    private val bridge = BridgeServer()
    private var controllerName = "Nenhuma manete detectada"
    private var connected = false
    private var lastSend = 0L

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); hud=Hud(); setContentView(hud); bridge.start(); hud.postDelayed({refreshController()},250) }
    override fun onDestroy(){bridge.stop();super.onDestroy()}
    override fun dispatchKeyEvent(event:KeyEvent):Boolean{if(!isGamepad(event.device))return super.dispatchKeyEvent(event);connected=true;controllerName=event.device?.name?:"Gamepad";hud.invalidate();sendKey(event.keyCode,event.action);return true}
    override fun onGenericMotionEvent(event:MotionEvent):Boolean{if(!isGamepad(event.device)||event.action!=MotionEvent.ACTION_MOVE)return super.onGenericMotionEvent(event);connected=true;controllerName=event.device?.name?:"Gamepad";val now=System.currentTimeMillis();if(now-lastSend>=8){lastSend=now;sendMotion(event)};hud.invalidate();return true}
    private fun sendKey(key:Int,action:Int){bridge.broadcast(JSONObject().apply{put("type","key");put("key",key);put("action",action);put("connected",true)}.toString())}
    private fun sendMotion(e:MotionEvent){bridge.broadcast(JSONObject().apply{put("type","motion");put("lx",axis(e,MotionEvent.AXIS_X));put("ly",axis(e,MotionEvent.AXIS_Y));put("rx",axis(e,MotionEvent.AXIS_Z));put("ry",axis(e,MotionEvent.AXIS_RZ));put("lt",axis01(e,MotionEvent.AXIS_LTRIGGER));put("rt",axis01(e,MotionEvent.AXIS_RTRIGGER));put("connected",true)}.toString())}
    private fun axis(e:MotionEvent,a:Int)=e.getAxisValue(a).coerceIn(-1f,1f)
    private fun axis01(e:MotionEvent,a:Int)=e.getAxisValue(a).coerceIn(0f,1f)
    private fun isGamepad(d:InputDevice?)=d!=null&&((d.sources and InputDevice.SOURCE_GAMEPAD)!=0||(d.sources and InputDevice.SOURCE_JOYSTICK)!=0)
    private fun refreshController(){for(id in InputDevice.getDeviceIds()){val d=InputDevice.getDevice(id);if(isGamepad(d)){connected=true;controllerName=d.name?:"Gamepad";break}};hud.invalidate()}

    private inner class Hud:View(this@ControlBridgeActivity){
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG);private val a=RectF();private val b=RectF();private val x=RectF();private val y=RectF();private var w=0f;private var cy=0f
        init{setBackgroundColor(Color.rgb(7,9,15));isFocusable=true}
        override fun onDraw(c:Canvas){w=width.toFloat();val h=height.toFloat();cy=h-150f;paint.typeface=android.graphics.Typeface.DEFAULT_BOLD;paint.color=Color.WHITE;paint.textSize=25f;c.drawText("CONTROLBRIDGE",28f,48f,paint);paint.typeface=android.graphics.Typeface.DEFAULT;paint.color=Color.rgb(135,151,180);paint.textSize=11f;c.drawText("PHONE CONTROLLER  •  LAN",29f,69f,paint);paint.color=Color.rgb(17,22,33);c.drawRoundRect(18f,92f,w-18f,178f,22f,22f,paint);paint.color=Color.rgb(115,150,255);paint.textSize=12f;c.drawText("●  CONEXÃO",34f,117f,paint);paint.color=Color.WHITE;paint.textSize=17f;c.drawText(controllerName,34f,144f,paint);paint.color=Color.rgb(150,160,178);paint.textSize=11f;c.drawText(bridge.url(),34f,165f,paint);paint.color=if(connected)Color.rgb(105,225,155)else Color.rgb(255,190,90);c.drawText(if(connected)"MANETE DETECTADA" else "CONECTE A MANETE PELO BLUETOOTH DO ANDROID",34f,192f,paint);paint.color=Color.rgb(12,16,24);c.drawRoundRect(18f,210f,w-18f,h-24f,22f,22f,paint);drawPad(c)}
        private fun drawPad(c:Canvas){paint.color=Color.rgb(30,39,56);c.drawCircle(125f,cy,62f,paint);c.drawCircle(w-125f,cy,62f,paint);paint.color=Color.rgb(80,95,120);c.drawCircle(125f,cy,26f,paint);c.drawCircle(w-125f,cy,26f,paint);a.set(w-185f,cy-75f,w-125f,cy-15f);b.set(w-115f,cy-115f,w-55f,cy-55f);x.set(w-255f,cy-115f,w-195f,cy-55f);y.set(w-185f,cy-155f,w-125f,cy-95f);drawButton(c,a,"A");drawButton(c,b,"B");drawButton(c,x,"X");drawButton(c,y,"Y")}
        private fun drawButton(c:Canvas,r:RectF,label:String){paint.color=Color.rgb(28,36,53);c.drawRoundRect(r,18f,18f,paint);paint.color=Color.WHITE;paint.textSize=18f;paint.textAlign=Paint.Align.CENTER;c.drawText(label,r.centerX(),r.centerY()+6f,paint);paint.textAlign=Paint.Align.LEFT}
        override fun onTouchEvent(e:MotionEvent):Boolean{connected=true;val px=e.x;val py=e.y;val action=if(e.actionMasked==MotionEvent.ACTION_UP)KeyEvent.ACTION_UP else if(e.actionMasked==MotionEvent.ACTION_DOWN)KeyEvent.ACTION_DOWN else return true;val key=when{a.contains(px,py)->KeyEvent.KEYCODE_BUTTON_A;b.contains(px,py)->KeyEvent.KEYCODE_BUTTON_B;x.contains(px,py)->KeyEvent.KEYCODE_BUTTON_X;y.contains(px,py)->KeyEvent.KEYCODE_BUTTON_Y;else->-1};if(key>=0)sendKey(key,action);invalidate();return true}
    }
}
