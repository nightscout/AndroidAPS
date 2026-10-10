package app.aaps.core.interfaces.rx

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Covers [chunkedOnQuietPeriod], the Flow replacement for the RxJava
 * `publish { shared.buffer(shared.debounce(quietPeriod)) }` batching used for the Wear health
 * events. The virtual clock of [runTest] makes the quiet period exact instead of flaky.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class ChunkedOnQuietPeriodTest {

    private val quietPeriod = 1_000L

    @Test
    fun `items sent in one burst come out as a single batch`() = runTest {
        val source = MutableSharedFlow<Int>()
        val batches = mutableListOf<List<Int>>()
        val collector = launch { source.chunkedOnQuietPeriod(quietPeriod).collect { batches += it } }
        runCurrent()

        source.emit(1)
        advanceTimeBy(quietPeriod / 2)
        source.emit(2)
        advanceTimeBy(quietPeriod / 2)
        source.emit(3)

        // Every item restarted the timer, so nothing is out yet even though 1.5 periods passed.
        advanceTimeBy(quietPeriod - 1)
        runCurrent()
        assertThat(batches).isEmpty()

        advanceTimeBy(1)
        runCurrent()
        assertThat(batches).containsExactly(listOf(1, 2, 3))
        collector.cancel()
    }

    @Test
    fun `a new burst after the quiet period starts a new batch`() = runTest {
        val source = MutableSharedFlow<Int>()
        val batches = mutableListOf<List<Int>>()
        val collector = launch { source.chunkedOnQuietPeriod(quietPeriod).collect { batches += it } }
        runCurrent()

        source.emit(1)
        advanceTimeBy(quietPeriod + 1)
        runCurrent()
        source.emit(2)
        source.emit(3)
        advanceTimeBy(quietPeriod + 1)
        runCurrent()

        // Batches must not carry items over from the batch before them.
        assertThat(batches).containsExactly(listOf(1), listOf(2, 3)).inOrder()
        collector.cancel()
    }

    @Test
    fun `a source that ends still delivers the last batch`() = runTest {
        // flowOf completes right away, so the batch is only released by the quiet period timer.
        val batches = flowOf(1, 2, 3).chunkedOnQuietPeriod(quietPeriod).toList()

        assertThat(batches).containsExactly(listOf(1, 2, 3))
    }

    @Test
    fun `a source that sends nothing produces no batch`() = runTest {
        val source = MutableSharedFlow<Int>()
        val batches = mutableListOf<List<Int>>()
        val collector = launch { source.chunkedOnQuietPeriod(quietPeriod).collect { batches += it } }
        runCurrent()

        advanceTimeBy(quietPeriod * 5)
        runCurrent()

        // The timer only runs while a batch is open, so an idle source stays silent.
        assertThat(batches).isEmpty()
        collector.cancel()
    }
}
