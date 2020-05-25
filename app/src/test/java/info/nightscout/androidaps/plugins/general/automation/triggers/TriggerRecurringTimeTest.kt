package info.nightscout.androidaps.plugins.general.automation.triggers

import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.MidnightTime
import info.nightscout.androidaps.utils.T
import org.json.JSONObject
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(DateUtil::class)
class TriggerRecurringTimeTest : TriggerTestBase() {

    var now : Long = 0L

    @Before fun mock() {
        now = MidnightTime.calc() + T.mins(95).msecs() // 95 min from midnight
        PowerMockito.`when`(dateUtil._now()).thenReturn(now)
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

    private var timeJson = "{\"data\":{\"WEDNESDAY\":false,\"MONDAY\":false,\"THURSDAY\":false,\"SUNDAY\":false,\"TUESDAY\":false,\"FRIDAY\":false,\"SATURDAY\":false,\"time\":4444},\"type\":\"info.nightscout.androidaps.plugins.general.automation.triggers.TriggerRecurringTime\"}"

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