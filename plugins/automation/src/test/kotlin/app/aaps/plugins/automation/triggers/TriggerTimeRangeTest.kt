package app.aaps.plugins.automation.triggers

import app.aaps.core.interfaces.utils.MidnightTime
import app.aaps.plugins.automation.R
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`

class TriggerTimeRangeTest : TriggerTestBase() {

    private var timeJson = "{\"data\":{\"start\":753,\"end\":784},\"type\":\"TriggerTimeRange\"}"

    @BeforeEach
    fun mock() {
        now = 754 // in minutes from midnight
        val nowMills = MidnightTime.calcPlusMinutes(now.toInt())
        `when`(dateUtil.now()).thenReturn(nowMills)
        `when`(rh.gs(R.string.timerange_value)).thenReturn("Time is between %1\$s and %2\$s")
    }

    @Test
    fun shouldRunTest() {
        // range starts 1 min in the future
        var t: TriggerTimeRange = TriggerTimeRange(injector).period((now + 1).toInt(), (now + 30).toInt())
        Assertions.assertEquals(false, t.shouldRun())

        // range starts 30 min back
        t = TriggerTimeRange(injector).period((now - 30).toInt(), (now + 30).toInt())
        Assertions.assertEquals(true, t.shouldRun())

        // Period is all day long
        t = TriggerTimeRange(injector).period(1, 1440)
        Assertions.assertEquals(true, t.shouldRun())
    }

    @Test
    fun toJSONTest() {
        val t: TriggerTimeRange = TriggerTimeRange(injector).period((now - 1).toInt(), (now + 30).toInt())
        Assertions.assertEquals(timeJson, t.toJSON())
    }

    @Test
    fun fromJSONTest() {
        val t: TriggerTimeRange = TriggerTimeRange(injector).period(120, 180)
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerTimeRange
        Assertions.assertEquals((now - 1).toInt(), t2.period(753, 360).range.start)
        Assertions.assertEquals(360, t2.period(753, 360).range.end)
    }

    @Test fun copyConstructorTest() {
        val t = TriggerTimeRange(injector)
        t.period(now.toInt(), (now + 30).toInt())
        val t1 = t.duplicate() as TriggerTimeRange
        Assertions.assertEquals(now.toInt(), t1.range.start)
    }

    @Test fun friendlyNameTest() {
        Assertions.assertEquals(R.string.time_range, TriggerTimeRange(injector).friendlyName())
    }

    @Test fun friendlyDescriptionTest() {
        Assertions.assertEquals("Time is between 12:34PM and 12:34PM", TriggerTimeRange(injector).friendlyDescription())
    }

    @Test fun iconTest() {
        assertThat(TriggerTimeRange(injector).icon().get()).isEqualTo(app.aaps.core.main.R.drawable.ic_access_alarm_24dp)
    }
}