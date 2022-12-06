package info.nightscout.automation.triggers

import com.google.common.base.Optional
import info.nightscout.shared.utils.T
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`

class TriggerTimeTest : TriggerTestBase() {

    var now = 1514766900000L

    @BeforeEach fun mock() {
        `when`(dateUtil.now()).thenReturn(now)
    }

    @Test fun shouldRunTest() {

        // scheduled 1 min before
        var t: TriggerTime = TriggerTime(injector).runAt(now - T.mins(1).msecs())
        Assert.assertTrue(t.shouldRun())

        // scheduled 1 min in the future
        t = TriggerTime(injector).runAt(now + T.mins(1).msecs())
        Assert.assertFalse(t.shouldRun())
    }

    private var timeJson = "{\"data\":{\"runAt\":1514766840000},\"type\":\"TriggerTime\"}"
    @Test fun toJSONTest() {
        val t: TriggerTime = TriggerTime(injector).runAt(now - T.mins(1).msecs())
        Assert.assertEquals(timeJson, t.toJSON())
    }

    @Test @Throws(JSONException::class) fun fromJSONTest() {
        val t: TriggerTime = TriggerTime(injector).runAt(now - T.mins(1).msecs())
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerTime
        Assert.assertEquals(now - T.mins(1).msecs(), t2.time.value)
    }

    @Test fun copyConstructorTest() {
        val t = TriggerTime(injector)
        t.runAt(now)
        val t1 = t.duplicate() as TriggerTime
        Assert.assertEquals(now, t1.time.value)
    }

    @Test fun friendlyNameTest() {
        Assert.assertEquals(info.nightscout.core.ui.R.string.time, TriggerTime(injector).friendlyName())
    }

    @Test fun friendlyDescriptionTest() {
        Assert.assertEquals(null, TriggerTime(injector).friendlyDescription()) //not mocked    }
    }

    @Test fun iconTest() {
        Assert.assertEquals(Optional.of(info.nightscout.core.main.R.drawable.ic_access_alarm_24dp), TriggerTime(injector).icon())
    }
}