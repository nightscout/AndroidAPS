package app.aaps.plugins.constraints.signatureVerifier

import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SignatureVerifierPluginTest : TestBase() {

    @Suppress("SameParameterValue")
    private fun singleCharUnMap(shortHash: String): String {
        val array = ByteArray(shortHash.length)
        val sb = StringBuilder()
        for (i in array.indices) {
            if (i != 0) sb.append(":")
            sb.append(String.format("%02X", 0xFF and map[map.indexOf(shortHash[i])].code))
        }
        return sb.toString()
    }

    @Suppress("SpellCheckingInspection")
    private var map =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!\"§$%&/()=?,.-;:_<>|°^`´\\@€*'#+~{}[]¿¡áéíóúàèìòùöäü`ÁÉÍÓÚÀÈÌÒÙÖÄÜßÆÇÊËÎÏÔŒÛŸæçêëîïôœûÿĆČĐŠŽćđšžñΑΒΓΔΕΖΗΘΙΚΛΜΝΞΟΠΡ\u03A2ΣΤΥΦΧΨΩαβγδεζηθικλμνξοπρςστυφχψωϨϩϪϫϬϭϮϯϰϱϲϳϴϵ϶ϷϸϹϺϻϼϽϾϿЀЁЂЃЄЅІЇЈЉЊЋЌЍЎЏАБВГДЕЖЗ"

    @Test
    fun singleCharUnMapTest() {
        @Suppress("SpellCheckingInspection") val key = "2ΙšÄΠΒϨÒÇeЄtЄЗž-*Ж*ZcHijЊÄœ<|x\"Ε"
        val unmapped = singleCharUnMap(key)
        assertThat(unmapped).isEqualTo("32:99:61:C4:A0:92:E8:D2:C7:65:04:74:04:17:7E:2D:2A:16:2A:5A:63:48:69:6A:0A:C4:53:3C:7C:78:22:95")
    }
}
