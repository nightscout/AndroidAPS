package info.nightscout.androidaps.plugins.general.automation.triggers

import com.google.common.base.Optional
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.general.automation.elements.Comparator
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.androidaps.utils.DateUtil
import org.json.JSONObject
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner
import java.util.*

@RunWith(PowerMockRunner::class)
@PrepareForTest(DateUtil::class, IobCobCalculatorPlugin::class, ProfileFunction::class)
class TriggerBgTest : TriggerTestBase() {

    var now = 1514766900000L

    @Before
    fun prepare() {
        `when`(profileFunction.getUnits()).thenReturn(Constants.MGDL)
        `when`(iobCobCalculatorPlugin.dataLock).thenReturn(Unit)
        PowerMockito.mockStatic(DateUtil::class.java)
        `when`(DateUtil.now()).thenReturn(now)
    }

    @Test
    fun shouldRunTest() {
        `when`(iobCobCalculatorPlugin.bgReadings).thenReturn(generateOneCurrentRecordBgData())
        var t: TriggerBg = TriggerBg(injector).setUnits(Constants.MMOL).setValue(4.1).comparator(Comparator.Compare.IS_EQUAL)
        Assert.assertFalse(t.shouldRun())
        t = TriggerBg(injector).setUnits(Constants.MGDL).setValue(214.0).comparator(Comparator.Compare.IS_EQUAL)
        Assert.assertTrue(t.shouldRun())
        t = TriggerBg(injector).setUnits(Constants.MGDL).setValue(214.0).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        Assert.assertTrue(t.shouldRun())
        t = TriggerBg(injector).setUnits(Constants.MGDL).setValue(214.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assert.assertTrue(t.shouldRun())
        t = TriggerBg(injector).setUnits(Constants.MGDL).setValue(215.0).comparator(Comparator.Compare.IS_EQUAL)
        Assert.assertFalse(t.shouldRun())
        t = TriggerBg(injector).setUnits(Constants.MGDL).setValue(215.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assert.assertTrue(t.shouldRun())
        t = TriggerBg(injector).setUnits(Constants.MGDL).setValue(215.0).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        Assert.assertFalse(t.shouldRun())
        t = TriggerBg(injector).setUnits(Constants.MGDL).setValue(213.0).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        Assert.assertTrue(t.shouldRun())
        t = TriggerBg(injector).setUnits(Constants.MGDL).setValue(213.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assert.assertFalse(t.shouldRun())
        `when`(iobCobCalculatorPlugin.bgReadings).thenReturn(ArrayList())
        t = TriggerBg(injector).setUnits(Constants.MGDL).setValue(213.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assert.assertFalse(t.shouldRun())
        t = TriggerBg(injector).comparator(Comparator.Compare.IS_NOT_AVAILABLE)
        Assert.assertTrue(t.shouldRun())
    }

    @Test
    fun copyConstructorTest() {
        val t: TriggerBg = TriggerBg(injector).setUnits(Constants.MGDL).setValue(213.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        val t1 = t.duplicate() as TriggerBg
        Assert.assertEquals(213.0, t1.bg.value, 0.01)
        Assert.assertEquals(Constants.MGDL, t1.bg.units)
        Assert.assertEquals(Comparator.Compare.IS_EQUAL_OR_LESSER, t.comparator.value)
    }

    private var bgJson = "{\"data\":{\"comparator\":\"IS_EQUAL\",\"bg\":4.1,\"units\":\"mmol\"},\"type\":\"info.nightscout.androidaps.plugins.general.automation.triggers.TriggerBg\"}"

    @Test
    fun toJSONTest() {
        val t: TriggerBg = TriggerBg(injector).setUnits(Constants.MMOL).setValue(4.1).comparator(Comparator.Compare.IS_EQUAL)
        Assert.assertEquals(bgJson, t.toJSON())
    }

    @Test
    fun fromJSONTest() {
        val t: TriggerBg = TriggerBg(injector).setUnits(Constants.MMOL).setValue(4.1).comparator(Comparator.Compare.IS_EQUAL)
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerBg
        Assert.assertEquals(Comparator.Compare.IS_EQUAL, t2.comparator.value)
        Assert.assertEquals(4.1, t2.bg.value, 0.01)
        Assert.assertEquals(Constants.MMOL, t2.bg.units)
    }

    @Test
    fun iconTest() {
        Assert.assertEquals(Optional.of(R.drawable.ic_cp_bgcheck), TriggerBg(injector).icon())
    }

    private fun generateOneCurrentRecordBgData(): List<GlucoseValue> {
        val list: MutableList<GlucoseValue> = ArrayList()
        list.add(GlucoseValue(
            raw = 0.0,
            noise = 0.0,
            value = 214.0,
            timestamp = now - 1,
            sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
            trendArrow = GlucoseValue.TrendArrow.FLAT
        ))
        return list
    }
}