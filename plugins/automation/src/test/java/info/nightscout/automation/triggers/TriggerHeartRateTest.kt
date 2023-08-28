package info.nightscout.automation.triggers

import info.nightscout.automation.R
import info.nightscout.automation.elements.Comparator
import info.nightscout.database.entities.HeartRate
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`

class TriggerHeartRateTest : TriggerTestBase() {

    private var now = 1000L

    @BeforeEach
    fun mock() {
        `when`(dateUtil.now()).thenReturn(now)
    }

    @Test
    fun friendlyName() {
        assertEquals(R.string.triggerHeartRate, TriggerHeartRate(injector).friendlyName())
    }

    @Test
    fun friendlyDescription() {
        val t = TriggerHeartRate(injector)
        `when`(rh.gs(Comparator.Compare.IS_EQUAL_OR_GREATER.stringRes)).thenReturn(">")
        `when`(rh.gs(R.string.triggerHeartRateDesc, ">", 80.0)).thenReturn("test")
        assertEquals("test", t.friendlyDescription())
    }

    @Test
    fun duplicate() {
        val t = TriggerHeartRate(injector).apply {
            heartRate.value = 100.0
            comparator.value = Comparator.Compare.IS_GREATER
        }
        val dup = t.duplicate() as TriggerHeartRate
        assertNotSame(t, dup)
        assertEquals(100.0, dup.heartRate.value, 0.01)
        assertEquals(Comparator.Compare.IS_GREATER, dup.comparator.value)

    }

    @Test
    fun shouldRunNotAvailable() {
        val t = TriggerHeartRate(injector).apply { comparator.value = Comparator.Compare.IS_NOT_AVAILABLE }
        assertTrue(t.shouldRun())
        verifyNoMoreInteractions(repository)
    }

    @Test
    fun shouldRunNoHeartRate() {
        val t = TriggerHeartRate(injector).apply {
            heartRate.value = 100.0
            comparator.value = Comparator.Compare.IS_GREATER
        }
        `when`(repository.getHeartRatesFromTime(now - t.averageHeartRateDurationMillis)).thenReturn(emptyList())
        assertFalse(t.shouldRun())
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
        `when`(repository.getHeartRatesFromTime(now - t.averageHeartRateDurationMillis)).thenReturn(hrs)
        assertFalse(t.shouldRun())
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
        `when`(repository.getHeartRatesFromTime(now - t.averageHeartRateDurationMillis)).thenReturn(hrs)
        assertTrue(t.shouldRun())
        verify(repository).getHeartRatesFromTime(now - t.averageHeartRateDurationMillis)
        verifyNoMoreInteractions(repository)
    }

    @Test
    fun toJSON() {
        val t = TriggerHeartRate(injector).apply {
            heartRate.value = 100.0
            comparator.value = Comparator.Compare.IS_GREATER
        }
        assertEquals(Comparator.Compare.IS_GREATER, t.comparator.value)

        assertEquals(
            """{"data":{"comparator":"IS_GREATER","heartRate":100},"type":"TriggerHeartRate"}""".trimMargin(),
            t.toJSON()
        )
    }

    @Test
    fun fromJSON() {
        val t = TriggerDummy(injector).instantiate(
            JSONObject(
                """{"data":{"comparator":"IS_GREATER","heartRate":100},"type":"TriggerHeartRate"}"""
            )
        ) as TriggerHeartRate
        assertEquals(Comparator.Compare.IS_GREATER, t.comparator.value)
        assertEquals(100.0, t.heartRate.value, 0.01)
    }
}