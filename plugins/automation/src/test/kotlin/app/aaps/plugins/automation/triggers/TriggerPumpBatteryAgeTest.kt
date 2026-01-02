package app.aaps.plugins.automation.triggers

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.time.T
import app.aaps.plugins.automation.elements.Comparator
import app.aaps.pump.virtual.VirtualPumpPlugin
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.whenever
import org.skyscreamer.jsonassert.JSONAssert
import java.util.Optional

class TriggerPumpBatteryAgeTest : TriggerTestBase() {

    @Mock lateinit var virtualPumpPlugin: VirtualPumpPlugin

    @Test fun shouldRunTest() {
        val pumpBatteryChangeEvent = TE(glucoseUnit = GlucoseUnit.MGDL, timestamp = now - T.hours(6).msecs(), type = TE.Type.PUMP_BATTERY_CHANGE)
        whenever(persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.PUMP_BATTERY_CHANGE)).thenReturn(pumpBatteryChangeEvent)
        var t: TriggerPumpBatteryAge = TriggerPumpBatteryAge(injector).setValue(1.0).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isFalse()
        t = TriggerPumpBatteryAge(injector).setValue(6.0).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerPumpBatteryAge(injector).setValue(5.0).comparator(Comparator.Compare.IS_GREATER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerPumpBatteryAge(injector).setValue(5.0).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerPumpBatteryAge(injector).setValue(6.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerPumpBatteryAge(injector).setValue(1.0).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isFalse()
        t = TriggerPumpBatteryAge(injector).setValue(10.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerPumpBatteryAge(injector).setValue(5.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.shouldRun()).isFalse()
    }

    @Test fun shouldRunNotAvailable() {
        whenever(persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.PUMP_BATTERY_CHANGE)).thenReturn(null)
        var t = TriggerPumpBatteryAge(injector).apply { comparator.value = Comparator.Compare.IS_NOT_AVAILABLE }
        assertThat(t.shouldRun()).isTrue()
        t = TriggerPumpBatteryAge(injector).setValue(6.0).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isFalse()
    }

    @Test fun shouldRunBatteryAgeSupport() {
        val pumpBatteryChangeEvent = TE(glucoseUnit = GlucoseUnit.MGDL, timestamp = now - T.hours(6).msecs(), type = TE.Type.PUMP_BATTERY_CHANGE)
        whenever(persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.PUMP_BATTERY_CHANGE)).thenReturn(pumpBatteryChangeEvent)
        val t: TriggerPumpBatteryAge = TriggerPumpBatteryAge(injector).setValue(6.0).comparator(Comparator.Compare.IS_EQUAL)
        whenever(activePlugin.activePump).thenReturn(virtualPumpPlugin)
        val pumpDescription = PumpDescription()
        whenever(virtualPumpPlugin.pumpDescription).thenReturn(pumpDescription)

        whenever(virtualPumpPlugin.isBatteryChangeLoggingEnabled()).thenReturn(false)
        pumpDescription.isBatteryReplaceable = false
        assertThat(t.shouldRun()).isFalse()

        whenever(virtualPumpPlugin.isBatteryChangeLoggingEnabled()).thenReturn(true)
        assertThat(t.shouldRun()).isTrue()

        whenever(virtualPumpPlugin.isBatteryChangeLoggingEnabled()).thenReturn(false)
        pumpDescription.isBatteryReplaceable = true
        assertThat(t.shouldRun()).isTrue()
    }

    @Test fun copyConstructorTest() {
        val t: TriggerPumpBatteryAge = TriggerPumpBatteryAge(injector).setValue(213.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.pumpBatteryAgeHours.value).isWithin(0.01).of(213.0)
        assertThat(t.comparator.value).isEqualTo(Comparator.Compare.IS_EQUAL_OR_LESSER)
    }

    @Test fun toJSONTest() {
        val triggerJson = "{\"data\":{\"comparator\":\"IS_EQUAL\",\"pumpBatteryAgeHours\":4},\"type\":\"TriggerPumpBatteryAge\"}"
        val t: TriggerPumpBatteryAge = TriggerPumpBatteryAge(injector).setValue(4.0).comparator(Comparator.Compare.IS_EQUAL)
        JSONAssert.assertEquals(triggerJson, t.toJSON(), true)
    }

    @Test fun fromJSONTest() {
        val t: TriggerPumpBatteryAge = TriggerPumpBatteryAge(injector).setValue(4.0).comparator(Comparator.Compare.IS_EQUAL)
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerPumpBatteryAge
        assertThat(t2.comparator.value).isEqualTo(Comparator.Compare.IS_EQUAL)
        assertThat(t2.pumpBatteryAgeHours.value).isWithin(0.01).of(4.0)
    }

    @Test fun iconTest() {
        val t= TriggerPumpBatteryAge(injector)
        assertThat(t.icon()).isEqualTo(Optional.of(app.aaps.core.objects.R.drawable.ic_cp_age_battery))
    }
}
