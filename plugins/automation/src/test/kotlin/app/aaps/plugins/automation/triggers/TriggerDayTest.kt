package app.aaps.plugins.automation.triggers

import app.aaps.core.ui.elements.WeekDay
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

class TriggerDayTest : TriggerTestBase() {

    @Test fun shouldRunTest() {
        var t = TriggerDay(injector)
        t.days[WeekDay.DayOfWeek.MONDAY] = true
        assertThat(t.shouldRun()).isFalse()

        // scheduled 1 min before
        t = TriggerDay(injector)
        t.days[WeekDay.DayOfWeek.SUNDAY] = true
        assertThat(t.shouldRun()).isTrue()
    }

    private var dayJson =
        "{\"data\":{\"WEDNESDAY\":false,\"MONDAY\":false,\"THURSDAY\":false,\"SUNDAY\":true,\"TUESDAY\":false,\"FRIDAY\":false,\"SATURDAY\":false,\"type\":\"TriggerRecurringDay\"}"

    @Test
    fun toJSONTest() {
        val t = TriggerDay(injector)
        t.days[WeekDay.DayOfWeek.SUNDAY] = true
        JSONAssert.assertEquals(dayJson, t.toJSON(), true)
    }

    @Test
    fun fromJSONTest() {
        val t = TriggerDay(injector)
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerDay
        assertThat(t2.days.getSelectedDays()).contains(WeekDay.DayOfWeek.SUNDAY)
    }
}
