package app.aaps.plugins.automation.triggers

import app.aaps.database.entities.HeartRate
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.Comparator
import io.reactivex.rxjava3.core.Single
import org.json.JSONObject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`

class TriggerHeartRateTest : TriggerTestBase() {

    @Test
    fun friendlyName() {
        Assertions.assertEquals(R.string.triggerHeartRate, TriggerHeartRate(injector).friendlyName())
    }

    @Test
    fun friendlyDescription() {
        val t = TriggerHeartRate(injector)
        `when`(rh.gs(Comparator.Compare.IS_EQUAL_OR_GREATER.stringRes)).thenReturn(">")
        `when`(rh.gs(R.string.triggerHeartRateDesc, ">", 80.0)).thenReturn("test")
        Assertions.assertEquals("test", t.friendlyDescription())
    }

    @Test
    fun duplicate() {
        val t = TriggerHeartRate(injector).apply {
            heartRate.value = 100.0
            comparator.value = Comparator.Compare.IS_GREATER
        }
        val dup = t.duplicate() as TriggerHeartRate
        Assertions.assertNotSame(t, dup)
        Assertions.assertEquals(100.0, dup.heartRate.value, 0.01)
        Assertions.assertEquals(Comparator.Compare.IS_GREATER, dup.comparator.value)

    }

    @Test
    fun shouldRunNotAvailable() {
        val t = TriggerHeartRate(injector).apply { comparator.value = Comparator.Compare.IS_NOT_AVAILABLE }
        Assertions.assertTrue(t.shouldRun())
        verifyNoMoreInteractions(repository)
    }

    @Test
    fun shouldRunNoHeartRate() {
        val t = TriggerHeartRate(injector).apply {
            heartRate.value = 100.0
            comparator.value = Comparator.Compare.IS_GREATER
        }
        `when`(repository.getHeartRatesFromTime(now - t.averageHeartRateDurationMillis)).thenReturn(Single.just(emptyList()))
        Assertions.assertFalse(t.shouldRun())
        verify(repository).getHeartRatesFromTime(now - t.averageHeartRateDurationMillis)
        verifyNoMoreInteractions(repository)
    }

    @Test
    fun shouldRunBelowThreshold() {
        val t = TriggerHeartRate(injector).apply {
            heartRate.value = 100.0
            comparator.value = Comparator.Compare.IS_GREATER
        }
        val hrs = listOf(
            HeartRate(duration = 300_000, timestamp = now - 300_000, beatsPerMinute = 80.0, device = "test"),
            HeartRate(duration = 300_000, timestamp = now, beatsPerMinute = 60.0, device = "test"),
        )
        `when`(repository.getHeartRatesFromTime(now - t.averageHeartRateDurationMillis)).thenReturn(Single.just(hrs))
        Assertions.assertFalse(t.shouldRun())
        verify(repository).getHeartRatesFromTime(now - t.averageHeartRateDurationMillis)
        verifyNoMoreInteractions(repository)
    }

    @Test
    fun shouldRunTrigger() {
        val t = TriggerHeartRate(injector).apply {
            heartRate.value = 100.0
            comparator.value = Comparator.Compare.IS_GREATER
        }
        val hrs = listOf(
            HeartRate(duration = 300_000, timestamp = now, beatsPerMinute = 120.0, device = "test"),
        )
        `when`(repository.getHeartRatesFromTime(now - t.averageHeartRateDurationMillis)).thenReturn(Single.just(hrs))
        Assertions.assertTrue(t.shouldRun())
        verify(repository).getHeartRatesFromTime(now - t.averageHeartRateDurationMillis)
        verifyNoMoreInteractions(repository)
    }

    @Test
    fun toJSON() {
        val t = TriggerHeartRate(injector).apply {
            heartRate.value = 100.0
            comparator.value = Comparator.Compare.IS_GREATER
        }
        Assertions.assertEquals(Comparator.Compare.IS_GREATER, t.comparator.value)

        Assertions.assertEquals("""{"data":{"comparator":"IS_GREATER","heartRate":100},"type":"TriggerHeartRate"}""".trimMargin(), t.toJSON())
    }

    @Test
    fun fromJSON() {
        val t = TriggerDummy(injector).instantiate(
            JSONObject(
                """{"data":{"comparator":"IS_GREATER","heartRate":100},"type":"TriggerHeartRate"}"""
            )
        ) as TriggerHeartRate
        Assertions.assertEquals(Comparator.Compare.IS_GREATER, t.comparator.value)
        Assertions.assertEquals(100.0, t.heartRate.value, 0.01)
    }
}