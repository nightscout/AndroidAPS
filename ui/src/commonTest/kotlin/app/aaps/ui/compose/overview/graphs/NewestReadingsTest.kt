package app.aaps.ui.compose.overview.graphs

import app.aaps.core.interfaces.overview.graph.BgDataPoint
import app.aaps.core.interfaces.overview.graph.BgRange
import app.aaps.core.interfaces.overview.graph.BgType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The readings the screen reader speaks for the glucose graph.
 *
 * The graph data arrives newest first. Reading it as oldest first made TalkBack speak the five
 * readings from the start of the 24 hour window, so a user at 120 mg/dl heard 70-90 and the values
 * hardly changed. These pin that the newest readings are spoken, newest first, in any input order.
 */
class NewestReadingsTest {

    private fun reading(timestamp: Long, value: Double) = BgDataPoint(timestamp, value, BgRange.IN_RANGE, BgType.REGULAR)

    private val oldestFirst = (1..8).map { reading(it * 300_000L, it * 10.0) }

    @Test
    fun newestFirstInputGivesTheNewestReadings() {
        val result = newestReadings(oldestFirst.reversed(), 5)

        assertEquals(listOf(80.0, 70.0, 60.0, 50.0, 40.0), result.map { it.value })
    }

    @Test
    fun oldestFirstInputGivesTheSameReadings() {
        val result = newestReadings(oldestFirst, 5)

        assertEquals(listOf(80.0, 70.0, 60.0, 50.0, 40.0), result.map { it.value })
    }

    @Test
    fun fewerReadingsThanAskedGivesAllOfThem() {
        val result = newestReadings(oldestFirst.take(2), 5)

        assertEquals(listOf(20.0, 10.0), result.map { it.value })
    }

    @Test
    fun noReadingsGivesNothing() {
        assertEquals(emptyList(), newestReadings(emptyList(), 5))
    }
}
