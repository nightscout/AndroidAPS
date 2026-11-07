package app.aaps.implementation.protection

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * That an iOS build agrees with an Android one about a stored password hash.
 *
 * This is the test that matters most in this class. The hash lives in the user's preferences and
 * travels between platforms in an export, so a difference here would not look like a bug - it would
 * look like the user typing the wrong master password, on a screen that will not let them in.
 *
 * The reference values below were produced independently, with Python's `hmac` module, not by
 * running this code. A test that compares an implementation with itself would pass just as happily
 * with the salt and the message the wrong way round.
 */
class IosPasswordHasherTest {

    private class RecordingLogger : AAPSLogger {

        val errors = mutableListOf<String>()

        override fun error(tag: LTag, message: String) {
            errors.add(message)
        }

        override fun debug(message: String) {}
        override fun debug(enable: Boolean, tag: LTag, message: String) {}
        override fun debug(tag: LTag, message: String) {}
        override fun debug(tag: LTag, accessor: () -> String) {}
        override fun debug(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun warn(tag: LTag, message: String) {}
        override fun warn(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun info(tag: LTag, message: String) {}
        override fun info(tag: LTag, format: String, vararg arguments: Any?) {}
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

    private val logger = RecordingLogger()
    private val hasher = IosPasswordHasher(logger)

    private val salt = "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff"

    /** Computed with Python: hmac.new(salt.encode(), password.encode(), sha256).hexdigest(). */
    private fun stored(mac: String) = "hmac:$salt:$mac"

    @Test
    fun `an ascii password matches the reference hash`() {
        assertTrue(hasher.checkPassword("verySecret", stored("44c4e131f3325988bd9e2aea70c601c74ea58c321e4bba8067d32fe9e6319a39")))
    }

    @Test
    fun `an empty password matches the reference hash`() {
        assertTrue(hasher.checkPassword("", stored("1637177535067e2306f08cb0fb01221f623c1a8127efbe158861237b105be535")))
    }

    /** Non-ascii goes in as UTF-8 on both platforms - a different encoding would still hash cleanly. */
    @Test
    fun `an accented password matches the reference hash`() {
        assertTrue(hasher.checkPassword("hésločšž123", stored("c0c1051d3624eab89d37f378ba683e141591b4b2602510302bb4bf2058aff306")))
    }

    @Test
    fun `a wrong password does not match`() {
        assertFalse(hasher.checkPassword("notIt", stored("44c4e131f3325988bd9e2aea70c601c74ea58c321e4bba8067d32fe9e6319a39")))
    }

    @Test
    fun `what this hashes can be checked back`() {
        val hash = hasher.hashPassword("myPassword")

        assertTrue(hasher.checkPassword("myPassword", hash))
        assertFalse(hasher.checkPassword("myPasswore", hash))
    }

    @Test
    fun `the stored form is the one Android writes`() {
        val parts = hasher.hashPassword("myPassword").split(":")

        assertEquals(3, parts.size)
        assertEquals("hmac", parts[0])
        assertEquals(64, parts[1].length, "32 salt bytes as hex")
        assertEquals(64, parts[2].length, "a sha256 mac as hex")
        assertTrue(parts[1].all { it in "0123456789abcdef" }, "lowercase hex, as toHex writes it")
        assertTrue(parts[2].all { it in "0123456789abcdef" }, "lowercase hex, as toHex writes it")
    }

    /** A fresh salt each time, or two users with one password would share a hash. */
    @Test
    fun `hashing the same password twice gives different text`() {
        assertTrue(hasher.hashPassword("same") != hasher.hashPassword("same"))
    }

    /** Hashing a hash would store a hash of a hash and lock the user out. */
    @Test
    fun `an already hashed value is returned untouched`() {
        val hash = hasher.hashPassword("myPassword")

        assertEquals(hash, hasher.hashPassword(hash))
    }

    /** How a password saved before hashing existed still gets in. */
    @Test
    fun `a reference that is not a hash is compared as plain text`() {
        assertTrue(hasher.checkPassword("plain", "plain"))
        assertFalse(hasher.checkPassword("plain", "other"))
    }

    @Test
    fun `a damaged stored hash is refused rather than trusted`() {
        assertFalse(hasher.checkPassword("verySecret", "hmac:onlyTwoParts"))
        assertEquals(1, logger.errors.size)
    }
}
