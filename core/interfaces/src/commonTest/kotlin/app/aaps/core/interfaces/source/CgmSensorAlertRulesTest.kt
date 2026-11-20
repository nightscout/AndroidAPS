package app.aaps.core.interfaces.source

import kotlin.test.Test
import kotlin.test.assertEquals

/** The priority order of [CgmSensorAlertRules.map], the same for every BG source. */
class CgmSensorAlertRulesTest {

    private val now = 1_000_000_000L
    private val hour = 3_600_000L

    private fun input(
        stopped: Boolean = false,
        sensorStored: Boolean = true,
        failure: CgmSensorFailure? = null,
        endedBySensor: Boolean = false,
        expiresAtMs: Long? = now + 100 * hour,
        linkLostSinceMs: Long? = null,
        lastReadingAtMs: Long? = now,
        readingExpected: Boolean = true,
        issueSinceMs: Long? = null,
        secondSensorReadyAtMs: Long? = null,
        warmupDoneAtMs: Long? = null,
        expiringLeadMs: Long = 12 * hour,
    ) = CgmSensorAlertRules.Input(
        stopped, sensorStored, failure, endedBySensor, expiresAtMs, linkLostSinceMs, lastReadingAtMs, readingExpected,
        issueSinceMs, secondSensorReadyAtMs, warmupDoneAtMs, expiringLeadMs, now
    )

    private fun map(i: CgmSensorAlertRules.Input) = CgmSensorAlertRules.map(i)

    @Test
    fun aHealthySensorIsOk() {
        assertEquals(CgmSensorAlertState.Ok, map(input()))
    }

    @Test
    fun aStoppedSourceSaysNothing() {
        assertEquals(CgmSensorAlertState.Ok, map(input(stopped = true, failure = CgmSensorFailure.SENSOR_ERROR)))
    }

    @Test
    fun withoutASensorOnlyAReadySecondSensorCounts() {
        assertEquals(CgmSensorAlertState.Ok, map(input(sensorStored = false, linkLostSinceMs = now - hour)))
        assertEquals(CgmSensorAlertState.SecondSensorReady(5L), map(input(sensorStored = false, secondSensorReadyAtMs = 5L)))
    }

    @Test
    fun failureComesFirst() {
        assertEquals(
            CgmSensorAlertState.Failed(CgmSensorFailure.SENSOR_ERROR),
            map(input(failure = CgmSensorFailure.SENSOR_ERROR, expiresAtMs = now - hour, linkLostSinceMs = now - hour))
        )
    }

    @Test
    fun anEndTheSensorReportsIsExpiredAfterTheUsableTimeElseEndedEarly() {
        val endsAt = now - hour
        assertEquals(CgmSensorAlertState.Expired(endsAt), map(input(endedBySensor = true, expiresAtMs = endsAt)))
        assertEquals(CgmSensorAlertState.Failed(CgmSensorFailure.ENDED_EARLY), map(input(endedBySensor = true)))
    }

    @Test
    fun theEndKeepsItsTimeSoItIsOneEpisode() {
        val endsAt = now - hour
        assertEquals(CgmSensorAlertState.Expired(endsAt), map(input(expiresAtMs = endsAt)))
        assertEquals(CgmSensorAlertState.Expired(endsAt), map(input(expiresAtMs = endsAt, linkLostSinceMs = now - 1)))
    }

    @Test
    fun aLostLinkOrSilenceIsASignalLoss() {
        assertEquals(CgmSensorAlertState.SignalLost(now - 60_000L), map(input(linkLostSinceMs = now - 60_000L)))
        val last = now - CgmSensorAlertRules.SILENCE_MS
        assertEquals(CgmSensorAlertState.SignalLost(last), map(input(lastReadingAtMs = last)))
        assertEquals(CgmSensorAlertState.Ok, map(input(lastReadingAtMs = last + 1)))
        // No reading on purpose (warm-up): silence is not a loss.
        assertEquals(CgmSensorAlertState.Ok, map(input(lastReadingAtMs = now - hour, readingExpected = false)))
    }

    @Test
    fun aSensorIssueComesAfterASignalLoss() {
        assertEquals(CgmSensorAlertState.SensorIssue(7L), map(input(issueSinceMs = 7L)))
        assertEquals(CgmSensorAlertState.SignalLost(3L), map(input(issueSinceMs = 7L, linkLostSinceMs = 3L)))
    }

    @Test
    fun endingSoonOnlyInsideTheLeadTime() {
        val endsAt = now + 12 * hour
        assertEquals(CgmSensorAlertState.Expiring(endsAt), map(input(expiresAtMs = endsAt)))
        assertEquals(CgmSensorAlertState.Ok, map(input(expiresAtMs = endsAt + 1)))
        // Before the lead time a ready second sensor is not hidden.
        assertEquals(CgmSensorAlertState.SecondSensorReady(9L), map(input(expiresAtMs = endsAt + 1, secondSensorReadyAtMs = 9L)))
        assertEquals(CgmSensorAlertState.Expiring(endsAt), map(input(expiresAtMs = endsAt, secondSensorReadyAtMs = 9L)))
    }

    @Test
    fun warmupDoneIsTheLeastImportant() {
        assertEquals(CgmSensorAlertState.WarmupDone(4L), map(input(warmupDoneAtMs = 4L)))
        assertEquals(CgmSensorAlertState.SecondSensorReady(9L), map(input(warmupDoneAtMs = 4L, secondSensorReadyAtMs = 9L)))
    }
}
