package info.nightscout.androidaps.plugins.constraints.signatureVerifier

import org.junit.Test

import org.junit.Assert.*

class SignatureVerifierPluginTest {

    @Test
    fun singleCharUnMapTest() {
        val key = "2ΙšÄΠΒϨÒÇeЄtЄЗž-*Ж*ZcHijЊÄœ<|x\"Ε"
        val unmapped = SignatureVerifierPlugin.getPlugin().singleCharUnMap(key)
        assertEquals("32:99:61:C4:A0:92:E8:D2:C7:65:04:74:04:17:7E:2D:2A:16:2A:5A:63:48:69:6A:0A:C4:53:3C:7C:78:22:95", unmapped)
    }
}