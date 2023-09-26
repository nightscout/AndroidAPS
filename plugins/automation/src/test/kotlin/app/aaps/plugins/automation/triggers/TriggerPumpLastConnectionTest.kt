package app.aaps.plugins.automation.triggers

import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.Comparator
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`

class TriggerPumpLastConnectionTest : TriggerTestBase() {

    @Test
    fun shouldRunTest() {
//        System.currentTimeMillis() is always 0
//        and so is every last connection time
        Assertions.assertEquals(0L, testPumpPlugin.lastDataTime())
        `when`(dateUtil.now()).thenReturn(now + 10 * 60 * 1000) // set current time to now + 10 min
        var t = TriggerPumpLastConnection(injector).setValue(110).comparator(Comparator.Compare.IS_EQUAL)
        Assertions.assertEquals(110, t.minutesAgo.value)
        Assertions.assertEquals(Comparator.Compare.IS_EQUAL, t.comparator.value)
        Assertions.assertFalse(t.shouldRun())
        t = TriggerPumpLastConnection(injector).setValue(10).comparator(Comparator.Compare.IS_EQUAL)
        Assertions.assertEquals(10, t.minutesAgo.value)
        Assertions.assertFalse(t.shouldRun()) // 0 == 10 -> FALSE
        t = TriggerPumpLastConnection(injector).setValue(5).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        Assertions.assertTrue(t.shouldRun()) // 5 => 0 -> TRUE
        t = TriggerPumpLastConnection(injector).setValue(310).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assertions.assertFalse(t.shouldRun()) // 310 <= 0 -> FALSE
        t = TriggerPumpLastConnection(injector).setValue(420).comparator(Comparator.Compare.IS_EQUAL)
        Assertions.assertFalse(t.shouldRun()) // 420 == 0 -> FALSE
    }

    @Test fun copyConstructorTest() {
        val t: TriggerPumpLastConnection = TriggerPumpLastConnection(injector).setValue(213).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        val t1 = t.duplicate() as TriggerPumpLastConnection
        Assertions.assertEquals(213, t1.minutesAgo.value)
        Assertions.assertEquals(Comparator.Compare.IS_EQUAL_OR_LESSER, t.comparator.value)
    }

    private var lbJson = "{\"data\":{\"comparator\":\"IS_EQUAL\",\"minutesAgo\":410},\"type\":\"TriggerPumpLastConnection\"}"
    @Test fun toJSONTest() {
        val t: TriggerPumpLastConnection = TriggerPumpLastConnection(injector).setValue(410).comparator(Comparator.Compare.IS_EQUAL)
        Assertions.assertEquals(lbJson, t.toJSON())
    }

    @Test
    fun fromJSONTest() {
        val t: TriggerPumpLastConnection = TriggerPumpLastConnection(injector).setValue(410).comparator(Comparator.Compare.IS_EQUAL)
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerPumpLastConnection
        Assertions.assertEquals(Comparator.Compare.IS_EQUAL, t2.comparator.value)
        Assertions.assertEquals(410, t2.minutesAgo.value)
    }

    @Test fun iconTest() {
        assertThat(TriggerPumpLastConnection(injector).icon().get()).isEqualTo(app.aaps.core.main.R.drawable.ic_remove)
    }

    @Test fun friendlyNameTest() {
        Assertions.assertEquals(R.string.automation_trigger_pump_last_connection_label, TriggerPumpLastConnection(injector).friendlyName())
    }
}