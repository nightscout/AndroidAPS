package app.aaps.plugins.source

import app.aaps.core.interfaces.source.CgmSensorAlertState
import app.aaps.core.interfaces.source.CgmSensorFailure
import app.aaps.core.interfaces.source.StagingState
import app.aaps.plugins.dexcomoneplus.OnePlusWarmupState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * How the ONE+ / G7 driver events map to the generic sensor alarm state. In `androidAppleTest`, so
 * it runs on the JVM and on the iOS simulator.
 */
class DexcomOnePlusSensorAlertsTest {

    private val now = 1_000_000_000L
    private val hour = 3_600_000L
    private val lead = 12 * hour
    private val farEnd = now + 100 * hour

    private fun warmup(phase: OnePlusWarmupState.Phase, message: String? = null) = OnePlusWarmupState(phase = phase, message = message)

    private fun DexcomOnePlusSensorAlerts.at(nowMs: Long, expiresAtMs: Long? = farEnd, running: Boolean = true, sensorStored: Boolean = true) =
        state(running = running, sensorStored = sensorStored, expiresAtMs = expiresAtMs, nowMs = nowMs, expiringLeadMs = lead)

    @Test
    fun readingsKeepItOk() {
        val alerts = DexcomOnePlusSensorAlerts()
        alerts.onWarmup(warmup(OnePlusWarmupState.Phase.READY), now)
        alerts.onReading(now)
        assertEquals(CgmSensorAlertState.Ok, alerts.at(now + 60_000L))
    }

    @Test
    fun aStoppedPluginRaisesNothing() {
        val alerts = DexcomOnePlusSensorAlerts()
        alerts.onWarmup(warmup(OnePlusWarmupState.Phase.FAILED, "SensorFailed"), now)
        assertEquals(CgmSensorAlertState.Ok, alerts.at(now, running = false))
    }

    @Test
    fun sensorFailedIsASensorError() {
        val alerts = DexcomOnePlusSensorAlerts()
        alerts.onWarmup(warmup(OnePlusWarmupState.Phase.FAILED, "SensorFailed"), now)
        assertEquals(CgmSensorAlertState.Failed(CgmSensorFailure.SENSOR_ERROR), alerts.at(now))
    }

    @Test
    fun anyOtherFailureIsOther() {
        val alerts = DexcomOnePlusSensorAlerts()
        alerts.onWarmup(warmup(OnePlusWarmupState.Phase.FAILED, "pairing refused"), now)
        assertEquals(CgmSensorAlertState.Failed(CgmSensorFailure.OTHER), alerts.at(now))
    }

    @Test
    fun anEndTheSensorReportsIsEndedEarlyBeforeTheUsableTimeAndExpiredAfter() {
        listOf("Ended", "SensorExpired", "SensorStopped").forEach { name ->
            val alerts = DexcomOnePlusSensorAlerts()
            alerts.onWarmup(warmup(OnePlusWarmupState.Phase.FAILED, name), now)
            assertEquals(CgmSensorAlertState.Failed(CgmSensorFailure.ENDED_EARLY), alerts.at(now), name)
            val end = now - hour
            assertEquals(CgmSensorAlertState.Expired(end), alerts.at(now, expiresAtMs = end), name)
        }
    }

    @Test
    fun reconnectingIsASignalLossSinceItsFirstEvent() {
        val alerts = DexcomOnePlusSensorAlerts()
        alerts.onReading(now)
        alerts.onWarmup(warmup(OnePlusWarmupState.Phase.RECONNECTING), now + 1_000L)
        alerts.onWarmup(warmup(OnePlusWarmupState.Phase.RECONNECTING), now + 60_000L)
        assertEquals(CgmSensorAlertState.SignalLost(now + 1_000L), alerts.at(now + 120_000L))
        // The link is back.
        alerts.onWarmup(warmup(OnePlusWarmupState.Phase.READY), now + 180_000L)
        alerts.onReading(now + 180_000L)
        assertEquals(CgmSensorAlertState.Ok, alerts.at(now + 200_000L))
    }

    @Test
    fun noReadingForFiveMinutesIsASignalLossButNotDuringWarmup() {
        val alerts = DexcomOnePlusSensorAlerts()
        alerts.onWarmup(warmup(OnePlusWarmupState.Phase.READY), now)
        alerts.onReading(now)
        assertEquals(CgmSensorAlertState.SignalLost(now), alerts.at(now + 5 * 60_000L))
        alerts.onWarmup(warmup(OnePlusWarmupState.Phase.WARMING, "WarmingUp"), now + 6 * 60_000L)
        assertEquals(CgmSensorAlertState.Ok, alerts.at(now + 30 * 60_000L))
    }

    @Test
    fun noiseIsASensorIssueAndAReadingEndsIt() {
        val alerts = DexcomOnePlusSensorAlerts()
        alerts.onReading(now)
        alerts.onWarmup(warmup(OnePlusWarmupState.Phase.WARMING, "ExcessNoise"), now + 1_000L)
        alerts.onWarmup(warmup(OnePlusWarmupState.Phase.WARMING, "ExcessNoise"), now + 2_000L)
        assertEquals(CgmSensorAlertState.SensorIssue(now + 1_000L), alerts.at(now + 3_000L))
        alerts.onWarmup(warmup(OnePlusWarmupState.Phase.READY), now + 4_000L)
        alerts.onReading(now + 4_000L)
        assertEquals(CgmSensorAlertState.Ok, alerts.at(now + 5_000L))
    }

    @Test
    fun readyAfterAWarmupIsWarmupDoneButNotAfterAnIssue() {
        val warmed = DexcomOnePlusSensorAlerts()
        warmed.onWarmup(warmup(OnePlusWarmupState.Phase.WARMING, "WarmingUp"), now)
        warmed.onWarmup(warmup(OnePlusWarmupState.Phase.READY), now + hour)
        warmed.onReading(now + hour)
        assertEquals(CgmSensorAlertState.WarmupDone(now + hour), warmed.at(now + hour + 1_000L))

        val noisy = DexcomOnePlusSensorAlerts()
        noisy.onWarmup(warmup(OnePlusWarmupState.Phase.WARMING, "ExcessNoise"), now)
        noisy.onWarmup(warmup(OnePlusWarmupState.Phase.READY), now + 60_000L)
        noisy.onReading(now + 60_000L)
        assertEquals(CgmSensorAlertState.Ok, noisy.at(now + 61_000L))
    }

    @Test
    fun theEndOfTheUsableTimeGivesEndingSoonThenEnded() {
        val alerts = DexcomOnePlusSensorAlerts()
        alerts.onReading(now)
        val end = now + 6 * hour
        assertEquals(CgmSensorAlertState.Expiring(end), alerts.at(now, expiresAtMs = end))
        alerts.onReading(end + 1_000L)
        assertEquals(CgmSensorAlertState.Expired(end), alerts.at(end + 2_000L, expiresAtMs = end))
    }

    @Test
    fun aReadySecondSensorIsReportedSinceItBecameReady() {
        val alerts = DexcomOnePlusSensorAlerts()
        alerts.onReading(now)
        alerts.onStaging(StagingState.READY, now + 1_000L)
        alerts.onStaging(StagingState.READY, now + 2_000L)
        assertEquals(CgmSensorAlertState.SecondSensorReady(now + 1_000L), alerts.at(now + 3_000L))
        alerts.onStaging(StagingState.ABSENT, now + 4_000L)
        assertEquals(CgmSensorAlertState.Ok, alerts.at(now + 5_000L))
    }

    @Test
    fun aNewSensorForgetsTheOldOne() {
        val alerts = DexcomOnePlusSensorAlerts()
        alerts.onWarmup(warmup(OnePlusWarmupState.Phase.FAILED, "SensorFailed"), now)
        alerts.onNewSensor()
        assertEquals(CgmSensorAlertState.Ok, alerts.at(now + 1_000L))
    }
}
