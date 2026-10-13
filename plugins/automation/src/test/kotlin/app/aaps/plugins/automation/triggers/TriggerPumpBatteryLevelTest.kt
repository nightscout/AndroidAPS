package app.aaps.plugins.automation.triggers

import app.aaps.core.data.pump.defs.PumpType
import app.aaps.plugins.automation.elements.Comparator
import app.aaps.pump.virtual.VirtualPumpPlugin
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.whenever
import org.skyscreamer.jsonassert.JSONAssert
import java.util.Optional

class TriggerPumpBatteryLevelTest : TriggerTestBase() {

    @Mock lateinit var virtualPumpPlugin: VirtualPumpPlugin

    @Test fun shouldRunTest() {
        whenever(activePlugin.activePump).thenReturn(virtualPumpPlugin)
        whenever(virtualPumpPlugin.model()).thenReturn(PumpType.GENERIC_AAPS)
        whenever(virtualPumpPlugin.batteryLevel).thenReturn(6)
        var t: TriggerPumpBatteryLevel = TriggerPumpBatteryLevel(injector).setValue(1.0).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isFalse()
        t = TriggerPumpBatteryLevel(injector).setValue(6.0).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerPumpBatteryLevel(injector).setValue(5.0).comparator(Comparator.Compare.IS_GREATER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerPumpBatteryLevel(injector).setValue(5.0).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerPumpBatteryLevel(injector).setValue(6.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerPumpBatteryLevel(injector).setValue(1.0).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isFalse()
        t = TriggerPumpBatteryLevel(injector).setValue(10.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerPumpBatteryLevel(injector).setValue(5.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.shouldRun()).isFalse()
    }

    @Test fun shouldRunBatteryLevelSupport() {
        whenever(activePlugin.activePump).thenReturn(virtualPumpPlugin)
        whenever(virtualPumpPlugin.model()).thenReturn(PumpType.GENERIC_AAPS)
        whenever(virtualPumpPlugin.batteryLevel).thenReturn(6)
        val t: TriggerPumpBatteryLevel = TriggerPumpBatteryLevel(injector).setValue(6.0).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isTrue()

        whenever(virtualPumpPlugin.model()).thenReturn(PumpType.OMNIPOD_EROS)
        whenever(virtualPumpPlugin.isUseRileyLinkBatteryLevel()).thenReturn(true)
        whenever(virtualPumpPlugin.batteryLevel).thenReturn(6)
        assertThat(t.shouldRun()).isTrue()

        whenever(virtualPumpPlugin.model()).thenReturn(PumpType.OMNIPOD_DASH)
        whenever(virtualPumpPlugin.batteryLevel).thenReturn(0)
        assertThat(t.shouldRun()).isFalse()
    }

    @Test fun copyConstructorTest() {
        val t: TriggerPumpBatteryLevel = TriggerPumpBatteryLevel(injector).setValue(213.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.pumpBatteryLevel.value).isWithin(0.01).of(213.0)
        assertThat(t.comparator.value).isEqualTo(Comparator.Compare.IS_EQUAL_OR_LESSER)
    }

    @Test fun toJSONTest() {
        val triggerJson = "{\"data\":{\"comparator\":\"IS_EQUAL\",\"pumpBatteryLevel\":4},\"type\":\"TriggerPumpBatteryLevel\"}"
        val t: TriggerPumpBatteryLevel = TriggerPumpBatteryLevel(injector).setValue(4.0).comparator(Comparator.Compare.IS_EQUAL)
        JSONAssert.assertEquals(triggerJson, t.toJSON(), true)
    }

    @Test fun fromJSONTest() {
        val t: TriggerPumpBatteryLevel = TriggerPumpBatteryLevel(injector).setValue(4.0).comparator(Comparator.Compare.IS_EQUAL)
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerPumpBatteryLevel
        assertThat(t2.comparator.value).isEqualTo(Comparator.Compare.IS_EQUAL)
        assertThat(t2.pumpBatteryLevel.value).isWithin(0.01).of(4.0)
    }

    @Test fun iconTest() {
        val t= TriggerPumpBatteryLevel(injector)
        assertThat(t.icon()).isEqualTo(Optional.of(app.aaps.core.objects.R.drawable.ic_cp_age_battery))
    }
}
