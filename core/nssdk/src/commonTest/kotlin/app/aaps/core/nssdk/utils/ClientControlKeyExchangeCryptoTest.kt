package app.aaps.core.nssdk.utils

import app.aaps.core.nssdk.localmodel.clientcontrol.KeyExchangeEnvelope
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The key exchange that replaces a pairing secret. In commonTest, so the Apple provider (CryptoKit) is held to the same
 * result as the JDK one: an iOS client and an Android master must come to the same keys.
 */
class ClientControlKeyExchangeCryptoTest {

    private val oldSecret = ByteArray(32) { it.toByte() }
    private val clientId = "client-1"

    @Test
    fun bothSidesComeToTheSameKeys() {
        val client = ClientControlKeyExchangeCrypto.newKeyPair()
        val master = ClientControlKeyExchangeCrypto.newKeyPair()

        val onClient = ClientControlKeyExchangeCrypto.deriveKeys(client.privateKey, master.publicKey, oldSecret, clientId, client.publicKey, master.publicKey)
        val onMaster = ClientControlKeyExchangeCrypto.deriveKeys(master.privateKey, client.publicKey, oldSecret, clientId, client.publicKey, master.publicKey)

        assertContentEquals(onMaster.secret, onClient.secret)
        assertContentEquals(onMaster.tokenKey, onClient.tokenKey)
        assertEquals(32, onClient.secret.size)
        assertFalse(onClient.secret.contentEquals(onClient.tokenKey))
        // The new secret is new: it is not the old one.
        assertFalse(onClient.secret.contentEquals(oldSecret))
    }

    /** The old secret is part of the keys: the same two key pairs under another pairing give other keys. */
    @Test
    fun anotherPairingGivesOtherKeys() {
        val client = ClientControlKeyExchangeCrypto.newKeyPair()
        val master = ClientControlKeyExchangeCrypto.newKeyPair()

        val ours = ClientControlKeyExchangeCrypto.deriveKeys(client.privateKey, master.publicKey, oldSecret, clientId, client.publicKey, master.publicKey)
        val other = ClientControlKeyExchangeCrypto.deriveKeys(client.privateKey, master.publicKey, ByteArray(32) { 9 }, clientId, client.publicKey, master.publicKey)

        assertFalse(ours.secret.contentEquals(other.secret))
    }

    @Test
    fun theTokenOpensWithTheKeyOfTheExchangeOnly() {
        val client = ClientControlKeyExchangeCrypto.newKeyPair()
        val master = ClientControlKeyExchangeCrypto.newKeyPair()
        val onMaster = ClientControlKeyExchangeCrypto.deriveKeys(master.privateKey, client.publicKey, oldSecret, clientId, client.publicKey, master.publicKey)
        val onClient = ClientControlKeyExchangeCrypto.deriveKeys(client.privateKey, master.publicKey, oldSecret, clientId, client.publicKey, master.publicKey)

        val (iv, sealed) = ClientControlKeyExchangeCrypto.encryptToken(onMaster.tokenKey, "aapstablet-0123456789abcdef")

        assertEquals("aapstablet-0123456789abcdef", ClientControlKeyExchangeCrypto.decryptToken(onClient.tokenKey, iv, sealed))
        assertNull(ClientControlKeyExchangeCrypto.decryptToken(onClient.secret, iv, sealed))
    }

    @Test
    fun theAnswerIsSignedWithTheOldSecret() {
        val draft = KeyExchangeEnvelope(clientId = clientId, clientPublicKey = "c", masterPublicKey = "m", timestamp = 1L, signature = "")
        val signed = ClientControlCrypto.signKeyExchange(oldSecret, draft)

        assertTrue(ClientControlCrypto.verifyKeyExchange(oldSecret, signed))
        assertFalse(ClientControlCrypto.verifyKeyExchange(ByteArray(32) { 9 }, signed))
        // A master key swapped on the way does not verify.
        assertFalse(ClientControlCrypto.verifyKeyExchange(oldSecret, signed.copy(masterPublicKey = "x")))
    }
}
