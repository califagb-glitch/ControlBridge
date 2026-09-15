package com.controlbridge.core

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import android.util.Base64

object WsProtocol {
    private const val MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

    fun acceptKey(key: String): String = Base64.encodeToString(
        MessageDigest.getInstance("SHA-1").digest((key + MAGIC).toByteArray(StandardCharsets.US_ASCII)), Base64.NO_WRAP
    )

    fun textFrame(text: String): ByteArray {
        val payload = text.toByteArray(StandardCharsets.UTF_8)
        val out = ByteArrayOutputStream(payload.size + 16)
        out.write(0x81)
        when {
            payload.size < 126 -> out.write(payload.size)
            payload.size <= 65535 -> { out.write(126); out.write(payload.size ushr 8); out.write(payload.size) }
            else -> { out.write(127); for (i in 7 downTo 0) out.write((payload.size.toLong() ushr (i * 8)).toInt()) }
        }
        out.write(payload)
        return out.toByteArray()
    }

    fun readTextFrame(input: InputStream): String? {
        val first = input.read(); if (first < 0) return null
        val second = input.read(); if (second < 0) return null
        val masked = (second and 0x80) != 0
        var len = (second and 0x7f).toLong()
        if (len == 126L) len = ((input.read() shl 8) or input.read()).toLong()
        else if (len == 127L) { len = 0; repeat(8) { len = (len shl 8) or input.read().toLong() } }
        if (len > 1024 * 1024) return null
        val mask = if (masked) ByteArray(4).also { readFully(input, it) } else null
        val data = ByteArray(len.toInt()); readFully(input, data)
        if (mask != null) for (i in data.indices) data[i] = (data[i].toInt() xor mask[i and 3].toInt()).toByte()
        return if ((first and 0x0f) == 1) String(data, StandardCharsets.UTF_8) else ""
    }

    private fun readFully(input: InputStream, data: ByteArray) {
        var offset = 0
        while (offset < data.size) { val n = input.read(data, offset, data.size - offset); if (n < 0) throw java.io.EOFException(); offset += n }
    }
}
