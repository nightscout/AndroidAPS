package info.nightscout.androidaps.plugins.general.automation.triggers

import com.google.common.base.Optional
import info.nightscout.androidaps.R
import info.nightscout.androidaps.plugins.general.automation.elements.Comparator
import info.nightscout.androidaps.utils.DateUtil
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(DateUtil::class)
class TriggerProfilePercentTest : TriggerTestBase() {

    private val now = 1514766900000L

    @Before fun mock() {
        `when`(profileFunction.getProfile()).thenReturn(validProfile)
        PowerMockito.mockStatic(DateUtil::class.java)
        PowerMockito.`when`(DateUtil.now()).thenReturn(now)
    }

    @Test fun shouldRunTest() {
        var t: TriggerProfilePercent = TriggerProfilePercent(injector).setValue(101.0).comparator(Comparator.Compare.IS_EQUAL)
        Assert.assertFalse(t.shouldRun())
        t = TriggerProfilePercent(injector).setValue(100.0).comparator(Comparator.Compare.IS_EQUAL)
        Assert.assertTrue(t.shouldRun())
        t = TriggerProfilePercent(injector).setValue(100.0).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        Assert.assertTrue(t.shouldRun())
        t = TriggerProfilePercent(injector).setValue(90.0).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        Assert.assertTrue(t.shouldRun())
        t = TriggerProfilePercent(injector).setValue(100.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assert.assertTrue(t.shouldRun())
        t = TriggerProfilePercent(injector).setValue(101.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assert.assertTrue(t.shouldRun())
        t = TriggerProfilePercent(injector).setValue(215.0).comparator(Comparator.Compare.IS_EQUAL)
        Assert.assertFalse(t.shouldRun())
        t = TriggerProfilePercent(injector).setValue(110.0).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        Assert.assertFalse(t.shouldRun())
        t = TriggerProfilePercent(injector).setValue(90.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assert.assertFalse(t.shouldRun())
    }

    @Test fun copyConstructorTest() {
        val t: TriggerProfilePercent = TriggerProfilePercent(injector).setValue(213.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        val t1 = t.duplicate() as TriggerProfilePercent
        Assert.assertEquals(213.0, t1.pct.value, 0.01)
        Assert.assertEquals(Comparator.Compare.IS_EQUAL_OR_LESSER, t.comparator.value)
    }

    private val bgJson = "{\"data\":{\"comparator\":\"IS_EQUAL\",\"percentage\":110},\"type\":\"info.nightscout.androidaps.plugins.general.automation.triggers.TriggerProfilePercent\"}"
    @Test fun toJSONTest() {
        val t: TriggerProfilePercent = TriggerProfilePercent(injector).setValue(110.0).comparator(Comparator.Compare.IS_EQUAL)
        Assert.assertEquals(bgJson, t.toJSON())
    }

    @Test @Throws(JSONException::class) fun fromJSONTest() {
        val t: TriggerProfilePercent = TriggerProfilePercent(injector).setValue(120.0).comparator(Comparator.Compare.IS_EQUAL)
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerProfilePercent
        Assert.assertEquals(Comparator.Compare.IS_EQUAL, t2.comparator.value)
        Assert.assertEquals(120.0, t2.pct.value, 0.01)
    }

    @Test fun iconTest() {
        Assert.assertEquals(Optional.of(R.drawable.ic_actions_profileswitch), TriggerProfilePercent(injector).icon())
    }

    @Test fun friendlyNameTest() {
        Assert.assertEquals(R.string.profilepercentage.toLong(), TriggerProfilePercent(injector).friendlyName().toLong()) // not mocked
    }
}