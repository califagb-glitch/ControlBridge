package com.controlbridge.core

import org.json.JSONObject

data class InputPacket(
    val type: String,
    val timestamp: Long = System.nanoTime(),
    val key: Int = 0,
    val down: Boolean = false,
    val lx: Float = 0f,
    val ly: Float = 0f,
    val rx: Float = 0f,
    val ry: Float = 0f,
    val lt: Float = 0f,
    val rt: Float = 0f
) {
    fun json(): String = JSONObject().apply {
        put("v", 1); put("type", type); put("ts", timestamp); put("key", key); put("down", down)
        put("lx", lx); put("ly", ly); put("rx", rx); put("ry", ry); put("lt", lt); put("rt", rt)
    }.toString()

    companion object {
        fun parse(raw: String): InputPacket? = runCatching {
            val o = JSONObject(raw)
            InputPacket(
                type = o.optString("type"), timestamp = o.optLong("ts"), key = o.optInt("key"),
                down = o.optBoolean("down"), lx = o.optDouble("lx").toFloat(), ly = o.optDouble("ly").toFloat(),
                rx = o.optDouble("rx").toFloat(), ry = o.optDouble("ry").toFloat(),
                lt = o.optDouble("lt").toFloat(), rt = o.optDouble("rt").toFloat()
            )
        }.getOrNull()
    }
}
