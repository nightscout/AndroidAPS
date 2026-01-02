package app.aaps.plugins.automation.triggers

import app.aaps.core.data.time.T
import app.aaps.plugins.automation.R
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.skyscreamer.jsonassert.JSONAssert

class TriggerTimeTest : TriggerTestBase() {

    fun mock() {
    }

    @Test
    fun shouldRunTest() {
        whenever(rh.gs(R.string.atspecifiedtime)).thenReturn("At %1\$s")

        // scheduled 1 min before
        var t: TriggerTime = TriggerTime(injector).runAt(now - T.mins(1).msecs())
        assertThat(t.shouldRun()).isTrue()

        // scheduled 1 min in the future
        t = TriggerTime(injector).runAt(now + T.mins(1).msecs())
        assertThat(t.shouldRun()).isFalse()
    }

    private var timeJson = "{\"data\":{\"runAt\":1656358762000},\"type\":\"TriggerTime\"}"

    @Test
    fun toJSONTest() {
        val t: TriggerTime = TriggerTime(injector).runAt(now - T.mins(1).msecs())
        JSONAssert.assertEquals(timeJson, t.toJSON(), true)
    }

    @Test
    fun fromJSONTest() {
        val t: TriggerTime = TriggerTime(injector).runAt(now - T.mins(1).msecs())
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerTime
        assertThat(t2.time.value).isEqualTo(now - T.mins(1).msecs())
    }

    @Test
    fun copyConstructorTest() {
        val t = TriggerTime(injector)
        t.runAt(now)
        val t1 = t.duplicate() as TriggerTime
        assertThat(t1.time.value).isEqualTo(now)
    }

    @Test
    fun friendlyNameTest() {
        assertThat(TriggerTime(injector).friendlyName()).isEqualTo(app.aaps.core.ui.R.string.time)
    }

    @Test
    fun friendlyDescriptionTest() {
        whenever(rh.gs(R.string.atspecifiedtime)).thenReturn("At %1\$s")
        assertThat(TriggerTime(injector).friendlyDescription()).startsWith("At ")
    }

    @Test
    fun iconTest() {
        assertThat(TriggerTime(injector).icon().get()).isEqualTo(app.aaps.core.objects.R.drawable.ic_access_alarm_24dp)
    }
}
