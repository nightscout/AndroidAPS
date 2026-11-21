package app.aaps.plugins.automation.triggers

import app.aaps.core.data.pump.defs.PumpType
import app.aaps.plugins.automation.asJsonObject
import app.aaps.plugins.automation.elements.Comparator
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doCallRealMethod
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.skyscreamer.jsonassert.JSONAssert

class TriggerPumpBatteryLevelTest : TriggerTestBase() {

    @Test fun shouldRunTest() = runTest {
        whenever(pumpPluginWithConcentration.model()).thenReturn(PumpType.GENERIC_AAPS)
        doCallRealMethod().whenever(pumpPluginWithConcentration).reportsBatteryLevel()
        whenever(pumpPluginWithConcentration.batteryLevel).thenReturn(MutableStateFlow(6))
        var t: TriggerPumpBatteryLevel = TriggerPumpBatteryLevel(triggerDeps).setValue(1.0).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isFalse()
        t = TriggerPumpBatteryLevel(triggerDeps).setValue(6.0).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerPumpBatteryLevel(triggerDeps).setValue(5.0).comparator(Comparator.Compare.IS_GREATER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerPumpBatteryLevel(triggerDeps).setValue(5.0).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerPumpBatteryLevel(triggerDeps).setValue(6.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerPumpBatteryLevel(triggerDeps).setValue(1.0).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isFalse()
        t = TriggerPumpBatteryLevel(triggerDeps).setValue(10.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerPumpBatteryLevel(triggerDeps).setValue(5.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.shouldRun()).isFalse()
    }

    @Test fun shouldRunBatteryLevelSupport() = runTest {
        // The default: the pump type says whether the level is real. The do-form, because the real method calls model().
        doCallRealMethod().whenever(pumpPluginWithConcentration).reportsBatteryLevel()
        whenever(pumpPluginWithConcentration.model()).thenReturn(PumpType.GENERIC_AAPS)
        whenever(pumpPluginWithConcentration.batteryLevel).thenReturn(MutableStateFlow(6))
        val t: TriggerPumpBatteryLevel = TriggerPumpBatteryLevel(triggerDeps).setValue(6.0).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isTrue()

        // A type without a level, as a virtual pump that emulates Eros: its made-up level is not used.
        whenever(pumpPluginWithConcentration.model()).thenReturn(PumpType.OMNIPOD_EROS)
        assertThat(t.shouldRun()).isFalse()

        // The Eros driver with the RileyLink level turned on.
        doReturn(true).whenever(pumpPluginWithConcentration).reportsBatteryLevel()
        assertThat(t.shouldRun()).isTrue()

        doCallRealMethod().whenever(pumpPluginWithConcentration).reportsBatteryLevel()
        whenever(pumpPluginWithConcentration.model()).thenReturn(PumpType.OMNIPOD_DASH)
        whenever(pumpPluginWithConcentration.batteryLevel).thenReturn(MutableStateFlow(0))
        assertThat(t.shouldRun()).isFalse()
    }

    @Test fun copyConstructorTest() = runTest {
        val t: TriggerPumpBatteryLevel = TriggerPumpBatteryLevel(triggerDeps).setValue(213.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.pumpBatteryLevel.value).isWithin(0.01).of(213.0)
        assertThat(t.comparator.value).isEqualTo(Comparator.Compare.IS_EQUAL_OR_LESSER)
    }

    @Test fun toJSONTest() = runTest {
        val triggerJson = "{\"data\":{\"comparator\":\"IS_EQUAL\",\"pumpBatteryLevel\":4},\"type\":\"TriggerPumpBatteryLevel\"}"
        val t: TriggerPumpBatteryLevel = TriggerPumpBatteryLevel(triggerDeps).setValue(4.0).comparator(Comparator.Compare.IS_EQUAL)
        JSONAssert.assertEquals(triggerJson, t.toJSON(), true)
    }

    @Test fun fromJSONTest() = runTest {
        val t: TriggerPumpBatteryLevel = TriggerPumpBatteryLevel(triggerDeps).setValue(4.0).comparator(Comparator.Compare.IS_EQUAL)
        val t2 = triggerFactory.instantiate(t.toJSON().asJsonObject()) as TriggerPumpBatteryLevel
        assertThat(t2.comparator.value).isEqualTo(Comparator.Compare.IS_EQUAL)
        assertThat(t2.pumpBatteryLevel.value).isWithin(0.01).of(4.0)
    }
}
