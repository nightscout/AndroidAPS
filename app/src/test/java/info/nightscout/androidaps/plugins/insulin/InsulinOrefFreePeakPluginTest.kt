package info.nightscout.androidaps.plugins.insulin

import info.nightscout.androidaps.R
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunction
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import junit.framework.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule

/**
 * Created by adrian on 2019-12-25.
 */

class InsulinOrefFreePeakPluginTest {


    // TODO: move to a base class
    // Add a JUnit rule that will setup the @Mock annotated vars and log.
    // Another possibility would be to add `MockitoAnnotations.initMocks(this) to the setup method.
    @get:Rule
    val mockitoRule: MockitoRule = MockitoJUnit.rule()

    lateinit var sut: InsulinOrefFreePeakPlugin

    @Mock lateinit var sp: SP
    @Mock lateinit var resourceHelper: ResourceHelper
    @Mock lateinit var rxBus: RxBusWrapper
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var aapsLogger: AAPSLogger

    @Before
    fun setup() {
        sut = InsulinOrefFreePeakPlugin(sp = sp,
            resourceHelper = resourceHelper,
            rxBus = rxBus,
            profileFunction = profileFunction,
            aapsLogger = aapsLogger)
    }

    @Test
    fun `simple peak test`() {
        `when`(sp.getInt(eq(R.string.key_insulin_oref_peak), anyInt())).thenReturn(90)
        assertEquals(90, sut.peak)
    }

    // Workaround for Kotlin nullability. TODO: move to a base class
    // https://medium.com/@elye.project/befriending-kotlin-and-mockito-1c2e7b0ef791
    // https://stackoverflow.com/questions/30305217/is-it-possible-to-use-mockito-in-kotlin
    private fun <T> anyObject(): T {
        Mockito.any<T>()
        return uninitialized()
    }

    private fun <T> uninitialized(): T = null as T

}