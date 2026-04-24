package com.nightscout.eversense.packets.e3.util
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
class EversenseE3Writer {
    companion object {
        fun generateChecksumCRC16(data: ByteArray): ByteArray {
            var crc = 0xFFFF
            for (byte in data) {
                var currentByte = byte.toInt() and 0xFF
                repeat(8) {
                    val xor = ((crc shr 15) and 0x01) xor ((currentByte shr 7) and 0x01)
                    crc = (crc shl 1) and 0xFFFF
                    if (xor != 0) {
                        crc = (crc xor 0x1021) and 0xFFFF
                    }
                    currentByte = (currentByte shl 1) and 0xFF
                }
            }
            return writeInt16(crc)
        }
        fun writeDate(timestamp: Long): ByteArray {
            val calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"))
            calendar.setTimeInMillis(timestamp)
            val year = calendar.get(Calendar.YEAR) - 2000
            val month = calendar.get(Calendar.MONTH) + 1
            val day = calendar.get(Calendar.DAY_OF_MONTH)
            val byte1 = (month shl 5) or day
            val byte2 = (year shl 1) or (if (month >= 8) 1 else 0)
            return byteArrayOf(byte1.toByte(), byte2.toByte())
        }
        fun writeTime(timestamp: Long): ByteArray {
            val calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"))
            calendar.setTimeInMillis(timestamp)
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            val second = calendar.get(Calendar.SECOND)
            val byte1 = ((minute and 7) shl 5) or (second / 2)
            val byte2 = (hour shl 3) or ((minute and 56) shr 3)
            return byteArrayOf(byte1.toByte(), byte2.toByte())
        }
        fun writeTimezone(timestamp: Long): ByteArray {
            val timezoneOffset = TimeZone.getDefault().getOffset(timestamp)
            val timezoneNegative = if (timezoneOffset < 0) 255 else 0
            return writeTime(abs(timezoneOffset).toLong()) + byteArrayOf(timezoneNegative.toByte())
        }
        fun writeBoolean(value: Boolean): ByteArray {
            return byteArrayOf(if (value) 0x55 else 0x00)
        }
        fun writeDouble(value: Double): ByteArray {
            return writeInt16((value * 10).toInt())
        }
        fun writeInt16(value: Int): ByteArray {
            return byteArrayOf(
                value.toByte(),
                (value shr 8).toByte()
            )
        }
    }
}