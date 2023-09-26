package app.aaps.plugins.automation.triggers

import app.aaps.core.interfaces.utils.T
import app.aaps.plugins.automation.R
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class TriggerTimeTest : TriggerTestBase() {

    fun mock() {
    }

    @Test
    fun shouldRunTest() {
        Mockito.`when`(rh.gs(R.string.atspecifiedtime)).thenReturn("At %1\$s")

        // scheduled 1 min before
        var t: TriggerTime = TriggerTime(injector).runAt(now - T.mins(1).msecs())
        Assertions.assertTrue(t.shouldRun())

        // scheduled 1 min in the future
        t = TriggerTime(injector).runAt(now + T.mins(1).msecs())
        Assertions.assertFalse(t.shouldRun())
    }

    private var timeJson = "{\"data\":{\"runAt\":1656358762000},\"type\":\"TriggerTime\"}"

    @Test
    fun toJSONTest() {
        val t: TriggerTime = TriggerTime(injector).runAt(now - T.mins(1).msecs())
        Assertions.assertEquals(timeJson, t.toJSON())
    }

    @Test
    fun fromJSONTest() {
        val t: TriggerTime = TriggerTime(injector).runAt(now - T.mins(1).msecs())
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerTime
        Assertions.assertEquals(now - T.mins(1).msecs(), t2.time.value)
    }

    @Test
    fun copyConstructorTest() {
        val t = TriggerTime(injector)
        t.runAt(now)
        val t1 = t.duplicate() as TriggerTime
        Assertions.assertEquals(now, t1.time.value)
    }

    @Test
    fun friendlyNameTest() {
        Assertions.assertEquals(app.aaps.core.ui.R.string.time, TriggerTime(injector).friendlyName())
    }

    @Test
    fun friendlyDescriptionTest() {
        Mockito.`when`(rh.gs(R.string.atspecifiedtime)).thenReturn("At %1\$s")
        Assertions.assertTrue(TriggerTime(injector).friendlyDescription().startsWith("At "))
    }

    @Test
    fun iconTest() {
        assertThat(TriggerTime(injector).icon().get()).isEqualTo(app.aaps.core.main.R.drawable.ic_access_alarm_24dp)
    }
}