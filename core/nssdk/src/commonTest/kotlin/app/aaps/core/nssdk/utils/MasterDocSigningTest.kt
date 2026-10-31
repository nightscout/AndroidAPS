package app.aaps.core.nssdk.utils

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The master's signature on its settings docs. In commonTest, so the iOS client, which only checks signatures, runs
 * the same checks against its own crypto (CryptoKit) as Android does against its own.
 */
class MasterDocSigningTest {

    private val master = MasterDocSigning.newKeyPair()
    private val text = """{"signedAt":1000,"runningConfig":{"version":"4.0"}}"""

    @Test
    fun aSignedDocVerifies() {
        val signature = MasterDocSigning.sign(master.privateKey, "aaps", text)

        assertTrue(MasterDocSigning.verify(master.publicKey, "aaps", text, signature))
    }

    @Test
    fun aChangedTextDoesNotVerify() {
        val signature = MasterDocSigning.sign(master.privateKey, "aaps", text)

        assertFalse(MasterDocSigning.verify(master.publicKey, "aaps", text.replace("4.0", "4.1"), signature))
    }

    /** A hot doc signature must not make a cold doc of the same text. */
    @Test
    fun theSignatureBelongsToItsDoc() {
        val signature = MasterDocSigning.sign(master.privateKey, "aaps-state", text)

        assertFalse(MasterDocSigning.verify(master.publicKey, "aaps", text, signature))
    }

    @Test
    fun anotherKeyDoesNotVerify() {
        val other = MasterDocSigning.newKeyPair()
        val signature = MasterDocSigning.sign(other.privateKey, "aaps", text)

        assertFalse(MasterDocSigning.verify(master.publicKey, "aaps", text, signature))
    }

    @Test
    fun aSignatureOrKeyThatCannotBeReadDoesNotVerify() {
        val signature = MasterDocSigning.sign(master.privateKey, "aaps", text)

        assertFalse(MasterDocSigning.verify(master.publicKey, "aaps", text, "not base64 !"))
        assertFalse(MasterDocSigning.verify("AAAA", "aaps", text, signature))
    }
}
