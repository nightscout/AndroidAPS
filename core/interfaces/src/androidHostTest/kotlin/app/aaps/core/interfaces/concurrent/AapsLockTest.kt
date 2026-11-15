package app.aaps.core.interfaces.concurrent

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class AapsLockTest {

    @Test
    fun returnsWhateverTheActionReturns() {
        val lock = AapsLock()
        assertThat(lock.withLock { 42 }).isEqualTo(42)
    }

    /**
     * The property the ported call sites depend on: several of them call one guarded method from
     * inside another. A non-reentrant lock would deadlock here rather than fail visibly.
     */
    @Test
    fun isReentrant() {
        val lock = AapsLock()
        val result = lock.withLock {
            lock.withLock {
                lock.withLock { "three deep" }
            }
        }
        assertThat(result).isEqualTo("three deep")
    }

    @Test
    fun releasesWhenTheActionThrows() {
        val lock = AapsLock()
        runCatching { lock.withLock { error("boom") } }
        // Would block forever if the failed call had kept the lock.
        assertThat(lock.withLock { "still usable" }).isEqualTo("still usable")
    }

    @Test
    fun onlyOneThreadAtATime() {
        val lock = AapsLock()
        val threads = 8
        val perThread = 2000
        var unguarded = 0
        val inside = AtomicInteger(0)
        val overlaps = AtomicInteger(0)

        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        repeat(threads) {
            pool.execute {
                start.await()
                repeat(perThread) {
                    lock.withLock {
                        if (inside.incrementAndGet() != 1) overlaps.incrementAndGet()
                        unguarded++
                        inside.decrementAndGet()
                    }
                }
                done.countDown()
            }
        }
        start.countDown()
        done.await(30, TimeUnit.SECONDS)
        pool.shutdown()

        assertThat(overlaps.get()).isEqualTo(0)
        assertThat(unguarded).isEqualTo(threads * perThread)
    }
}
