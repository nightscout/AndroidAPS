package app.aaps.implementation.protection

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Storing the remembered export password on iOS.
 *
 * What is worth testing here is not the Keychain - that is Apple's - but the pair of values this has
 * to keep together. The shared rules in `ExportPasswordDataStoreImpl` measure the validity window
 * from the timestamp, so a secret that came back without its timestamp, or with the wrong one, would
 * be treated as infinitely fresh or infinitely stale. Both are silent: the user is either never asked
 * for their password again, or asked every single time with no explanation.
 *
 * So the cases below are mostly about a stored value that is not what this wrote - which is what a
 * half-finished write, an older format, or a corrupted entry looks like.
 */
class IosExportPasswordPlatformTest {

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

    private class FakeKeychain : Keychain {

        val entries = mutableMapOf<String, ByteArray>()

        override fun load(alias: String): ByteArray? = entries[alias]
        override fun store(alias: String, key: ByteArray) {
            entries[alias] = key
        }

        override fun delete(alias: String): Boolean = entries.remove(alias) != null
    }

    private val logger = RecordingLogger()
    private val keychain = FakeKeychain()
    private val platform = IosExportPasswordPlatform(logger, keychain)

    private fun putRaw(value: String) = keychain.store("export-password", value.encodeToByteArray())

    @Test
    fun `nothing stored reads as null`() {
        assertNull(platform.read())
        assertTrue(logger.errors.isEmpty(), "an empty store is normal, not a fault")
    }

    @Test
    fun `what was written comes back with its timestamp`() {
        platform.write("encrypted-envelope", 1_700_000_000_000)

        val stored = platform.read()

        assertEquals("encrypted-envelope", stored?.secret)
        assertEquals(1_700_000_000_000, stored?.timestamp)
    }

    @Test
    fun `writing again replaces what was there`() {
        platform.write("first", 1)
        platform.write("second", 2)

        assertEquals("second", platform.read()?.secret)
        assertEquals(2, platform.read()?.timestamp)
    }

    @Test
    fun `clearing forgets the secret`() {
        platform.write("encrypted-envelope", 1)

        platform.clear()

        assertNull(platform.read())
    }

    /** Clearing an empty store is not an error - the rules call it whenever they drop a password. */
    @Test
    fun `clearing when nothing is stored is quiet`() {
        platform.clear()

        assertTrue(logger.errors.isEmpty())
    }

    /**
     * The envelope is base64 and holds no colon, but the separator rule has to be the **first**
     * colon regardless - a secret that did contain one must not truncate.
     */
    @Test
    fun `a secret containing a colon survives the round trip`() {
        platform.write("aa:bb:cc", 42)

        assertEquals("aa:bb:cc", platform.read()?.secret)
        assertEquals(42, platform.read()?.timestamp)
    }

    @Test
    fun `a value with no separator is dropped rather than guessed at`() {
        putRaw("justsomethingelse")

        assertNull(platform.read())
        assertNull(keychain.entries["export-password"], "the bad value must not be left to fail again")
        assertEquals(1, logger.errors.size)
    }

    @Test
    fun `a value whose timestamp is not a number is dropped`() {
        putRaw("notanumber:encrypted-envelope")

        assertNull(platform.read())
        assertNull(keychain.entries["export-password"])
        assertEquals(1, logger.errors.size)
    }

    /** A leading colon means no timestamp at all, which is the same fault. */
    @Test
    fun `a value starting with the separator is dropped`() {
        putRaw(":encrypted-envelope")

        assertNull(platform.read())
        assertEquals(1, logger.errors.size)
    }

    /**
     * Android shortens the window from a marker file in its export directory. iOS has no equivalent
     * place, and inventing one would put a way of expiring passwords in minutes into a release build.
     */
    @Test
    fun `the validity window is never shortened on iOS`() {
        assertNull(platform.shortenedValidity())
    }
}
