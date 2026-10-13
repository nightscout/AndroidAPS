package app.aaps.plugins.main.extensions

import app.aaps.core.data.model.BS
import app.aaps.core.data.time.T
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

class BolusExtensionKtTest : TestBaseWithProfile() {

    @Mock lateinit var profileFunctions: ProfileFunction
    @Mock lateinit var uiInteraction: UiInteraction

    private lateinit var insulin: Insulin

    private val dia = 7.0

    @BeforeEach fun setup() {
        insulin = InsulinLyumjevPlugin(rh, profileFunctions, rxBus, aapsLogger, config, hardLimits, uiInteraction)
        whenever(activePlugin.activeInsulin).thenReturn(insulin)
    }

    @Test fun iobCalc() {
        val bolus = BS(timestamp = now - 1, amount = 1.0, type = BS.Type.NORMAL)
        // there should be almost full IOB after now
        assertThat(bolus.iobCalc(activePlugin, now, dia).iobContrib).isWithin(0.01).of(1.0)
        // there should be less than 5% after DIA -1
        assertThat(
            bolus.iobCalc(activePlugin, now + T.hours(dia.toLong() - 1).msecs(), dia).iobContrib
        ).isLessThan(0.05)
        // there should be zero after DIA
        assertThat(bolus.iobCalc(activePlugin, now + T.hours(dia.toLong() + 1).msecs(), dia).iobContrib).isEqualTo(0.0)
        // no IOB for invalid record
        bolus.isValid = false
        assertThat(bolus.iobCalc(activePlugin, now + T.hours(1).msecs(), dia).iobContrib).isEqualTo(0.0)
        bolus.isValid = true
        bolus.type = BS.Type.PRIMING
        assertThat(bolus.iobCalc(activePlugin, now + T.hours(1).msecs(), dia).iobContrib).isEqualTo(0.0)
    }
}
