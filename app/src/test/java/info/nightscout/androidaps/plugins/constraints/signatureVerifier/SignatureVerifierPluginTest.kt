package info.nightscout.androidaps.plugins.constraints.signatureVerifier

import android.content.Context
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.junit.Assert
import org.junit.Test
import org.mockito.Mock

class SignatureVerifierPluginTest : TestBase() {

    @Mock lateinit var resourceHelper: ResourceHelper
    @Mock lateinit var sp: SP
    @Mock lateinit var context: Context
    private val rxBus = RxBusWrapper(aapsSchedulers)

    val injector = HasAndroidInjector { AndroidInjector { } }

    @Test
    fun singleCharUnMapTest() {
        val plugin = SignatureVerifierPlugin(injector, aapsLogger, resourceHelper, sp, rxBus, context)
        val key = "2ΙšÄΠΒϨÒÇeЄtЄЗž-*Ж*ZcHijЊÄœ<|x\"Ε"
        val unmapped = plugin.singleCharUnMap(key)
        Assert.assertEquals("32:99:61:C4:A0:92:E8:D2:C7:65:04:74:04:17:7E:2D:2A:16:2A:5A:63:48:69:6A:0A:C4:53:3C:7C:78:22:95", unmapped)
    }
}