package info.nightscout.core.extensions

import info.nightscout.database.entities.TemporaryBasal
import info.nightscout.insulin.InsulinLyumjevPlugin
import info.nightscout.interfaces.aps.AutosensResult
import info.nightscout.interfaces.aps.SMBDefaults
import info.nightscout.interfaces.insulin.Insulin
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.shared.utils.T
import info.nightscout.sharedtests.TestBaseWithProfile
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito

class TemporaryBasalExtensionKtTest : TestBaseWithProfile() {

    @Mock lateinit var profileFunctions: ProfileFunction
    @Mock lateinit var uiInteraction: UiInteraction

    private lateinit var insulin: Insulin

    private val dia = 7.0

    @BeforeEach
    fun setup() {
        insulin = InsulinLyumjevPlugin(profileInjector, rh, profileFunctions, rxBus, aapsLogger, config, hardLimits, uiInteraction)
        Mockito.`when`(activePlugin.activeInsulin).thenReturn(insulin)
        Mockito.`when`(dateUtil.now()).thenReturn(now)
    }

    @Test
    fun iobCalc() {
        val temporaryBasal = TemporaryBasal(timestamp = now - 1, rate = 200.0, isAbsolute = false, duration = T.hours(1).msecs(), type = TemporaryBasal.Type.NORMAL)
        // there should zero IOB after now
        Assertions.assertEquals(0.0, temporaryBasal.iobCalc(now, validProfile, insulin).basaliob, 0.01)
        // there should be significant IOB at EB finish
        Assertions.assertTrue(0.8 < temporaryBasal.iobCalc(now + T.hours(1).msecs(), validProfile, insulin).basaliob)
        // there should be less that 5% after DIA -1
        Assertions.assertTrue(0.05 > temporaryBasal.iobCalc(now + T.hours(dia.toLong() - 1).msecs(), validProfile, insulin).basaliob)
        // there should be zero after DIA
        Assertions.assertEquals(0.0, temporaryBasal.iobCalc(now + T.hours(dia.toLong() + 1).msecs(), validProfile, insulin).basaliob)
        // no IOB for invalid record
        temporaryBasal.isValid = false
        Assertions.assertEquals(0.0, temporaryBasal.iobCalc(now + T.hours(1).msecs(), validProfile, insulin).basaliob)

        temporaryBasal.isValid = true
        val asResult = AutosensResult()
        // there should zero IOB after now
        Assertions.assertEquals(0.0, temporaryBasal.iobCalc(now, validProfile, asResult, SMBDefaults.exercise_mode, SMBDefaults.half_basal_exercise_target, true, insulin).basaliob, 0.01)
        // there should be significant IOB at EB finish
        Assertions.assertTrue(0.8 < temporaryBasal.iobCalc(now + T.hours(1).msecs(), validProfile, asResult, SMBDefaults.exercise_mode, SMBDefaults.half_basal_exercise_target, true, insulin).basaliob)
        // there should be less that 5% after DIA -1
        Assertions.assertTrue(
            0.05 > temporaryBasal.iobCalc(
                now + T.hours(dia.toLong() - 1).msecs(),
                validProfile,
                asResult,
                SMBDefaults.exercise_mode,
                SMBDefaults.half_basal_exercise_target,
                true,
                insulin
            ).basaliob
        )
        // there should be zero after DIA
        Assertions.assertEquals(
            0.0,
            temporaryBasal.iobCalc(
                now + T.hours(dia.toLong() + 1).msecs(),
                validProfile,
                asResult,
                SMBDefaults.exercise_mode,
                SMBDefaults.half_basal_exercise_target,
                true,
                insulin
            ).basaliob
        )
        // no IOB for invalid record
        temporaryBasal.isValid = false
        Assertions.assertEquals(
            0.0,
            temporaryBasal.iobCalc(now + T.hours(1).msecs(), validProfile, asResult, SMBDefaults.exercise_mode, SMBDefaults.half_basal_exercise_target, true, insulin).basaliob
        )
    }
}