package app.aaps.pump.dana.emulator

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * A single history event recorded by the emulated pump.
 */
data class HistoryEvent(
    val code: Int,
    val timestamp: Long,
    val param1: Int = 0,
    val param2: Int = 0
)

/**
 * Shared history event storage and formatting for Dana pump emulators.
 * Used by both DanaRS and DanaR emulators — the event wire format is identical.
 *
 * Event data layout (non-UTC, used by DanaR v2 and DanaRS v1/v3):
 *   [recordCode][year-2000][month][day][hour][min][sec][param1_hi][param1_lo][param2_hi][param2_lo]
 *
 * UTC layout (Dana-i / BLE5):
 *   [id_hi][id_lo][recordCode][epoch_4bytes_MSB][param1_hi][param1_lo][param2_hi][param2_lo]
 */
class HistoryEventStore {

    private val events = mutableListOf<HistoryEvent>()

    fun addEvent(code: Int, timestamp: Long, param1: Int, param2: Int) {
        events.add(HistoryEvent(code, timestamp, param1, param2))
    }

    /**
     * Parse a "from" timestamp from request params (6 bytes: Y-2000, M, D, H, Min, Sec).
     */
    fun parseFromTimestamp(params: ByteArray): Long {
        if (params.size < 5) return 0L
        val year = (params[0].toInt() and 0xFF) + 2000
        val month = params[1].toInt() and 0xFF
        val day = params[2].toInt() and 0xFF
        val hour = params[3].toInt() and 0xFF
        val minute = params[4].toInt() and 0xFF
        val second = if (params.size >= 6) params[5].toInt() and 0xFF else 0
        if (year == 2000 && month == 1 && day == 1 && hour == 0 && minute == 0 && second == 0) return 0L
        return try {
            kotlinx.datetime.LocalDateTime(year, month, day, hour, minute, second)
                .toInstant(TimeZone.UTC).toEpochMilliseconds()
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * Get events after the given timestamp.
     */
    fun getEventsAfter(fromMillis: Long): List<HistoryEvent> =
        events.filter { it.timestamp > fromMillis }

    /**
     * Build a non-UTC history event data payload (11 bytes).
     * Used by DanaR v2 and DanaRS v1/v3.
     */
    fun buildEventData(event: HistoryEvent): ByteArray {
        val data = ByteArray(11)
        val ldt = Instant.fromEpochMilliseconds(event.timestamp)
            .toLocalDateTime(TimeZone.currentSystemDefault())
        data[0] = event.code.toByte()
        data[1] = (ldt.year - 2000).toByte()
        data[2] = ldt.monthNumber.toByte()
        data[3] = ldt.dayOfMonth.toByte()
        data[4] = ldt.hour.toByte()
        data[5] = ldt.minute.toByte()
        data[6] = ldt.second.toByte()
        data[7] = ((event.param1 shr 8) and 0xFF).toByte()
        data[8] = (event.param1 and 0xFF).toByte()
        data[9] = ((event.param2 shr 8) and 0xFF).toByte()
        data[10] = (event.param2 and 0xFF).toByte()
        return data
    }

    /**
     * Build a UTC history event data payload (11 bytes).
     * Used by Dana-i / BLE5.
     */
    fun buildEventDataUtc(event: HistoryEvent, id: Short): ByteArray {
        val data = ByteArray(11)
        val epochSeconds = (event.timestamp / 1000).toInt()
        data[0] = ((id.toInt() shr 8) and 0xFF).toByte()
        data[1] = (id.toInt() and 0xFF).toByte()
        data[2] = event.code.toByte()
        data[3] = ((epochSeconds shr 24) and 0xFF).toByte()
        data[4] = ((epochSeconds shr 16) and 0xFF).toByte()
        data[5] = ((epochSeconds shr 8) and 0xFF).toByte()
        data[6] = (epochSeconds and 0xFF).toByte()
        data[7] = ((event.param1 shr 8) and 0xFF).toByte()
        data[8] = (event.param1 and 0xFF).toByte()
        data[9] = ((event.param2 shr 8) and 0xFF).toByte()
        data[10] = (event.param2 and 0xFF).toByte()
        return data
    }

    /** Done marker byte */
    val doneMarker: ByteArray get() = byteArrayOf(0xFF.toByte())
}
