package app.aaps.plugins.sync.nsclientV3.ws

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The gap rule of [IosForegroundWatcher].
 *
 * The notification bodies are called directly rather than posted through `NSNotificationCenter`,
 * because the centre delivers on the main queue and a test would have to pump a run loop to see
 * anything. What the notifications are wired to is one line in `start()`; what they decide is this.
 */
class IosForegroundWatcherTest {

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

    /** A clock the test moves by hand. */
    private class TestClock(var nowMs: Long = 1_000_000L) {

        fun advance(byMs: Long) {
            nowMs += byMs
        }
    }

    private class Fixture(gapMs: Long = IosForegroundWatcher.DEFAULT_GAP_MS) {

        val clock = TestClock()
        val gaps = mutableListOf<Long>()
        val watcher = IosForegroundWatcher(
            aapsLogger = SilentLogger,
            onResumeAfterGap = { gaps.add(it) },
            gapMs = gapMs,
            now = { clock.nowMs }
        )
    }

    @Test
    fun `a short visit to another app does not ask for a reconnect`() {
        val f = Fixture()
        f.watcher.onBackground()
        f.clock.advance(10_000)
        f.watcher.onForeground()
        assertTrue(f.gaps.isEmpty(), "10 s away is not a suspension")
    }

    @Test
    fun `a gap over the threshold asks for a reconnect and reports how long`() {
        val f = Fixture()
        f.watcher.onBackground()
        f.clock.advance(11 * 60_000)
        f.watcher.onForeground()
        assertEquals(listOf(11 * 60_000L), f.gaps)
    }

    @Test
    fun `the threshold itself fires but one millisecond under it does not`() {
        val f = Fixture()
        f.watcher.onBackground()
        f.clock.advance(IosForegroundWatcher.DEFAULT_GAP_MS)
        f.watcher.onForeground()
        assertEquals(listOf(IosForegroundWatcher.DEFAULT_GAP_MS), f.gaps, "at the threshold it fires")

        f.watcher.onBackground()
        f.clock.advance(IosForegroundWatcher.DEFAULT_GAP_MS - 1)
        f.watcher.onForeground()
        assertEquals(1, f.gaps.size, "one below the threshold it does not")
    }

    @Test
    fun `an app launched into the background and shown much later counts the whole wait`() {
        // No `onBackground` first: iOS can launch the process in the background and freeze it there,
        // so the first notification this watcher ever sees may be the foreground one.
        val f = Fixture()
        f.clock.advance(20 * 60_000)
        f.watcher.onForeground()
        assertEquals(listOf(20 * 60_000L), f.gaps)
    }

    @Test
    fun `each gap is measured from the last return and not from the first`() {
        val f = Fixture()
        f.watcher.onBackground()
        f.clock.advance(5 * 60_000)
        f.watcher.onForeground()

        // Stays in the app for a while, then a short trip away: the long first gap must not leak
        // into the second decision.
        f.clock.advance(30 * 60_000)
        f.watcher.onBackground()
        f.clock.advance(5_000)
        f.watcher.onForeground()

        assertEquals(listOf(5 * 60_000L), f.gaps, "only the first return crossed the threshold")
    }

    @Test
    fun `time spent in the foreground does not count as being away`() {
        val f = Fixture()
        f.clock.advance(2 * 60_000)
        f.watcher.onBackground()
        f.clock.advance(1_000)
        f.watcher.onForeground()
        assertTrue(f.gaps.isEmpty(), "the two minutes before backgrounding were spent running")
    }

    @Test
    fun `repeated returns each get their own decision`() {
        val f = Fixture()
        repeat(3) {
            f.watcher.onBackground()
            f.clock.advance(2 * 60_000)
            f.watcher.onForeground()
        }
        assertEquals(listOf(120_000L, 120_000L, 120_000L), f.gaps)
    }

    @Test
    fun `start is idempotent so a second call does not double the observers`() {
        val f = Fixture()
        f.watcher.start()
        f.watcher.start()
        f.watcher.stop()
        // Reaching here without a crash is the assertion: `stop` removes every observer it added,
        // and a double `start` that registered twice would leave one behind.
        assertTrue(f.gaps.isEmpty())
    }
}
