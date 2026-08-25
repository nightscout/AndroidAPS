package app.aaps.pump.dana.emulator

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
// java.util.TimeZone is what setDefault takes; kotlinx.datetime.TimeZone stays qualified below,
// since importing both simple names would collide.
import java.util.TimeZone

/**
 * Covers the one thing this store gets to decide: which zone a request's "from" fields are in.
 *
 * Every test runs under a forced non-UTC default zone. That is the whole point — the bug these
 * pin was invisible in UTC, so a test that inherits the machine's zone proves nothing on a CI box
 * running UTC and everything on a developer's laptop.
 */
class HistoryEventStoreTest {

    private lateinit var store: HistoryEventStore
    private var originalZone: TimeZone? = null

    @BeforeEach
    fun setup() {
        store = HistoryEventStore()
        originalZone = TimeZone.getDefault()
        // -05:00 in January, and kotlinx's currentSystemDefault() reads this.
        TimeZone.setDefault(TimeZone.getTimeZone(ZONE))
    }

    @AfterEach
    fun tearDown() {
        originalZone?.let { TimeZone.setDefault(it) }
    }

    @Test
    fun localRequestIsReadInLocalTime() {
        // 2026-01-15 12:00:00 in the forced zone == 17:00 UTC.
        val from = store.parseFromTimestamp(params(26, 1, 15, 12, 0, 0), usingUtc = false)

        assertThat(from).isEqualTo(utcMillis(2026, 1, 15, 17, 0, 0))
    }

    @Test
    fun utcRequestIsReadInUtc() {
        val from = store.parseFromTimestamp(params(26, 1, 15, 12, 0, 0), usingUtc = true)

        assertThat(from).isEqualTo(utcMillis(2026, 1, 15, 12, 0, 0))
    }

    /**
     * The regression this fix is for: a local request used to be read as UTC, so the cutoff landed
     * [OFFSET_HOURS] hours from where the driver meant and the sync returned the wrong slice of
     * history — silently, with no error anywhere.
     *
     * The expectation is written as absolute instants on purpose. Deriving the cutoff from
     * `parseFromTimestamp` and asserting relative to *that* would move with the bug and pass either
     * way, which is exactly what an earlier version of this test did.
     */
    @Test
    fun localRequestSelectsRecordsByLocalTimeNotUtc() {
        val before = utcMillis(2026, 1, 15, 16, 30, 0) // 11:30 local — before the requested 12:00
        val after = utcMillis(2026, 1, 15, 17, 30, 0)  // 12:30 local — after it
        store.addEvent(code = 0x02, timestamp = before, param1 = 100, param2 = 0)
        store.addEvent(code = 0x02, timestamp = after, param1 = 200, param2 = 0)

        val cutoff = store.parseFromTimestamp(params(26, 1, 15, 12, 0, 0), usingUtc = false)

        // Read as UTC, the cutoff would be 12:00Z and both would qualify.
        assertThat(store.getEventsAfter(cutoff).map { it.timestamp }).containsExactly(after)
    }

    @Test
    fun recordsAreWrittenInLocalTimeMatchingALocalRequest() {
        val timestamp = utcMillis(2026, 1, 15, 17, 0, 0) // 12:00 in the forced zone
        store.addEvent(code = ReviewRecordCodes.ALARM, timestamp = timestamp, param1 = 0, param2 = 0)

        val record = store.buildReviewRecordData(store.getEventsAfter(0).first())

        // Hour 12, not 17 — the same zone parseFromTimestamp(usingUtc = false) reads.
        assertThat(record[4].toInt() and 0xFF).isEqualTo(12)
    }

    @Test
    fun theLoadEverythingSentinelReadsAsZero() {
        // What DanaRSPacketAPSHistoryEvents sends for from == 0...
        assertThat(store.parseFromTimestamp(params(0, 1, 1, 0, 0, 0), usingUtc = false)).isEqualTo(0L)
        // ...and what DanaRSService.loadHistory sends, having never called with(from) at all.
        assertThat(store.parseFromTimestamp(ByteArray(6), usingUtc = false)).isEqualTo(0L)
    }

    private fun params(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int) =
        byteArrayOf(year.toByte(), month.toByte(), day.toByte(), hour.toByte(), minute.toByte(), second.toByte())

    private fun utcMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Long =
        LocalDateTime(year, month, day, hour, minute, second)
            .toInstant(kotlinx.datetime.TimeZone.UTC).toEpochMilliseconds()

    companion object {

        private const val ZONE = "America/New_York"
        private const val OFFSET_HOURS = 5
    }
}
