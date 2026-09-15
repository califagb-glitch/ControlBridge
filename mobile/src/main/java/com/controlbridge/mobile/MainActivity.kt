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

class MainActivity : Activity() {
    private lateinit var hud: ControllerHud
    private val bridge = BridgeServer()
    private var controllerName = "Nenhuma manete detectada"
    private var connected = false
    private var lastSend = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hud = ControllerHud()
        setContentView(hud)
        bridge.start()
        hud.postDelayed({ refreshController() }, 250)
    }
    override fun onDestroy() { bridge.stop(); super.onDestroy() }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!isGamepad(event.device)) return super.dispatchKeyEvent(event)
        connected = true; controllerName = event.device?.name ?: "Gamepad"; hud.invalidate()
        bridge.broadcast(JSONObject().apply { put("type","key");put("key",event.keyCode);put("action",event.action);put("connected",true) }.toString())
        return true
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (!isGamepad(event.device) || event.action != MotionEvent.ACTION_MOVE) return super.onGenericMotionEvent(event)
        connected = true; controllerName = event.device?.name ?: "Gamepad"
        val now=System.currentTimeMillis()
        if(now-lastSend>=8){lastSend=now;bridge.broadcast(JSONObject().apply{put("type","motion");put("lx",axis(event,MotionEvent.AXIS_X));put("ly",axis(event,MotionEvent.AXIS_Y));put("rx",axis(event,MotionEvent.AXIS_Z));put("ry",axis(event,MotionEvent.AXIS_RZ));put("lt",axis01(event,MotionEvent.AXIS_LTRIGGER));put("rt",axis01(event,MotionEvent.AXIS_RTRIGGER);put("connected",true)}.toString())}
        hud.invalidate(); return true
    }
    private fun axis(e:MotionEvent,a:Int)=e.getAxisValue(a).coerceIn(-1f,1f)
    private fun axis01(e:MotionEvent,a:Int)=e.getAxisValue(a).coerceIn(0f,1f)
    private fun isGamepad(d:InputDevice?):Boolean=d!=null&&((d.sources and InputDevice.SOURCE_GAMEPAD)!=0||(d.sources and InputDevice.SOURCE_JOYSTICK)!=0)
    private fun refreshController(){for(id in InputDevice.getDeviceIds()){val d=InputDevice.getDevice(id);if(isGamepad(d)){connected=true;controllerName=d.name?:"Gamepad";break}};hud.invalidate()}

    private inner class ControllerHud:View(this@MainActivity){
        private val p=Paint(Paint.ANTI_ALIAS_FLAG); private val a=RectF();private val b=RectF();private val x=RectF();private val y=RectF()
        init{setBackgroundColor(Color.rgb(7,9,15));isFocusable=true}
        override fun onDraw(c:Canvas){val w=width.toFloat();val h=height.toFloat();p.typeface=android.graphics.Typeface.DEFAULT_BOLD;p.color=Color.WHITE;p.textSize=25f;c.drawText("CONTROLBRIDGE",28f,48f,p);p.typeface=android.graphics.Typeface.DEFAULT;p.color=Color.rgb(135,151,180);p.textSize=11f;c.drawText("PHONE CONTROLLER  •  LAN",29f,69f,p)
            p.color=Color.rgb(17,22,33);c.drawRoundRect(18f,92f,w-18f,178f,22f,22f,p);p.color=Color.rgb(115,150,255);p.textSize=12f;c.drawText("●  CONEXÃO",34f,117f,p);p.color=Color.WHITE;p.textSize=17f;c.drawText(controllerName,34f,144f,p);p.color=Color.rgb(150,160,178);p.textSize=11f;c.drawText(bridge.url(),34f,165f,p);p.color=if(connected)Color.rgb(105,225,155)else Color.rgb(255,190,90);p.textSize=11f;c.drawText(if(connected)"MANETE DETECTADA" else "CONECTE A MANETE PELO BLUETOOTH DO ANDROID",34f,192f,p)
            p.color=Color.rgb(12,16,24);c.drawRoundRect(18f,210f,w-18f,h-24f,22f,22f,p);drawPad(c,w,h)
        }
        private fun drawPad(c:Canvas,w:Float,h:Float){val cy=h-150f;p.color=Color.rgb(30,39,56);c.drawCircle(125f,cy,62f,p);c.drawCircle(w-125f,cy,62f,p);p.color=Color.rgb(80,95,120);c.drawCircle(125f,cy,26f,p);c.drawCircle(w-125f,cy,26f,p);a.set(w-185f,cy-75f,w-125f,cy-15f);b.set(w-115f,cy-115f,w-55f,cy-55f);x.set(w-255f,cy-115f,w-195f,cy-55f);y.set(w-185f,cy-155f,w-125f,cy-95f);drawBtn(c,a,"A");drawBtn(c,b,"B");drawBtn(c,x,"X");drawBtn(c,y,"Y");p.color=Color.rgb(120,132,155);p.textSize=12f;c.drawText("TOQUE PARA CONTROLAR",w/2-70f,230f,p)}
        private fun drawBtn(c:Canvas,r:RectF,s:String){p.color=Color.rgb(28,36,53);c.drawRoundRect(r,18f,18f,p);p.color=Color.WHITE;p.textSize=18f;p.textAlign=Paint.Align.CENTER;c.drawText(s,r.centerX(),r.centerY()+6f,p);p.textAlign=Paint.Align.LEFT}
        override fun onTouchEvent(e:MotionEvent):Boolean{connected=true;invalidate();return true}
    }
}
