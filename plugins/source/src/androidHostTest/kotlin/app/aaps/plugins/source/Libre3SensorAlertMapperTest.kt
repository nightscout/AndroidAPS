package app.aaps.plugins.source

import app.aaps.core.interfaces.source.CgmSensorAlertState
import app.aaps.core.interfaces.source.CgmSensorFailure
import app.aaps.plugins.libre3.Libre3WarmupState.Phase
import app.aaps.plugins.libre3.parse.Libre3PatchState
import app.aaps.plugins.libre3.parse.Libre3PatchStatus
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * How the Libre 3 plugin state becomes the one generic sensor alarm state, and the priority order
 * Failed > Expired > SignalLost > Expiring > SecondSensorReady > WarmupDone > Ok.
 */
class Libre3SensorAlertMapperTest {

    private val minute = 60_000L
    private val hour = 60 * minute
    private val activated = 1_800_000_000_000L
    private val wearMinutes = 14 * 24 * 60
    private val lifeEnd = activated + wearMinutes * minute
    private val expires = lifeEnd + Libre3Staging.SENSOR_GRACE_MS
    private val lead = 12 * hour

    /** Day 5 of a running sensor, link up, a reading a minute ago: nothing to say. */
    private val running = Libre3SensorAlertMapper.Input(
        stopped = false,
        sensorStored = true,
        phase = Phase.READY,
        activatedAtMs = activated,
        wearMinutes = wearMinutes,
        fault = null,
        linkLostSinceMs = null,
        lastReadingAtMs = activated + 5 * 24 * hour - minute,
        warmupDoneAtMs = null,
        secondSensorReadyAtMs = null,
        expiringLeadMs = lead,
        nowMs = activated + 5 * 24 * hour,
    )

    private fun map(input: Libre3SensorAlertMapper.Input) = Libre3SensorAlertMapper.map(input)

    private fun status(errorData: Int = 0, patchState: Int = 4) = Libre3PatchStatus(
        lifeCount = 100, errorData = errorData, eventData = 0, index = 0, patchState = Libre3PatchState(patchState),
        currentLifeCount = 100, stackDisconnectReason = 0, appDisconnectReason = 0,
    )

    // region each state

    @Test
    fun `a running sensor is ok`() {
        assertThat(map(running)).isEqualTo(CgmSensorAlertState.Ok)
    }

    @Test
    fun `a final driver failure is failed, other`() {
        assertThat(map(running.copy(phase = Phase.FAILED))).isEqualTo(CgmSensorAlertState.Failed(CgmSensorFailure.OTHER))
    }

    @Test
    fun `a fault reported by the sensor is failed, sensor error`() {
        assertThat(map(running.copy(fault = Libre3SensorFault.ERROR))).isEqualTo(CgmSensorAlertState.Failed(CgmSensorFailure.SENSOR_ERROR))
    }

    @Test
    fun `an end reported before the end of life is failed, ended early`() {
        assertThat(map(running.copy(fault = Libre3SensorFault.ENDED)))
            .isEqualTo(CgmSensorAlertState.Failed(CgmSensorFailure.ENDED_EARLY))
    }

    @Test
    fun `an end reported after the end of life is expired, at the end of the usable time`() {
        val seen = lifeEnd + hour
        assertThat(map(running.copy(fault = Libre3SensorFault.ENDED, nowMs = seen + minute, lastReadingAtMs = seen)))
            .isEqualTo(CgmSensorAlertState.Expired(expires))
    }

    /** The urgent alarm sounds once per end: the episode must not move with the health message. */
    @Test
    fun `the first end message after life and grace keeps the same expired episode`() {
        val past = running.copy(nowMs = expires + hour, lastReadingAtMs = expires + hour - minute)
        val before = map(past)
        val after = map(past.copy(fault = Libre3SensorFault.ENDED, nowMs = past.nowMs + minute))

        assertThat(before).isEqualTo(CgmSensorAlertState.Expired(expires))
        assertThat(after).isEqualTo(before)
    }

    /** After a restart the plugin has forgotten the health message; the episode must still be the same. */
    @Test
    fun `a restart keeps the same expired episode`() {
        val ended = map(running.copy(fault = Libre3SensorFault.ENDED, nowMs = lifeEnd + 2 * hour, lastReadingAtMs = lifeEnd + hour))
        val afterRestart = map(running.copy(fault = null, nowMs = expires + hour, lastReadingAtMs = null))
        val afterRestartAndMessage = map(running.copy(fault = Libre3SensorFault.ENDED, nowMs = expires + hour, lastReadingAtMs = null))

        assertThat(ended).isEqualTo(CgmSensorAlertState.Expired(expires))
        assertThat(afterRestart).isEqualTo(ended)
        assertThat(afterRestartAndMessage).isEqualTo(ended)
    }

    @Test
    fun `past life and grace the sensor is expired`() {
        assertThat(map(running.copy(nowMs = expires, lastReadingAtMs = expires - minute))).isEqualTo(CgmSensorAlertState.Expired(expires))
    }

    @Test
    fun `from the warning time before the end the sensor is expiring`() {
        assertThat(map(running.copy(nowMs = expires - lead, lastReadingAtMs = expires - lead)))
            .isEqualTo(CgmSensorAlertState.Expiring(expires))
    }

    @Test
    fun `earlier than the warning time the sensor is not expiring yet`() {
        assertThat(map(running.copy(nowMs = expires - lead - 1, lastReadingAtMs = expires - lead - 1)))
            .isEqualTo(CgmSensorAlertState.Ok)
    }

    @Test
    fun `the end of the usable time is the sensor life plus 12 h of grace`() {
        assertThat(expires - lifeEnd).isEqualTo(12 * hour)
        assertThat(map(running.copy(nowMs = expires - 1, lastReadingAtMs = expires - 1))).isEqualTo(CgmSensorAlertState.Expiring(expires))
        assertThat(map(running.copy(nowMs = expires, lastReadingAtMs = expires))).isEqualTo(CgmSensorAlertState.Expired(expires))
    }

    @Test
    fun `the wear time read by the scan sets the end`() {
        val shortLife = 10 * 24 * 60
        val end = activated + shortLife * minute + Libre3Staging.SENSOR_GRACE_MS
        assertThat(map(running.copy(wearMinutes = shortLife, nowMs = end - hour, lastReadingAtMs = end - hour)))
            .isEqualTo(CgmSensorAlertState.Expiring(end))
    }

    @Test
    fun `an unknown start time gives no end of life`() {
        assertThat(map(running.copy(activatedAtMs = 0L, nowMs = expires + hour, lastReadingAtMs = expires + hour)))
            .isEqualTo(CgmSensorAlertState.Ok)
    }

    @Test
    fun `a lost link is signal lost since then`() {
        val since = running.nowMs - 3 * minute
        assertThat(map(running.copy(phase = Phase.RECONNECTING, linkLostSinceMs = since))).isEqualTo(CgmSensorAlertState.SignalLost(since))
    }

    @Test
    fun `no reading for 5 min with the link up is signal lost since the last reading`() {
        val last = running.nowMs - Libre3SensorAlertMapper.SILENCE_MS
        assertThat(map(running.copy(lastReadingAtMs = last))).isEqualTo(CgmSensorAlertState.SignalLost(last))
    }

    @Test
    fun `no reading for less than 5 min is no signal loss`() {
        assertThat(map(running.copy(lastReadingAtMs = running.nowMs - Libre3SensorAlertMapper.SILENCE_MS + 1))).isEqualTo(CgmSensorAlertState.Ok)
    }

    @Test
    fun `no reading during warm-up is no signal loss`() {
        assertThat(map(running.copy(phase = Phase.WARMING, lastReadingAtMs = running.nowMs - hour))).isEqualTo(CgmSensorAlertState.Ok)
    }

    @Test
    fun `no reading during pairing is no signal loss`() {
        assertThat(map(running.copy(phase = Phase.PAIRING, lastReadingAtMs = running.nowMs - hour))).isEqualTo(CgmSensorAlertState.Ok)
    }

    /** Idle after a restart, connecting, Bluetooth off: an old last reading is a lost signal whatever the phase. */
    @Test
    fun `an old last reading is signal lost since then in every other phase`() {
        val last = running.nowMs - hour
        listOf(Phase.IDLE, Phase.CONNECTING, Phase.RECONNECTING, Phase.READY).forEach { phase ->
            assertThat(map(running.copy(phase = phase, lastReadingAtMs = last))).isEqualTo(CgmSensorAlertState.SignalLost(last))
        }
    }

    @Test
    fun `idle with a recent reading is no signal loss`() {
        assertThat(map(running.copy(phase = Phase.IDLE, lastReadingAtMs = running.nowMs - minute))).isEqualTo(CgmSensorAlertState.Ok)
    }

    @Test
    fun `the end of warm-up is warm-up done`() {
        val at = running.nowMs - minute
        assertThat(map(running.copy(warmupDoneAtMs = at))).isEqualTo(CgmSensorAlertState.WarmupDone(at))
    }

    @Test
    fun `a ready pre-soak sensor is second sensor ready`() {
        val at = running.nowMs - minute
        assertThat(map(running.copy(secondSensorReadyAtMs = at))).isEqualTo(CgmSensorAlertState.SecondSensorReady(at))
    }

    /**
     * A pre-soak is usually started near the end of the sensor that feeds the loop. While that end
     * is not worth a warning yet, the ready second sensor must not be hidden by it.
     */
    @Test
    fun `a ready pre-soak sensor is reported while the production sensor nears its end`() {
        val now = expires - 30 * hour
        val at = now - hour
        val input = running.copy(nowMs = now, lastReadingAtMs = now, secondSensorReadyAtMs = at)

        assertThat(input.sensorStored).isTrue()
        assertThat(map(input)).isEqualTo(CgmSensorAlertState.SecondSensorReady(at))
        // From the warning time on, "ending soon" is more important.
        assertThat(map(input.copy(nowMs = expires - lead, lastReadingAtMs = expires - lead))).isEqualTo(CgmSensorAlertState.Expiring(expires))
    }

    @Test
    fun `without a production sensor only a ready second sensor counts`() {
        val noSensor = running.copy(sensorStored = false, phase = Phase.FAILED, fault = Libre3SensorFault.ERROR)
        assertThat(map(noSensor)).isEqualTo(CgmSensorAlertState.Ok)
        assertThat(map(noSensor.copy(secondSensorReadyAtMs = 5L))).isEqualTo(CgmSensorAlertState.SecondSensorReady(5L))
    }

    @Test
    fun `a stopped plugin is ok whatever it knows`() {
        assertThat(map(running.copy(stopped = true, phase = Phase.FAILED, fault = Libre3SensorFault.ERROR, nowMs = expires + hour)))
            .isEqualTo(CgmSensorAlertState.Ok)
    }

    // endregion

    // region priority

    @Test
    fun `failed comes before expired`() {
        assertThat(map(running.copy(phase = Phase.FAILED, nowMs = expires + hour))).isEqualTo(CgmSensorAlertState.Failed(CgmSensorFailure.OTHER))
    }

    @Test
    fun `expired comes before signal lost`() {
        assertThat(map(running.copy(nowMs = expires + hour, linkLostSinceMs = expires))).isEqualTo(CgmSensorAlertState.Expired(expires))
    }

    @Test
    fun `signal lost comes before expiring`() {
        val now = expires - hour
        assertThat(map(running.copy(nowMs = now, linkLostSinceMs = now - minute))).isEqualTo(CgmSensorAlertState.SignalLost(now - minute))
    }

    @Test
    fun `expiring comes before second sensor ready`() {
        val now = expires - hour
        assertThat(map(running.copy(nowMs = now, lastReadingAtMs = now, secondSensorReadyAtMs = now - minute)))
            .isEqualTo(CgmSensorAlertState.Expiring(expires))
    }

    @Test
    fun `second sensor ready comes before warm-up done`() {
        assertThat(map(running.copy(secondSensorReadyAtMs = 7L, warmupDoneAtMs = 8L))).isEqualTo(CgmSensorAlertState.SecondSensorReady(7L))
    }

    // endregion

    // region health message of the sensor

    @Test
    fun `a good health message is no fault`() {
        assertThat(Libre3SensorAlertMapper.faultOf(status())).isNull()
    }

    @Test
    fun `a sending problem is no fault`() {
        assertThat(Libre3SensorAlertMapper.faultOf(status(errorData = 7))).isNull()
    }

    @Test
    fun `an insertion failure or an unknown code is a sensor error`() {
        assertThat(Libre3SensorAlertMapper.faultOf(status(errorData = 3))).isEqualTo(Libre3SensorFault.ERROR)
        assertThat(Libre3SensorAlertMapper.faultOf(status(errorData = 99))).isEqualTo(Libre3SensorFault.ERROR)
    }

    @Test
    fun `an unknown code in an ended state is an end`() {
        assertThat(Libre3SensorAlertMapper.faultOf(status(errorData = 99, patchState = 6))).isEqualTo(Libre3SensorFault.ENDED)
    }

    @Test
    fun `an unknown code is a fault only after two health messages in a row`() {
        val filter = Libre3SensorFaultFilter()

        assertThat(filter.onStatus(status(errorData = 99))).isNull()
        assertThat(filter.onStatus(status(errorData = 99))).isEqualTo(Libre3SensorFault.ERROR)
        // A good message clears it and starts the count again.
        assertThat(filter.onStatus(status())).isNull()
        assertThat(filter.onStatus(status(errorData = 99))).isNull()
    }

    @Test
    fun `a good message between two unknown codes resets the count`() {
        val filter = Libre3SensorFaultFilter()

        filter.onStatus(status(errorData = 99))
        filter.onStatus(status())
        assertThat(filter.onStatus(status(errorData = 99))).isNull()
    }

    @Test
    fun `a known fault or an end counts at once`() {
        assertThat(Libre3SensorFaultFilter().onStatus(status(errorData = 3))).isEqualTo(Libre3SensorFault.ERROR)
        assertThat(Libre3SensorFaultFilter().onStatus(status(errorData = 5))).isEqualTo(Libre3SensorFault.ENDED)
        assertThat(Libre3SensorFaultFilter().onStatus(status(patchState = 6))).isEqualTo(Libre3SensorFault.ENDED)
    }

    @Test
    fun `a reset forgets the fault and the count`() {
        val filter = Libre3SensorFaultFilter()
        filter.onStatus(status(errorData = 3))
        filter.onStatus(status(errorData = 99))

        filter.reset()

        assertThat(filter.fault).isNull()
        assertThat(filter.onStatus(status(errorData = 99))).isNull()
    }

    @Test
    fun `an expired or terminated code or state is an end`() {
        assertThat(Libre3SensorAlertMapper.faultOf(status(errorData = 5))).isEqualTo(Libre3SensorFault.ENDED)
        assertThat(Libre3SensorAlertMapper.faultOf(status(errorData = 6))).isEqualTo(Libre3SensorFault.ENDED)
        assertThat(Libre3SensorAlertMapper.faultOf(status(patchState = 6))).isEqualTo(Libre3SensorFault.ENDED)
        assertThat(Libre3SensorAlertMapper.faultOf(status(patchState = 5))).isEqualTo(Libre3SensorFault.ENDED)
    }

    // endregion
}
