package info.nightscout.automation.triggers

import com.google.common.base.Optional
import info.nightscout.automation.R
import info.nightscout.automation.elements.Comparator
import org.json.JSONObject
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`

class TriggerPumpLastConnectionTest : TriggerTestBase() {

    var now = 1514766900000L

    @BeforeEach
    fun mock() {
        `when`(dateUtil.now()).thenReturn(now)
    }

    @Test
    fun shouldRunTest() {
//        System.currentTimeMillis() is always 0
//        and so is every last connection time
        Assert.assertEquals(0L, testPumpPlugin.lastDataTime())
        `when`(dateUtil.now()).thenReturn(now + 10 * 60 * 1000) // set current time to now + 10 min
        var t = TriggerPumpLastConnection(injector).setValue(110).comparator(Comparator.Compare.IS_EQUAL)
        Assert.assertEquals(110, t.minutesAgo.value)
        Assert.assertEquals(Comparator.Compare.IS_EQUAL, t.comparator.value)
        Assert.assertFalse(t.shouldRun())
        t = TriggerPumpLastConnection(injector).setValue(10).comparator(Comparator.Compare.IS_EQUAL)
        Assert.assertEquals(10, t.minutesAgo.value)
        Assert.assertFalse(t.shouldRun()) // 0 == 10 -> FALSE
        t = TriggerPumpLastConnection(injector).setValue(5).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        Assert.assertTrue(t.shouldRun()) // 5 => 0 -> TRUE
        t = TriggerPumpLastConnection(injector).setValue(310).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assert.assertFalse(t.shouldRun()) // 310 <= 0 -> FALSE
        t = TriggerPumpLastConnection(injector).setValue(420).comparator(Comparator.Compare.IS_EQUAL)
        Assert.assertFalse(t.shouldRun()) // 420 == 0 -> FALSE
    }

    @Test fun copyConstructorTest() {
        val t: TriggerPumpLastConnection = TriggerPumpLastConnection(injector).setValue(213).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        val t1 = t.duplicate() as TriggerPumpLastConnection
        Assert.assertEquals(213, t1.minutesAgo.value)
        Assert.assertEquals(Comparator.Compare.IS_EQUAL_OR_LESSER, t.comparator.value)
    }

    private var lbJson = "{\"data\":{\"comparator\":\"IS_EQUAL\",\"minutesAgo\":410},\"type\":\"TriggerPumpLastConnection\"}"
    @Test fun toJSONTest() {
        val t: TriggerPumpLastConnection = TriggerPumpLastConnection(injector).setValue(410).comparator(Comparator.Compare.IS_EQUAL)
        Assert.assertEquals(lbJson, t.toJSON())
    }

    @Test
    fun fromJSONTest() {
        val t: TriggerPumpLastConnection = TriggerPumpLastConnection(injector).setValue(410).comparator(Comparator.Compare.IS_EQUAL)
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerPumpLastConnection
        Assert.assertEquals(Comparator.Compare.IS_EQUAL, t2.comparator.value)
        Assert.assertEquals(410, t2.minutesAgo.value)
    }

    @Test fun iconTest() {
        Assert.assertEquals(Optional.of(info.nightscout.core.main.R.drawable.ic_remove), TriggerPumpLastConnection(injector).icon())
    }

    @Test fun friendlyNameTest() {
        Assert.assertEquals(R.string.automation_trigger_pump_last_connection_label, TriggerPumpLastConnection(injector).friendlyName())
    }
}