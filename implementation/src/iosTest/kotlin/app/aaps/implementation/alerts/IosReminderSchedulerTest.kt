package app.aaps.implementation.alerts

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The guard on a reminder interval, which is the one decision made before iOS is involved.
 *
 * Scheduling itself needs a notification centre and cannot run in a test binary. What can be checked
 * is that a nonsensical interval is refused with an explanation instead of being handed to iOS,
 * which rejects a non-positive `timeInterval` with an exception that would surface far from the
 * automation rule that caused it.
 */
class IosReminderSchedulerTest {

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
    private val scheduler = IosReminderScheduler(logger)

    @Test
    fun `zero seconds is refused with an explanation`() {
        scheduler.scheduleReminder(0, "now")

        assertEquals(1, logger.errors.size)
        assertTrue(logger.errors.single().contains("positive"))
    }

    @Test
    fun `a negative interval is refused too`() {
        scheduler.scheduleReminder(-30, "in the past")

        assertEquals(1, logger.errors.size)
    }

    /** Identifiers are namespaced so a reminder cannot collide with another module's notification. */
    @Test
    fun `reminder identifiers are namespaced to aaps`() {
        assertTrue(IosReminderScheduler.IDENTIFIER_PREFIX.startsWith("aaps-"))
    }
}
