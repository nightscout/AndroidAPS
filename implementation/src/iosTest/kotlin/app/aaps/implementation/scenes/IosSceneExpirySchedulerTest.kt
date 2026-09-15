package app.aaps.implementation.scenes

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins that scene expiry is loud about not working on iOS.
 *
 * This class looks like a stub, and the danger is that it reads as one: someone could "finish" it
 * with a coroutine timer, which would appear to work in the foreground and silently fail whenever
 * the app was suspended. The consequence is not a stale screen - `SceneExpiryRunner` reverts the SMB
 * toggle and the profile switch, and neither ends on its own.
 *
 * So the contract tested here is: scheduling reports an error, and cancelling does not.
 */
class IosSceneExpirySchedulerTest {

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
    private val scheduler = IosSceneExpiryScheduler(logger)

    /** Error, not debug: a scene that will never end is not routine. */
    @Test
    fun `scheduling reports an error rather than staying quiet`() {
        scheduler.schedule("Exercise", 3_600_000)

        assertEquals(1, logger.errors.size)
    }

    /** The message has to name the scene, or a log reader cannot tell which one is stuck. */
    @Test
    fun `the error names the scene and the delay`() {
        scheduler.schedule("Exercise", 3_600_000)

        val message = logger.errors.single()
        assertTrue(message.contains("Exercise"), message)
        assertTrue(message.contains("3600000"), message)
    }

    /** Cancelling a schedule that never existed is normal, not a failure. */
    @Test
    fun `cancelling is silent`() {
        scheduler.cancel()

        assertTrue(logger.errors.isEmpty())
    }

    @Test
    fun `every scheduling attempt is reported - not just the first`() {
        scheduler.schedule("Exercise", 1000)
        scheduler.schedule("Sleep", 2000)

        assertEquals(2, logger.errors.size)
    }
}
