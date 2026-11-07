package app.aaps.plugins.main.iob

import androidx.collection.LongSparseArray
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.aps.AutosensData
import app.aaps.core.interfaces.concurrent.withLock
import app.aaps.plugins.main.iob.iobCobCalculator.data.AutosensDataStoreObject
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * The store guards everything with one monitor, [AutosensDataStoreObject.dataLock].
 *
 * These tests pin the two things that were wrong before: the accessors locked `this` rather than
 * `dataLock`, so they excluded nothing that the compound operations did; and `reset()` locked the
 * table object and then replaced it, so a second thread locked the NEW table and ran straight
 * through.
 */
class AutosensDataStoreLockingTest : TestBaseWithProfile() {

    private fun gv(timestamp: Long) = GV(
        raw = 0.0,
        noise = 0.0,
        value = 100.0,
        timestamp = timestamp,
        sourceSensor = SourceSensor.UNKNOWN,
        trendArrow = TrendArrow.FLAT
    )

    /** reset() must not be able to interleave with newHistoryData(). */
    @Test
    fun resetAndNewHistoryDataDoNotInterleave() {
        val sut = AutosensDataStoreObject()
        val inside = AtomicInteger(0)
        val overlaps = AtomicInteger(0)
        val rounds = 500

        // Watches the table while the two writers race. Every observation is taken under the same
        // lock, so it can only ever see a state one of them left behind, never a half applied one.
        val table = LongSparseArray<AutosensData>()
        repeat(20) { table.put(it.toLong(), mock(AutosensData::class.java)) }

        val pool = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        val done = CountDownLatch(2)

        pool.execute {
            start.await()
            repeat(rounds) {
                sut.autosensDataTable = LongSparseArray<AutosensData>().apply {
                    repeat(20) { i -> put(i.toLong(), mock(AutosensData::class.java)) }
                }
                sut.dataLock.withLock {
                    if (inside.incrementAndGet() != 1) overlaps.incrementAndGet()
                    inside.decrementAndGet()
                }
                sut.reset()
            }
            done.countDown()
        }
        pool.execute {
            start.await()
            repeat(rounds) {
                sut.dataLock.withLock {
                    if (inside.incrementAndGet() != 1) overlaps.incrementAndGet()
                    inside.decrementAndGet()
                }
                sut.newHistoryData(10L, aapsLogger, dateUtil)
            }
            done.countDown()
        }

        start.countDown()
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue()
        pool.shutdown()
        assertThat(overlaps.get()).isEqualTo(0)
    }

    /**
     * A writer holding [AutosensDataStoreObject.dataLock] must block the property accessors. When
     * the accessors locked `this` instead, the read below went straight through mid-write.
     */
    @Test
    fun accessorsAreExcludedByTheCompoundLock() {
        val sut = AutosensDataStoreObject()
        val readerRan = AtomicBoolean(false)
        val readerEntered = CountDownLatch(1)

        val reader = Thread {
            readerEntered.countDown()
            sut.bgReadings = listOf(gv(1L))
            readerRan.set(true)
        }

        sut.dataLock.withLock {
            reader.start()
            // Give it every chance to barge in; it cannot, because the lock is held here.
            assertThat(readerEntered.await(5, TimeUnit.SECONDS)).isTrue()
            Thread.sleep(200)
            assertThat(readerRan.get()).isFalse()
        }

        reader.join(5_000)
        assertThat(readerRan.get()).isTrue()
        assertThat(sut.bgReadings).hasSize(1)
    }

    /** Reads and writes stay consistent under contention. */
    @Test
    fun concurrentReadersAndWritersSeeWholeValues() {
        val sut = AutosensDataStoreObject()
        val threads = 6
        val perThread = 1000
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        val torn = AtomicInteger(0)

        repeat(threads) { t ->
            pool.execute {
                start.await()
                repeat(perThread) { i ->
                    if (t % 2 == 0) {
                        sut.bgReadings = List(3) { gv((i + it).toLong()) }
                    } else {
                        val seen = sut.getBgReadingsDataTableCopy()
                        if (seen.isNotEmpty() && seen.size != 3) torn.incrementAndGet()
                    }
                }
                done.countDown()
            }
        }
        start.countDown()
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue()
        pool.shutdown()
        assertThat(torn.get()).isEqualTo(0)
    }
}
