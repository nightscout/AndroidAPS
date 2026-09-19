package app.aaps.implementation.protection

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Encrypting and decrypting secrets on iOS.
 *
 * The Keychain is faked, which is the reason it sits behind an interface: a test binary has no
 * keychain entitlements, so reaching the real one would make the cipher untestable. Everything else
 * here - the AES-GCM, the format, the header hash - is the production path.
 */
class IosSecureEncryptTest {

    private object SilentLogger : AAPSLogger {

        override fun debug(message: String) {}
        override fun debug(enable: Boolean, tag: LTag, message: String) {}
        override fun debug(tag: LTag, message: String) {}
        override fun debug(tag: LTag, accessor: () -> String) {}
        override fun debug(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun warn(tag: LTag, message: String) {}
        override fun warn(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun info(tag: LTag, message: String) {}
        override fun info(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun error(tag: LTag, message: String) {}
        override fun error(tag: LTag, message: String, throwable: Throwable) {}
        override fun error(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun error(message: String) {}
        override fun error(message: String, throwable: Throwable) {}
        override fun error(format: String, vararg arguments: Any?) {}
        override fun debug(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
        override fun info(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
        override fun warn(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
        override fun error(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
    }

    /** Keys held in memory, behaving as the Keychain does: one key per alias, kept until deleted. */
    private class FakeKeychain : Keychain {

        private val keys = mutableMapOf<String, ByteArray>()

        override fun load(alias: String): ByteArray? = keys[alias]
        override fun store(alias: String, key: ByteArray) {
            keys[alias] = key
        }

        override fun delete(alias: String): Boolean = keys.remove(alias) != null
    }

    private val keychain = FakeKeychain()
    private val secure = IosSecureEncrypt(SilentLogger, keychain)

    // ---- the round trip --------------------------------------------------------------------------

    @Test
    fun `a secret survives encryption and decryption`() {
        val encrypted = secure.encrypt("my nightscout token", "alias1")

        assertEquals("my nightscout token", secure.decrypt(encrypted))
    }

    @Test
    fun `a unicode secret survives`() {
        val secret = "hÄ›slo-Ð¿Ð°Ñ€Ð¾Ð»ÑŒ-🔑"

        assertEquals(secret, secure.decrypt(secure.encrypt(secret, "alias1")))
    }

    @Test
    fun `an empty secret is not encrypted`() {
        assertEquals("", secure.encrypt("", "alias1"))
        assertEquals("", secure.decrypt(""))
    }

    /**
     * A fresh IV every time.
     *
     * Encrypting the same secret twice must not produce the same ciphertext - reusing an IV under
     * one GCM key is the classic way to leak what the plaintexts have in common.
     */
    @Test
    fun `the same secret encrypts differently each time`() {
        val first = secure.encrypt("same", "alias1")
        val second = secure.encrypt("same", "alias1")

        assertNotEquals(first, second)
        assertEquals("same", secure.decrypt(first))
        assertEquals("same", secure.decrypt(second))
    }

    // ---- the envelope ----------------------------------------------------------------------------

    @Test
    fun `the stored string carries its alias`() {
        val encrypted = secure.encrypt("secret", "myAlias")

        assertEquals("myAlias", encrypted.split(":")[1])
    }

    @Test
    fun `a valid string validates`() {
        assertTrue(secure.isValidDataString(secure.encrypt("secret", "alias1")))
    }

    @Test
    fun `rubbish does not validate`() {
        assertFalse(secure.isValidDataString(null))
        assertFalse(secure.isValidDataString(""))
        assertFalse(secure.isValidDataString("no separator here"))
        assertFalse(secure.isValidDataString(":"))
    }

    /** The header hash is what turns a tampered or truncated string into a refusal. */
    @Test
    fun `a tampered body no longer validates`() {
        val encrypted = secure.encrypt("secret", "alias1")
        val tampered = encrypted.dropLast(2) + "ff"

        assertFalse(secure.isValidDataString(tampered))
        assertEquals("", secure.decrypt(tampered))
    }

    // ---- keys ------------------------------------------------------------------------------------

    /** Two aliases are two keys, so one cannot read the other's secret. */
    @Test
    fun `a secret cannot be read back under a different alias`() {
        val encrypted = secure.encrypt("secret", "alias1")
        val movedToOtherAlias = encrypted.replaceFirst(":alias1:", ":alias2:")

        // The header no longer matches the body, so it is refused before the key is even consulted.
        assertEquals("", secure.decrypt(movedToOtherAlias))
    }

    /**
     * After the key is deleted the old ciphertext is unreadable.
     *
     * A new key is generated for the alias on the next use, and GCM's tag check rejects the old
     * data rather than returning rubbish.
     */
    @Test
    fun `deleting the key makes old data undecryptable`() {
        val encrypted = secure.encrypt("secret", "alias1")
        secure.deleteKey("alias1")

        assertEquals("", secure.decrypt(encrypted))
    }

    @Test
    fun `a key is created once and reused`() {
        secure.encrypt("first", "alias1")
        val key = keychain.load("alias1")
        secure.encrypt("second", "alias1")

        assertTrue(key.contentEquals(keychain.load("alias1")))
    }
}
