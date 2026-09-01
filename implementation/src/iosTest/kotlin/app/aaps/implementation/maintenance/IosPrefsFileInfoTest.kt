package app.aaps.implementation.maintenance

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import platform.Foundation.NSTemporaryDirectory

/**
 * The two answers the maintenance and import screens read.
 *
 * The relative wording is not asserted on: it comes from `NSRelativeDateTimeFormatter` and is
 * different in every language, so a test on the words would only describe this machine. What is
 * checked instead is which of the two forms comes back, and that the directory answer is a real
 * look at the file system rather than a fixed true.
 */
class IosPrefsFileInfoTest {

    private class RecordingLogger : AAPSLogger {

        val debugs = mutableListOf<String>()

        override fun debug(tag: LTag, message: String) {
            debugs.add(message)
        }

        override fun debug(message: String) {}
        override fun debug(enable: Boolean, tag: LTag, message: String) {}
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

    private val logger = RecordingLogger()
    private val temporaryDirectory = NSTemporaryDirectory()

    private fun sut(directory: String? = temporaryDirectory) = IosPrefsFileInfo(logger, directory)

    @Test
    fun `an old export is named by its date`() {
        assertEquals("2001-01-01", sut().formatExportedAgo("2001-01-01T12:00:00.000Z"))
    }

    @Test
    fun `a broken timestamp is shown as it stands instead of throwing`() {
        assertEquals("nonsense", sut().formatExportedAgo("nonsense"))
        assertEquals(1, logger.debugs.size)
    }

    /**
     * Only that something comes back, and that it is not the date form. The words themselves are
     * the system's and change with the device language.
     */
    @Test
    fun `a recent export is described in words rather than by its date`() {
        val today = kotlin.time.Clock.System.now().toString()

        val text = sut().formatExportedAgo(today)

        assertTrue(text.isNotEmpty())
        assertFalse(text == ExportedAgo.datePart(today))
    }

    @Test
    fun `a directory that exists and can be written counts as granted`() {
        assertTrue(sut().isDirectoryAccessGranted())
    }

    @Test
    fun `a directory that is not there does not`() {
        assertFalse(sut("/no/such/place/for/aaps").isDirectoryAccessGranted())
    }

    @Test
    fun `no directory at all is a no - with a line saying so`() {
        assertFalse(sut(directory = null).isDirectoryAccessGranted())
        assertEquals(1, logger.debugs.size)
    }
}
