package info.nightscout.automation.triggers

import info.nightscout.interfaces.utils.MidnightTime
import info.nightscout.shared.utils.T
import org.json.JSONObject
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`

class TriggerRecurringTimeTest : TriggerTestBase() {

    var now: Long = 0L

    @BeforeEach fun mock() {
        now = MidnightTime.calc() + T.mins(95).msecs() // 95 min from midnight
        `when`(dateUtil.now()).thenReturn(now)
    }

    @Test fun shouldRunTest() {

        var t: TriggerRecurringTime = TriggerRecurringTime(injector).time(89)
        t.days.setAll(true)
        Assert.assertFalse(t.shouldRun())

        // scheduled 1 min before
        t = TriggerRecurringTime(injector).time(94)
        t.days.setAll(true)
        Assert.assertTrue(t.shouldRun())
    }

    private var timeJson =
        "{\"data\":{\"WEDNESDAY\":false,\"MONDAY\":false,\"THURSDAY\":false,\"SUNDAY\":false,\"TUESDAY\":false,\"FRIDAY\":false,\"SATURDAY\":false,\"time\":4444},\"type\":\"TriggerRecurringTime\"}"

    @Test
    fun toJSONTest() {
        val t = TriggerRecurringTime(injector).time(4444)
        Assert.assertEquals(timeJson, t.toJSON())
    }

    @Test
    fun fromJSONTest() {
        val t = TriggerRecurringTime(injector).time(4444)
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerRecurringTime
        Assert.assertEquals(4444, t2.time.value)
    }
}