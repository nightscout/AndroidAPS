package info.nightscout.androidaps.plugins.insulin

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.InsulinInterface
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.utils.resources.ResourceHelper
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule

class InsulinOrefUltraRapidActingPluginTest {

    @get:Rule
    val mockitoRule: MockitoRule = MockitoJUnit.rule()

    private lateinit var sut: InsulinOrefUltraRapidActingPlugin

    @Mock lateinit var resourceHelper: ResourceHelper
    @Mock lateinit var rxBus: RxBusWrapper
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var aapsLogger: AAPSLogger

    private var injector: HasAndroidInjector = HasAndroidInjector {
        AndroidInjector {
        }
    }

    @Before
    fun setup() {
        sut = InsulinOrefUltraRapidActingPlugin(injector, resourceHelper, profileFunction, rxBus, aapsLogger)
    }

    @Test
    fun `simple peak test`() {
        assertEquals(55, sut.peak)
    }

    @Test
    fun getIdTest() {
        assertEquals(InsulinInterface.InsulinType.OREF_ULTRA_RAPID_ACTING, sut.id)
    }

    @Test
    fun commentStandardTextTest() {
        `when`(resourceHelper.gs(eq(R.string.ultrafastactinginsulincomment))).thenReturn("Fiasp")
        assertEquals("Fiasp", sut.commentStandardText())
    }

    @Test
    fun getFriendlyNameTest() {
        `when`(resourceHelper.gs(eq(R.string.ultrarapid_oref))).thenReturn("Ultra-Rapid Oref")
        assertEquals("Ultra-Rapid Oref", sut.friendlyName)
    }

}