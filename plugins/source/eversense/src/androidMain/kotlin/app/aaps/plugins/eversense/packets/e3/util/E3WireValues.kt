package app.aaps.plugins.eversense.packets.e3.util

import app.aaps.plugins.eversense.protocol.BinaryFields
import app.aaps.plugins.eversense.protocol.WireEncoding
import java.util.TimeZone
import kotlin.math.abs

/** Compatibility entry points for the shared binary codec; no packet owns its own date math. */
class EversenseE3Writer {
    companion object {
        fun generateChecksumCRC16(data: ByteArray): ByteArray = WireEncoding.crc16(data)
        fun writeDate(timestamp: Long): ByteArray = BinaryFields.date(timestamp)
        fun writeTime(timestamp: Long): ByteArray = BinaryFields.time(timestamp)
        fun writeTimezone(timestamp: Long): ByteArray {
            val offset = TimeZone.getDefault().getOffset(timestamp)
            return BinaryFields.time(abs(offset).toLong()) + byteArrayOf(if (offset < 0) 0xff.toByte() else 0)
        }
        fun writeBoolean(value: Boolean): ByteArray = byteArrayOf(if (value) 0x55 else 0)
        fun writeDouble(value: Double): ByteArray = writeInt16((value * 10).toInt())
        fun writeInt16(value: Int): ByteArray = BinaryFields.littleEndian(value.toLong(), 2)
    }
}

class EversenseE3Parser {
    companion object {
        private fun available(data: UByteArray, start: Int, width: Int, operation: String) {
            require(data.size >= start + width) { "$operation: data too short (size=${data.size}, start=$start)" }
        }
        fun readDate(data: UByteArray, start: Int): Long {
            available(data, start, 2, "readDate")
            return BinaryFields.dateMillis(data, start)
        }
        fun readTime(data: UByteArray, start: Int): Long {
            available(data, start, 2, "readTime")
            return BinaryFields.timeMillis(data, start)
        }
        fun readTimezone(data: UByteArray, start: Int): Long {
            available(data, start, 3, "readTimezone")
            return BinaryFields.offsetMillis(data, start)
        }
        fun readGlucose(data: UByteArray, start: Int): Int {
            available(data, start, 2, "readGlucose")
            return BinaryFields.unsigned(data, start, 2).toInt()
        }
    }
}
