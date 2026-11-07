package app.aaps.ui.compose.overview.graphs

import app.aaps.core.interfaces.overview.graph.GraphConfig
import app.aaps.core.interfaces.overview.graph.SecondaryGraph
import app.aaps.core.interfaces.overview.graph.SeriesType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The height of a secondary graph, saved from its settings sheet.
 *
 * The height field also saves when the sheet closes, if it still has focus. That used to crash with
 * index -1 after a swipe-down (#5223), and after "Remove graph" it would have written the removed
 * graph back. These pin both: a swipe-down keeps the typed height, a removed graph stays removed.
 */
class SecondaryGraphHeightTest {

    private val cob = SecondaryGraph(listOf(SeriesType.COB), height = 100)
    private val bgi = SecondaryGraph(listOf(SeriesType.BGI, SeriesType.DEVIATIONS), height = 120)
    private val config = GraphConfig(secondaryGraphs = listOf(cob, bgi))

    @Test
    fun savesTheHeightOfTheEditedGraph() {
        val result = withSecondaryGraphHeight(config, 1, bgi.series, 200)

        assertEquals(listOf(cob, bgi.copy(height = 200)), result?.secondaryGraphs)
    }

    /** Quick +/- taps: the screen has not caught up, so the config already holds a newer height. */
    @Test
    fun savesWhenOnlyTheHeightChangedSinceTheSheetWasDrawn() {
        val newer = GraphConfig(secondaryGraphs = listOf(cob, bgi.copy(height = 130)))

        val result = withSecondaryGraphHeight(newer, 1, bgi.series, 140)

        assertEquals(140, result?.secondaryGraphs?.get(1)?.height)
    }

    /** The sheet for the second graph closes after that graph was removed: nothing is left there. */
    @Test
    fun writesNothingWhenTheIndexIsGone() {
        val afterRemove = GraphConfig(secondaryGraphs = listOf(cob))

        assertNull(withSecondaryGraphHeight(afterRemove, 1, bgi.series, 200))
    }

    /** The first graph was removed, so another graph moved to its place. It must not get the height. */
    @Test
    fun writesNothingWhenAnotherGraphMovedIntoThePlace() {
        val afterRemove = GraphConfig(secondaryGraphs = listOf(bgi))

        assertNull(withSecondaryGraphHeight(afterRemove, 0, cob.series, 200))
    }

    @Test
    fun writesNothingForANegativeIndex() {
        assertNull(withSecondaryGraphHeight(config, -1, cob.series, 200))
    }
}
