package com.controlbridge.tv.ui

import android.graphics.*
import android.view.View
import com.controlbridge.core.InputPacket
import kotlin.math.min

class TvDashboardView:View{constructor(c:android.content.Context):super(c);private val p=Paint(1);var state="Aguardando celular";var last="Nenhuma entrada";var count=0;var connected=false;private var packet:InputPacket?=null
    fun update(s:String){state=s;connected=s.contains("sincronizada");invalidate()};fun input(v:InputPacket){packet=v;count++;last=when(v.type){"key"->"KEY ${v.key} ${if(v.down)"DOWN" else "UP"}";"motion"->"ANALÓGICOS / GATILHOS";else->v.type};invalidate()}
    override fun onDraw(c:Canvas){val w=width.toFloat();val h=height.toFloat();val u=min(w,h);p.shader=LinearGradient(0f,0f,w,h,Color.rgb(5,7,12),Color.rgb(16,20,31),Shader.TileMode.CLAMP);c.drawRect(0f,0f,w,h,p);p.shader=null;text(c,"CONTROLBRIDGE TV",54f,65f,25f,Color.WHITE,true);text(c,"RECEPTOR DE CONTROLE • REDE LOCAL",54f,88f,10f,Color.rgb(130,145,170),false);p.color=if(connected)Color.rgb(88,222,159)else Color.rgb(110,120,140);c.drawCircle(w-190f,62f,6f,p);text(c,state,w-172f,67f,10f,Color.rgb(205,214,230),false)
        card(c,54f,135f,w*.47f,h-54f,"SINCRONIZAÇÃO",if(connected)"CONTROLE ONLINE" else "PROCURANDO CELULAR",connected);card(c,w*.52f,135f,w-54f,h-54f,"ÚLTIMA ENTRADA",last,true);text(c,"PACOTES RECEBIDOS  $count",w*.52f,245f,11f,Color.rgb(135,150,178),true);text(c,"Este receptor controla a interface ControlBridge. Para jogos externos, use o modo Bluetooth HID do celular.",w*.52f,285f,11f,Color.rgb(145,158,182),false)
    }
    private fun card(c:Canvas,l:Float,t:Float,r:Float,b:Float,a:String,v:String,on:Boolean){p.color=Color.argb(140,11,16,27);c.drawRoundRect(l,t,r,b,28f,28f,p);p.style=Paint.Style.STROKE;p.strokeWidth=1f;p.color=Color.argb(if(on)90 else 35,105,140,205);c.drawRoundRect(l,t,r,b,28f,28f,p);p.style=Paint.Style.FILL;text(c,a,l+24,t+32,10f,Color.rgb(120,138,165),true);text(c,v,l+24,t+75,22f,if(on)Color.rgb(91,226,163)else Color.WHITE,true)}
    private fun text(c:Canvas,s:String,x:Float,y:Float,size:Float,color:Int,bold:Boolean){p.color=color;p.textSize=size;p.typeface=if(bold)Typeface.DEFAULT_BOLD else Typeface.DEFAULT;c.drawText(s,x,y,p)}
}
