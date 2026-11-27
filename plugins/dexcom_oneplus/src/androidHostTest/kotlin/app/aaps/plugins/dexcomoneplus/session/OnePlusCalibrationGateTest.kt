package app.aaps.plugins.dexcomoneplus.session

import app.aaps.plugins.dexcomoneplus.OnePlusGlucoseSample
import app.aaps.plugins.dexcomoneplus.OnePlusWarmupState
import app.aaps.plugins.dexcomoneplus.session.OnePlusCalibrationGate.Refusal
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class OnePlusCalibrationGateTest {

    private val now = 1_700_000_000_000L

    private fun reading(ageMs: Long = 60_000L, rate: Double? = 0.5) =
        OnePlusGlucoseSample(mgdl = 120.0, timestampMs = now - ageMs, trendSlopeMgdlPerMin = rate)

    private fun check(
        glucose: Int = 120,
        bloodAgoMs: Long = 60_000L,
        phase: OnePlusWarmupState.Phase = OnePlusWarmupState.Phase.READY,
        last: OnePlusGlucoseSample? = reading(),
    ) = OnePlusCalibrationGate.check(glucose, now - bloodAgoMs, now, phase, last)

    @Test
    fun `a stable sensor with a fresh reading takes the value`() {
        assertThat(check()).isNull()
    }

    @Test
    fun `the gap between two radio windows is not a reason to refuse`() {
        assertThat(check(phase = OnePlusWarmupState.Phase.RECONNECTING)).isNull()
        assertThat(check(phase = OnePlusWarmupState.Phase.CONNECTING)).isNull()
    }

    @Test
    fun `values outside 40 to 400 are refused`() {
        assertThat(check(glucose = 39)).isEqualTo(Refusal.OUT_OF_RANGE)
        assertThat(check(glucose = 401)).isEqualTo(Refusal.OUT_OF_RANGE)
        assertThat(check(glucose = 40)).isNull()
        assertThat(check(glucose = 400)).isNull()
    }

    @Test
    fun `blood older than one hour or in the future is refused`() {
        assertThat(check(bloodAgoMs = 61L * 60_000L)).isEqualTo(Refusal.TOO_OLD)
        assertThat(check(bloodAgoMs = -60_000L)).isEqualTo(Refusal.TOO_OLD)
    }

    @Test
    fun `a warming or failed sensor is refused`() {
        assertThat(check(phase = OnePlusWarmupState.Phase.WARMING)).isEqualTo(Refusal.NOT_READY)
        assertThat(check(phase = OnePlusWarmupState.Phase.FAILED)).isEqualTo(Refusal.NOT_READY)
    }

    @Test
    fun `no recent reading is refused`() {
        assertThat(check(last = null)).isEqualTo(Refusal.NO_RECENT_READING)
        assertThat(check(last = reading(ageMs = OnePlusCalibrationGate.RECENT_READING_MS + 1))).isEqualTo(Refusal.NO_RECENT_READING)
    }

    @Test
    fun `glucose changing faster than 2 per minute is refused in both directions`() {
        assertThat(check(last = reading(rate = 2.1))).isEqualTo(Refusal.CHANGING_TOO_FAST)
        assertThat(check(last = reading(rate = -2.1))).isEqualTo(Refusal.CHANGING_TOO_FAST)
        assertThat(check(last = reading(rate = 2.0))).isNull()
    }

    @Test
    fun `a reading without a trend is refused`() {
        assertThat(check(last = reading(rate = null))).isEqualTo(Refusal.RATE_UNKNOWN)
    }
}
