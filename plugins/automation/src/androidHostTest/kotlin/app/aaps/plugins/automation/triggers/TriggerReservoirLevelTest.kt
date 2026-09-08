package app.aaps.plugins.automation.triggers

import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.plugins.automation.asJsonObject
import app.aaps.plugins.automation.elements.Comparator
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.skyscreamer.jsonassert.JSONAssert

class TriggerReservoirLevelTest : TriggerTestBase() {

    /** The reservoir reading is converted with the in-force insulin's concentration. */
    private suspend fun givenRunningInsulin() {
        whenever(profileFunction.getRunningOrRequestedICfg()).thenReturn(someICfg)
    }

    @Test fun shouldNotRunWithoutInsulinInUse() = runTest {
        whenever(pumpPluginWithConcentration.reservoirLevel).thenReturn(MutableStateFlow(PumpInsulin(6.0)))
        whenever(profileFunction.getRunningOrRequestedICfg()).thenReturn(null)

        // No insulin in force → no correct unit conversion, so the trigger must not fire rather than guess.
        val t = TriggerReservoirLevel(triggerDeps).setValue(6.0).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isFalse()
    }

    @Test fun shouldRunTest() = runTest {
        whenever(pumpPluginWithConcentration.reservoirLevel).thenReturn(MutableStateFlow(PumpInsulin(6.0)))
        givenRunningInsulin()

        var t: TriggerReservoirLevel = TriggerReservoirLevel(triggerDeps).setValue(1.0).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isFalse()
        t = TriggerReservoirLevel(triggerDeps).setValue(6.0).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerReservoirLevel(triggerDeps).setValue(5.0).comparator(Comparator.Compare.IS_GREATER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerReservoirLevel(triggerDeps).setValue(5.0).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerReservoirLevel(triggerDeps).setValue(6.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerReservoirLevel(triggerDeps).setValue(1.0).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isFalse()
        t = TriggerReservoirLevel(triggerDeps).setValue(10.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerReservoirLevel(triggerDeps).setValue(5.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.shouldRun()).isFalse()
    }

    @Test fun copyConstructorTest() = runTest {
        val t: TriggerReservoirLevel = TriggerReservoirLevel(triggerDeps).setValue(213.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.reservoirLevel.value).isWithin(0.01).of(213.0)
        assertThat(t.comparator.value).isEqualTo(Comparator.Compare.IS_EQUAL_OR_LESSER)
    }

    @Test fun toJSONTest() = runTest {
        val triggerJson = "{\"data\":{\"comparator\":\"IS_EQUAL\",\"reservoirLevel\":4},\"type\":\"TriggerReservoirLevel\"}"
        val t: TriggerReservoirLevel = TriggerReservoirLevel(triggerDeps).setValue(4.0).comparator(Comparator.Compare.IS_EQUAL)
        JSONAssert.assertEquals(triggerJson, t.toJSON(), true)
    }

    @Test fun fromJSONTest() = runTest {
        val t: TriggerReservoirLevel = TriggerReservoirLevel(triggerDeps).setValue(4.0).comparator(Comparator.Compare.IS_EQUAL)
        val t2 = triggerFactory.instantiate(t.toJSON().asJsonObject()) as TriggerReservoirLevel
        assertThat(t2.comparator.value).isEqualTo(Comparator.Compare.IS_EQUAL)
        assertThat(t2.reservoirLevel.value).isWithin(0.01).of(4.0)
    }
}
