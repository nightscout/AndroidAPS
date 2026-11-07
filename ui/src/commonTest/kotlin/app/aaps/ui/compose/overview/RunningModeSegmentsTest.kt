package app.aaps.ui.compose.overview

import app.aaps.core.data.model.RM
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The bands behind the treatment belt that say which mode the loop was in.
 *
 * The band used to be built by walking the records and ending the last one at
 * `timestamp + duration`. A permanent mode stores duration 0 - `RM.isTemporary()` is `duration > 0`,
 * and `handleRunningModeChange` requires it for OPEN_LOOP, CLOSED_LOOP and DISABLED_LOOP - so the
 * last band came out zero wide, and the belt drops anything under half a pixel. The mode the loop was
 * in *right now* was therefore never drawn: a user who turned the loop off and then switched to open
 * loop for a few minutes still saw "off" stretching across the whole afternoon.
 *
 * Samples are the mode at each boundary; each one holds until the next.
 */
class RunningModeSegmentsTest {

    private val start = 1_000_000L
    private val end = start + 6 * HOUR

    @Test
    fun `a permanent mode reaches the right edge of the graph`() {
        val segments = mergeRunningModeSegments(listOf(start to RM.Mode.OPEN_LOOP), end)

        assertEquals(1, segments.size)
        assertEquals(RM.Mode.OPEN_LOOP, segments[0].mode)
        assertEquals(start, segments[0].startTime)
        assertEquals(end, segments[0].endTime)
    }

    /** The reported bug: the mode switched to a few minutes ago has to be visible. */
    @Test
    fun `the mode in use now is drawn - not just the one before it`() {
        val switched = start + 4 * HOUR
        val segments = mergeRunningModeSegments(
            listOf(start to RM.Mode.DISABLED_LOOP, switched to RM.Mode.OPEN_LOOP),
            end
        )

        assertEquals(2, segments.size)
        assertEquals(RM.Mode.DISABLED_LOOP, segments[0].mode)
        assertEquals(switched, segments[0].endTime)
        assertEquals(RM.Mode.OPEN_LOOP, segments[1].mode)
        assertEquals(end, segments[1].endTime)
    }

    /**
     * Nothing is written when a temporary mode runs out, so the sample after its expiry carries the
     * permanent mode again and the band has to come back rather than leaving a hole.
     */
    @Test
    fun `the permanent mode returns once a temporary one has run out`() {
        val suspendedAt = start + HOUR
        val expiredAt = suspendedAt + HOUR
        val segments = mergeRunningModeSegments(
            listOf(
                start to RM.Mode.CLOSED_LOOP,
                suspendedAt to RM.Mode.SUSPENDED_BY_USER,
                expiredAt to RM.Mode.CLOSED_LOOP
            ),
            end
        )

        assertEquals(3, segments.size)
        assertEquals(RM.Mode.SUSPENDED_BY_USER, segments[1].mode)
        assertEquals(suspendedAt, segments[1].startTime)
        assertEquals(expiredAt, segments[1].endTime)
        assertEquals(RM.Mode.CLOSED_LOOP, segments[2].mode)
        assertEquals(end, segments[2].endTime)
    }

    @Test
    fun `neighbours with the same mode become one band`() {
        val segments = mergeRunningModeSegments(
            listOf(
                start to RM.Mode.CLOSED_LOOP,
                (start + HOUR) to RM.Mode.CLOSED_LOOP,
                (start + 2 * HOUR) to RM.Mode.CLOSED_LOOP
            ),
            end
        )

        assertEquals(1, segments.size)
        assertEquals(start, segments[0].startTime)
        assertEquals(end, segments[0].endTime)
    }

    /** Two records at the same moment must not produce a band of no width. */
    @Test
    fun `samples that would give an empty band are skipped`() {
        val segments = mergeRunningModeSegments(
            listOf(start to RM.Mode.OPEN_LOOP, start to RM.Mode.DISABLED_LOOP),
            end
        )

        assertEquals(1, segments.size)
        assertEquals(RM.Mode.DISABLED_LOOP, segments[0].mode)
        assertEquals(end, segments[0].endTime)
    }

    @Test
    fun `no samples means no bands`() {
        assertEquals(emptyList(), mergeRunningModeSegments(emptyList(), end))
    }

    private companion object {

        const val HOUR = 3_600_000L
    }
}
