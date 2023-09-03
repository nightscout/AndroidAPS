package info.nightscout.core.extensions

import info.nightscout.database.entities.ExtendedBolus
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

class ExtendedBolusExtensionKtTest : TestBaseWithProfile() {

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
        val bolus = ExtendedBolus(timestamp = now - 1, amount = 1.0, duration = T.hours(1).msecs())
        // there should zero IOB after now
        Assertions.assertEquals(0.0, bolus.iobCalc(now, validProfile, insulin).iob, 0.01)
        // there should be significant IOB at EB finish
        Assertions.assertTrue(0.8 < bolus.iobCalc(now + T.hours(1).msecs(), validProfile, insulin).iob)
        // there should be less that 5% after DIA -1
        Assertions.assertTrue(0.05 > bolus.iobCalc(now + T.hours(dia.toLong() - 1).msecs(), validProfile, insulin).iob)
        // there should be zero after DIA
        Assertions.assertEquals(0.0, bolus.iobCalc(now + T.hours(dia.toLong() + 1).msecs(), validProfile, insulin).iob)
        // no IOB for invalid record
        bolus.isValid = false
        Assertions.assertEquals(0.0, bolus.iobCalc(now + T.hours(1).msecs(), validProfile, insulin).iob)

        bolus.isValid = true
        val asResult = AutosensResult()
        // there should zero IOB after now
        Assertions.assertEquals(0.0, bolus.iobCalc(now, validProfile, asResult, SMBDefaults.exercise_mode, SMBDefaults.half_basal_exercise_target, true, insulin).iob, 0.01)
        // there should be significant IOB at EB finish
        Assertions.assertTrue(0.8 < bolus.iobCalc(now + T.hours(1).msecs(), validProfile, asResult, SMBDefaults.exercise_mode, SMBDefaults.half_basal_exercise_target, true, insulin).iob)
        // there should be less that 5% after DIA -1
        Assertions.assertTrue(0.05 > bolus.iobCalc(now + T.hours(dia.toLong() - 1).msecs(), validProfile, asResult, SMBDefaults.exercise_mode, SMBDefaults.half_basal_exercise_target, true, insulin).iob)
        // there should be zero after DIA
        Assertions.assertEquals(0.0, bolus.iobCalc(now + T.hours(dia.toLong() + 1).msecs(), validProfile, asResult, SMBDefaults.exercise_mode, SMBDefaults.half_basal_exercise_target, true, insulin).iob)
        // no IOB for invalid record
        bolus.isValid = false
        Assertions.assertEquals(0.0, bolus.iobCalc(now + T.hours(1).msecs(), validProfile, asResult, SMBDefaults.exercise_mode, SMBDefaults.half_basal_exercise_target, true, insulin).iob)
    }

    @Test
    fun isInProgress() {
        val bolus = ExtendedBolus(timestamp = now - 1, amount = 1.0, duration = T.hours(1).msecs())
        Mockito.`when`(dateUtil.now()).thenReturn(now)
        Assertions.assertTrue(bolus.isInProgress(dateUtil))
        Mockito.`when`(dateUtil.now()).thenReturn(now + T.hours(2).msecs())
        Assertions.assertFalse(bolus.isInProgress(dateUtil))
    }

    @Test
    fun toTemporaryBasal() {
        val bolus = ExtendedBolus(timestamp = now - 1, amount = 1.0, duration = T.hours(1).msecs())
        val tbr = bolus.toTemporaryBasal(validProfile)
        Assertions.assertEquals(bolus.timestamp, tbr.timestamp)
        Assertions.assertEquals(bolus.duration, tbr.duration)
        Assertions.assertEquals(bolus.rate + validProfile.getBasal(now), tbr.rate)
        Assertions.assertEquals(TemporaryBasal.Type.FAKE_EXTENDED, tbr.type)
    }
}