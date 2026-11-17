package app.aaps.plugins.source

import app.aaps.core.interfaces.source.StagingState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The pre-soak counter and the moment the card may say Ready.
 *
 * A field report showed "Settling", "Readings collected 2049" and a last reading many hours old,
 * with no promote button, on a sensor that had been inserted for more than twelve hours. About
 * 150 readings is what twelve hours of a five-minute sensor can produce. These tests pin both
 * halves of that: a backfill replay must not inflate the counter, and a stale reading must not
 * by itself keep a soaked sensor out of Ready.
 */
class DexcomOnePlusStagingReadingsTest {

    private val now = 1_800_000_000_000L
    private val hour = 60L * 60L * 1000L
    private val fiveMin = DexcomOnePlusStaging.STAGING_READING_INTERVAL_MS

    @Test
    fun aStaleReadingDoesNotKeepASoakedSensorOutOfReady() {
        val state = DexcomOnePlusStaging.computeStagingState(
            present = true,
            warming = false,
            sessionStartMs = now - 13 * hour,
            validEgvCount = DexcomOnePlusStaging.STAGING_MIN_VALID_EGV,
            nowMs = now,
        )
        assertEquals(StagingState.READY, state)
        // The early offer is the one that looks at freshness. Ready does not.
        assertFalse(
            DexcomOnePlusStaging.canPromoteEarly(
                validEgvCount = 2049,
                lastValueAtEpochMs = now - 12 * hour,
                nowMs = now,
            ),
        )
    }

    @Test
    fun readyAppearsWhenTheClockCrossesTwelveHoursWithoutANewReading() {
        val start = now - DexcomOnePlusStaging.STAGING_MIN_SETTLE_MS + fiveMin
        val before = DexcomOnePlusStaging.computeStagingState(
            present = true,
            warming = false,
            sessionStartMs = start,
            validEgvCount = 10,
            nowMs = now,
        )
        val after = DexcomOnePlusStaging.computeStagingState(
            present = true,
            warming = false,
            sessionStartMs = start,
            validEgvCount = 10,
            nowMs = now + fiveMin,
        )
        assertEquals(StagingState.SETTLING, before)
        assertEquals(StagingState.READY, after)
    }

    @Test
    fun aBackfillReplayedAfterTheLiveReadingIsCountedOnce() {
        val live = now
        var newest = 0L
        var count = 0
        fun feed(timestampMs: Long) {
            val decision = DexcomOnePlusStaging.acceptCollectedReading(newest, timestampMs)
            if (decision.accepted) {
                count++
                newest = decision.newestCountedMs
            }
        }
        feed(live)
        repeat(3) {
            for (i in 1..36) feed(live - i * fiveMin)
        }
        assertEquals(1, count)
        feed(live + fiveMin)
        repeat(3) {
            for (i in 0..36) feed(live + fiveMin - i * fiveMin)
        }
        assertEquals(2, count)
    }

    @Test
    fun oldestFirstBackfillCountsEachMomentOnceAcrossReconnects() {
        val start = now - 3 * hour
        val unique = (0 until 36).map { start + it * fiveMin }
        val replayed = unique + unique + unique
        var newest = 0L
        var count = 0
        for (timestampMs in replayed) {
            val decision = DexcomOnePlusStaging.acceptCollectedReading(newest, timestampMs)
            if (decision.accepted) {
                count++
                newest = decision.newestCountedMs
            }
        }
        assertEquals(36, count)
        assertEquals(unique.last(), newest)
    }

    @Test
    fun aNearDuplicateOfTheNewestReadingDoesNotCount() {
        val first = DexcomOnePlusStaging.acceptCollectedReading(0L, now)
        assertTrue(first.accepted)
        val again = DexcomOnePlusStaging.acceptCollectedReading(
            first.newestCountedMs,
            now + DexcomOnePlusStaging.STAGING_DEDUP_WINDOW_MS - 1L,
        )
        assertFalse(again.accepted)
        val next = DexcomOnePlusStaging.acceptCollectedReading(
            first.newestCountedMs,
            now + fiveMin,
        )
        assertTrue(next.accepted)
    }

    @Test
    fun anInflatedTwelveHourCountIsBroughtBackToWhatTheSensorCanHaveProduced() {
        val start = now - 12 * hour
        val repair = DexcomOnePlusStaging.repairCollectedCount(
            storedCount = 2049,
            newestCountedMs = 0L,
            sessionStartMs = start,
            nowMs = now,
        )
        val plausible = DexcomOnePlusStaging.plausibleReadingCount(start, now)
        assertEquals(plausible, repair.count)
        assertTrue(repair.count in 140..160)
        assertEquals(now, repair.newestCountedMs)
    }

    @Test
    fun aBelievableCountIsKeptAndOnlyTheMissingHighWaterIsFilledIn() {
        val start = now - 12 * hour
        val repair = DexcomOnePlusStaging.repairCollectedCount(
            storedCount = 150,
            newestCountedMs = 0L,
            sessionStartMs = start,
            nowMs = now,
        )
        assertEquals(150, repair.count)
        assertEquals(now, repair.newestCountedMs)
    }

    @Test
    fun aCountAlreadyTrackedIsNotRepairedAgain() {
        val repair = DexcomOnePlusStaging.repairCollectedCount(
            storedCount = 40,
            newestCountedMs = now - fiveMin,
            sessionStartMs = now - 12 * hour,
            nowMs = now,
        )
        assertEquals(40, repair.count)
        assertEquals(now - fiveMin, repair.newestCountedMs)
    }

    @Test
    fun aReadingThirtyMinutesOldCannotBePromoted() {
        val limit = DexcomOnePlusStaging.PROMOTION_MAX_READING_AGE_MS
        assertTrue(DexcomOnePlusStaging.readingFreshForPromotion(now - limit + 1L, now))
        assertFalse(DexcomOnePlusStaging.readingFreshForPromotion(now - limit, now))
        assertFalse(DexcomOnePlusStaging.readingFreshForPromotion(null, now))
    }

    @Test
    fun aQuietSlotIsAskedAgainAndADeliberateStopIsNot() {
        assertTrue(DexcomOnePlusStaging.slotShouldBeAskedAgain(sessionUp = false, reason = "egv_loop_exit"))
        assertTrue(DexcomOnePlusStaging.slotShouldBeAskedAgain(sessionUp = false, reason = null))
        assertFalse(DexcomOnePlusStaging.slotShouldBeAskedAgain(sessionUp = true, reason = "session_up"))
        assertFalse(DexcomOnePlusStaging.slotShouldBeAskedAgain(sessionUp = false, reason = "disconnect"))
        assertFalse(DexcomOnePlusStaging.slotShouldBeAskedAgain(sessionUp = false, reason = "shutdown"))
        assertFalse(DexcomOnePlusStaging.slotShouldBeAskedAgain(sessionUp = false, reason = "cancelled"))
    }
}
