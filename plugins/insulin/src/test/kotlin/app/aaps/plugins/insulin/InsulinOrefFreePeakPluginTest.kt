package app.aaps.plugins.insulin

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.Preferences
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.`when`

/**
 * Created by adrian on 2019-12-25.
 */

class InsulinOrefFreePeakPluginTest : TestBase() {

    private lateinit var sut: InsulinOrefFreePeakPlugin

    @Mock lateinit var preferences: Preferences
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var config: Config
    @Mock lateinit var hardLimits: HardLimits
    @Mock lateinit var uiInteraction: UiInteraction

    @BeforeEach
    fun setup() {
        sut = InsulinOrefFreePeakPlugin(preferences, rh, profileFunction, rxBus, aapsLogger, config, hardLimits, uiInteraction)
    }

    @Test
    fun `simple peak test`() {
        `when`(preferences.get(IntKey.InsulinOrefPeak)).thenReturn(90)
        assertThat(sut.peak).isEqualTo(90)
    }

    @Test
    fun getIdTest() {
        assertThat(sut.id).isEqualTo(Insulin.InsulinType.OREF_FREE_PEAK)
    }

    @Test
    fun commentStandardTextTest() {
        `when`(preferences.get(IntKey.InsulinOrefPeak)).thenReturn(90)
        `when`(rh.gs(eq(R.string.insulin_peak_time))).thenReturn("Peak Time [min]")
        assertThat(sut.commentStandardText()).isEqualTo("Peak Time [min]: 90")
    }

    @Test
    fun getFriendlyNameTest() {
        `when`(rh.gs(eq(R.string.free_peak_oref))).thenReturn("Free-Peak Oref")
        assertThat(sut.friendlyName).isEqualTo("Free-Peak Oref")
    }
}
