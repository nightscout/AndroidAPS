package app.aaps.wear.complications.cwf

import app.aaps.wear.AAPSLoggerTest
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The two arithmetic rules that decide *when* a frame is asked for and *which second* it depicts.
 *
 * Both came from faults seen on a watch, and both were got wrong at least once:
 *
 * - a frame drawn for the second it started in lands a second late, which the wearer sees as the
 *   second hand jumping two seconds;
 * - backing off the rate the moment the queue is empty slows the watch down at exactly the moment
 *   the wearer raised their wrist to look at it.
 */
class CwfPacingTest {

    // ---- which second a frame should depict -------------------------------------------------

    @Test
    fun `a frame that lands within the same second depicts that second`() {
        // Produced in 100 ms at 10.100, lands at 10.200 - still second 10
        assertThat(CwfFaceComplication.landingSecondAt(now = 10_100, productionMs = 100)).isEqualTo(10_000)
    }

    @Test
    fun `a frame that lands after the second turns depicts the next one`() {
        // The fault seen on the watch: begun at 54.92, produced in 194 ms, landing at 55.11
        assertThat(CwfFaceComplication.landingSecondAt(now = 54_920, productionMs = 194)).isEqualTo(55_000)
    }

    @Test
    fun `a tie rounds up rather than down`() {
        // Showing the next second a few tens of milliseconds early is invisible; showing the previous
        // one late is the fault this rule exists for
        assertThat(CwfFaceComplication.landingSecondAt(now = 10_900, productionMs = 0)).isEqualTo(11_000)
    }

    @Test
    fun `a slow watch still depicts the second it will land in`() {
        // 750 ms is what a warm Galaxy Watch 4 measured
        assertThat(CwfFaceComplication.landingSecondAt(now = 10_500, productionMs = 750)).isEqualTo(11_000)
    }

    @Test
    fun `never draws for an instant earlier than the one already drawn`() {
        // What a frame costs swings from 200 ms to over a second on a busy watch, so a cheap frame
        // drawn just after an expensive one would otherwise aim at an earlier second. On the watch
        // that showed as the minute hand stepping back and then forward again.
        val expensiveFrame = CwfFaceComplication.landingSecondAt(now = 10_000, productionMs = 1_245)
        val cheapFrameJustAfter = CwfFaceComplication.landingSecondAt(
            now = 10_500, productionMs = 200, lastDrawn = expensiveFrame
        )

        assertThat(cheapFrameJustAfter).isAtLeast(expensiveFrame)
    }

    @Test
    fun `still moves forward when time has genuinely passed`() {
        // The clamp must not freeze the clock: a later frame still advances
        val earlier = CwfFaceComplication.landingSecondAt(now = 10_000, productionMs = 100)

        assertThat(CwfFaceComplication.landingSecondAt(now = 12_000, productionMs = 100, lastDrawn = earlier))
            .isGreaterThan(earlier)
    }

    // ---- how often to ask when nothing is prepared -------------------------------------------

    @Test
    fun `holds the second rate through the first empty ticks after a wake`() {
        (1..3).forEach { tick ->
            assertThat(CwfComplicationUpdater.intervalWhenNothingPrepared(tick, lastFrameMs = 900))
                .isEqualTo(1_000)
        }
    }

    @Test
    fun `keeps the second rate on a watch fast enough to sustain it`() {
        assertThat(CwfComplicationUpdater.intervalWhenNothingPrepared(consecutiveEmptyTicks = 10, lastFrameMs = 200))
            .isEqualTo(1_000)
    }

    @Test
    fun `backs off once the watch has repeatedly failed to keep up`() {
        assertThat(CwfComplicationUpdater.intervalWhenNothingPrepared(consecutiveEmptyTicks = 10, lastFrameMs = 600))
            .isEqualTo(2_000)
    }

    @Test
    fun `backs off further when a frame costs most of a second`() {
        assertThat(CwfComplicationUpdater.intervalWhenNothingPrepared(consecutiveEmptyTicks = 10, lastFrameMs = 900))
            .isEqualTo(5_000)
    }

    @Test
    fun `stays optimistic before anything has been drawn`() {
        assertThat(CwfComplicationUpdater.intervalWhenNothingPrepared(consecutiveEmptyTicks = 10, lastFrameMs = 0))
            .isEqualTo(1_000)
    }
}

// ---- whether anybody is looking at all --------------------------------------------------

/**
 * The rule that keeps the whole renderer idle on a watch showing any other face: a request from a
 * bound face is the only proof that the picture is seen, and it goes stale after a while.
 */
class CwfDemandTest {

    @Test
    fun `nothing is wanted before the first request`() {
        assertThat(CwfComplicationUpdater.demandActive(lastRequestMs = 0, now = 1_000_000)).isFalse()
    }

    @Test
    fun `a recent request is demand`() {
        assertThat(CwfComplicationUpdater.demandActive(lastRequestMs = 1_000_000, now = 1_000_000 + 60_000)).isTrue()
    }

    @Test
    fun `demand goes stale once the face has stopped asking`() {
        // Bound, the runtime asks at least every few minutes; ten minutes of silence means the face is gone
        assertThat(CwfComplicationUpdater.demandActive(lastRequestMs = 1_000_000, now = 1_000_000 + 10 * 60_000)).isFalse()
    }

    @Test
    fun `the window is the caller's to choose`() {
        assertThat(CwfComplicationUpdater.demandActive(lastRequestMs = 1_000, now = 3_000, timeoutMs = 1_000)).isFalse()
        assertThat(CwfComplicationUpdater.demandActive(lastRequestMs = 1_000, now = 1_500, timeoutMs = 1_000)).isTrue()
    }

    // ---- the runtime's own signal, which ends demand at once ----------------------------------

    private val logger = AAPSLoggerTest()

    // The slot table is process-wide state, so every test here leaves it empty when done. The
    // "nothing known yet" state before the first callback cannot be brought back and is not tested.

    @Test
    fun `demand ends the moment the last bound slot lets go`() {
        CwfFaceComplication.noteActivated("CwfFaceComplication#6", logger)
        CwfFaceComplication.noteActivated("CwfAmbientFaceComplication#7", logger)
        CwfFaceComplication.noteRequest()
        assertThat(CwfFaceComplication.lastRequestMs).isNotEqualTo(0L)

        // One slot gone, the other still shows the picture
        CwfFaceComplication.noteDeactivated("CwfFaceComplication#6", logger)
        assertThat(CwfFaceComplication.lastRequestMs).isNotEqualTo(0L)

        CwfFaceComplication.noteDeactivated("CwfAmbientFaceComplication#7", logger)
        assertThat(CwfFaceComplication.lastRequestMs).isEqualTo(0L)
    }

    @Test
    fun `an activation is demand by itself`() {
        // The runtime asks before it activates, so the activation must not wait for a request
        CwfFaceComplication.noteActivated("CwfFaceComplication#6", logger)
        assertThat(CwfFaceComplication.lastRequestMs).isNotEqualTo(0L)

        CwfFaceComplication.noteDeactivated("CwfFaceComplication#6", logger)
    }

    @Test
    fun `a request after the runtime let go does not revive demand`() {
        // Measured: a request queued by our own last tick landed 12 ms after the deactivation
        CwfFaceComplication.noteActivated("CwfFaceComplication#6", logger)
        CwfFaceComplication.noteDeactivated("CwfFaceComplication#6", logger)
        assertThat(CwfFaceComplication.lastRequestMs).isEqualTo(0L)

        CwfFaceComplication.noteRequest()
        assertThat(CwfFaceComplication.lastRequestMs).isEqualTo(0L)
    }

    @Test
    fun `a deactivation of an unknown slot leaves a bound one alone`() {
        CwfFaceComplication.noteActivated("CwfFaceComplication#6", logger)
        CwfFaceComplication.noteDeactivated("CwfFaceComplication#99", logger)
        assertThat(CwfFaceComplication.lastRequestMs).isNotEqualTo(0L)

        CwfFaceComplication.noteDeactivated("CwfFaceComplication#6", logger)
        assertThat(CwfFaceComplication.lastRequestMs).isEqualTo(0L)
    }
}
