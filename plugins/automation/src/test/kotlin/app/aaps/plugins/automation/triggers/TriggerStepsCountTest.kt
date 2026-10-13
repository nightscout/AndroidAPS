package app.aaps.plugins.automation.triggers

import app.aaps.core.data.model.SC
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.Comparator
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.skyscreamer.jsonassert.JSONAssert

class TriggerStepsCountTest : TriggerTestBase() {

    @Test
    fun friendlyName() {
        assertThat(TriggerStepsCount(injector).friendlyName()).isEqualTo(R.string.triggerStepsCountLabel)
    }

    @Test
    fun friendlyDescription() {
        val t = TriggerStepsCount(injector)
        whenever(rh.gs(Comparator.Compare.IS_EQUAL_OR_GREATER.stringRes)).thenReturn(">")
        whenever(rh.gs(R.string.triggerStepsCountDesc, "5", ">", 100.0)).thenReturn("test")

        assertThat(t.friendlyDescription()).isEqualTo("test")
    }

    @Test
    fun duplicate() {
        val t = TriggerStepsCount(injector).apply {
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
    fun shouldRunNotAvailable() {
        val t = TriggerStepsCount(injector).apply { comparator.value = Comparator.Compare.IS_NOT_AVAILABLE }
        assertThat(t.shouldRun()).isTrue()
        verifyNoMoreInteractions(persistenceLayer)
    }

    @Test
    fun shouldRunNoStepsAvailable() {
        val t = TriggerStepsCount(injector).apply {
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
    fun shouldRunBelowThreshold() {
        val t = TriggerStepsCount(injector).apply {
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
    fun shouldRunTrigger() {
        val t = TriggerStepsCount(injector).apply {
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

    @Test
    fun toJSON() {
        val t = TriggerStepsCount(injector).apply {
            stepsCount.value = 110.0
            measurementDuration.value = "15"
            comparator.value = Comparator.Compare.IS_GREATER
        }
        assertThat(t.comparator.value).isEqualTo(Comparator.Compare.IS_GREATER)

        JSONAssert.assertEquals("""{"data":{"comparator":"IS_GREATER","stepsCount":110,"measurementDuration":"15"},"type":"TriggerStepsCount"}""", t.toJSON(), true)
    }

    @Test
    fun fromJSON() {
        val t = TriggerDummy(injector).instantiate(
            JSONObject(
                """{"data":{"comparator":"IS_GREATER","stepsCount":110,"measurementDuration":"10"},"type":"TriggerStepsCount"}"""
            )
        ) as TriggerStepsCount
        assertThat(t.comparator.value).isEqualTo(Comparator.Compare.IS_GREATER)
        assertThat(t.stepsCount.value).isWithin(0.01).of(110.0)
        assertThat(t.measurementDuration.value).isEqualTo("10")
    }
}
