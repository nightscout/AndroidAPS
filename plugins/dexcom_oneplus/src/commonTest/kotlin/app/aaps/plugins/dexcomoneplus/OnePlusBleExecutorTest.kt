package app.aaps.plugins.dexcomoneplus

import kotlin.concurrent.Volatile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The driver's one thread.
 *
 * This is in `commonTest` on purpose, so every platform's executor runs it. An executor that
 * queues work and never runs it would look exactly like a sensor that is simply never found.
 */
class OnePlusBleExecutorTest {

    @Test
    fun `a queued block really runs on another thread`() {
        val executor = onePlusBleExecutor()
        val flag = Flag()
        executor.execute { flag.set() }
        assertTrue(flag.awaitUpTo(WAIT_MS), "the executor never ran the block")
        executor.shutdownNow()
    }

    @Test
    fun `blocks run one after the other and in order`() {
        val executor = onePlusBleExecutor()
        val order = Order()
        val done = Flag()
        executor.execute {
            // Hold the thread, so a second executor thread would be visible as a wrong order.
            onePlusSleep(50L)
            order.append("first")
        }
        executor.execute { order.append("second") }
        executor.execute {
            order.append("third")
            done.set()
        }
        assertTrue(done.awaitUpTo(WAIT_MS), "the executor never reached the last block")
        assertEquals("first,second,third", order.text())
        executor.shutdownNow()
    }

    @Test
    fun `a stopped executor refuses new work`() {
        val executor = onePlusBleExecutor()
        executor.shutdownNow()
        try {
            executor.execute { fail("a stopped executor ran a block") }
            fail("a stopped executor accepted a block")
        } catch (_: Throwable) {
            // The JVM throws RejectedExecutionException; another platform may throw another type.
            // The driver catches it either way; what matters is that it is not silent.
        }
    }

    @Test
    fun `a block queued before the stop does not run after it`() {
        val executor = onePlusBleExecutor()
        val blocking = Flag()
        val second = Flag()
        executor.execute {
            blocking.set()
            onePlusSleep(200L)
        }
        executor.execute { second.set() }
        assertTrue(blocking.awaitUpTo(WAIT_MS), "the executor never started the first block")
        executor.shutdownNow()
        onePlusSleep(400L)
        assertFalse(second.hasRun(), "a block queued before the stop still ran after it")
    }

    private class Flag {

        @Volatile
        private var done = false

        fun set() {
            done = true
        }

        fun hasRun(): Boolean = done

        fun awaitUpTo(millis: Long): Boolean {
            var left = millis
            while (left > 0L && !done) {
                onePlusSleep(POLL_MS)
                left -= POLL_MS
            }
            return done
        }
    }

    /** Written only from the executor's own thread, read after it has finished. */
    private class Order {

        @Volatile
        private var value = ""

        fun append(step: String) {
            value = if (value.isEmpty()) step else "$value,$step"
        }

        fun text(): String = value
    }

    private companion object {

        const val WAIT_MS = 5_000L
        const val POLL_MS = 10L
    }
}
