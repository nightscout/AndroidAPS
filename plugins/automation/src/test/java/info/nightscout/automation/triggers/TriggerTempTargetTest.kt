package info.nightscout.automation.triggers

import com.google.common.base.Optional
import info.nightscout.automation.R
import info.nightscout.automation.elements.ComparatorExists
import org.json.JSONObject
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`

class TriggerTempTargetTest : TriggerTestBase() {

    var now = 1514766900000L

    @BeforeEach fun mock() {
        `when`(dateUtil.now()).thenReturn(now)
    }

    /*
        @Test fun shouldRunTest() {
            `when`(repository.getTemporaryTargetActiveAt(anyObject())).thenReturn(null)
            var t: TriggerTempTarget = TriggerTempTarget(injector).comparator(ComparatorExists.Compare.EXISTS)
            Assert.assertFalse(t.shouldRun())
            t = TriggerTempTarget(injector).comparator(ComparatorExists.Compare.NOT_EXISTS)
            Assert.assertTrue(t.shouldRun())
            `when`(repository.getTemporaryTargetActiveAt(anyObject())).thenReturn(TemporaryTarget(duration = 0, highTarget = 0.0, lowTarget = 0.0, reason = TemporaryTarget.Reason.CUSTOM, timestamp = 0))
            t = TriggerTempTarget(injector).comparator(ComparatorExists.Compare.NOT_EXISTS)
            Assert.assertFalse(t.shouldRun())
            t = TriggerTempTarget(injector).comparator(ComparatorExists.Compare.EXISTS)
            Assert.assertTrue(t.shouldRun())
        }
    */
    @Test fun copyConstructorTest() {
        val t: TriggerTempTarget = TriggerTempTarget(injector).comparator(ComparatorExists.Compare.NOT_EXISTS)
        val t1 = t.duplicate() as TriggerTempTarget
        Assert.assertEquals(ComparatorExists.Compare.NOT_EXISTS, t1.comparator.value)
    }

    private var ttJson = "{\"data\":{\"comparator\":\"EXISTS\"},\"type\":\"TriggerTempTarget\"}"
    @Test fun toJSONTest() {
        val t: TriggerTempTarget = TriggerTempTarget(injector).comparator(ComparatorExists.Compare.EXISTS)
        Assert.assertEquals(ttJson, t.toJSON())
    }

    @Test
    fun fromJSONTest() {
        val t: TriggerTempTarget = TriggerTempTarget(injector).comparator(ComparatorExists.Compare.NOT_EXISTS)
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerTempTarget
        Assert.assertEquals(ComparatorExists.Compare.NOT_EXISTS, t2.comparator.value)
    }

    @Test fun iconTest() {
        Assert.assertEquals(Optional.of(R.drawable.ic_keyboard_tab), TriggerTempTarget(injector).icon())
    }
}