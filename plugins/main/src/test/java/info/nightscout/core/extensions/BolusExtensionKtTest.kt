package info.nightscout.core.extensions

import info.nightscout.androidaps.TestBaseWithProfile
import info.nightscout.database.entities.Bolus
import info.nightscout.insulin.InsulinLyumjevPlugin
import info.nightscout.interfaces.insulin.Insulin
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.shared.utils.T
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito

class BolusExtensionKtTest : TestBaseWithProfile() {

    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var profileFunctions: ProfileFunction
    @Mock lateinit var uiInteraction: UiInteraction

    private lateinit var insulin: Insulin

    private val now = 1000000L
    private val dia = 7.0

    @BeforeEach
    fun setup() {
        insulin = InsulinLyumjevPlugin(profileInjector, rh, profileFunctions, rxBus, aapsLogger, config, hardLimits, uiInteraction)
        Mockito.`when`(activePlugin.activeInsulin).thenReturn(insulin)
    }

    @Test
    fun iobCalc() {
        val bolus = Bolus(timestamp = now - 1, amount = 1.0, type = Bolus.Type.NORMAL)
        // there should be almost full IOB after now
        Assertions.assertEquals(1.0, bolus.iobCalc(activePlugin, now, dia).iobContrib, 0.01)
        // there should be less than 5% after DIA -1
        Assertions.assertTrue(0.05 > bolus.iobCalc(activePlugin, now + T.hours(dia.toLong() - 1).msecs(), dia).iobContrib)
        // there should be zero after DIA
        Assertions.assertEquals(0.0, bolus.iobCalc(activePlugin, now + T.hours(dia.toLong() + 1).msecs(), dia).iobContrib)
        // no IOB for invalid record
        bolus.isValid = false
        Assertions.assertEquals(0.0, bolus.iobCalc(activePlugin, now + T.hours(1).msecs(), dia).iobContrib)
        bolus.isValid = true
        bolus.type = Bolus.Type.PRIMING
        Assertions.assertEquals(0.0, bolus.iobCalc(activePlugin, now + T.hours(1).msecs(), dia).iobContrib)
    }
}