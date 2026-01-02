package app.aaps.plugins.automation.triggers

import app.aaps.core.interfaces.utils.MidnightTime
import app.aaps.plugins.automation.R
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.skyscreamer.jsonassert.JSONAssert

class TriggerTimeRangeTest : TriggerTestBase() {

    private var timeJson = "{\"data\":{\"start\":753,\"end\":784},\"type\":\"TriggerTimeRange\"}"

    @BeforeEach
    fun mock() {
        now = 754 // in minutes from midnight
        val nowMills = MidnightTime.calcMidnightPlusMinutes(now.toInt())
        whenever(dateUtil.now()).thenReturn(nowMills)
        whenever(rh.gs(R.string.timerange_value)).thenReturn("Time is between %1\$s and %2\$s")
    }

    @Test
    fun shouldRunTest() {
        // range starts 1 min in the future
        var t: TriggerTimeRange = TriggerTimeRange(injector).period((now + 1).toInt(), (now + 30).toInt())
        assertThat(t.shouldRun()).isFalse()

        // range starts 30 min back
        t = TriggerTimeRange(injector).period((now - 30).toInt(), (now + 30).toInt())
        assertThat(t.shouldRun()).isTrue()

        // Period is all day long
        t = TriggerTimeRange(injector).period(1, 1440)
        assertThat(t.shouldRun()).isTrue()
    }

    @Test
    fun toJSONTest() {
        val t: TriggerTimeRange = TriggerTimeRange(injector).period((now - 1).toInt(), (now + 30).toInt())
        JSONAssert.assertEquals(timeJson, t.toJSON(), true)
    }

    @Test
    fun fromJSONTest() {
        val t: TriggerTimeRange = TriggerTimeRange(injector).period(120, 180)
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerTimeRange
        assertThat(t2.period(753, 360).range.start).isEqualTo((now - 1).toInt())
        assertThat(t2.period(753, 360).range.end).isEqualTo(360)
    }

    @Test fun copyConstructorTest() {
        val t = TriggerTimeRange(injector)
        t.period(now.toInt(), (now + 30).toInt())
        val t1 = t.duplicate() as TriggerTimeRange
        assertThat(t1.range.start).isEqualTo(now.toInt())
    }

    @Test fun friendlyNameTest() {
        assertThat(TriggerTimeRange(injector).friendlyName()).isEqualTo(R.string.time_range)
    }

    @Test fun friendlyDescriptionTest() {
        assertThat(TriggerTimeRange(injector).friendlyDescription()).isEqualTo("Time is between 12:34 PM and 12:34 PM")
    }

    @Test fun iconTest() {
        assertThat(TriggerTimeRange(injector).icon().get()).isEqualTo(app.aaps.core.objects.R.drawable.ic_access_alarm_24dp)
    }
}
