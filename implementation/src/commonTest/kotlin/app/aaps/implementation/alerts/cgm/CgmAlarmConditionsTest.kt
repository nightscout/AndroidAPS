package app.aaps.implementation.alerts.cgm

import app.aaps.core.interfaces.alerts.CgmAlarmType
import app.aaps.core.interfaces.source.CgmSensorAlertState
import app.aaps.core.interfaces.source.CgmSensorFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The thresholds of [CgmAlarmConditions]. All glucose values are mg/dL. */
class CgmAlarmConditionsTest {

    private val now = 2_000_000_000L
    private val levels = CgmAlarmLevels(lowMgdl = 70.0, highMgdl = 250.0, fallDropMgdl = 30.0, riseMgdl = 30.0)

    private fun glucose(bg: Double, rate: Double? = null, fall: Double? = null, rise: Double? = null, ageMs: Long = 0L) =
        CgmGlucoseInput(latestMgdl = bg, latestAt = now - ageMs, ratePerMinute = rate, fallMgdl = fall, riseMgdl = rise)

    private fun conditions(input: CgmGlucoseInput, held: Set<CgmAlarmType> = emptySet()) =
        CgmAlarmConditions.glucoseConditions(input, levels, held, now)

    @Test
    fun urgentLowIsFixedAt55() {
        assertTrue(CgmAlarmType.URGENT_LOW in conditions(glucose(55.0)))
        assertFalse(CgmAlarmType.URGENT_LOW in conditions(glucose(56.0)))
    }

    @Test
    fun lowAndHighFollowTheLevelsTheUserSet() {
        assertTrue(CgmAlarmType.LOW in conditions(glucose(70.0)))
        assertFalse(CgmAlarmType.LOW in conditions(glucose(71.0)))
        assertTrue(CgmAlarmType.HIGH in conditions(glucose(250.0)))
        assertFalse(CgmAlarmType.HIGH in conditions(glucose(249.0)))
    }

    @Test
    fun aHeldAlarmStaysOnInsideTheHysteresisBand() {
        assertTrue(CgmAlarmType.LOW in conditions(glucose(73.0), held = setOf(CgmAlarmType.LOW)))
        assertFalse(CgmAlarmType.LOW in conditions(glucose(75.0), held = setOf(CgmAlarmType.LOW)))
        assertTrue(CgmAlarmType.HIGH in conditions(glucose(245.0), held = setOf(CgmAlarmType.HIGH)))
        assertFalse(CgmAlarmType.HIGH in conditions(glucose(240.0), held = setOf(CgmAlarmType.HIGH)))
    }

    @Test
    fun urgentLowSoonLooksTwentyMinutesAhead() {
        // 95 - 2 * 20 = 55: urgent low in 20 minutes.
        assertTrue(CgmAlarmType.URGENT_LOW_SOON in conditions(glucose(95.0, rate = -2.0)))
        assertFalse(CgmAlarmType.URGENT_LOW_SOON in conditions(glucose(96.0, rate = -2.0)))
        assertFalse(CgmAlarmType.URGENT_LOW_SOON in conditions(glucose(60.0, rate = 1.0)), "a rise is never urgent low soon")
        assertFalse(CgmAlarmType.URGENT_LOW_SOON in conditions(glucose(60.0, rate = null)), "no rate, no forecast")
    }

    @Test
    fun theRateAlarmsUseTheDropAndRiseInTheirWindow() {
        assertTrue(CgmAlarmType.FALL_RATE in conditions(glucose(120.0, fall = 30.0)))
        assertFalse(CgmAlarmType.FALL_RATE in conditions(glucose(120.0, fall = 29.0)))
        // Cleared only when the fall has flattened to half the threshold.
        assertTrue(CgmAlarmType.FALL_RATE in conditions(glucose(120.0, fall = 16.0), held = setOf(CgmAlarmType.FALL_RATE)))
        assertFalse(CgmAlarmType.FALL_RATE in conditions(glucose(120.0, fall = 15.0), held = setOf(CgmAlarmType.FALL_RATE)))
        assertTrue(CgmAlarmType.RISE_RATE in conditions(glucose(150.0, rise = 30.0)))
        assertFalse(CgmAlarmType.RISE_RATE in conditions(glucose(150.0, rise = 29.0)))
    }

    @Test
    fun anOldReadingRaisesNoGlucoseAlarm() {
        assertTrue(conditions(glucose(40.0, ageMs = CgmAlarmConditions.FRESH_MS + 1)).isEmpty())
        assertTrue(CgmAlarmType.URGENT_LOW in conditions(glucose(40.0, ageMs = CgmAlarmConditions.FRESH_MS)))
        assertTrue(conditions(CgmGlucoseInput(null, null, null, null, null)).isEmpty())
    }

    @Test
    fun eachSensorStateHasItsAlarmAndEpisode() {
        assertNull(CgmAlarmConditions.sensorCondition(CgmSensorAlertState.Ok, now))
        assertEquals(
            CgmAlarmType.SENSOR_FAILED to CgmAlarmCondition(episode = "SENSOR_ERROR"),
            CgmAlarmConditions.sensorCondition(CgmSensorAlertState.Failed(CgmSensorFailure.SENSOR_ERROR), now)
        )
        assertEquals(
            CgmAlarmType.SENSOR_ENDED to CgmAlarmCondition(episode = "123"),
            CgmAlarmConditions.sensorCondition(CgmSensorAlertState.Expired(123L), now)
        )
        assertEquals(
            CgmAlarmType.SIGNAL_LOSS to CgmAlarmCondition(episode = "5", sinceMs = 5L),
            CgmAlarmConditions.sensorCondition(CgmSensorAlertState.SignalLost(5L), now)
        )
        assertEquals(
            CgmAlarmType.SENSOR_ISSUE to CgmAlarmCondition(episode = "6", sinceMs = 6L),
            CgmAlarmConditions.sensorCondition(CgmSensorAlertState.SensorIssue(6L), now)
        )
        assertEquals(CgmAlarmType.SENSOR_ENDING_SOON, CgmAlarmConditions.sensorCondition(CgmSensorAlertState.Expiring(now + 1), now)?.first)
        assertEquals(CgmAlarmType.SECOND_SENSOR_READY, CgmAlarmConditions.sensorCondition(CgmSensorAlertState.SecondSensorReady(now), now)?.first)
    }

    @Test
    fun warmupDoneIsOnlyShownForAWhile() {
        assertEquals(CgmAlarmType.WARMUP_DONE, CgmAlarmConditions.sensorCondition(CgmSensorAlertState.WarmupDone(now - 60_000L), now)?.first)
        assertNull(CgmAlarmConditions.sensorCondition(CgmSensorAlertState.WarmupDone(now - CgmAlarmConditions.WARMUP_DONE_SHOWN_MS), now))
    }

    @Test
    fun glucoseAndSensorConditionsAreJoined() {
        val result = CgmAlarmConditions.evaluate(glucose(50.0), levels, CgmSensorAlertState.SignalLost(now - 1), emptySet(), now)
        assertTrue(CgmAlarmType.URGENT_LOW in result)
        assertTrue(CgmAlarmType.LOW in result)
        assertTrue(CgmAlarmType.SIGNAL_LOSS in result)
    }
}
