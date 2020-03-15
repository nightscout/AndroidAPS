package info.nightscout.androidaps.plugins.general.automation.triggers

import info.nightscout.androidaps.utils.DateUtil
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

    var now = 1514766900000L

    @Before fun mock() {
        PowerMockito.mockStatic(DateUtil::class.java)
        PowerMockito.`when`(DateUtil.now()).thenReturn(now)
//        val calendar = GregorianCalendar()
//        calendar.timeInMillis = now
//        PowerMockito.`when`(DateUtil.gregorianCalendar()).thenReturn(calendar)
    }

    @Test fun shouldRunTest() {

        // limit by validTo
        var t: TriggerRecurringTime = TriggerRecurringTime(injector).time(94)
        t.days.setAll(true)
        Assert.assertFalse(t.shouldRun())

        // scheduled 1 min before
//        t = new TriggerRecurringTime().hour(1).minute(34);
//        t.setAll(true);
//        Assert.assertTrue(t.shouldRun());

        // already run
        t = TriggerRecurringTime(injector).time(94)
        t.days.setAll(true)
        Assert.assertFalse(t.shouldRun())
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