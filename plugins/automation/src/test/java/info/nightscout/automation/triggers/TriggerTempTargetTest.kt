package info.nightscout.automation.triggers

import com.google.common.base.Optional
import info.nightscout.automation.R
import info.nightscout.automation.elements.ComparatorExists
import org.json.JSONObject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class TriggerTempTargetTest : TriggerTestBase() {

    /*
       @Test fun shouldRunTest() {
           `when`(repository.getTemporaryTargetActiveAt(anyObject())).thenReturn(null)
           var t: TriggerTempTarget = TriggerTempTarget(injector).comparator(ComparatorExists.Compare.EXISTS)
           Assertions.assertFalse(t.shouldRun())
           t = TriggerTempTarget(injector).comparator(ComparatorExists.Compare.NOT_EXISTS)
           Assertions.assertTrue(t.shouldRun())
           `when`(repository.getTemporaryTargetActiveAt(anyObject())).thenReturn(TemporaryTarget(duration = 0, highTarget = 0.0, lowTarget = 0.0, reason = TemporaryTarget.Reason.CUSTOM, timestamp = 0))
           t = TriggerTempTarget(injector).comparator(ComparatorExists.Compare.NOT_EXISTS)
           Assertions.assertFalse(t.shouldRun())
           t = TriggerTempTarget(injector).comparator(ComparatorExists.Compare.EXISTS)
           Assertions.assertTrue(t.shouldRun())
       }
   */
    @Test fun copyConstructorTest() {
        val t: TriggerTempTarget = TriggerTempTarget(injector).comparator(ComparatorExists.Compare.NOT_EXISTS)
        val t1 = t.duplicate() as TriggerTempTarget
        Assertions.assertEquals(ComparatorExists.Compare.NOT_EXISTS, t1.comparator.value)
    }

    private var ttJson = "{\"data\":{\"comparator\":\"EXISTS\"},\"type\":\"TriggerTempTarget\"}"
    @Test fun toJSONTest() {
        val t: TriggerTempTarget = TriggerTempTarget(injector).comparator(ComparatorExists.Compare.EXISTS)
        Assertions.assertEquals(ttJson, t.toJSON())
    }

    @Test
    fun fromJSONTest() {
        val t: TriggerTempTarget = TriggerTempTarget(injector).comparator(ComparatorExists.Compare.NOT_EXISTS)
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerTempTarget
        Assertions.assertEquals(ComparatorExists.Compare.NOT_EXISTS, t2.comparator.value)
    }

    @Test fun iconTest() {
        Assertions.assertEquals(Optional.of(R.drawable.ic_keyboard_tab), TriggerTempTarget(injector).icon())
    }
}