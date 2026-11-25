package app.aaps.ui.compose.overview.graphs

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The highest basal of the visible window, see [basalWindowMax]. The BG graph scales its basal to it,
 * so the highest basal in view always reaches the same height.
 *
 * Basal is stored only where the rate changes, so these pin the cases where the visible window holds
 * no change point but a rate is still drawn across it.
 */
class BasalWindowMaxTest {

    // Profile 1.0 U/h all day. A temp basal of 3.0 from x=100 to x=220, then 0.5.
    private val profile = listOf(0.0 to 1.0, 300.0 to 1.0)
    private val actual = listOf(0.0 to 1.0, 100.0 to 3.0, 220.0 to 0.5, 300.0 to 0.5)
    private val maxBasal = 3.0

    @Test fun `the highest basal of the window counts`() {
        assertEquals(3.0, basalWindowMax(profile, actual, 90.0, 200.0, maxBasal), 0.001)
    }

    @Test fun `a window after the high temp is scaled to its own rates and not the whole day`() {
        assertEquals(1.0, basalWindowMax(profile, actual, 230.0, 290.0, maxBasal), 0.001)
    }

    @Test fun `a high temp that started before the window counts though it has no point inside`() {
        assertEquals(3.0, basalWindowMax(profile, actual, 120.0, 200.0, maxBasal), 0.001)
    }

    @Test fun `without a window all points count`() {
        assertEquals(3.0, basalWindowMax(profile, actual, null, null, maxBasal), 0.001)
    }

    @Test fun `a window with only zero basal falls back to the max basal instead of a flat basal`() {
        val zeroProfile = listOf(0.0 to 0.0, 300.0 to 0.0)
        val zeroActual = listOf(0.0 to 0.0, 300.0 to 0.0)
        assertEquals(maxBasal, basalWindowMax(zeroProfile, zeroActual, 50.0, 100.0, maxBasal), 0.001)
    }

    @Test fun `no basal at all gives zero`() {
        assertEquals(0.0, basalWindowMax(emptyList(), emptyList(), 50.0, 100.0, 0.0), 0.001)
    }
}
