package app.aaps.core.interfaces.db

import app.aaps.core.data.model.TimeStamped
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.utils.DateUtil
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ClockSkewCompensationTest {

    private data class Item(override var timestamp: Long) : TimeStamped

    private fun config(client: Boolean): Config = mock<Config>().also {
        whenever(it.AAPSCLIENT).thenReturn(client)
    }

    private fun dateUtil(now: Long): DateUtil = mock<DateUtil>().also {
        whenever(it.now()).thenReturn(now)
    }

    @Test
    fun `pass-through on APS phone (no AAPSCLIENT)`() = runTest {
        val source = flowOf(listOf(Item(timestamp = 100_000L), Item(timestamp = 200_000L)))
        val out = source.compensateForClockSkew(config(client = false), dateUtil(now = 50_000L)).toList()

        // No re-emission, exactly one passthrough
        assertThat(out).hasSize(1)
        assertThat(out[0].map { it.timestamp }).containsExactly(100_000L, 200_000L)
    }

    @Test
    fun `no future row - single emission only`() = runTest {
        val items = listOf(Item(timestamp = 90_000L), Item(timestamp = 95_000L))
        val source = flowOf(items)
        val out = source.compensateForClockSkew(config(client = true), dateUtil(now = 100_000L)).toList()

        assertThat(out).hasSize(1)
        assertThat(out[0]).isEqualTo(items)
    }

    @Test
    fun `future row within tolerance - emits twice with delay`() = runTest {
        val nowMs = 100_000L
        val gap = 3_000L                                  // 3s — within 5s tolerance
        val items = listOf(Item(timestamp = nowMs + gap))
        val source = flowOf(items)

        val collected = mutableListOf<List<Item>>()
        val job = launch {
            source.compensateForClockSkew(config(client = true), dateUtil(now = nowMs))
                .collect { collected.add(it) }
        }
        // First emission immediate
        advanceTimeBy(1)
        assertThat(collected).hasSize(1)

        // Re-emission scheduled at gap + 500ms = 3500ms
        advanceTimeBy(gap + 500L)
        advanceUntilIdle()
        assertThat(collected).hasSize(2)
        assertThat(collected[1]).isEqualTo(items)

        job.cancel()
    }

    @Test
    fun `future row beyond tolerance - single emission, no re-emit`() = runTest {
        val nowMs = 100_000L
        val gap = 30_000L                                 // 30s — beyond 5s tolerance (scheduled event)
        val items = listOf(Item(timestamp = nowMs + gap))
        val source = flowOf(items)

        val collected = mutableListOf<List<Item>>()
        val job = launch {
            source.compensateForClockSkew(config(client = true), dateUtil(now = nowMs))
                .collect { collected.add(it) }
        }
        advanceTimeBy(60_000L)                            // far beyond gap
        advanceUntilIdle()

        assertThat(collected).hasSize(1)
        assertThat(collected[0]).isEqualTo(items)
        job.cancel()
    }

    @Test
    fun `multiple items - uses largest future gap`() = runTest {
        val nowMs = 100_000L
        val items = listOf(
            Item(timestamp = nowMs - 500L),
            Item(timestamp = nowMs + 1_000L),
            Item(timestamp = nowMs + 4_000L)              // largest, within tolerance
        )
        val source = flowOf(items)

        val collected = mutableListOf<List<Item>>()
        val job = launch {
            source.compensateForClockSkew(config(client = true), dateUtil(now = nowMs))
                .collect { collected.add(it) }
        }
        advanceTimeBy(1)
        assertThat(collected).hasSize(1)

        // Re-emit not yet fired at 1ms
        advanceTimeBy(3_000L)
        assertThat(collected).hasSize(1)

        // Largest gap = 4000ms, re-emit at 4500ms
        advanceTimeBy(2_000L)
        advanceUntilIdle()
        assertThat(collected).hasSize(2)
        assertThat(collected[1]).isEqualTo(items)

        job.cancel()
    }

    @Test
    fun `boundary - exactly at tolerance triggers re-emit`() = runTest {
        val nowMs = 100_000L
        val gap = 5_000L                                  // exactly at boundary (in 1..5000)
        val items = listOf(Item(timestamp = nowMs + gap))
        val source = flowOf(items)

        val collected = mutableListOf<List<Item>>()
        val job = launch {
            source.compensateForClockSkew(config(client = true), dateUtil(now = nowMs))
                .collect { collected.add(it) }
        }
        advanceTimeBy(gap + 500L + 100L)
        advanceUntilIdle()

        assertThat(collected).hasSize(2)
        job.cancel()
    }

    @Test
    fun `each emission gets its own compensation`() = runTest {
        val nowMs = 100_000L
        val source = MutableSharedFlow<List<Item>>(replay = 0)

        val collected = mutableListOf<List<Item>>()
        val job = launch {
            source.compensateForClockSkew(config(client = true), dateUtil(now = nowMs))
                .collect { collected.add(it) }
        }
        advanceUntilIdle()                                // ensure collector is subscribed before first emit

        // First emission with future row
        source.emit(listOf(Item(timestamp = nowMs + 2_000L)))
        advanceTimeBy(2_500L)
        advanceUntilIdle()
        assertThat(collected).hasSize(2)                  // initial + delayed re-emit

        // Second emission, no future row
        source.emit(listOf(Item(timestamp = nowMs - 1_000L)))
        advanceUntilIdle()
        assertThat(collected).hasSize(3)                  // just the new immediate

        job.cancel()
    }
}
