package app.aaps.plugins.automation.triggers

import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.Comparator
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.skyscreamer.jsonassert.JSONAssert

class TriggerIobTest : TriggerTestBase() {

    @BeforeEach fun mock() {
        whenever(profileFunction.getProfile()).thenReturn(validProfile)
    }

    @Test fun shouldRunTest() {
        whenever(iobCobCalculator.calculateFromTreatmentsAndTemps(ArgumentMatchers.anyLong(), anyOrNull())).thenReturn(generateIobRecordData())
        var t: TriggerIob = TriggerIob(injector).setValue(1.1).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isFalse()
        t = TriggerIob(injector).setValue(1.0).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerIob(injector).setValue(0.8).comparator(Comparator.Compare.IS_GREATER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerIob(injector).setValue(0.8).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerIob(injector).setValue(0.9).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerIob(injector).setValue(1.2).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerIob(injector).setValue(1.1).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isFalse()
        t = TriggerIob(injector).setValue(1.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerIob(injector).setValue(0.9).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.shouldRun()).isFalse()
    }

    @Test fun copyConstructorTest() {
        val t: TriggerIob = TriggerIob(injector).setValue(213.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.insulin.value).isWithin(0.01).of(213.0)
        assertThat(t.comparator.value).isEqualTo(Comparator.Compare.IS_EQUAL_OR_LESSER)
    }

    private var bgJson = "{\"data\":{\"comparator\":\"IS_EQUAL\",\"insulin\":4.1},\"type\":\"TriggerIob\"}"
    @Test fun toJSONTest() {
        val t: TriggerIob = TriggerIob(injector).setValue(4.1).comparator(Comparator.Compare.IS_EQUAL)
        JSONAssert.assertEquals(bgJson, t.toJSON(), true)
    }

    @Test
    fun fromJSONTest() {
        val t: TriggerIob = TriggerIob(injector).setValue(4.1).comparator(Comparator.Compare.IS_EQUAL)
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerIob
        assertThat(t2.comparator.value).isEqualTo(Comparator.Compare.IS_EQUAL)
        assertThat(t2.insulin.value).isWithin(0.01).of(4.1)
    }

    @Test fun iconTest() {
        assertThat(TriggerIob(injector).icon().get()).isEqualTo(R.drawable.ic_keyboard_capslock)
    }

    private fun generateIobRecordData(): IobTotal {
        val iobTotal = IobTotal(1)
        iobTotal.iob = 1.0
        return iobTotal
    }
}
