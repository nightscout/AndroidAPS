package app.aaps.implementation.scenes

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
 * When [IosSceneExpiryScheduler] asks for the runner, which is the whole of what it decides.
 *
 * ## The warning this file used to carry, and why it still stands
 *
 * This class tested that scene expiry was loud about **not** working on iOS, and warned that
 * somebody might "finish" it with a coroutine timer - which works in the foreground and silently
 * fails whenever iOS suspends the app. The consequence is not a stale screen: `SceneExpiryRunner`
 * reverts the SMB toggle and the profile switch, and neither ends on its own.
 *
 * That warning is still right about a timer **on its own**, and nothing here should be read as
 * permission to rely on one. What changed is that scene expiry gained a backstop:
 * `PostCalculationRunner.endOverdueScene` calls `endIfOverdue` before every loop run, so an expiry
 * this timer never got to fire is caught by the next glucose calculation. The scheduler is that
 * timer plus a sweep when the app comes to the front, and the loop-run sweep behind both.
 *
 * So the contract tested here is the timer half, which is the part this class owns: it asks for the
 * runner when the time is up, once, and not after a cancel. The sweep half belongs to
 * `SceneExpiryRunner.endIfOverdue`, which has its own tests in `SceneExpiryRunnerTest`.
 *
 * The provider throws a marker instead of returning a [SceneExpiryRunner]: building a real one needs
 * twelve collaborators, none of which would be read here, and it is final, so there is nothing to
 * subclass and Kotlin/Native has no Mockito. The marker is caught by the scope's own handler, so
 * throwing records that the moment arrived rather than failing the test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IosSceneExpirySchedulerTest {

    private class RunnerAsked : Exception("the scheduler asked for the runner")

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

        val scheduler = IosSceneExpiryScheduler(
            aapsLogger = SilentLogger,
            runner = {
                asked++
                throw RunnerAsked()
            },
            scope = scope
        )

        fun advance(byMs: Long) {
            dispatcher.scheduler.advanceTimeBy(byMs)
            dispatcher.scheduler.runCurrent()
        }
    }

    @Test
    fun `the runner is asked for when the scene's time is up and not before`() {
        val f = Fixture()
        f.scheduler.schedule("Exercise", 60 * 60_000)

        f.advance(60 * 60_000 - 1)
        assertEquals(0, f.asked, "one millisecond early is still early")

        f.advance(1)
        assertEquals(1, f.asked, "at the end of the scene the runner runs")
        assertTrue(f.caught.all { it is RunnerAsked }, "nothing but the marker came out of the scope")
    }

    @Test
    fun `a second schedule replaces the first so only one scene end fires`() {
        val f = Fixture()
        f.scheduler.schedule("Exercise", 10 * 60_000)
        // There is at most one active scene, so a new one replaces the pending end rather than adding to it.
        f.scheduler.schedule("Sleep", 30 * 60_000)

        f.advance(10 * 60_000)
        assertEquals(0, f.asked, "the end that was replaced must not fire")

        f.advance(20 * 60_000)
        assertEquals(1, f.asked, "only the end that is current fires")
    }

    @Test
    fun `cancel before the time is up means the scene end never runs`() {
        val f = Fixture()
        f.scheduler.schedule("Exercise", 5 * 60_000)
        f.scheduler.cancel()

        f.advance(24 * 60 * 60_000)
        assertEquals(0, f.asked, "a scene that ended early has no end left to run")
    }

    @Test
    fun `cancel with nothing pending does nothing`() {
        val f = Fixture()
        f.scheduler.cancel()
        f.scheduler.cancel()

        f.advance(24 * 60 * 60_000)
        assertEquals(0, f.asked)
    }

    @Test
    fun `a scene that is already over ends at the first chance`() {
        // What a restored or re-read scene looks like: the end has passed, so the delay is none at all.
        val f = Fixture()
        f.scheduler.schedule("Exercise", 0)

        f.advance(0)
        assertEquals(1, f.asked, "an end that is already due runs straight away")
    }

    @Test
    fun `scheduling is not reported as an error any more`() {
        // The old implementation logged at error on every schedule, because nothing would ever end the
        // scene. Pinned so that the error channel goes back to meaning something has gone wrong.
        val errors = mutableListOf<String>()
        val recording = object : AAPSLogger by SilentLogger {
            override fun error(tag: LTag, message: String) {
                errors.add(message)
            }
        }
        val dispatcher = StandardTestDispatcher()
        val scope = CoroutineScope(dispatcher + SupervisorJob() + CoroutineExceptionHandler { _, _ -> })
        val scheduler = IosSceneExpiryScheduler(recording, { throw RunnerAsked() }, scope)

        scheduler.schedule("Exercise", 60 * 60_000)

        assertTrue(errors.isEmpty(), "a scheduled scene end is routine now, not a failure: $errors")
    }
}
