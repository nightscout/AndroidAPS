package info.nightscout.androidaps.plugins.general.automation.triggers

import com.google.common.base.Optional
import info.nightscout.androidaps.R
import info.nightscout.androidaps.plugins.general.automation.elements.Comparator
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
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
@PrepareForTest(DateUtil::class, TreatmentsPlugin::class)
class TriggerBolusAgoTest : TriggerTestBase() {

    var now = 1514766900000L

    @Before
    fun mock() {
        PowerMockito.mockStatic(DateUtil::class.java)
        PowerMockito.`when`(DateUtil.now()).thenReturn(now)
    }

    @Test
    fun shouldRunTest() {
        `when`(treatmentsPlugin.getLastBolusTime(true)).thenReturn(now) // Set last bolus time to now
        `when`(DateUtil.now()).thenReturn(now + 10 * 60 * 1000) // set current time to now + 10 min
        var t = TriggerBolusAgo(injector).setValue(110).comparator(Comparator.Compare.IS_EQUAL)
        Assert.assertEquals(110, t.minutesAgo.value)
        Assert.assertEquals(Comparator.Compare.IS_EQUAL, t.comparator.value)
        Assert.assertFalse(t.shouldRun())
        t = TriggerBolusAgo(injector).setValue(10).comparator(Comparator.Compare.IS_EQUAL)
        Assert.assertEquals(10, t.minutesAgo.value)
        Assert.assertTrue(t.shouldRun())
        t = TriggerBolusAgo(injector).setValue(5).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        Assert.assertTrue(t.shouldRun())
        t = TriggerBolusAgo(injector).setValue(310).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assert.assertTrue(t.shouldRun())
        t = TriggerBolusAgo(injector).setValue(420).comparator(Comparator.Compare.IS_EQUAL)
        Assert.assertFalse(t.shouldRun())
        t = TriggerBolusAgo(injector).setValue(390).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assert.assertTrue(t.shouldRun())
        t = TriggerBolusAgo(injector).setValue(390).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        Assert.assertFalse(t.shouldRun())
        t = TriggerBolusAgo(injector).setValue(2).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        Assert.assertTrue(t.shouldRun())
        t = TriggerBolusAgo(injector).setValue(390).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assert.assertTrue(t.shouldRun())
        PowerMockito.`when`(treatmentsPlugin.getLastBolusTime(true)).thenReturn(0L) // Set last bolus time to 0
        t = TriggerBolusAgo(injector).comparator(Comparator.Compare.IS_NOT_AVAILABLE)
        Assert.assertTrue(t.shouldRun())
    }

    @Test fun copyConstructorTest() {
        val t: TriggerBolusAgo = TriggerBolusAgo(injector).setValue(213).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        val t1 = t.duplicate() as TriggerBolusAgo
        Assert.assertEquals(213, t1.minutesAgo.value)
        Assert.assertEquals(Comparator.Compare.IS_EQUAL_OR_LESSER, t.comparator.value)
    }

    private var lbJson = "{\"data\":{\"comparator\":\"IS_EQUAL\",\"minutesAgo\":410},\"type\":\"info.nightscout.androidaps.plugins.general.automation.triggers.TriggerBolusAgo\"}"
    @Test fun toJSONTest() {
        val t: TriggerBolusAgo = TriggerBolusAgo(injector).setValue(410).comparator(Comparator.Compare.IS_EQUAL)
        Assert.assertEquals(lbJson, t.toJSON())
    }

    @Test @Throws(JSONException::class) fun fromJSONTest() {
        val t: TriggerBolusAgo = TriggerBolusAgo(injector).setValue(410).comparator(Comparator.Compare.IS_EQUAL)
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerBolusAgo
        Assert.assertEquals(Comparator.Compare.IS_EQUAL, t2.comparator.value)
        Assert.assertEquals(410, t2.minutesAgo.value)
    }

    @Test fun iconTest() {
        Assert.assertEquals(Optional.of(R.drawable.ic_bolus), TriggerBolusAgo(injector).icon())
    }
}