package app.aaps.implementation.widget

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * There is no iOS widget yet, so a refresh has nothing to redraw.
 *
 * Tested because the class looks like an oversight and is not: unlike scene expiry, a widget that
 * does not refresh is harmless - nothing in AAPS acts on the redraw, it only means stale glucose on
 * a home screen that does not exist yet. The log line is what tells a developer why nothing happens.
 */
class IosWidgetUpdaterTest {

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
    private val updater = IosWidgetUpdater(logger)

    /** Debug, not error: a missing widget is not a fault, unlike a scene that never ends. */
    @Test
    fun `a refresh is logged rather than ignored silently`() {
        updater.update("LoopPlugin")

        assertEquals(1, logger.debugs.size)
    }

    /** The caller tag is the only useful thing in the message, so it has to survive. */
    @Test
    fun `the log names the caller`() {
        updater.update("LoopPlugin")

        assertTrue(logger.debugs.single().contains("LoopPlugin"))
    }

    @Test
    fun `repeated refreshes stay harmless`() {
        repeat(5) { updater.update("caller$it") }

        assertEquals(5, logger.debugs.size)
    }
}
