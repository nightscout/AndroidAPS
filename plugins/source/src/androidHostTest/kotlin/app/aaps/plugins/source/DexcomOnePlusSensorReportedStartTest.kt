package app.aaps.plugins.source

import app.aaps.plugins.source.DexcomOnePlusSensorReportedStart.Decision
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * When the start a G7 / ONE+ sensor reports replaces the one AAPS stored. The case it is for: a
 * sensor started in xDrip+ three days ago got "now" as its start when AAPS connected to it.
 */
class DexcomOnePlusSensorReportedStartTest {

    private val hour = 3_600_000L
    private val day = 24 * hour
    private val now = 1_800_000_000_000L
    private val maxAge = 10 * day + 12 * hour

    private fun decide(settled: Boolean = false, current: Long, reported: Long) =
        DexcomOnePlusSensorReportedStart.decide(settled, current, reported, now, maxAge)

    @Test
    fun aRunningSensorGivenTheConnectTimeGetsItsRealStart() {
        assertEquals(Decision.MOVE, decide(current = now - hour, reported = now - 3 * day))
    }

    @Test
    fun noStoredStartTakesTheSensorStart() {
        assertEquals(Decision.MOVE, decide(current = 0L, reported = now - 3 * day))
    }

    @Test
    fun aStartWithinFifteenMinutesIsTheSameStart() {
        assertEquals(Decision.SAME, decide(current = now - 3 * day, reported = now - 3 * day + 14 * 60_000L))
    }

    @Test
    fun aSettledStartIsNeverMoved() {
        assertEquals(Decision.IGNORE, decide(settled = true, current = now - hour, reported = now - 3 * day))
    }

    @Test
    fun aStartInTheFutureIsIgnored() {
        assertEquals(Decision.IGNORE, decide(current = now - hour, reported = now + hour))
    }

    @Test
    fun aStartOlderThanAWholeSensorLifeIsIgnored() {
        assertEquals(Decision.IGNORE, decide(current = now - hour, reported = now - maxAge - hour))
    }
}
