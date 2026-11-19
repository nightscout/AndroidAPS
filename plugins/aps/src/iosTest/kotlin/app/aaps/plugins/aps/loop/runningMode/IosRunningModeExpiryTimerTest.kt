package app.aaps.plugins.aps.loop.runningMode

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * When [IosRunningModeExpiryTimer] asks for the job - which is the whole of what it decides.
 *
 * The provider throws a marker instead of returning a [RunningModeExpiryJob]. Building a real one
 * needs five collaborators (`Config`, `DateUtil`, `ProcessedTbrEbData`, `CommandQueue` and a
 * logger), and none of them would be read: this test is about **when** the job is asked for, not
 * what the job then does. `RunningModeExpiryJob` is final, so there is nothing to subclass either,
 * and Kotlin/Native has no Mockito. The marker is caught by the scope's own handler, so a throw
 * does not fail the test - it records that the moment arrived.
 *
 * The clock is a [StandardTestDispatcher], so the delay is moved by hand rather than waited out.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IosRunningModeExpiryTimerTest {

    private class JobAsked : Exception("the timer asked for the job")

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

    private class Fixture {

        val dispatcher = StandardTestDispatcher()
        var asked = 0
        val caught = mutableListOf<Throwable>()

        /** A supervisor and a handler of its own: the marker must not take the test down with it. */
        private val scope = CoroutineScope(dispatcher + SupervisorJob() + CoroutineExceptionHandler { _, t -> caught.add(t) })

        val timer = IosRunningModeExpiryTimer(
            aapsLogger = SilentLogger,
            expiryJob = {
                asked++
                throw JobAsked()
            },
            scope = scope
        )

        fun advance(byMs: Long) {
            dispatcher.scheduler.advanceTimeBy(byMs)
            dispatcher.scheduler.runCurrent()
        }
    }

    @Test
    fun `the job is asked for when the delay is up and not before`() {
        val f = Fixture()
        f.timer.schedule(20 * 60_000)

        f.advance(20 * 60_000 - 1)
        assertEquals(0, f.asked, "one millisecond early is still early")

        f.advance(1)
        assertEquals(1, f.asked, "at the end of the mode the job runs")
        assertTrue(f.caught.all { it is JobAsked }, "nothing but the marker came out of the scope")
    }

    @Test
    fun `a second schedule replaces the first so the job runs once`() {
        val f = Fixture()
        f.timer.schedule(10 * 60_000)
        // The user extends the mode before the first end arrives.
        f.timer.schedule(30 * 60_000)

        f.advance(10 * 60_000)
        assertEquals(0, f.asked, "the end that was replaced must not fire")

        f.advance(20 * 60_000)
        assertEquals(1, f.asked, "only the end that is current fires")
    }

    @Test
    fun `cancel before the delay means the job never runs`() {
        val f = Fixture()
        f.timer.schedule(5 * 60_000)
        f.timer.cancel()

        f.advance(60 * 60_000)
        assertEquals(0, f.asked, "a cancelled mode has no end to clean up")
    }

    @Test
    fun `cancel with nothing pending does nothing`() {
        val f = Fixture()
        f.timer.cancel()
        f.timer.cancel()

        f.advance(60 * 60_000)
        assertEquals(0, f.asked)
    }

    @Test
    fun `an end that is already due runs at the first chance`() {
        // What the next start after a suspension looks like: the scheduler works out that the end has
        // passed and asks for no delay at all.
        val f = Fixture()
        f.timer.schedule(0)

        f.advance(0)
        assertEquals(1, f.asked)
    }
}
