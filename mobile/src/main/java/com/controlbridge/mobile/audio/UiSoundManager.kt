package com.controlbridge.mobile.audio

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator

class UiSoundManager(private val context: Context) {
    private val tone=ToneGenerator(AudioManager.STREAM_MUSIC,55)
    fun focus(){play("ui_hover",ToneGenerator.TONE_PROP_BEEP,35)}
    fun click(){play("ui_click",ToneGenerator.TONE_PROP_ACK,55)}
    fun open(){play("ui_open",ToneGenerator.TONE_PROP_PROMPT,70)}
    fun close(){play("ui_close",ToneGenerator.TONE_PROP_NACK,55)}
    private fun play(name:String,fallback:Int,duration:Int){val id=context.resources.getIdentifier(name,"raw",context.packageName);if(id==0){tone.startTone(fallback,duration);return};runCatching{MediaPlayer.create(context,id)?.apply{setOnCompletionListener{it.release()};start()}}.onFailure{tone.startTone(fallback,duration)}}
    fun release(){tone.release()}
}
