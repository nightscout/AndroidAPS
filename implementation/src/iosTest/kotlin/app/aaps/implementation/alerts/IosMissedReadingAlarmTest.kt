package app.aaps.implementation.alerts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** When the booked missed-readings alarm rings. It must follow the same rule as the in-app alarm. */
class IosMissedReadingAlarmTest {

    private fun ringAt(
        lastReadingMs: Long? = NOW - 2 * MIN,
        enabled: Boolean = true,
        thresholdMinutes: Int = 30,
        snoozedUntilMs: Long = 0L
    ) = IosMissedReadingAlarm.ringAt(lastReadingMs, NOW, enabled, thresholdMinutes, snoozedUntilMs)

    @Test
    fun `rings the threshold after the last reading`() {
        assertEquals(NOW - 2 * MIN + 30 * MIN, ringAt())
    }

    @Test
    fun `nothing is booked when the alarm is off`() {
        assertNull(ringAt(enabled = false))
    }

    @Test
    fun `nothing is booked before the first reading as the in-app alarm stays quiet then`() {
        assertNull(ringAt(lastReadingMs = null))
    }

    @Test
    fun `nothing is booked for a moment that has passed as the in-app alarm rings then`() {
        assertNull(ringAt(lastReadingMs = NOW - 31 * MIN))
    }

    @Test
    fun `a snooze moves the alarm later`() {
        assertEquals(NOW + 60 * MIN, ringAt(snoozedUntilMs = NOW + 60 * MIN))
    }

    /** After the in-app alarm rang, it moved its next time on; the booking follows, so it repeats. */
    @Test
    fun `an old snooze does not move the alarm earlier`() {
        assertEquals(NOW - 2 * MIN + 30 * MIN, ringAt(snoozedUntilMs = NOW - 10 * MIN))
    }

    @Test
    fun `a threshold that is not positive books nothing`() {
        assertNull(ringAt(thresholdMinutes = 0))
    }

    private companion object {

        const val MIN = 60_000L
        const val NOW = 1_790_000_000_000L
    }
}
