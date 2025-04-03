package app.aaps.plugins.insulin

import android.content.SharedPreferences
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.ICfg
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.validators.preferences.AdaptiveIntPreference
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InsulinPluginTest: TestBaseWithProfile() {

    private lateinit var sut: InsulinPlugin

    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var uel: UserEntryLogger
    @Mock lateinit var sharedPrefs: SharedPreferences

    init {
        addInjector {
            if (it is AdaptiveIntPreference) {
                it.profileUtil = profileUtil
                it.preferences = preferences
                it.config = config
            }
        }
    }


    @BeforeEach
    fun setup() {
        sut = InsulinPlugin(preferences, rh, profileFunction, rxBus, aapsLogger, config, hardLimits, uiInteraction, uel, activePlugin)
    }

    @Test
    fun `simple peak test`() {
        `when`(profileFunction.getProfile()).thenReturn(validProfile)
        assertThat(sut.peak).isEqualTo(75)
    }

    @Test
    fun getIdTest() {
        assertThat(sut.id).isEqualTo(Insulin.InsulinType.UNKNOWN)
    }

    @Test
    fun getFriendlyNameTest() {
        `when`(rh.gs(eq(app.aaps.core.interfaces.R.string.insulin_plugin))).thenReturn("Insulin")
        assertThat(sut.friendlyName).isEqualTo("Insulin")
    }

    @Test
    fun testIobCalcForTreatment() {
        val treatment = BS(timestamp = 0, amount = 10.0, type = BS.Type.NORMAL)
        val iCfg = ICfg("Test", 30, Constants.defaultDIA)
        val time = System.currentTimeMillis()
        // check directly after bolus
        treatment.timestamp = time
        treatment.amount = 10.0
        assertThat(sut.iobCalcForTreatment(treatment, time, iCfg).iobContrib).isWithin(0.01).of(10.0)
        // check after 1 hour
        treatment.timestamp = time - 1 * 60 * 60 * 1000 // 1 hour
        treatment.amount = 10.0
        assertThat(sut.iobCalcForTreatment(treatment, time, iCfg).iobContrib).isWithin(0.01).of(3.92)
        // check after 2 hour
        treatment.timestamp = time - 2 * 60 * 60 * 1000 // 2 hours
        treatment.amount = 10.0
        assertThat(sut.iobCalcForTreatment(treatment, time, iCfg).iobContrib).isWithin(0.01).of(0.77)
        // check after 3 hour
        treatment.timestamp = time - 3 * 60 * 60 * 1000 // 3 hours
        treatment.amount = 10.0
        assertThat(sut.iobCalcForTreatment(treatment, time, iCfg).iobContrib).isWithin(0.01).of(0.10)
        // check after dia
        treatment.timestamp = time - 4 * 60 * 60 * 1000 // 4 hours
        treatment.amount = 10.0
        assertThat(sut.iobCalcForTreatment(treatment, time, iCfg).iobContrib).isWithin(0.01).of(0.0)
    }
}
