package app.aaps.ui.compose.overview.graphs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The stretches of COB failover that the treatment belt draws as a bar, see [cobFailoverStretches].
 *
 * Failover comes as one point per 5 minute step. These pin how the points join into stretches.
 */
class CobFailoverStretchesTest {

    private val minTimestamp = 1_000_000_000L

    private fun minutes(count: Long) = minTimestamp + count * 60_000L

    @Test fun `no points give no stretch`() {
        assertTrue(cobFailoverStretches(emptyList(), minTimestamp).isEmpty())
    }

    @Test fun `one point covers half a step on each side`() {
        assertEquals(listOf(7.5..12.5), cobFailoverStretches(listOf(minutes(10)), minTimestamp))
    }

    @Test fun `points one step apart join into one stretch`() {
        assertEquals(listOf(7.5..22.5), cobFailoverStretches(listOf(minutes(10), minutes(15), minutes(20)), minTimestamp))
    }

    @Test fun `a point that comes a little late does not split a stretch`() {
        assertEquals(listOf(7.5..19.5), cobFailoverStretches(listOf(minutes(10), minutes(17)), minTimestamp))
    }

    @Test fun `a real pause splits it`() {
        assertEquals(
            listOf(7.5..12.5, 37.5..42.5),
            cobFailoverStretches(listOf(minutes(40), minutes(10)), minTimestamp)
        )
    }
}
