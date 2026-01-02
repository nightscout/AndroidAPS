package app.aaps.plugins.automation.triggers

import app.aaps.core.data.time.T
import app.aaps.core.interfaces.utils.MidnightTime
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.skyscreamer.jsonassert.JSONAssert

class TriggerRecurringTimeTest : TriggerTestBase() {

    @BeforeEach fun mock() {
        now = MidnightTime.calc() + T.mins(95).msecs() // 95 min from midnight
        whenever(dateUtil.now()).thenReturn(now)
    }

    @Test fun shouldRunTest() {

        var t: TriggerRecurringTime = TriggerRecurringTime(injector).time(89)
        t.days.setAll(true)
        assertThat(t.shouldRun()).isFalse()

        // scheduled 1 min before
        t = TriggerRecurringTime(injector).time(94)
        t.days.setAll(true)
        assertThat(t.shouldRun()).isTrue()
    }

    private var timeJson =
        "{\"data\":{\"WEDNESDAY\":false,\"MONDAY\":false,\"THURSDAY\":false,\"SUNDAY\":false,\"TUESDAY\":false,\"FRIDAY\":false,\"SATURDAY\":false,\"time\":4444},\"type\":\"TriggerRecurringTime\"}"

    @Test
    fun toJSONTest() {
        val t = TriggerRecurringTime(injector).time(4444)
        JSONAssert.assertEquals(timeJson, t.toJSON(), true)
    }

    @Test
    fun fromJSONTest() {
        val t = TriggerRecurringTime(injector).time(4444)
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerRecurringTime
        assertThat(t2.time.value).isEqualTo(4444)
    }
}
