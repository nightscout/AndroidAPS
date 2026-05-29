package com.nightscout.eversense.packets.e365.utils

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.experimental.or

fun UByteArray.toUtfString(): String {
    val sb = StringBuilder()
    for (i in this) {
        if (i == 0.toUByte()) {
            continue
        }

        sb.append(i.toInt().toChar())
    }

    return sb.toString()
}

fun Short.toByteArray(): ByteArray {
    val allocate = ByteBuffer.allocate(2)
    allocate.order(ByteOrder.LITTLE_ENDIAN)
    allocate.putShort(this)
    return allocate.array()
}

fun Int.toTimeZone(): ByteArray {
    val totalMinutes = this / (60 * 1000)
    val hour = totalMinutes / 60
    val minute = totalMinutes % 60

    val byte1: Byte = ((minute and 7) shl 5).toByte()
    val byte2: Byte = ((hour shl 3) or ((minute and 56) shr 3) ).toByte()

    return byteArrayOf(byte1, byte2)
}

fun UByteArray.toShort(): Short {
    return ((this[0].toInt() and 0xFF) or ((this[1].toInt() and 0xFF) shl 8)).toShort()
}

fun UByteArray.toInt(): Int {
    return (this[0].toInt() and 0xFF) or ((this[1].toInt() and 0xFF) shl 8)
}

fun UByteArray.toLong(): Long {
    var result = 0L
    for (i in indices) {
        val shifted = (this[i].toLong() and 0xFF) shl (8 * i)
        result = result or shifted
    }
    return result
}
fun ByteArray.toLong(): Long {
    return this.toUByteArray().toLong()
}

const val UNIX = 946_684_800_000
fun UByteArray.toUnix(): Long {
    val offset = this.toLong() / 1024 * 1000
    return UNIX + offset
}

fun Long.toUnixArray(): ByteArray {
    val offset = (this - UNIX) / 1000 * 1024

    val allocate = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
    allocate.putLong(offset)
    return allocate.array()
}