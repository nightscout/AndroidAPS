package app.aaps.plugins.automation.triggers

import app.aaps.core.data.iob.CobInfo
import app.aaps.plugins.automation.elements.Comparator
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.skyscreamer.jsonassert.JSONAssert

class TriggerCOBTest : TriggerTestBase() {

    @Test fun shouldRunTest() {
        // COB value is 6
        `when`(iobCobCalculator.getCobInfo("AutomationTriggerCOB")).thenReturn(CobInfo(0, 6.0, 2.0))
        var t: TriggerCOB = TriggerCOB(injector).setValue(1.0).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isFalse()
        t = TriggerCOB(injector).setValue(6.0).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerCOB(injector).setValue(5.0).comparator(Comparator.Compare.IS_GREATER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerCOB(injector).setValue(5.0).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerCOB(injector).setValue(6.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerCOB(injector).setValue(1.0).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isFalse()
        t = TriggerCOB(injector).setValue(10.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerCOB(injector).setValue(5.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.shouldRun()).isFalse()
    }

    @Test fun copyConstructorTest() {
        val t: TriggerCOB = TriggerCOB(injector).setValue(213.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.cob.value).isWithin(0.01).of(213.0)
        assertThat(t.comparator.value).isEqualTo(Comparator.Compare.IS_EQUAL_OR_LESSER)
    }

    private var bgJson = "{\"data\":{\"comparator\":\"IS_EQUAL\",\"carbs\":4},\"type\":\"TriggerCOB\"}"
    @Test fun toJSONTest() {
        val t: TriggerCOB = TriggerCOB(injector).setValue(4.0).comparator(Comparator.Compare.IS_EQUAL)
        JSONAssert.assertEquals(bgJson, t.toJSON(), true)
    }

    @Test
    fun fromJSONTest() {
        val t: TriggerCOB = TriggerCOB(injector).setValue(4.0).comparator(Comparator.Compare.IS_EQUAL)
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerCOB
        assertThat(t2.comparator.value).isEqualTo(Comparator.Compare.IS_EQUAL)
        assertThat(t2.cob.value).isWithin(0.01).of(4.0)
    }

    @Test fun iconTest() {
        assertThat(TriggerCOB(injector).icon().get()).isEqualTo(app.aaps.core.objects.R.drawable.ic_cp_bolus_carbs)
    }
}
