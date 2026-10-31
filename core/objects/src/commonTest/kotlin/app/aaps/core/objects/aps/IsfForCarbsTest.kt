package app.aaps.core.objects.aps

import app.aaps.core.interfaces.db.PersistenceLayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The ISF for carbs a master and its clients all work out from the same stored loop results. */
class IsfForCarbsTest {

    /** A loop result [minutes] after [START], with its variable sensitivity. */
    private fun result(minutes: Double, variableSens: Double?) =
        PersistenceLayer.LoopSensitivity(START + (minutes * 60_000).toLong(), variableSens)

    private fun averageAt(minutes: Double, vararg results: PersistenceLayer.LoopSensitivity): Double? =
        IsfForCarbs(results.toList()).averageMgdl(START + (minutes * 60_000).toLong())

    @Test
    fun withoutResultsTheProfileIsfApplies() {
        assertNull(IsfForCarbs(emptyList()).averageMgdl(START))
    }

    @Test
    fun averagesTheResultsOfTheLast24Hours() {
        assertEquals(62.0, averageAt(10.5, result(0.5, 60.0), result(5.5, 62.0), result(10.5, 64.0)))
    }

    /** A result after the time asked for must not count, or a past value would change when it arrives. */
    @Test
    fun aResultAfterTheTimeIsNotUsed() {
        assertEquals(61.0, averageAt(9.0, result(0.5, 60.0), result(5.5, 62.0), result(10.5, 99.0)))
    }

    @Test
    fun theWindowIsExactly24HoursAndOpenAtItsStart() {
        val dayMinutes = 24 * 60.0
        assertEquals(70.0, averageAt(dayMinutes, result(0.0, 50.0), result(dayMinutes, 70.0)))
        assertEquals(60.0, averageAt(dayMinutes, result(0.001, 50.0), result(dayMinutes, 70.0)))
    }

    @Test
    fun anOldResultAloneIsNotEnough() {
        assertNull(averageAt(24 * 60.0 + 1, result(0.5, 60.0)))
    }

    /** A burst of loop runs counts once, with the last value, however many runs it had. */
    @Test
    fun onlyTheLastResultOfEvery5MinutesCounts() {
        assertEquals(75.0, averageAt(6.0, result(0.5, 60.0), result(4.0, 70.0), result(5.5, 80.0)))
    }

    /**
     * The master stores every loop run of a burst. A client gets the device status only once, five seconds
     * after the last run, so it stores only that one. Both must get the same number.
     */
    @Test
    fun aClientWithOnlyTheLastRunOfABurstGetsTheSameValueAsTheMaster() {
        val master = averageAt(15.0, result(0.5, 60.0), result(5.2, 61.0), result(5.25, 63.0), result(10.5, 64.0))
        val client = averageAt(15.0, result(0.5, 60.0), result(5.25, 63.0), result(10.5, 64.0))
        assertEquals(master, client)
    }

    /** A client can store the same device status twice, for example after a restart. */
    @Test
    fun theSameResultStoredTwiceCountsOnce() {
        assertEquals(62.0, averageAt(10.5, result(0.5, 60.0), result(5.5, 62.0), result(5.5, 62.0), result(10.5, 64.0)))
    }

    @Test
    fun whenTheNewestResultHasNoValueTheProfileIsfApplies() {
        assertNull(averageAt(10.5, result(0.5, 60.0), result(5.5, 62.0), result(10.5, null)))
    }

    /** Older results stored 0.0 for "no value", and a broken one can hold a value that is not a number. */
    @Test
    fun zeroAndNotFiniteValuesMeanNoValue() {
        assertNull(averageAt(10.5, result(0.5, 60.0), result(10.5, 0.0)))
        assertNull(averageAt(10.5, result(0.5, 60.0), result(10.5, Double.POSITIVE_INFINITY)))
        assertNull(averageAt(10.5, result(0.5, 60.0), result(10.5, Double.NaN)))
        assertNull(averageAt(10.5, result(0.5, 60.0), result(10.5, -5.0)))
    }

    @Test
    fun resultsWithoutAValueAreLeftOutOfTheAverage() {
        assertEquals(62.0, averageAt(15.5, result(0.5, 60.0), result(5.5, null), result(10.5, 0.0), result(15.5, 64.0)))
    }

    /** The last run in 5 minutes decides for them, also when it had no value. */
    @Test
    fun aLaterRunWithoutValueEmptiesItsSlot() {
        assertEquals(70.0, averageAt(6.0, result(0.5, 60.0), result(4.0, null), result(5.5, 70.0)))
    }

    private companion object {

        /** The start of a 5 minute slot, so the minutes in the tests say which slot a result is in. */
        const val START = 1_700_000_100_000L
    }
}
