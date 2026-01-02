package app.aaps.plugins.automation.triggers

import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.ComparatorExists
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

class TriggerTempTargetTest : TriggerTestBase() {

    /*
       @Test fun shouldRunTest() {
           whenever(repository.getTemporaryTargetActiveAt(anyOrNull())).thenReturn(null)
           var t: TriggerTempTarget = TriggerTempTarget(injector).comparator(ComparatorExists.Compare.EXISTS)
           assertThat(t.shouldRun()).isFalse()
           t = TriggerTempTarget(injector).comparator(ComparatorExists.Compare.NOT_EXISTS)
           assertThat(t.shouldRun()).isTrue()
           whenever(repository.getTemporaryTargetActiveAt(anyOrNull())).thenReturn(TemporaryTarget(duration = 0, highTarget = 0.0, lowTarget = 0.0, reason = TemporaryTarget.Reason.CUSTOM, timestamp = 0))
           t = TriggerTempTarget(injector).comparator(ComparatorExists.Compare.NOT_EXISTS)
           assertThat(t.shouldRun()).isFalse()
           t = TriggerTempTarget(injector).comparator(ComparatorExists.Compare.EXISTS)
           assertThat(t.shouldRun()).isTrue()
       }
   */
    @Test fun copyConstructorTest() {
        val t: TriggerTempTarget = TriggerTempTarget(injector).comparator(ComparatorExists.Compare.NOT_EXISTS)
        val t1 = t.duplicate() as TriggerTempTarget
        assertThat(t1.comparator.value).isEqualTo(ComparatorExists.Compare.NOT_EXISTS)
    }

    private var ttJson = "{\"data\":{\"comparator\":\"EXISTS\"},\"type\":\"TriggerTempTarget\"}"
    @Test fun toJSONTest() {
        val t: TriggerTempTarget = TriggerTempTarget(injector).comparator(ComparatorExists.Compare.EXISTS)
        JSONAssert.assertEquals(ttJson, t.toJSON(), true)
    }

    @Test
    fun fromJSONTest() {
        val t: TriggerTempTarget = TriggerTempTarget(injector).comparator(ComparatorExists.Compare.NOT_EXISTS)
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerTempTarget
        assertThat(t2.comparator.value).isEqualTo(ComparatorExists.Compare.NOT_EXISTS)
    }

    @Test fun iconTest() {
        assertThat(TriggerTempTarget(injector).icon().get()).isEqualTo(R.drawable.ic_keyboard_tab)
    }
}
