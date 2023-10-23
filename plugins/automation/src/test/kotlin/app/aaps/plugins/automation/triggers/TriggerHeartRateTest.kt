package app.aaps.plugins.automation.triggers

import app.aaps.database.entities.HeartRate
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.Comparator
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Single
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`
import org.skyscreamer.jsonassert.JSONAssert

class TriggerHeartRateTest : TriggerTestBase() {

    @Test
    fun friendlyName() {
        assertThat(TriggerHeartRate(injector).friendlyName()).isEqualTo(R.string.triggerHeartRate)
    }

    @Test
    fun friendlyDescription() {
        val t = TriggerHeartRate(injector)
        `when`(rh.gs(Comparator.Compare.IS_EQUAL_OR_GREATER.stringRes)).thenReturn(">")
        `when`(rh.gs(R.string.triggerHeartRateDesc, ">", 80.0)).thenReturn("test")
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
        verifyNoMoreInteractions(repository)
    }

    @Test
    fun shouldRunNoHeartRate() {
        val t = TriggerHeartRate(injector).apply {
            heartRate.value = 100.0
            comparator.value = Comparator.Compare.IS_GREATER
        }
        `when`(repository.getHeartRatesFromTime(now - t.averageHeartRateDurationMillis)).thenReturn(Single.just(emptyList()))
        assertThat(t.shouldRun()).isFalse()
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
        assertThat(t.shouldRun()).isFalse()
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
        assertThat(t.shouldRun()).isTrue()
        verify(repository).getHeartRatesFromTime(now - t.averageHeartRateDurationMillis)
        verifyNoMoreInteractions(repository)
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
