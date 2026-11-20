package app.aaps.plugins.eversense.protocol

import java.util.Calendar
import java.util.TimeZone

/** Binary primitives shared by E3, 365 and DMS; protocol units remain explicit at call sites. */
internal object BinaryFields {
    private val utc = TimeZone.getTimeZone("GMT")
    const val SENSOR_EPOCH_MS = 946_684_800_000L

    fun littleEndian(value: Long, width: Int): ByteArray = ByteArray(width) { position ->
        (value ushr (position * 8)).toByte()
    }
    fun unsigned(bytes: UByteArray, offset: Int = 0, width: Int = bytes.size - offset): Long {
        var number = 0L
        repeat(width) { position -> number = number or (bytes[offset + position].toLong() shl (position * 8)) }
        return number
    }
    private fun calendar(timestamp: Long) = Calendar.getInstance(utc).apply { timeInMillis = timestamp }

    fun date(timestamp: Long): ByteArray {
        val value = calendar(timestamp)
        val month = value.get(Calendar.MONTH) + 1
        val bits = value.get(Calendar.DAY_OF_MONTH) or ((month and 7) shl 5) or
            (((value.get(Calendar.YEAR) - 2000) * 2 + (month ushr 3)) shl 8)
        return littleEndian(bits.toLong(), 2)
    }
    fun time(timestamp: Long): ByteArray {
        val value = calendar(timestamp)
        return clockFields(value.get(Calendar.HOUR_OF_DAY), value.get(Calendar.MINUTE), value.get(Calendar.SECOND))
    }
    fun clockFields(hour: Int, minute: Int, second: Int = 0): ByteArray = byteArrayOf(
        (((minute and 7) shl 5) or (second / 2)).toByte(),
        ((hour shl 3) or ((minute and 56) ushr 3)).toByte()
    )
    fun dateMillis(bytes: UByteArray, offset: Int): Long {
        val bits = unsigned(bytes, offset, 2).toInt()
        val year = 2000 + (bits ushr 9)
        val month = ((bits ushr 5) and 7) + (((bits ushr 8) and 1) * 8)
        return Calendar.getInstance(utc).apply {
            clear()
            set(year, month - 1, bits and 31, 0, 0, 0)
        }.timeInMillis
    }
    fun timeMillis(bytes: UByteArray, offset: Int): Long {
        val bits = unsigned(bytes, offset, 2).toInt()
        val hours = bits ushr 11
        val minutes = (bits ushr 5) and 63
        val seconds = (bits and 31) * 2
        return ((hours * 60L + minutes) * 60 + seconds) * 1000
    }
    fun offsetMillis(bytes: UByteArray, offset: Int): Long {
        val bits = unsigned(bytes, offset, 2).toInt()
        val magnitude = ((bits ushr 11) * 60L + ((bits ushr 5) and 63)) * 60_000
        return if (bytes[offset + 2].toInt() == 0) magnitude else -magnitude
    }
    fun sensorTimestamp(bytes: UByteArray): Long = SENSOR_EPOCH_MS + unsigned(bytes) / 1024 * 1000
    fun sensorTimestamp(timestamp: Long): ByteArray = littleEndian((timestamp - SENSOR_EPOCH_MS) / 1000 * 1024, 8)
}
