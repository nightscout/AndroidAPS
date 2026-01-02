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

class TriggerCannulaAgeTest : TriggerTestBase() {

    @Mock lateinit var virtualPumpPlugin: VirtualPumpPlugin

    @Test fun shouldRunTest() {
        // Cannula age is 6
        val cannulaChangeEvent = TE(glucoseUnit = GlucoseUnit.MGDL, timestamp = now - T.hours(6).msecs(), type = TE.Type.CANNULA_CHANGE)
        whenever(persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.CANNULA_CHANGE)).thenReturn(cannulaChangeEvent)
        var t: TriggerCannulaAge = TriggerCannulaAge(injector).setValue(1.0).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isFalse()
        t = TriggerCannulaAge(injector).setValue(6.0).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerCannulaAge(injector).setValue(5.0).comparator(Comparator.Compare.IS_GREATER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerCannulaAge(injector).setValue(5.0).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerCannulaAge(injector).setValue(6.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerCannulaAge(injector).setValue(1.0).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isFalse()
        t = TriggerCannulaAge(injector).setValue(10.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerCannulaAge(injector).setValue(5.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.shouldRun()).isFalse()
    }

    @Test fun shouldRunNotAvailable() {
        whenever(persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.CANNULA_CHANGE)).thenReturn(null)
        var t = TriggerCannulaAge(injector).apply { comparator.value = Comparator.Compare.IS_NOT_AVAILABLE }
        assertThat(t.shouldRun()).isTrue()
        t = TriggerCannulaAge(injector).setValue(6.0).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isFalse()
    }

    @Test fun copyConstructorTest() {
        val t: TriggerCannulaAge = TriggerCannulaAge(injector).setValue(213.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.cannulaAgeHours.value).isWithin(0.01).of(213.0)
        assertThat(t.comparator.value).isEqualTo(Comparator.Compare.IS_EQUAL_OR_LESSER)
    }

    @Test fun toJSONTest() {
        val cannulaJson = "{\"data\":{\"comparator\":\"IS_EQUAL\",\"cannulaAgeHours\":4},\"type\":\"TriggerCannulaAge\"}"
        val t: TriggerCannulaAge = TriggerCannulaAge(injector).setValue(4.0).comparator(Comparator.Compare.IS_EQUAL)
        JSONAssert.assertEquals(cannulaJson, t.toJSON(), true)
    }

    @Test fun fromJSONTest() {
        val t: TriggerCannulaAge = TriggerCannulaAge(injector).setValue(4.0).comparator(Comparator.Compare.IS_EQUAL)
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerCannulaAge
        assertThat(t2.comparator.value).isEqualTo(Comparator.Compare.IS_EQUAL)
        assertThat(t2.cannulaAgeHours.value).isWithin(0.01).of(4.0)
    }

    @Test fun iconTest() {
        val t = TriggerCannulaAge(injector)
        whenever(activePlugin.activePump).thenReturn(virtualPumpPlugin)
        val pumpDescription = PumpDescription()
        pumpDescription.isPatchPump = false
        whenever(virtualPumpPlugin.pumpDescription).thenReturn(pumpDescription)
        assertThat(t.icon()).isEqualTo(Optional.of(app.aaps.core.objects.R.drawable.ic_cp_age_cannula))
        pumpDescription.isPatchPump = true
        assertThat(t.icon()).isEqualTo(Optional.of(app.aaps.core.objects.R.drawable.ic_patch_pump_outline))
    }
}
