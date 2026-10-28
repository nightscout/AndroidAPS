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
}
