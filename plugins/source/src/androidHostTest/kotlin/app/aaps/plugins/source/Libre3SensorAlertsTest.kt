package app.aaps.plugins.source

import app.aaps.core.interfaces.source.CgmSensorAlertState
import app.aaps.core.interfaces.source.CgmSensorFailure
import app.aaps.core.interfaces.source.StagingState
import app.aaps.plugins.libre3.Libre3WarmupState.Phase
import app.aaps.plugins.libre3.parse.Libre3PatchState
import app.aaps.plugins.libre3.parse.Libre3PatchStatus
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * How the Libre 3 driver events become the generic sensor alarm state. The priority order of the
 * states is the shared one of `CgmSensorAlertRules` and is tested there.
 */
class Libre3SensorAlertsTest {

    private val now = 1_800_000_000_000L
    private val minute = 60_000L
    private val hour = 60 * minute

    private fun Libre3SensorAlerts.at(
        nowMs: Long = now,
        phase: Phase = Phase.READY,
        expiresAtMs: Long? = now + 100 * hour,
        stopped: Boolean = false,
        sensorStored: Boolean = true,
    ) = state(stopped = stopped, sensorStored = sensorStored, phase = phase, expiresAtMs = expiresAtMs, nowMs = nowMs, expiringLeadMs = 12 * hour)

    private fun status(errorData: Int = 0, patchState: Int = 4) = Libre3PatchStatus(
        lifeCount = 100, errorData = errorData, eventData = 0, index = 0, patchState = Libre3PatchState(patchState),
        currentLifeCount = 100, stackDisconnectReason = 0, appDisconnectReason = 0,
    )

    @Test
    fun `a running sensor is ok`() {
        val alerts = Libre3SensorAlerts()
        alerts.onReading(now)
        assertThat(alerts.at()).isEqualTo(CgmSensorAlertState.Ok)
    }

    @Test
    fun `a final driver failure is failed other and a stopped plugin is ok`() {
        val alerts = Libre3SensorAlerts()
        assertThat(alerts.at(phase = Phase.FAILED)).isEqualTo(CgmSensorAlertState.Failed(CgmSensorFailure.OTHER))
        assertThat(alerts.at(phase = Phase.FAILED, stopped = true)).isEqualTo(CgmSensorAlertState.Ok)
    }

    // region link and readings

    @Test
    fun `reconnecting is a lost signal since its first event`() {
        val alerts = Libre3SensorAlerts()
        alerts.onReading(now)
        alerts.onWarmup(Phase.RECONNECTING, now + 1_000L)
        alerts.onWarmup(Phase.RECONNECTING, now + 2_000L)
        // A retry passes through CONNECTING: the link is not back yet.
        alerts.onWarmup(Phase.CONNECTING, now + 2_500L)
        assertThat(alerts.at(now + 3_000L)).isEqualTo(CgmSensorAlertState.SignalLost(now + 1_000L))

        alerts.onWarmup(Phase.READY, now + 4_000L)
        assertThat(alerts.at(now + 5_000L)).isEqualTo(CgmSensorAlertState.Ok)
    }

    @Test
    fun `only a session that lost its link is a lost signal`() {
        val alerts = Libre3SensorAlerts()
        alerts.onReading(now)
        alerts.onSession(up = false, linkLost = false, nowMs = now + 1_000L)
        assertThat(alerts.at(now + 2_000L)).isEqualTo(CgmSensorAlertState.Ok)

        alerts.onSession(up = false, linkLost = true, nowMs = now + 3_000L)
        assertThat(alerts.at(now + 4_000L)).isEqualTo(CgmSensorAlertState.SignalLost(now + 3_000L))

        alerts.onSession(up = true, linkLost = false, nowMs = now + 5_000L)
        assertThat(alerts.at(now + 6_000L)).isEqualTo(CgmSensorAlertState.Ok)
    }

    @Test
    fun `no reading for 5 min is a lost signal since the last reading`() {
        val alerts = Libre3SensorAlerts()
        alerts.onReading(now)
        assertThat(alerts.at(now + 4 * minute)).isEqualTo(CgmSensorAlertState.Ok)
        assertThat(alerts.at(now + 5 * minute)).isEqualTo(CgmSensorAlertState.SignalLost(now))
    }

    @Test
    fun `no reading during warm-up or pairing is no lost signal`() {
        val alerts = Libre3SensorAlerts()
        alerts.onReading(now)
        assertThat(alerts.at(now + hour, phase = Phase.WARMING)).isEqualTo(CgmSensorAlertState.Ok)
        assertThat(alerts.at(now + hour, phase = Phase.PAIRING)).isEqualTo(CgmSensorAlertState.Ok)
    }

    @Test
    fun `after a restart the last stored reading still counts`() {
        val alerts = Libre3SensorAlerts()
        alerts.restoreLastReading(now)
        assertThat(alerts.at(now + 10 * minute)).isEqualTo(CgmSensorAlertState.SignalLost(now))
    }

    @Test
    fun `warm-up is done at the first reading after warming`() {
        val alerts = Libre3SensorAlerts()
        alerts.onWarmup(Phase.WARMING, now)
        alerts.onWarmup(Phase.READY, now + hour)
        assertThat(alerts.at(now + hour)).isEqualTo(CgmSensorAlertState.Ok)

        alerts.onReading(now + hour + minute)
        assertThat(alerts.at(now + hour + minute)).isEqualTo(CgmSensorAlertState.WarmupDone(now + hour + minute))

        // The next readings do not move the episode.
        alerts.onReading(now + hour + 2 * minute)
        assertThat(alerts.at(now + hour + 2 * minute)).isEqualTo(CgmSensorAlertState.WarmupDone(now + hour + minute))
    }

    // endregion

    // region end of life

    @Test
    fun `past the end of life the sensor has ended`() {
        val alerts = Libre3SensorAlerts()
        alerts.onReading(now)
        assertThat(alerts.at(expiresAtMs = now - 1_000L)).isEqualTo(CgmSensorAlertState.Expired(now - 1_000L))
    }

    @Test
    fun `from the warning time before the end the sensor is ending soon`() {
        val alerts = Libre3SensorAlerts()
        alerts.onReading(now)
        assertThat(alerts.at(expiresAtMs = now + 12 * hour)).isEqualTo(CgmSensorAlertState.Expiring(now + 12 * hour))
        assertThat(alerts.at(expiresAtMs = now + 13 * hour)).isEqualTo(CgmSensorAlertState.Ok)
    }

    @Test
    fun `the end of life is the life read by the scan with no grace period`() {
        val activated = now - hour
        assertThat(Libre3Staging.endOfLifeMs(activated, 15 * 24 * 60)).isEqualTo(activated + 15 * 24 * hour)
        assertThat(Libre3Staging.endOfLifeMs(activated, null)).isEqualTo(activated + Libre3Staging.DEFAULT_LIFE_MS)
        assertThat(Libre3Staging.endOfLifeMs(activated, 0)).isEqualTo(activated + Libre3Staging.DEFAULT_LIFE_MS)
        assertThat(Libre3Staging.endOfLifeMs(0L, 15 * 24 * 60)).isNull()
    }

    // endregion

    // region second sensor

    @Test
    fun `a ready second sensor is reported since it became ready`() {
        val alerts = Libre3SensorAlerts()
        alerts.onReading(now)
        alerts.onStaging(StagingState.READY, now)
        alerts.onStaging(StagingState.READY, now + minute)
        assertThat(alerts.at(now + 2 * minute)).isEqualTo(CgmSensorAlertState.SecondSensorReady(now))

        alerts.onStaging(StagingState.ABSENT, now + 3 * minute)
        assertThat(alerts.at(now + 3 * minute)).isEqualTo(CgmSensorAlertState.Ok)
    }

    @Test
    fun `without a production sensor only a ready second sensor counts`() {
        val alerts = Libre3SensorAlerts()
        assertThat(alerts.at(phase = Phase.FAILED, sensorStored = false)).isEqualTo(CgmSensorAlertState.Ok)
        alerts.onStaging(StagingState.READY, now)
        assertThat(alerts.at(phase = Phase.FAILED, sensorStored = false)).isEqualTo(CgmSensorAlertState.SecondSensorReady(now))
    }

    // endregion

    // region health messages of the sensor

    @Test
    fun `a known sensor fault is a sensor error at once`() {
        val alerts = Libre3SensorAlerts()
        alerts.onStatus(status(errorData = 3))
        assertThat(alerts.at()).isEqualTo(CgmSensorAlertState.Failed(CgmSensorFailure.SENSOR_ERROR))
    }

    @Test
    fun `an end reported before the end of life is ended early`() {
        val alerts = Libre3SensorAlerts()
        alerts.onStatus(status(patchState = 6))
        assertThat(alerts.at()).isEqualTo(CgmSensorAlertState.Failed(CgmSensorFailure.ENDED_EARLY))
    }

    @Test
    fun `an end reported after the end of life keeps the expired episode at the end of life`() {
        val alerts = Libre3SensorAlerts()
        alerts.onStatus(status(errorData = 5))
        assertThat(alerts.at(expiresAtMs = now - hour)).isEqualTo(CgmSensorAlertState.Expired(now - hour))
    }

    @Test
    fun `an expired or terminated code or state is an end`() {
        listOf(status(errorData = 5), status(errorData = 6), status(patchState = 6), status(patchState = 5), status(errorData = 99, patchState = 6)).forEach {
            val alerts = Libre3SensorAlerts()
            alerts.onStatus(it)
            assertThat(alerts.at()).isEqualTo(CgmSensorAlertState.Failed(CgmSensorFailure.ENDED_EARLY))
        }
    }

    @Test
    fun `an unknown code counts only the second time in a row`() {
        val alerts = Libre3SensorAlerts()
        alerts.onStatus(status(errorData = 99))
        assertThat(alerts.at()).isEqualTo(CgmSensorAlertState.Ok)
        alerts.onStatus(status(errorData = 99))
        assertThat(alerts.at()).isEqualTo(CgmSensorAlertState.Failed(CgmSensorFailure.SENSOR_ERROR))
    }

    @Test
    fun `a good message between two unknown codes starts the count again`() {
        val alerts = Libre3SensorAlerts()
        alerts.onStatus(status(errorData = 99))
        alerts.onStatus(status())
        alerts.onStatus(status(errorData = 99))
        assertThat(alerts.at()).isEqualTo(CgmSensorAlertState.Ok)
    }

    @Test
    fun `a good message clears the fault and a sending problem is no fault`() {
        val alerts = Libre3SensorAlerts()
        alerts.onStatus(status(errorData = 3))
        alerts.onStatus(status())
        assertThat(alerts.at()).isEqualTo(CgmSensorAlertState.Ok)
        alerts.onStatus(status(errorData = 7))
        assertThat(alerts.at()).isEqualTo(CgmSensorAlertState.Ok)
    }

    @Test
    fun `a new sensor forgets what was known of the old one`() {
        val alerts = Libre3SensorAlerts()
        alerts.onReading(now)
        alerts.onStatus(status(errorData = 3))
        alerts.onStatus(status(errorData = 99))
        alerts.onWarmup(Phase.RECONNECTING, now)
        alerts.onStaging(StagingState.READY, now)

        alerts.onNewSensor()

        // The ready second sensor is not about the production sensor, so it stays.
        assertThat(alerts.at(now + hour)).isEqualTo(CgmSensorAlertState.SecondSensorReady(now))
        alerts.onStaging(StagingState.ABSENT, now + hour)
        assertThat(alerts.at(now + hour)).isEqualTo(CgmSensorAlertState.Ok)
        // The count of unknown codes started again too.
        alerts.onStatus(status(errorData = 99))
        assertThat(alerts.at(now + hour)).isEqualTo(CgmSensorAlertState.Ok)
    }

    // endregion
}
