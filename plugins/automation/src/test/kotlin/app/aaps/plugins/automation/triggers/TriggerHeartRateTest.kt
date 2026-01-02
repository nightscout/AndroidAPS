package app.aaps.plugins.automation.triggers

import app.aaps.core.data.model.HR
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.Comparator
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.skyscreamer.jsonassert.JSONAssert

class TriggerHeartRateTest : TriggerTestBase() {

    @Test
    fun friendlyName() {
        assertThat(TriggerHeartRate(injector).friendlyName()).isEqualTo(R.string.triggerHeartRate)
    }

    @Test
    fun friendlyDescription() {
        val t = TriggerHeartRate(injector)
        whenever(rh.gs(Comparator.Compare.IS_EQUAL_OR_GREATER.stringRes)).thenReturn(">")
        whenever(rh.gs(R.string.triggerHeartRateDesc, ">", 80.0)).thenReturn("test")
        assertThat(t.friendlyDescription()).isEqualTo("test")
    }

    @Test
    fun duplicate() {
        val t = TriggerHeartRate(injector).apply {
            heartRate.value = 100.0
            comparator.value = Comparator.Compare.IS_GREATER
        }
        val dup = t.duplicate() as TriggerHeartRate
        assertThat(dup).isNotSameInstanceAs(t)
        assertThat(dup.heartRate.value).isWithin(0.01).of(100.0)
        assertThat(dup.comparator.value).isEqualTo(Comparator.Compare.IS_GREATER)

    }

    @Test
    fun shouldRunNotAvailable() {
        val t = TriggerHeartRate(injector).apply { comparator.value = Comparator.Compare.IS_NOT_AVAILABLE }
        assertThat(t.shouldRun()).isTrue()
        verifyNoMoreInteractions(persistenceLayer)
    }

    @Test
    fun shouldRunNoHeartRate() {
        val t = TriggerHeartRate(injector).apply {
            heartRate.value = 100.0
            comparator.value = Comparator.Compare.IS_GREATER
        }
        whenever(persistenceLayer.getHeartRatesFromTime(now - t.averageHeartRateDurationMillis)).thenReturn(emptyList())
        assertThat(t.shouldRun()).isFalse()
        verify(persistenceLayer).getHeartRatesFromTime(now - t.averageHeartRateDurationMillis)
        verifyNoMoreInteractions(persistenceLayer)
    }

    @Test
    fun shouldRunBelowThreshold() {
        val t = TriggerHeartRate(injector).apply {
            heartRate.value = 100.0
            comparator.value = Comparator.Compare.IS_GREATER
        }
        val hrs = listOf(
            HR(duration = 300_000, timestamp = now - 300_000, beatsPerMinute = 80.0, device = "test"),
            HR(duration = 300_000, timestamp = now, beatsPerMinute = 60.0, device = "test"),
        )
        whenever(persistenceLayer.getHeartRatesFromTime(now - t.averageHeartRateDurationMillis)).thenReturn(hrs)
        assertThat(t.shouldRun()).isFalse()
        verify(persistenceLayer).getHeartRatesFromTime(now - t.averageHeartRateDurationMillis)
        verifyNoMoreInteractions(persistenceLayer)
    }

    @Test
    fun shouldRunTrigger() {
        val t = TriggerHeartRate(injector).apply {
            heartRate.value = 100.0
            comparator.value = Comparator.Compare.IS_GREATER
        }
        val hrs = listOf(
            HR(duration = 300_000, timestamp = now, beatsPerMinute = 120.0, device = "test"),
        )
        whenever(persistenceLayer.getHeartRatesFromTime(now - t.averageHeartRateDurationMillis)).thenReturn(hrs)
        assertThat(t.shouldRun()).isTrue()
        verify(persistenceLayer).getHeartRatesFromTime(now - t.averageHeartRateDurationMillis)
        verifyNoMoreInteractions(persistenceLayer)
    }

    @Test
    fun toJSON() {
        val t = TriggerHeartRate(injector).apply {
            heartRate.value = 100.0
            comparator.value = Comparator.Compare.IS_GREATER
        }
        assertThat(t.comparator.value).isEqualTo(Comparator.Compare.IS_GREATER)

        JSONAssert.assertEquals("""{"data":{"comparator":"IS_GREATER","heartRate":100},"type":"TriggerHeartRate"}""", t.toJSON(), true)
    }

    @Test
    fun fromJSON() {
        val t = TriggerDummy(injector).instantiate(
            JSONObject(
                """{"data":{"comparator":"IS_GREATER","heartRate":100},"type":"TriggerHeartRate"}"""
            )
        ) as TriggerHeartRate
        assertThat(t.comparator.value).isEqualTo(Comparator.Compare.IS_GREATER)
        assertThat(t.heartRate.value).isWithin(0.01).of(100.0)
    }
}
