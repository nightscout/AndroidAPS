package app.aaps.plugins.main.extensions

import app.aaps.core.data.aps.SMBDefaults
import app.aaps.core.data.model.TB
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.aps.AutosensResult
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.objects.extensions.iobCalc
import app.aaps.plugins.insulin.InsulinLyumjevPlugin
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.whenever

class TemporaryBasalExtensionKtTest : TestBaseWithProfile() {

    @Mock lateinit var profileFunctions: ProfileFunction
    @Mock lateinit var uiInteraction: UiInteraction

    private lateinit var insulin: Insulin

    private val dia = 7.0

    @BeforeEach
    fun setup() {
        insulin = InsulinLyumjevPlugin(rh, profileFunctions, rxBus, aapsLogger, config, hardLimits, uiInteraction)
        whenever(activePlugin.activeInsulin).thenReturn(insulin)
        whenever(dateUtil.now()).thenReturn(now)
    }

    @Test
    fun iobCalc() {
        val temporaryBasal = TB(timestamp = now - 1, rate = 200.0, isAbsolute = false, duration = T.hours(1).msecs(), type = TB.Type.NORMAL)
        // there should zero IOB after now
        assertThat(temporaryBasal.iobCalc(now, validProfile, insulin).basaliob).isWithin(0.01).of(0.0)
        // there should be significant IOB at EB finish
        assertThat(temporaryBasal.iobCalc(now + T.hours(1).msecs(), validProfile, insulin).basaliob).isGreaterThan(0.8)
        // there should be less that 5% after DIA -1
        assertThat(temporaryBasal.iobCalc(now + T.hours(dia.toLong() - 1).msecs(), validProfile, insulin).basaliob).isLessThan(0.05)
        // there should be zero after DIA
        assertThat(temporaryBasal.iobCalc(now + T.hours(dia.toLong() + 1).msecs(), validProfile, insulin).basaliob).isEqualTo(0.0)
        // no IOB for invalid record
        temporaryBasal.isValid = false
        assertThat(temporaryBasal.iobCalc(now + T.hours(1).msecs(), validProfile, insulin).basaliob).isEqualTo(0.0)

        temporaryBasal.isValid = true
        val asResult = AutosensResult()
        // there should zero IOB after now
        assertThat(temporaryBasal.iobCalc(now, validProfile, asResult, SMBDefaults.exercise_mode, SMBDefaults.half_basal_exercise_target, true, insulin).basaliob).isWithin(0.01).of(0.0)
        // there should be significant IOB at EB finish
        assertThat(temporaryBasal.iobCalc(now + T.hours(1).msecs(), validProfile, asResult, SMBDefaults.exercise_mode, SMBDefaults.half_basal_exercise_target, true, insulin).basaliob).isGreaterThan(
            0.8
        )
        // there should be less that 5% after DIA -1
        assertThat(
            temporaryBasal.iobCalc(
                now + T.hours(dia.toLong() - 1).msecs(),
                validProfile,
                asResult,
                SMBDefaults.exercise_mode,
                SMBDefaults.half_basal_exercise_target,
                true,
                insulin
            ).basaliob
        ).isLessThan(0.05)
        // there should be zero after DIA
        assertThat(
            temporaryBasal.iobCalc(
                now + T.hours(dia.toLong() + 1).msecs(),
                validProfile,
                asResult,
                SMBDefaults.exercise_mode,
                SMBDefaults.half_basal_exercise_target,
                true,
                insulin
            ).basaliob
        ).isEqualTo(0.0)
        // no IOB for invalid record
        temporaryBasal.isValid = false
        assertThat(
            temporaryBasal.iobCalc(now + T.hours(1).msecs(), validProfile, asResult, SMBDefaults.exercise_mode, SMBDefaults.half_basal_exercise_target, true, insulin).basaliob
        ).isEqualTo(0.0)
    }
}
