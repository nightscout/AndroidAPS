package app.aaps.plugins.dexcomoneplus.parse

import app.aaps.plugins.dexcomoneplus.OnePlusWarmupState
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class OnePlusCalibrationMapperTest {

    @Test
    fun `ok maps to READY`() {
        val s = OnePlusCalibrationMapper.toWarmupState(OnePlusCalibrationState.Ok)
        assertThat(s.phase).isEqualTo(OnePlusWarmupState.Phase.READY)
    }

    @Test
    fun `warmingUp maps to WARMING`() {
        val s = OnePlusCalibrationMapper.toWarmupState(OnePlusCalibrationState.WarmingUp)
        assertThat(s.phase).isEqualTo(OnePlusWarmupState.Phase.WARMING)
    }

    @Test
    fun `failed maps to FAILED`() {
        val s = OnePlusCalibrationMapper.toWarmupState(OnePlusCalibrationState.SensorFailed)
        assertThat(s.phase).isEqualTo(OnePlusWarmupState.Phase.FAILED)
    }

    @Test
    fun `a 15-day sensor counts its warm-up from its own length`() {
        // 40 min into a 62 min warm-up: past the 30 min default, still 22 min to go.
        val s = OnePlusCalibrationMapper.toWarmupState(
            state = OnePlusCalibrationState.WarmingUp,
            nowMs = 1_000_000L,
            sessionAgeSeconds = 40 * 60,
            warmupMs = 62L * 60_000L,
        )
        assertThat(s.phase).isEqualTo(OnePlusWarmupState.Phase.WARMING)
        assertThat(s.remainingMs).isEqualTo(22L * 60_000L)
        assertThat(s.endsAtEpochMs).isEqualTo(1_000_000L + 22L * 60_000L)
    }
}
