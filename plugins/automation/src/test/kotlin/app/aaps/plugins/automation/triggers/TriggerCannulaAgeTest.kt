package app.aaps.plugins.automation.triggers

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TE
import app.aaps.core.data.time.T
import app.aaps.plugins.automation.asJsonObject
import app.aaps.plugins.automation.elements.Comparator
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.skyscreamer.jsonassert.JSONAssert

class TriggerCannulaAgeTest : TriggerTestBase() {

    @Test fun shouldRunTest() = runTest {
        // Cannula age is 6
        val cannulaChangeEvent = TE(glucoseUnit = GlucoseUnit.MGDL, timestamp = now - T.hours(6).msecs(), type = TE.Type.CANNULA_CHANGE)
        whenever(persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.CANNULA_CHANGE)).thenReturn(cannulaChangeEvent)
        var t: TriggerCannulaAge = TriggerCannulaAge(triggerDeps).setValue(1.0).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isFalse()
        t = TriggerCannulaAge(triggerDeps).setValue(6.0).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerCannulaAge(triggerDeps).setValue(5.0).comparator(Comparator.Compare.IS_GREATER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerCannulaAge(triggerDeps).setValue(5.0).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerCannulaAge(triggerDeps).setValue(6.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerCannulaAge(triggerDeps).setValue(1.0).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isFalse()
        t = TriggerCannulaAge(triggerDeps).setValue(10.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerCannulaAge(triggerDeps).setValue(5.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.shouldRun()).isFalse()
    }

    @Test fun shouldRunNotAvailable() = runTest {
        whenever(persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.CANNULA_CHANGE)).thenReturn(null)
        var t = TriggerCannulaAge(triggerDeps).apply { comparator.value = Comparator.Compare.IS_NOT_AVAILABLE }
        assertThat(t.shouldRun()).isTrue()
        t = TriggerCannulaAge(triggerDeps).setValue(6.0).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isFalse()
    }

    @Test fun copyConstructorTest() {
        val t: TriggerCannulaAge = TriggerCannulaAge(triggerDeps).setValue(213.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.cannulaAgeHours.value).isWithin(0.01).of(213.0)
        assertThat(t.comparator.value).isEqualTo(Comparator.Compare.IS_EQUAL_OR_LESSER)
    }

    @Test fun toJSONTest() {
        val cannulaJson = "{\"data\":{\"comparator\":\"IS_EQUAL\",\"cannulaAgeHours\":4},\"type\":\"TriggerCannulaAge\"}"
        val t: TriggerCannulaAge = TriggerCannulaAge(triggerDeps).setValue(4.0).comparator(Comparator.Compare.IS_EQUAL)
        JSONAssert.assertEquals(cannulaJson, t.toJSON(), true)
    }

    @Test fun fromJSONTest() {
        val t: TriggerCannulaAge = TriggerCannulaAge(triggerDeps).setValue(4.0).comparator(Comparator.Compare.IS_EQUAL)
        val t2 = triggerFactory.instantiate(t.toJSON().asJsonObject()) as TriggerCannulaAge
        assertThat(t2.comparator.value).isEqualTo(Comparator.Compare.IS_EQUAL)
        assertThat(t2.cannulaAgeHours.value).isWithin(0.01).of(4.0)
    }
}
