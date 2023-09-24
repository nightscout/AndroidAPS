package info.nightscout.insulin

import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.insulin.Insulin
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.interfaces.utils.HardLimits
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.`when`

/**
 * Created by adrian on 2019-12-25.
 */

class InsulinOrefFreePeakPluginTest : TestBase() {

    private lateinit var sut: InsulinOrefFreePeakPlugin

    @Mock lateinit var sp: SP
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var config: Config
    @Mock lateinit var hardLimits: HardLimits
    @Mock lateinit var uiInteraction: UiInteraction

    private var injector: HasAndroidInjector = HasAndroidInjector {
        AndroidInjector {
        }
    }

    @BeforeEach
    fun setup() {
        sut = InsulinOrefFreePeakPlugin(injector, sp, rh, profileFunction, rxBus, aapsLogger, config, hardLimits, uiInteraction)
    }

    @Test
    fun `simple peak test`() {
        `when`(sp.getInt(eq(info.nightscout.core.utils.R.string.key_insulin_oref_peak), anyInt())).thenReturn(90)
        assertThat(sut.peak).isEqualTo(90)
    }

    @Test
    fun getIdTest() {
        assertThat(sut.id).isEqualTo(Insulin.InsulinType.OREF_FREE_PEAK)
    }

    @Test
    fun commentStandardTextTest() {
        `when`(sp.getInt(eq(info.nightscout.core.utils.R.string.key_insulin_oref_peak), anyInt())).thenReturn(90)
        `when`(rh.gs(eq(R.string.insulin_peak_time))).thenReturn("Peak Time [min]")
        assertThat(sut.commentStandardText()).isEqualTo("Peak Time [min]: 90")
    }

    @Test
    fun getFriendlyNameTest() {
        `when`(rh.gs(eq(R.string.free_peak_oref))).thenReturn("Free-Peak Oref")
        assertThat(sut.friendlyName).isEqualTo("Free-Peak Oref")
    }
}
