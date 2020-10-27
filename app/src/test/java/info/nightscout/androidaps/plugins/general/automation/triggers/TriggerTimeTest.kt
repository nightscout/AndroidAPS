package info.nightscout.androidaps.plugins.general.automation.triggers

import com.google.common.base.Optional
import info.nightscout.androidaps.R
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.T
import org.json.JSONException
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
class TriggerTimeTest : TriggerTestBase() {

    var now = 1514766900000L

    @Before fun mock() {
        PowerMockito.mockStatic(DateUtil::class.java)
        PowerMockito.`when`(DateUtil.now()).thenReturn(now)
    }

    @Test fun shouldRunTest() {

        // scheduled 1 min before
        var t: TriggerTime = TriggerTime(injector).runAt(now - T.mins(1).msecs())
        Assert.assertTrue(t.shouldRun())

        // scheduled 1 min in the future
        t = TriggerTime(injector).runAt(now + T.mins(1).msecs())
        Assert.assertFalse(t.shouldRun())
    }

    private var timeJson = "{\"data\":{\"runAt\":1514766840000},\"type\":\"info.nightscout.androidaps.plugins.general.automation.triggers.TriggerTime\"}"
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
        Assert.assertEquals(R.string.time.toLong(), TriggerTime(injector).friendlyName().toLong())
    }

    @Test fun friendlyDescriptionTest() {
        Assert.assertEquals(null, TriggerTime(injector).friendlyDescription()) //not mocked    }
    }

    @Test fun iconTest() {
        Assert.assertEquals(Optional.of(R.drawable.ic_access_alarm_24dp), TriggerTime(injector).icon())
    }
}