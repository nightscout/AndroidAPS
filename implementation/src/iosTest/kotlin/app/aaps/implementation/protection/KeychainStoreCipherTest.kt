package app.aaps.implementation.protection

import app.aaps.core.interfaces.sharedPreferences.SealedDataUnreadableException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The settings store's cipher on iOS. The Keychain is faked (a test binary has none); the AES-GCM is the
 * production path.
 */
class KeychainStoreCipherTest {

    private val keychain = FakeKeychain()
    private val cipher = KeychainStoreCipher(keychain, ALIAS)
    private val plain = """{"nsclient_token":["s","secret-token-value"]}""".encodeToByteArray()

    @Test
    fun `what is sealed opens again also with a new cipher on the same key`() {
        val sealed = cipher.encrypt(plain)

        assertContentEquals(plain, cipher.decrypt(sealed))
        assertContentEquals(plain, KeychainStoreCipher(keychain, ALIAS).decrypt(sealed))
    }

    @Test
    fun `the sealed bytes do not hold the plain text`() {
        val sealed = cipher.encrypt(plain).decodeToString()

        assertFalse(sealed.contains("secret-token-value"))
    }

    @Test
    fun `the first write makes the key and the next writes use the same one`() {
        cipher.encrypt(plain)
        val first = assertNotNull(keychain.entries[ALIAS]).copyOf()

        cipher.encrypt(plain)
        KeychainStoreCipher(keychain, ALIAS).encrypt(plain)

        assertEquals(1, keychain.entries.size)
        assertContentEquals(first, keychain.entries[ALIAS])
    }

    @Test
    fun `a read with no key is unreadable data and makes no key`() {
        val sealed = KeychainStoreCipher(FakeKeychain(), ALIAS).encrypt(plain)

        assertFailsWith<SealedDataUnreadableException> { cipher.decrypt(sealed) }
        assertTrue(keychain.entries.isEmpty())
    }

    @Test
    fun `data made with another key is unreadable`() {
        val sealed = KeychainStoreCipher(FakeKeychain(), ALIAS).encrypt(plain)
        cipher.encrypt(plain)

        assertFailsWith<SealedDataUnreadableException> { cipher.decrypt(sealed) }
    }

    @Test
    fun `changed data is unreadable`() {
        val sealed = cipher.encrypt(plain)
        sealed[sealed.size - 1] = (sealed[sealed.size - 1].toInt() xor 1).toByte()

        assertFailsWith<SealedDataUnreadableException> { cipher.decrypt(sealed) }
        assertFailsWith<SealedDataUnreadableException> { cipher.decrypt(ByteArray(10)) }
    }

    @Test
    fun `a Keychain that does not answer is not mistaken for a missing key`() {
        val sealed = cipher.encrypt(plain)
        keychain.unavailableStatus = ERR_SEC_INTERACTION_NOT_ALLOWED

        val reader = KeychainStoreCipher(keychain, ALIAS)

        assertFailsWith<KeychainUnavailableException> { reader.decrypt(sealed) }
        assertFailsWith<KeychainUnavailableException> { reader.encrypt(plain) }
        keychain.unavailableStatus = null
        assertContentEquals(plain, reader.decrypt(sealed))
    }

    private companion object {

        const val ALIAS = "preferences"

        /** `errSecInteractionNotAllowed`: what the Keychain answers before the first unlock. */
        const val ERR_SEC_INTERACTION_NOT_ALLOWED = -25308
    }
}
