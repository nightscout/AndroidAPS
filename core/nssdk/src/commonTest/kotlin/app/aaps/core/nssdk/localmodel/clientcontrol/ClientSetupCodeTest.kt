package app.aaps.core.nssdk.localmodel.clientcontrol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

/**
 * The text in the master's set-up QR code. In commonTest, so the iOS client that will read it later is held to the
 * same format.
 */
class ClientSetupCodeTest {

    private val pairing = PairingPayload(masterInstallId = "master-1", clientId = "client-1", secretHex = "ab".repeat(32), expiresAt = 1_000L)

    @Test
    fun aCodeWithoutPairingReadsBack() {
        val code = ClientSetupCode(url = "https://ns.example", token = "aapstablet-0123456789abcdef")

        val decoded = ClientSetupCode.decode(ClientSetupCode.encode(code))

        assertEquals(code, assertIs<ClientSetupCode.Decoded.Ok>(decoded).code)
    }

    @Test
    fun aCodeWithPairingReadsBack() {
        val code = ClientSetupCode(url = "https://ns.example", token = "aapstablet-0123456789abcdef", pairing = pairing)

        val decoded = ClientSetupCode.decode(ClientSetupCode.encode(code))

        assertEquals(pairing, assertIs<ClientSetupCode.Decoded.Ok>(decoded).code.pairing)
    }

    /** No pairing means no `pairing` key at all, not `"pairing":null`: a shorter code is easier to scan. */
    @Test
    fun noPairingKeyWithoutPairing() {
        assertFalse(ClientSetupCode.encode(ClientSetupCode(url = "https://ns.example", token = "t-0123456789abcdef")).contains("pairing"))
    }

    @Test
    fun otherQrCodesAreRefused() {
        assertEquals(ClientSetupCode.Decoded.NotACode, ClientSetupCode.decode("https://example.com"))
        assertEquals(ClientSetupCode.Decoded.NotACode, ClientSetupCode.decode("""{"url":"https://ns.example","token":"t"}"""))
        assertEquals(ClientSetupCode.Decoded.NotACode, ClientSetupCode.decode("""{"type":"something-else","v":1,"url":"https://ns.example","token":"t"}"""))
        assertEquals(ClientSetupCode.Decoded.NotACode, ClientSetupCode.decode("""{"type":"aaps-client-setup","v":1,"url":"","token":"t"}"""))
        assertEquals(ClientSetupCode.Decoded.NotACode, ClientSetupCode.decode("""["aaps-client-setup"]"""))
    }

    /** A code without a version is not taken for this version. */
    @Test
    fun aCodeWithoutVersionIsRefused() {
        assertEquals(
            ClientSetupCode.Decoded.OtherVersion,
            ClientSetupCode.decode("""{"type":"aaps-client-setup","url":"https://ns.example","token":"t-0123456789abcdef"}""")
        )
    }

    /** Master and client must run the same version: a code of another version is refused, not read as far as it goes. */
    @Test
    fun anotherVersionIsRefused() {
        assertEquals(
            ClientSetupCode.Decoded.OtherVersion,
            ClientSetupCode.decode("""{"type":"aaps-client-setup","v":2,"url":"https://ns.example","token":"t-0123456789abcdef"}""")
        )
    }

    @Test
    fun printingACodeHidesTheTokenAndTheSecret() {
        val text = ClientSetupCode(url = "https://ns.example", token = "aapstablet-0123456789abcdef", pairing = pairing).toString()

        assertFalse(text.contains("0123456789abcdef"))
        assertFalse(text.contains("abab"))
    }
}
