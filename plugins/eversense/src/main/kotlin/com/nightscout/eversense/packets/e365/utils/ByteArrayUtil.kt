package com.nightscout.eversense.packets.e365.utils

import java.nio.ByteBuffer
import java.nio.ByteOrder

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

fun UByteArray.toLong(): Long {
    var result = 0L
    for (i in this.indices) {
        result = result or (this[i].toLong() and 0xFF shl (8 * (this.size - 1 - i)))
    }
    return result
}