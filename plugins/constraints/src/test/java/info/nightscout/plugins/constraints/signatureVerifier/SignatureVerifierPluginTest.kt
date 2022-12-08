package info.nightscout.plugins.constraints.signatureVerifier

import android.content.Context
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import org.junit.Assert
import org.junit.jupiter.api.Test
import org.mockito.Mock

class SignatureVerifierPluginTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var sp: SP
    @Mock lateinit var context: Context
    @Mock lateinit var uiInteraction: UiInteraction

    val injector = HasAndroidInjector { AndroidInjector { } }

    fun singleCharUnMap(shortHash: String): String {
        val array = ByteArray(shortHash.length)
        val sb = StringBuilder()
        for (i in array.indices) {
            if (i != 0) sb.append(":")
            sb.append(String.format("%02X", 0xFF and map[map.indexOf(shortHash[i])].code))
        }
        return sb.toString()
    }

    var map =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!\"§$%&/()=?,.-;:_<>|°^`´\\@€*'#+~{}[]¿¡áéíóúàèìòùöäü`ÁÉÍÓÚÀÈÌÒÙÖÄÜßÆÇÊËÎÏÔŒÛŸæçêëîïôœûÿĆČĐŠŽćđšžñΑΒΓΔΕΖΗΘΙΚΛΜΝΞΟΠΡ\u03A2ΣΤΥΦΧΨΩαβγδεζηθικλμνξοπρςστυφχψωϨϩϪϫϬϭϮϯϰϱϲϳϴϵ϶ϷϸϹϺϻϼϽϾϿЀЁЂЃЄЅІЇЈЉЊЋЌЍЎЏАБВГДЕЖЗ"

    @Test
    fun singleCharUnMapTest() {
        val key = "2ΙšÄΠΒϨÒÇeЄtЄЗž-*Ж*ZcHijЊÄœ<|x\"Ε"
        val unmapped = singleCharUnMap(key)
        Assert.assertEquals("32:99:61:C4:A0:92:E8:D2:C7:65:04:74:04:17:7E:2D:2A:16:2A:5A:63:48:69:6A:0A:C4:53:3C:7C:78:22:95", unmapped)
    }
}