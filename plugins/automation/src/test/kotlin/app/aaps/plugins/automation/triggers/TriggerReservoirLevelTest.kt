package app.aaps.plugins.automation.triggers

import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.plugins.automation.elements.Comparator
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.skyscreamer.jsonassert.JSONAssert
import java.util.Optional

class TriggerReservoirLevelTest : TriggerTestBase() {

    @Test fun shouldRunTest() = runTest {
        whenever(pumpPluginWithConcentration.reservoirLevel).thenReturn(MutableStateFlow(PumpInsulin(6.0)))
        whenever(insulin.iCfg).thenReturn(someICfg)

        var t: TriggerReservoirLevel = TriggerReservoirLevel(injector).setValue(1.0).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isFalse()
        t = TriggerReservoirLevel(injector).setValue(6.0).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerReservoirLevel(injector).setValue(5.0).comparator(Comparator.Compare.IS_GREATER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerReservoirLevel(injector).setValue(5.0).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerReservoirLevel(injector).setValue(6.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerReservoirLevel(injector).setValue(1.0).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isFalse()
        t = TriggerReservoirLevel(injector).setValue(10.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerReservoirLevel(injector).setValue(5.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.shouldRun()).isFalse()
    }

    @Test fun copyConstructorTest() = runTest {
        val t: TriggerReservoirLevel = TriggerReservoirLevel(injector).setValue(213.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.reservoirLevel.value).isWithin(0.01).of(213.0)
        assertThat(t.comparator.value).isEqualTo(Comparator.Compare.IS_EQUAL_OR_LESSER)
    }

    @Test fun toJSONTest() = runTest {
        val triggerJson = "{\"data\":{\"comparator\":\"IS_EQUAL\",\"reservoirLevel\":4},\"type\":\"TriggerReservoirLevel\"}"
        val t: TriggerReservoirLevel = TriggerReservoirLevel(injector).setValue(4.0).comparator(Comparator.Compare.IS_EQUAL)
        JSONAssert.assertEquals(triggerJson, t.toJSON(), true)
    }

    @Test fun fromJSONTest() = runTest {
        val t: TriggerReservoirLevel = TriggerReservoirLevel(injector).setValue(4.0).comparator(Comparator.Compare.IS_EQUAL)
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerReservoirLevel
        assertThat(t2.comparator.value).isEqualTo(Comparator.Compare.IS_EQUAL)
        assertThat(t2.reservoirLevel.value).isWithin(0.01).of(4.0)
    }

    @Test fun iconTest() = runTest {
        val t = TriggerReservoirLevel(injector)
        assertThat(t.icon()).isEqualTo(Optional.of(app.aaps.core.objects.R.drawable.ic_cp_age_insulin))
    }
}
