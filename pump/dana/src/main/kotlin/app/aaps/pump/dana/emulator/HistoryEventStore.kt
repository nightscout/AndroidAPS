package app.aaps.pump.dana.emulator

import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
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
     *
     * [usingUtc] says how the *requesting driver* wrote those fields, and callers must pass what
     * their own command does — there is no single right answer:
     *  - `DanaRSPacketAPSHistoryEvents` writes UTC only when the pump is `usingUTC` (Dana-i), local
     *    otherwise, so its caller mirrors that flag.
     *  - `DanaRSPacketHistory` (the per-type review history) and `MsgHistoryEventsV2` build theirs
     *    from a plain `GregorianCalendar`, i.e. always local.
     *
     * This used to assume UTC unconditionally while [buildEventData] and [buildReviewRecordData]
     * write local time, so on any non-UTC machine a windowed request was misread by the zone offset
     * and silently returned the wrong slice of history.
     */
    fun parseFromTimestamp(params: ByteArray, usingUtc: Boolean): Long {
        if (params.size < 5) return 0L
        val year = (params[0].toInt() and 0xFF) + 2000
        val month = params[1].toInt() and 0xFF
        val day = params[2].toInt() and 0xFF
        val hour = params[3].toInt() and 0xFF
        val minute = params[4].toInt() and 0xFF
        val second = if (params.size >= 6) params[5].toInt() and 0xFF else 0
        if (year == 2000 && month == 1 && day == 1 && hour == 0 && minute == 0 && second == 0) return 0L
        val zone = if (usingUtc) TimeZone.UTC else TimeZone.currentSystemDefault()
        return try {
            kotlinx.datetime.LocalDateTime(year, month, day, hour, minute, second)
                .toInstant(zone).toEpochMilliseconds()
        } catch (_: Exception) {
            // Also the "load everything" path for review history: DanaRSService.loadHistory never
            // calls with(from), so every field arrives as 0 and LocalDateTime rejects month 0.
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
        data[2] = ldt.month.number.toByte()
        data[3] = ldt.day.toByte()
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

    /**
     * Build a review-history record payload (10 bytes), as served by the per-type `REVIEW__*`
     * commands behind the pump history screen — a different wire format and a different consumer
     * from [buildEventData]'s APS event history, so the two are kept in separate stores.
     *
     * Layout: `[recordCode][year-2000][month][day][hour][min][sec][subCode][value_hi][value_lo]`,
     * where [HistoryEvent.param1] is the raw value (hundredths for insulin: 150 = 1.50 U) and
     * [HistoryEvent.param2] is the record's sub-code — its meaning depends on `recordCode`, e.g.
     * the bolus type (`0x80` = standard) for `0x02`, or the alarm letter (`'O'` = occlusion) for
     * `0x0a`. Local time, matching the parser's `DateTime(2000 + year, ...)`.
     */
    fun buildReviewRecordData(event: HistoryEvent): ByteArray {
        val data = ByteArray(10)
        val ldt = Instant.fromEpochMilliseconds(event.timestamp)
            .toLocalDateTime(TimeZone.currentSystemDefault())
        data[0] = event.code.toByte()
        data[1] = (ldt.year - 2000).toByte()
        data[2] = ldt.month.number.toByte()
        data[3] = ldt.day.toByte()
        data[4] = ldt.hour.toByte()
        data[5] = ldt.minute.toByte()
        data[6] = ldt.second.toByte()
        data[7] = event.param2.toByte()
        data[8] = ((event.param1 shr 8) and 0xFF).toByte()
        data[9] = (event.param1 and 0xFF).toByte()
        return data
    }

    /**
     * End-of-history marker for the review commands: a single result byte, 0 = success. It is the
     * payload's *length* that ends the upload — the driver treats any 1-byte reply as the end.
     */
    val reviewDoneMarker: ByteArray get() = byteArrayOf(0x00)

    /** Done marker byte */
    val doneMarker: ByteArray get() = byteArrayOf(0xFF.toByte())
}
