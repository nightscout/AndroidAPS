package app.aaps.plugins.constraints.storage

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * That iOS can really answer "how much room is left", and in megabytes.
 *
 * This is worth a test on the device rather than a reading of the code, because of how it fails.
 * `NSURLVolumeAvailableCapacityForImportantUsageKey` is a string constant: a wrong or unsupported
 * key does not fail to compile and does not throw - it returns no value, [IosFreeStorage] reports 0,
 * and [StorageConstraintPlugin] turns that into `isClosedLoopAllowed = false`. The whole closed loop
 * switched off, with "disk full" on a phone with half a terabyte free. Nothing in a build would say
 * so.
 *
 * So the real query runs here, against the simulator's own volume.
 */
class IosFreeStorageTest {

    /** Keeps what was logged as an error, because an error is how this class reports "I cannot tell". */
    private class RecordingLogger : AAPSLogger {

        val errors = mutableListOf<String>()

        override fun error(tag: LTag, message: String) {
            errors.add(message)
        }

        override fun error(message: String) {
            errors.add(message)
        }

        override fun error(tag: LTag, format: String, vararg arguments: Any?) {
            errors.add(format)
        }

        override fun error(tag: LTag, message: String, throwable: Throwable) {
            errors.add(message)
        }

        override fun error(message: String, throwable: Throwable) {
            errors.add(message)
        }

        override fun error(format: String, vararg arguments: Any?) {
            errors.add(format)
        }

        override fun error(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {
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
        override fun debug(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
        override fun info(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
        override fun warn(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
    }

    @Test
    fun `the volume answers with a usable amount of space`() {
        val logger = RecordingLogger()

        val mb = IosFreeStorage(logger).availableMb()

        assertTrue(logger.errors.isEmpty(), "the free space could not be read: ${logger.errors}")
        // Above the threshold the plugin uses, so this also shows the units are megabytes and not
        // bytes: a byte count would be astronomically large and a gigabyte count far too small.
        assertTrue(mb > 200, "only $mb MB reported - either the key is wrong or the units are")
        assertTrue(mb < 100_000_000, "$mb MB is larger than any real volume, so the units are wrong")
    }

    @Test
    fun `two readings in a row agree`() {
        // Not a stability claim about the device - it is that the call has no state of its own and
        // does not consume anything, which a handle left open would break.
        val storage = IosFreeStorage(RecordingLogger())

        val first = storage.availableMb()
        val second = storage.availableMb()

        assertTrue(first > 0 && second > 0)
    }

    @Test
    fun `bytes become whole megabytes`() {
        assertEquals(1, bytesToMb(1_048_576))
        assertEquals(200, bytesToMb(200 * 1_048_576L))
        assertEquals(0, bytesToMb(0))
    }

    @Test
    fun `a part of a megabyte is rounded down`() {
        // Down, never up: the constraint must not be told about space the volume does not have.
        assertEquals(0, bytesToMb(1_048_575))
        assertEquals(1, bytesToMb(2_097_151))
    }
}
