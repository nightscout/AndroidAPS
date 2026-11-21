package app.aaps.plugins.eversense.protocol

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.number
import kotlinx.datetime.offsetAt
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Binary primitives shared by E3, 365 and DMS; protocol units remain explicit at call sites. */
internal object BinaryFields {

    const val SENSOR_EPOCH_MS = 946_684_800_000L

    fun littleEndian(value: Long, width: Int): ByteArray = ByteArray(width) { position ->
        (value ushr (position * 8)).toByte()
    }

    fun unsigned(bytes: UByteArray, offset: Int = 0, width: Int = bytes.size - offset): Long {
        var number = 0L
        repeat(width) { position -> number = number or (bytes[offset + position].toLong() shl (position * 8)) }
        return number
    }

    /** The offset of the phone's time zone from UTC at [timestamp], in milliseconds, summer time included. */
    fun zoneOffsetMillis(timestamp: Long): Int =
        TimeZone.currentSystemDefault().offsetAt(Instant.fromEpochMilliseconds(timestamp)).totalSeconds * 1000

    private fun utc(timestamp: Long) = Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(TimeZone.UTC)

    fun date(timestamp: Long): ByteArray {
        val value = utc(timestamp)
        val month = value.month.number
        val bits = value.day or ((month and 7) shl 5) or (((value.year - 2000) * 2 + (month ushr 3)) shl 8)
        return littleEndian(bits.toLong(), 2)
    }

    fun time(timestamp: Long): ByteArray {
        val value = utc(timestamp)
        return clockFields(value.hour, value.minute, value.second)
    }

    fun clockFields(hour: Int, minute: Int, second: Int = 0): ByteArray = byteArrayOf(
        (((minute and 7) shl 5) or (second / 2)).toByte(),
        ((hour shl 3) or ((minute and 56) ushr 3)).toByte()
    )

    /**
     * The UTC midnight of the date in [bytes]. A month or day out of range rolls over into the next or previous
     * month, as the lenient calendar of the first version did: month 0 is December of the year before, day 0 the last
     * day of the month before.
     */
    fun dateMillis(bytes: UByteArray, offset: Int): Long {
        val bits = unsigned(bytes, offset, 2).toInt()
        val year = 2000 + (bits ushr 9)
        val month = ((bits ushr 5) and 7) + (((bits ushr 8) and 1) * 8)
        return LocalDate(year, 1, 1)
            .plus(month - 1, DateTimeUnit.MONTH)
            .plus((bits and 31) - 1, DateTimeUnit.DAY)
            .atStartOfDayIn(TimeZone.UTC)
            .toEpochMilliseconds()
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

    /** Little-endian IEEE 754 float at [offset]. */
    fun float(bytes: ByteArray, offset: Int = 0): Float =
        Float.fromBits(unsigned(bytes.toUByteArray(), offset, 4).toInt())
}
