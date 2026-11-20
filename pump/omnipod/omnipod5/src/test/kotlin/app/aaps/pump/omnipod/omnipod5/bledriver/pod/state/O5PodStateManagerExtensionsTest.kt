package app.aaps.pump.omnipod.omnipod5.bledriver.pod.state

import app.aaps.pump.omnipod.common.bledriver.pod.definition.ActivationProgress
import app.aaps.pump.omnipod.common.bledriver.pod.definition.BasalProgram
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Duration
import java.time.ZonedDateTime
import java.util.TimeZone

/**
 * [expiry] reads wall-clock time directly ([ZonedDateTime.now]/[System.currentTimeMillis])
 * rather than an injectable clock, so these tests assert against a tolerance window instead
 * of an exact instant - tight enough to catch a wrong formula (wrong sign, wrong field) but
 * not flaky on slow CI. The `.minusHours(8)` term is copied verbatim from
 * [app.aaps.pump.omnipod.common.bledriver.pod.state.OmnipodDashPodStateManagerImpl.expiry]
 * with no explanation found anywhere in this codebase for why it's there - this test locks
 * down the current (copied) behavior, it does not claim to justify the 8 hours.
 */
class O5PodStateManagerExtensionsTest : TestBase() {

    private fun mockState(
        podLifeInHours: Short? = null,
        minutesSinceActivation: Short? = null,
        lastStatusResponseReceived: Long? = null
    ): O5PodStateManager {
        val state = mock<O5PodStateManager>()
        whenever(state.podLifeInHours).thenReturn(podLifeInHours)
        whenever(state.minutesSinceActivation).thenReturn(minutesSinceActivation)
        whenever(state.lastStatusResponseReceived).thenReturn(lastStatusResponseReceived)
        return state
    }

    @Test
    fun `expiry is null when podLifeInHours is unknown`() {
        val state = mockState(minutesSinceActivation = 0, lastStatusResponseReceived = System.currentTimeMillis())

        assertThat(state.expiry).isNull()
    }

    @Test
    fun `expiry is null when minutesSinceActivation is unknown`() {
        val state = mockState(podLifeInHours = 72, lastStatusResponseReceived = System.currentTimeMillis())

        assertThat(state.expiry).isNull()
    }

    @Test
    fun `expiry is null when no status response has ever been received`() {
        val state = mockState(podLifeInHours = 72, minutesSinceActivation = 0)

        assertThat(state.expiry).isNull()
    }

    @Test
    fun `freshly activated pod expires close to podLifeInHours minus the 8h grace period from now`() {
        val state = mockState(podLifeInHours = 72, minutesSinceActivation = 0, lastStatusResponseReceived = System.currentTimeMillis())

        val expiresAt = requireNotNull(state.expiry)

        val expected = ZonedDateTime.now().plusHours(64)
        val diffSeconds = Duration.between(expected, expiresAt).abs().seconds
        assertThat(diffSeconds).isLessThan(10)
    }

    @Test
    fun `elapsed minutesSinceActivation shortens the remaining life`() {
        val state = mockState(podLifeInHours = 72, minutesSinceActivation = 60, lastStatusResponseReceived = System.currentTimeMillis())

        val expiresAt = requireNotNull(state.expiry)

        val expected = ZonedDateTime.now().plusHours(63)
        val diffSeconds = Duration.between(expected, expiresAt).abs().seconds
        assertThat(diffSeconds).isLessThan(10)
    }

    @Test
    fun `time elapsed since the last status response is also subtracted`() {
        val state = mockState(
            podLifeInHours = 72,
            minutesSinceActivation = 0,
            lastStatusResponseReceived = System.currentTimeMillis() - Duration.ofMinutes(30).toMillis()
        )

        val expiresAt = requireNotNull(state.expiry)

        val expected = ZonedDateTime.now().plusHours(63).plusMinutes(30)
        val diffSeconds = Duration.between(expected, expiresAt).abs().seconds
        assertThat(diffSeconds).isLessThan(10)
    }


    private fun mockTimeState(
        podActivatedAt: Long? = null,
        minutesSinceActivation: Short? = null,
        timeZoneOffset: Int? = null,
        lastStatusResponseReceived: Long? = null
    ): O5PodStateManager {
        val state = mock<O5PodStateManager>()
        whenever(state.podActivatedAt).thenReturn(podActivatedAt)
        whenever(state.minutesSinceActivation).thenReturn(minutesSinceActivation)
        whenever(state.timeZoneOffset).thenReturn(timeZoneOffset)
        whenever(state.lastStatusResponseReceived).thenReturn(lastStatusResponseReceived)
        return state
    }

    @Test
    fun `activationTime prefers podActivatedAt when page 5 has been read`() {
        val activatedAt = System.currentTimeMillis() - Duration.ofHours(5).toMillis()
        val state = mockTimeState(
            podActivatedAt = activatedAt,
            minutesSinceActivation = 300,
            lastStatusResponseReceived = System.currentTimeMillis()
        )

        assertThat(state.activationTime).isEqualTo(activatedAt)
    }

    @Test
    fun `activationTime falls back to the last status response minus the elapsed minutes`() {
        val lastUpdated = System.currentTimeMillis()
        val state = mockTimeState(minutesSinceActivation = 120, lastStatusResponseReceived = lastUpdated)

        assertThat(state.activationTime).isEqualTo(lastUpdated - Duration.ofMinutes(120).toMillis())
    }

    @Test
    fun `activationTime is null when nothing is known`() {
        assertThat(mockTimeState().activationTime).isNull()
        assertThat(mockTimeState(minutesSinceActivation = 120).activationTime).isNull()
    }

    @Test
    fun `time is null until the pod clock time zone is known`() {
        val state = mockTimeState(
            podActivatedAt = System.currentTimeMillis(),
            minutesSinceActivation = 0,
            lastStatusResponseReceived = System.currentTimeMillis()
        )

        assertThat(state.time).isNull()
    }

    @Test
    fun `time on a pod set to the phone time zone matches the phone clock`() {
        val now = System.currentTimeMillis()
        val state = mockTimeState(
            podActivatedAt = now - Duration.ofHours(10).toMillis(),
            minutesSinceActivation = 600,
            timeZoneOffset = TimeZone.getDefault().getOffset(now),
            lastStatusResponseReceived = now
        )

        val podTime = requireNotNull(state.time)

        assertThat(Duration.between(ZonedDateTime.now(), podTime).abs().seconds).isLessThan(10)
        assertThat(requireNotNull(state.timeDrift).abs().seconds).isLessThan(10)
    }

    @Test
    fun `time keeps running while no new status response arrives`() {
        val now = System.currentTimeMillis()
        val lastUpdated = now - Duration.ofMinutes(30).toMillis()
        val state = mockTimeState(
            podActivatedAt = lastUpdated - Duration.ofHours(10).toMillis(),
            minutesSinceActivation = 600,
            timeZoneOffset = TimeZone.getDefault().getOffset(now),
            lastStatusResponseReceived = lastUpdated
        )

        val podTime = requireNotNull(state.time)

        assertThat(Duration.between(ZonedDateTime.now(), podTime).abs().seconds).isLessThan(10)
    }

    @Test
    fun `a pod counter running ahead of the activation anchor shows as drift`() {
        val now = System.currentTimeMillis()
        val state = mockTimeState(
            podActivatedAt = now - Duration.ofHours(10).toMillis(),
            minutesSinceActivation = 630,
            timeZoneOffset = TimeZone.getDefault().getOffset(now),
            lastStatusResponseReceived = now
        )

        val drift = requireNotNull(state.timeDrift)

        assertThat(drift.minusMinutes(30).abs().seconds).isLessThan(10)
    }

    @Test
    fun `the pod time zone only changes how the time is shown, not the instant`() {
        val now = System.currentTimeMillis()
        val phoneOffset = TimeZone.getDefault().getOffset(now)
        val state = mockTimeState(
            podActivatedAt = now - Duration.ofHours(10).toMillis(),
            minutesSinceActivation = 600,
            timeZoneOffset = phoneOffset - Duration.ofHours(2).toMillis().toInt(),
            lastStatusResponseReceived = now
        )

        val podTime = requireNotNull(state.time)

        assertThat(podTime.offset.totalSeconds).isEqualTo(phoneOffset / 1000 - 2 * 3600)
        assertThat(Duration.between(ZonedDateTime.now(), podTime).abs().seconds).isLessThan(10)
    }

    private fun mockDriftState(
        totalPulsesDelivered: Short? = null,
        cumulativeBolusPulsesDelivered: Short? = null,
        basalExpected: Double? = null
    ): O5PodStateManager {
        val state = mock<O5PodStateManager>()
        whenever(state.totalPulsesDelivered).thenReturn(totalPulsesDelivered)
        whenever(state.cumulativeBolusPulsesDelivered).thenReturn(cumulativeBolusPulsesDelivered)
        whenever(state.basalExpected).thenReturn(basalExpected)
        return state
    }

    @Test
    fun `basalPulsesDelivered is null when either input is unknown`() {
        assertThat(mockDriftState(totalPulsesDelivered = 100).basalPulsesDelivered).isNull()
        assertThat(mockDriftState(cumulativeBolusPulsesDelivered = 20).basalPulsesDelivered).isNull()
    }

    @Test
    fun `basalPulsesDelivered and basalDelivered subtract bolus pulses from the pod total`() {
        val state = mockDriftState(totalPulsesDelivered = 100, cumulativeBolusPulsesDelivered = 20)

        assertThat(state.basalPulsesDelivered).isEqualTo(80.toShort())
        assertThat(state.basalDelivered).isWithin(1e-9).of(4.0)
    }

    @Test
    fun `basalDrift is zero when nothing is expected yet - falls back to basalDelivered itself`() {
        val state = mockDriftState(totalPulsesDelivered = 100, cumulativeBolusPulsesDelivered = 20, basalExpected = null)

        assertThat(state.basalDrift).isWithin(1e-9).of(0.0)
    }

    @Test
    fun `basalDrift is positive when more basal was delivered than expected`() {
        val state = mockDriftState(totalPulsesDelivered = 100, cumulativeBolusPulsesDelivered = 20, basalExpected = 3.0)

        assertThat(state.basalDrift).isWithin(1e-9).of(1.0)
    }

    @Test
    fun `basalDrift is negative when less basal was delivered than expected`() {
        val state = mockDriftState(totalPulsesDelivered = 100, cumulativeBolusPulsesDelivered = 20, basalExpected = 5.0)

        assertThat(state.basalDrift).isWithin(1e-9).of(-1.0)
    }


    /** Full-day flat-rate program - sidesteps day-boundary/timezone edge cases entirely
     *  since the rate is constant across every slot the day-loop could add. */
    private fun flatRateProgram(unitsPerHour: Double) =
        BasalProgram(listOf(BasalProgram.Segment(0, 48, (unitsPerHour * 100).toInt())))

    @Test
    fun `integrateO5ExpectedDelivery returns null when startTime is after endTime`() {
        val now = System.currentTimeMillis()
        assertThat(
            integrateO5ExpectedDelivery(now, now - 1000, null, null, null, flatRateProgram(1.0))
        ).isNull()
    }

    @Test
    fun `integrateO5ExpectedDelivery returns null when there is no basal program and no temp basal`() {
        val now = System.currentTimeMillis()
        assertThat(
            integrateO5ExpectedDelivery(now, now + 3600_000L, null, null, null, null)
        ).isNull()
    }

    @Test
    fun `integrateO5ExpectedDelivery integrates a flat basal program over one hour`() {
        val now = System.currentTimeMillis()
        val result = integrateO5ExpectedDelivery(now, now + 3600_000L, null, null, null, flatRateProgram(1.0))

        assertThat(requireNotNull(result)).isWithin(1e-6).of(1.0)
    }

    @Test
    fun `integrateO5ExpectedDelivery uses the temp basal rate for the whole window when it fully covers it`() {
        val now = System.currentTimeMillis()
        val result = integrateO5ExpectedDelivery(
            now, now + 3600_000L,
            activeTempBasalStartTime = now, activeTempBasalRate = 2.0, activeTempBasalDurationMinutes = 120,
            basalProgram = flatRateProgram(1.0)
        )

        assertThat(requireNotNull(result)).isWithin(1e-6).of(2.0)
    }

    @Test
    fun `integrateO5ExpectedDelivery blends temp basal and scheduled basal across a boundary`() {
        val now = System.currentTimeMillis()
        val result = integrateO5ExpectedDelivery(
            now, now + 3600_000L,
            activeTempBasalStartTime = now, activeTempBasalRate = 2.0, activeTempBasalDurationMinutes = 30,
            basalProgram = flatRateProgram(1.0)
        )

        assertThat(requireNotNull(result)).isWithin(1e-6).of(1.5)
    }


    @Test
    fun `nextBasalExpected stays null before activation completes`() {
        val state = mock<O5PodStateManager>()
        whenever(state.basalExpected).thenReturn(null)
        whenever(state.activationProgress).thenReturn(ActivationProgress.PRIME_COMPLETED)

        assertThat(state.nextBasalExpected(previousUpdate = null, now = System.currentTimeMillis())).isNull()
    }

    @Test
    fun `nextBasalExpected seeds itself from basalDelivered the first time activation is complete`() {
        val state = mock<O5PodStateManager>()
        whenever(state.basalExpected).thenReturn(null)
        whenever(state.activationProgress).thenReturn(ActivationProgress.COMPLETED)
        whenever(state.totalPulsesDelivered).thenReturn(100)
        whenever(state.cumulativeBolusPulsesDelivered).thenReturn(0)

        val result = state.nextBasalExpected(previousUpdate = null, now = System.currentTimeMillis())

        assertThat(requireNotNull(result)).isWithin(1e-9).of(5.0)
    }

    @Test
    fun `nextBasalExpected leaves the value unchanged when there is no previous update to integrate from`() {
        val state = mock<O5PodStateManager>()
        whenever(state.basalExpected).thenReturn(2.0)

        val result = state.nextBasalExpected(previousUpdate = null, now = System.currentTimeMillis())

        assertThat(requireNotNull(result)).isWithin(1e-9).of(2.0)
    }

    @Test
    fun `nextBasalExpected integrates the elapsed period onto the existing value`() {
        val state = mock<O5PodStateManager>()
        whenever(state.basalExpected).thenReturn(2.0)
        whenever(state.activeTempBasalStartTime).thenReturn(null)
        whenever(state.activeTempBasalRate).thenReturn(null)
        whenever(state.activeTempBasalDurationMinutes).thenReturn(null)
        whenever(state.basalProgram).thenReturn(flatRateProgram(1.0))
        val now = System.currentTimeMillis()

        val result = state.nextBasalExpected(previousUpdate = now - 3600_000L, now = now)

        assertThat(requireNotNull(result)).isWithin(1e-6).of(3.0)
    }
}
