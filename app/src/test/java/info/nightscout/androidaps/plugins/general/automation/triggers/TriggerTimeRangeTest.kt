package info.nightscout.androidaps.plugins.general.automation.triggers

import com.google.common.base.Optional
import info.nightscout.androidaps.R
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.MidnightTime
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
class TriggerTimeRangeTest : TriggerTestBase() {

    var now = 754 // in minutes from midnight
    private var timeJson = "{\"data\":{\"start\":753,\"end\":784},\"type\":\"info.nightscout.androidaps.plugins.general.automation.triggers.TriggerTimeRange\"}"

    @Before
    fun mock() {
        PowerMockito.mockStatic(DateUtil::class.java)
        val nowMills = MidnightTime.calcPlusMinutes(now)
        PowerMockito.`when`(DateUtil.now()).thenReturn(nowMills)
    }

    @Test
    fun shouldRunTest() {
        // range starts 1 min in the future
        var t: TriggerTimeRange = TriggerTimeRange(injector).period(now + 1, now + 30)
        Assert.assertEquals(false, t.shouldRun())

        // range starts 30 min back
        t = TriggerTimeRange(injector).period(now - 30, now + 30)
        Assert.assertEquals(true, t.shouldRun())

        // Period is all day long
        t = TriggerTimeRange(injector).period(1, 1440)
        Assert.assertEquals(true, t.shouldRun())
    }

    @Test
    fun toJSONTest() {
        val t: TriggerTimeRange = TriggerTimeRange(injector).period(now - 1, now + 30)
        Assert.assertEquals(timeJson, t.toJSON())
    }

    @Test
    fun fromJSONTest() {
        val t: TriggerTimeRange = TriggerTimeRange(injector).period(120, 180)
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerTimeRange
        Assert.assertEquals(now - 1, t2.period(753, 360).range.start)
        Assert.assertEquals(360, t2.period(753, 360).range.end)
    }

    @Test fun copyConstructorTest() {
        val t = TriggerTimeRange(injector)
        t.period(now, now + 30)
        val t1 = t.duplicate() as TriggerTimeRange
        Assert.assertEquals(now, t1.range.start)
    }

    @Test fun friendlyNameTest() {
        Assert.assertEquals(R.string.time_range.toLong(), TriggerTimeRange(injector).friendlyName().toLong())
    }

    @Test fun friendlyDescriptionTest() {
        Assert.assertEquals(null, TriggerTimeRange(injector).friendlyDescription()) //not mocked    }
    }

    @Test fun iconTest() {
        Assert.assertEquals(Optional.of(R.drawable.ic_access_alarm_24dp), TriggerTimeRange(injector).icon())
    }
}