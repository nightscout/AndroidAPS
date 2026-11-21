package app.aaps.implementation.lifecycle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The bookkeeping of [BackgroundTimeHold]: every task that iOS gave is ended exactly once. */
class IosBackgroundTimeTest {

    private class FakeTasks(private val give: ULong? = 7u) : BackgroundTasks {

        val ended = mutableListOf<ULong>()
        var onExpire: (() -> Unit)? = null
        var limit: (() -> Unit)? = null
        var limitMs: Long? = null

        override fun begin(name: String, onExpire: () -> Unit): ULong? {
            this.onExpire = onExpire
            return give
        }

        override fun end(id: ULong) {
            ended += id
        }

        override fun after(ms: Long, block: () -> Unit) {
            limitMs = ms
            limit = block
        }
    }

    @Test
    fun `a release ends the task once`() {
        val tasks = FakeTasks()
        val hold = BackgroundTimeHold.start(tasks, null, "test", 30_000)

        hold.release()
        hold.release()

        assertEquals(listOf<ULong>(7u), tasks.ended)
    }

    @Test
    fun `when the time runs out the task ends and a later release does nothing`() {
        val tasks = FakeTasks()
        val hold = BackgroundTimeHold.start(tasks, null, "test", 30_000)

        tasks.onExpire?.invoke()
        hold.release()

        assertEquals(listOf<ULong>(7u), tasks.ended)
    }

    @Test
    fun `the limit of the caller ends the task`() {
        val tasks = FakeTasks()
        val hold = BackgroundTimeHold.start(tasks, null, "test", 600_000)

        assertEquals(600_000L, tasks.limitMs)
        tasks.limit?.invoke()
        hold.release()

        assertEquals(listOf<ULong>(7u), tasks.ended)
    }

    @Test
    fun `no time from iOS leaves nothing to end`() {
        val tasks = FakeTasks(give = null)
        val hold = BackgroundTimeHold.start(tasks, null, "test", 30_000)

        hold.release()

        assertTrue(tasks.ended.isEmpty())
        assertEquals(null, tasks.limitMs)
    }

    @Test
    fun `before the app installs it nothing is asked`() {
        // The state of a test binary, which has no UIApplication.
        BackgroundTimeHold.start(null, null, "test", 30_000).release()
    }
}
