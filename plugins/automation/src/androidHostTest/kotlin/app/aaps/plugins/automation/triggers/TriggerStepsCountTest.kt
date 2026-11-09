package app.aaps.plugins.automation.triggers

import app.aaps.core.data.model.SC
import app.aaps.plugins.automation.AutomationStrings
import app.aaps.plugins.automation.asJsonObject
import app.aaps.plugins.automation.elements.Comparator
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.skyscreamer.jsonassert.JSONAssert

class TriggerStepsCountTest : TriggerTestBase() {

    @Test
    fun friendlyName() {
        assertThat(TriggerStepsCount(triggerDeps).friendlyName()).isEqualTo(AutomationStrings.triggerStepsCountLabel)
    }

    @Test
    fun friendlyDescription() {
        val t = TriggerStepsCount(triggerDeps)

        assertThat(t.friendlyDescription()).isEqualTo("Steps count per 5 minutes is equal or greater than 100")
    }

    @Test
    fun duplicate() {
        val t = TriggerStepsCount(triggerDeps).apply {
            stepsCount.value = 100.0
            measurementDuration.value = "5"
            comparator.value = Comparator.Compare.IS_GREATER
        }
        val dup = t.duplicate() as TriggerStepsCount
        assertThat(dup).isNotSameInstanceAs(t)
        assertThat(dup.stepsCount.value).isWithin(0.01).of(100.0)
        assertThat(dup.measurementDuration.value).isEqualTo("5")
        assertThat(dup.comparator.value).isEqualTo(Comparator.Compare.IS_GREATER)
    }

    @Test
    fun shouldRunNotAvailable() = runTest {
        val t = TriggerStepsCount(triggerDeps).apply { comparator.value = Comparator.Compare.IS_NOT_AVAILABLE }
        assertThat(t.shouldRun()).isTrue()
        verifyNoMoreInteractions(persistenceLayer)
    }

    @Test
    fun shouldRunNoStepsAvailable() = runTest {
        val t = TriggerStepsCount(triggerDeps).apply {
            stepsCount.value = 100.0
            measurementDuration.value = "5"
            comparator.value = Comparator.Compare.IS_GREATER
        }
        whenever(persistenceLayer.getStepsCountFromTime(now - 300000L)).thenReturn(emptyList())
        assertThat(t.shouldRun()).isFalse()
        verify(persistenceLayer).getStepsCountFromTime(now - 300000L)
        verifyNoMoreInteractions(persistenceLayer)
    }

    @Test
    fun shouldRunBelowThreshold() = runTest {
        val t = TriggerStepsCount(triggerDeps).apply {
            stepsCount.value = 100.0
            measurementDuration.value = "5"
            comparator.value = Comparator.Compare.IS_GREATER
        }
        val scs = listOf(SC(duration = 300_000, timestamp = now, steps5min = 80, steps10min = 110, steps15min = 0, steps30min = 0, steps60min = 0, steps180min = 0, device = "test"))

        whenever(persistenceLayer.getStepsCountFromTime(now - 300000L)).thenReturn(scs)
        assertThat(t.shouldRun()).isFalse()
        verify(persistenceLayer).getStepsCountFromTime(now - 300000L)
        verifyNoMoreInteractions(persistenceLayer)
    }

    @Test
    fun shouldRunTrigger() = runTest {
        val t = TriggerStepsCount(triggerDeps).apply {
            stepsCount.value = 100.0
            measurementDuration.value = "5"
            comparator.value = Comparator.Compare.IS_GREATER
        }
        val scs = listOf(SC(duration = 300_000, timestamp = now, steps5min = 112, steps10min = 110, steps15min = 0, steps30min = 0, steps60min = 0, steps180min = 0, device = "test"))

        whenever(persistenceLayer.getStepsCountFromTime(now - 300000L)).thenReturn(scs)
        assertThat(t.shouldRun()).isTrue()
        verify(persistenceLayer).getStepsCountFromTime(now - 300000L)
        verifyNoMoreInteractions(persistenceLayer)
    }

    private fun tenMinutesOver100() = TriggerStepsCount(triggerDeps).apply {
        stepsCount.value = 100.0
        measurementDuration.value = "10"
        comparator.value = Comparator.Compare.IS_GREATER
    }

    private fun sc(duration: Long, timestamp: Long, steps5: Int, steps10: Int) =
        SC(duration = duration, timestamp = timestamp, steps5min = steps5, steps10min = steps10, steps15min = 0, steps30min = 0, steps60min = 0, steps180min = 0, device = "test")

    @Test
    fun oneRowWithAllWindowsIsUsedForEveryWindow() = runTest {
        // A phone step counter writes one row (duration 5 min) with all windows filled (#5499)
        whenever(persistenceLayer.getStepsCountFromTime(now - 300000L)).thenReturn(listOf(sc(300_000, now, 60, 110)))
        assertThat(tenMinutesOver100().shouldRun()).isTrue()
    }

    @Test
    fun olderWatchRowsAreReadFromTheLongestDurationInAnyOrder() = runTest {
        // An older watch app: six rows with the same time, a shorter duration leaves the longer windows at 0.
        // The database does not define their order, so the 5 min row may come last.
        val rows = listOf(
            sc(180 * 60_000L, now, 60, 110), sc(10 * 60_000L, now, 60, 110), sc(60 * 60_000L, now, 60, 110),
            sc(30 * 60_000L, now, 60, 110), sc(15 * 60_000L, now, 60, 110), sc(5 * 60_000L, now, 60, 0)
        )
        whenever(persistenceLayer.getStepsCountFromTime(now - 300000L)).thenReturn(rows)
        assertThat(tenMinutesOver100().shouldRun()).isTrue()
    }

    @Test
    fun newestReadingWins() = runTest {
        // An older reading with more steps must not be used when a newer one exists
        val rows = listOf(sc(180 * 60_000L, now - 120_000L, 60, 500), sc(180 * 60_000L, now, 60, 90))
        whenever(persistenceLayer.getStepsCountFromTime(now - 300000L)).thenReturn(rows)
        assertThat(tenMinutesOver100().shouldRun()).isFalse()
    }

    @Test
    fun toJSON() {
        val t = TriggerStepsCount(triggerDeps).apply {
            stepsCount.value = 110.0
            measurementDuration.value = "15"
            comparator.value = Comparator.Compare.IS_GREATER
        }
        assertThat(t.comparator.value).isEqualTo(Comparator.Compare.IS_GREATER)

        JSONAssert.assertEquals("""{"data":{"comparator":"IS_GREATER","stepsCount":110,"measurementDuration":"15"},"type":"TriggerStepsCount"}""", t.toJSON(), true)
    }

    @Test
    fun fromJSON() {
        val t = triggerFactory.instantiate(
            """{"data":{"comparator":"IS_GREATER","stepsCount":110,"measurementDuration":"10"},"type":"TriggerStepsCount"}""".asJsonObject()
        ) as TriggerStepsCount
        assertThat(t.comparator.value).isEqualTo(Comparator.Compare.IS_GREATER)
        assertThat(t.stepsCount.value).isWithin(0.01).of(110.0)
        assertThat(t.measurementDuration.value).isEqualTo("10")
    }
}
