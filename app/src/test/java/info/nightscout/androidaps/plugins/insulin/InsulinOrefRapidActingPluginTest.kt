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

class InsulinOrefRapidActingPluginTest {

    @get:Rule
    val mockitoRule: MockitoRule = MockitoJUnit.rule()

    private lateinit var sut: InsulinOrefRapidActingPlugin

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
        sut = InsulinOrefRapidActingPlugin(injector, resourceHelper, profileFunction, rxBus, aapsLogger)
    }

    @Test
    fun `simple peak test`() {
        assertEquals(75, sut.peak)
    }

    @Test
    fun getIdTest() {
        assertEquals(InsulinInterface.InsulinType.OREF_RAPID_ACTING, sut.id)
    }

    @Test
    fun commentStandardTextTest() {
        `when`(resourceHelper.gs(eq(R.string.fastactinginsulincomment))).thenReturn("Novorapid, Novolog, Humalog")
        assertEquals("Novorapid, Novolog, Humalog", sut.commentStandardText())
    }

    @Test
    fun getFriendlyNameTest() {
        `when`(resourceHelper.gs(eq(R.string.rapid_acting_oref))).thenReturn("Rapid-Acting Oref")
        assertEquals("Rapid-Acting Oref", sut.friendlyName)
    }

}