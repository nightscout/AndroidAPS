package info.nightscout.androidaps.plugins.insulin

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.interfaces.Config
import info.nightscout.androidaps.interfaces.Insulin
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.`when`

/**
 * Created by adrian on 2019-12-25.
 */

class InsulinOrefFreePeakPluginTest : TestBase() {

    lateinit var sut: InsulinOrefFreePeakPlugin

    @Mock lateinit var sp: SP
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var rxBus: RxBus
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var config: Config

    private var injector: HasAndroidInjector = HasAndroidInjector {
        AndroidInjector {
        }
    }

    @Before
    fun setup() {
        sut = InsulinOrefFreePeakPlugin( injector, sp, rh, profileFunction, rxBus, aapsLogger, config)
    }

    @Test
    fun `simple peak test`() {
        `when`(sp.getInt(eq(R.string.key_insulin_oref_peak), anyInt())).thenReturn(90)
        assertEquals(90, sut.peak)
    }

    @Test
    fun getIdTest() {
        assertEquals(Insulin.InsulinType.OREF_FREE_PEAK, sut.id)
    }

    @Test
    fun commentStandardTextTest() {
        `when`(sp.getInt(eq(R.string.key_insulin_oref_peak), anyInt())).thenReturn(90)
        `when`(rh.gs(eq(R.string.insulin_peak_time))).thenReturn("Peak Time [min]")
        assertEquals("Peak Time [min]: 90", sut.commentStandardText())
    }

    @Test
    fun getFriendlyNameTest() {
        `when`(rh.gs(eq(R.string.free_peak_oref))).thenReturn("Free-Peak Oref")
        assertEquals("Free-Peak Oref", sut.friendlyName)
    }
}