package info.nightscout.automation.triggers

import info.nightscout.interfaces.utils.MidnightTime
import info.nightscout.shared.utils.T
import org.json.JSONObject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class TriggerRecurringTimeTest : TriggerTestBase() {

    @BeforeEach fun mock() {
        now = MidnightTime.calc() + T.mins(95).msecs() // 95 min from midnight
        Mockito.`when`(dateUtil.now()).thenReturn(now)
    }

    @Test fun shouldRunTest() {

        var t: TriggerRecurringTime = TriggerRecurringTime(injector).time(89)
        t.days.setAll(true)
        Assertions.assertFalse(t.shouldRun())

        // scheduled 1 min before
        t = TriggerRecurringTime(injector).time(94)
        t.days.setAll(true)
        Assertions.assertTrue(t.shouldRun())
    }

    private var timeJson =
        "{\"data\":{\"WEDNESDAY\":false,\"MONDAY\":false,\"THURSDAY\":false,\"SUNDAY\":false,\"TUESDAY\":false,\"FRIDAY\":false,\"SATURDAY\":false,\"time\":4444},\"type\":\"TriggerRecurringTime\"}"

    @Test
    fun toJSONTest() {
        val t = TriggerRecurringTime(injector).time(4444)
        Assertions.assertEquals(timeJson, t.toJSON())
    }

    @Test
    fun fromJSONTest() {
        val t = TriggerRecurringTime(injector).time(4444)
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerRecurringTime
        Assertions.assertEquals(4444, t2.time.value)
    }
}