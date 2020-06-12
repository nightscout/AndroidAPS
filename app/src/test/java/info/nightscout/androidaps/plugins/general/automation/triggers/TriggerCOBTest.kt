package info.nightscout.androidaps.plugins.general.automation.triggers

import com.google.common.base.Optional
import info.nightscout.androidaps.R
import info.nightscout.androidaps.plugins.general.automation.elements.Comparator
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.CobInfo
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.androidaps.utils.DateUtil
import org.json.JSONObject
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(DateUtil::class, IobCobCalculatorPlugin::class)
class TriggerCOBTest : TriggerTestBase() {

    var now = 1514766900000L

    @Before fun mock() {
        PowerMockito.mockStatic(DateUtil::class.java)
        PowerMockito.`when`(DateUtil.now()).thenReturn(now)
        PowerMockito.`when`(sp.getInt(ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt())).thenReturn(48)
    }

    @Test fun shouldRunTest() {
        // COB value is 6
        PowerMockito.`when`(iobCobCalculatorPlugin.getCobInfo(false, "AutomationTriggerCOB")).thenReturn(CobInfo(6.0, 2.0))
        var t: TriggerCOB = TriggerCOB(injector).setValue(1.0).comparator(Comparator.Compare.IS_EQUAL)
        Assert.assertFalse(t.shouldRun())
        t = TriggerCOB(injector).setValue(6.0).comparator(Comparator.Compare.IS_EQUAL)
        Assert.assertTrue(t.shouldRun())
        t = TriggerCOB(injector).setValue(5.0).comparator(Comparator.Compare.IS_GREATER)
        Assert.assertTrue(t.shouldRun())
        t = TriggerCOB(injector).setValue(5.0).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        Assert.assertTrue(t.shouldRun())
        t = TriggerCOB(injector).setValue(6.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assert.assertTrue(t.shouldRun())
        t = TriggerCOB(injector).setValue(1.0).comparator(Comparator.Compare.IS_EQUAL)
        Assert.assertFalse(t.shouldRun())
        t = TriggerCOB(injector).setValue(10.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assert.assertTrue(t.shouldRun())
        t = TriggerCOB(injector).setValue(5.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assert.assertFalse(t.shouldRun())
    }

    @Test fun copyConstructorTest() {
        val t: TriggerCOB = TriggerCOB(injector).setValue(213.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assert.assertEquals(213.0, t.cob.value, 0.01)
        Assert.assertEquals(Comparator.Compare.IS_EQUAL_OR_LESSER, t.comparator.value)
    }

    private var bgJson = "{\"data\":{\"comparator\":\"IS_EQUAL\",\"carbs\":4},\"type\":\"info.nightscout.androidaps.plugins.general.automation.triggers.TriggerCOB\"}"
    @Test fun toJSONTest() {
        val t: TriggerCOB = TriggerCOB(injector).setValue(4.0).comparator(Comparator.Compare.IS_EQUAL)
        Assert.assertEquals(bgJson, t.toJSON())
    }

    @Test
    fun fromJSONTest() {
        val t: TriggerCOB = TriggerCOB(injector).setValue(4.0).comparator(Comparator.Compare.IS_EQUAL)
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerCOB
        Assert.assertEquals(Comparator.Compare.IS_EQUAL, t2.comparator.value)
        Assert.assertEquals(4.0, t2.cob.value, 0.01)
    }

    @Test fun iconTest() {
        Assert.assertEquals(Optional.of(R.drawable.ic_cp_bolus_carbs), TriggerCOB(injector).icon())
    }

    fun generateCobInfo(): CobInfo {
        return CobInfo(6.0, 0.0)
    }
}