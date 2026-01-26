package app.aaps.plugins.main.extensions

import app.aaps.core.data.aps.SMBDefaults
import app.aaps.core.data.model.TB
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.aps.AutosensResult
import app.aaps.core.objects.extensions.iobCalc
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class TemporaryBasalExtensionKtTest : TestBaseWithProfile() {

    @Test
    fun iobCalc() {
        val dia = effectiveProfile.iCfg.dia

        val temporaryBasal = TB(timestamp = now - 1, rate = 200.0, isAbsolute = false, duration = T.hours(1).msecs(), type = TB.Type.NORMAL)
        // there should zero IOB after now
        assertThat(temporaryBasal.iobCalc(now, effectiveProfile).basaliob).isWithin(0.01).of(0.0)
        // there should be significant IOB at EB finish
        assertThat(temporaryBasal.iobCalc(now + T.hours(1).msecs(), effectiveProfile).basaliob).isGreaterThan(0.8)
        // there should be less that 5% after DIA -1
        assertThat(temporaryBasal.iobCalc(now + T.hours(dia.toLong() - 1).msecs(), effectiveProfile).basaliob).isLessThan(0.05)
        // there should be zero after DIA
        assertThat(temporaryBasal.iobCalc(now + T.hours(dia.toLong() + 1).msecs(), effectiveProfile).basaliob).isEqualTo(0.0)
        // no IOB for invalid record
        temporaryBasal.isValid = false
        assertThat(temporaryBasal.iobCalc(now + T.hours(1).msecs(), effectiveProfile).basaliob).isEqualTo(0.0)

        temporaryBasal.isValid = true
        val asResult = AutosensResult()
        // there should zero IOB after now
        assertThat(temporaryBasal.iobCalc(now, effectiveProfile, asResult, SMBDefaults.exercise_mode, SMBDefaults.half_basal_exercise_target, true).basaliob).isWithin(0.01).of(0.0)
        // there should be significant IOB at EB finish
        assertThat(temporaryBasal.iobCalc(now + T.hours(1).msecs(), effectiveProfile, asResult, SMBDefaults.exercise_mode, SMBDefaults.half_basal_exercise_target, true).basaliob).isGreaterThan(
            0.8
        )
        // there should be less that 5% after DIA -1
        assertThat(
            temporaryBasal.iobCalc(now + T.hours(dia.toLong() - 1).msecs(), effectiveProfile, asResult, SMBDefaults.exercise_mode, SMBDefaults.half_basal_exercise_target, true).basaliob
        ).isLessThan(0.05)
        // there should be zero after DIA
        assertThat(
            temporaryBasal.iobCalc(now + T.hours(dia.toLong() + 1).msecs(), effectiveProfile, asResult, SMBDefaults.exercise_mode, SMBDefaults.half_basal_exercise_target, true).basaliob
        ).isEqualTo(0.0)
        // no IOB for invalid record
        temporaryBasal.isValid = false
        assertThat(
            temporaryBasal.iobCalc(now + T.hours(1).msecs(), effectiveProfile, asResult, SMBDefaults.exercise_mode, SMBDefaults.half_basal_exercise_target, true).basaliob
        ).isEqualTo(0.0)
    }
}
