package app.aaps.plugins.source

import app.aaps.plugins.dexcomoneplus.OnePlusCalibrationOutcome
import app.aaps.plugins.dexcomoneplus.session.OnePlusCalibrationGate
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The user text for each calibration result. "Not sent" and "unknown" ask the user for opposite
 * things (enter it again / do not send it again), so they must never share a text.
 */
class DexcomOnePlusCalibrationTextTest {

    @Test
    fun `a value dropped before the write says it was not sent and may be entered again`() {
        val notSent = OnePlusCalibrationOutcome.NotSent(OnePlusCalibrationOutcome.NotSent.Reason.NOT_READY)

        assertThat(DexcomOnePlusCalibrationText.status(notSent, waiting = false))
            .isEqualTo(SourceStrings.dexcom_oneplus_calibration_dropped)
    }

    @Test
    fun `a written value with no clear answer stays unknown`() {
        assertThat(DexcomOnePlusCalibrationText.status(OnePlusCalibrationOutcome.Unknown("no answer"), waiting = false))
            .isEqualTo(SourceStrings.dexcom_oneplus_calibration_unknown)
    }

    @Test
    fun `not sent and unknown never share a text`() {
        val notSentTexts = OnePlusCalibrationOutcome.NotSent.Reason.entries.map {
            DexcomOnePlusCalibrationText.status(OnePlusCalibrationOutcome.NotSent(it), waiting = false)
        }.toSet()

        assertThat(notSentTexts).containsExactly(SourceStrings.dexcom_oneplus_calibration_dropped)
        assertThat(notSentTexts).doesNotContain(SourceStrings.dexcom_oneplus_calibration_unknown)
    }

    @Test
    fun `the reason of a dropped value reads like the refusal before the confirmation`() {
        OnePlusCalibrationGate.Refusal.entries.forEach { refusal ->
            assertThat(DexcomOnePlusCalibrationText.notSentReason(OnePlusCalibrationOutcome.NotSent.Reason.of(refusal)))
                .isEqualTo(DexcomOnePlusCalibrationText.refusal(refusal))
        }
        assertThat(DexcomOnePlusCalibrationText.notSentReason(OnePlusCalibrationOutcome.NotSent.Reason.SENSOR_CHANGED))
            .isEqualTo(SourceStrings.dexcom_oneplus_calibration_dropped_sensor_changed)
    }
}
