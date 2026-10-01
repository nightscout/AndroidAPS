package app.aaps.implementation.ui

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import platform.Foundation.NSURL

/**
 * What reaches the platform, and what is stopped before it.
 *
 * The point of the checks is that iOS fails silently: `openURL` on an address it cannot use simply
 * does nothing, so a wrong link and a working one look identical to the user. These tests pin that
 * such an address never gets that far and leaves a log line instead.
 */
class IosUrlOpenerTest {

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

    private class RecordingLauncher : UrlLauncher {

        var launched: NSURL? = null

        override fun launch(url: NSURL) {
            launched = url
        }
    }

    private val logger = RecordingLogger()
    private val launcher = RecordingLauncher()
    private val opener = IosUrlOpener(logger, launcher)

    @Test
    fun `a normal link reaches the platform unchanged`() {
        opener.open("https://androidaps.readthedocs.io/en/latest/")

        assertEquals("https://androidaps.readthedocs.io/en/latest/", launcher.launched?.absoluteString)
        assertTrue(logger.debugs.isEmpty())
    }

    @Test
    fun `text that is not an address is stopped and logged`() {
        opener.open("this is not a link")

        assertNull(launcher.launched)
        assertEquals(1, logger.debugs.size)
    }

    @Test
    fun `an address with no scheme is stopped - iOS would drop it without a word`() {
        opener.open("www.androidaps.org")

        assertNull(launcher.launched)
        assertEquals(1, logger.debugs.size)
    }

    @Test
    fun `an empty string opens nothing`() {
        opener.open("")

        assertNull(launcher.launched)
        assertEquals(1, logger.debugs.size)
    }

    /** The log line has to carry the address, or it says nothing a reader can act on. */
    @Test
    fun `the log line names the address that was refused`() {
        opener.open("www.androidaps.org")

        assertTrue(logger.debugs.single().contains("www.androidaps.org"))
    }

    /** Not only http: the about dialog can offer mail and other schemes too. */
    @Test
    fun `other schemes are passed on rather than second guessed`() {
        opener.open("mailto:someone@example.com")

        assertEquals("mailto:someone@example.com", launcher.launched?.absoluteString)
    }
}
