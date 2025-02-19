package app.aaps.pump.apex.utils

import app.aaps.core.utils.toHex
import kotlin.math.min

class ApexCrypto {
    companion object {
        // CRC16-MODBUS
        fun crc16(data: ByteArray, length: Int = Int.MAX_VALUE, offset: Int = 0): ByteArray {
            var c = 0xFFFF
            for (p in offset..<min(length, data.size - offset)) {
                c = c xor data[p].toUByte().toInt()
                for (a in 0..<8)
                    if (c and 1 != 0) {
                        c /= 2
                        c = c xor 0xA001
                    } else c /=2
            }
            return byteArrayOf(c.toByte(), (c shr 8).toByte())
        }
    }
}