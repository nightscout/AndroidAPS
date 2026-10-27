package app.aaps.workflow.iob

import app.aaps.core.interfaces.db.PersistenceLayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The master's ISF for carbs at a past time, from the results the client stored. */
class CarbsIsfHistoryTest {

    /** Three loop results, five minutes apart, with the ISF rising as it does when BG falls. */
    private val history = CarbsIsfHistory(
        listOf(
            PersistenceLayer.CarbsIsf(1_000L, 56.0),
            PersistenceLayer.CarbsIsf(1_300L, 59.0),
            PersistenceLayer.CarbsIsf(1_600L, 64.0)
        )
    )

    @Test
    fun betweenTwoResultsTheEarlierOneIsInForce() {
        assertEquals(59.0, history.at(1_450L))
    }

    @Test
    fun atAResultsOwnTimeThatResultIsInForce() {
        assertEquals(64.0, history.at(1_600L))
    }

    @Test
    fun afterTheLastResultItStaysInForce() {
        assertEquals(64.0, history.at(9_000L))
    }

    @Test
    fun beforeTheFirstResultThereIsNoValue() {
        assertNull(history.at(999L))
    }

    @Test
    fun anEmptyHistoryHasNoValue() {
        assertNull(CarbsIsfHistory.EMPTY.at(1_450L))
    }
}
