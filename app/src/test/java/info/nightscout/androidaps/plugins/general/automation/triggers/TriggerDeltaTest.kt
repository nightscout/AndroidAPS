package info.nightscout.androidaps.plugins.general.automation.triggers

import com.google.common.base.Optional
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.db.BgReading
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.general.automation.elements.Comparator
import info.nightscout.androidaps.plugins.general.automation.elements.InputDelta.DeltaType
import info.nightscout.androidaps.plugins.general.nsclient.data.NSSgv
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
class TriggerDeltaTest : TriggerTestBase() {

    var now = 1514766900000L

    @Before
    fun mock() {
        PowerMockito.mockStatic(DateUtil::class.java)
        PowerMockito.`when`(DateUtil.now()).thenReturn(now)
        `when`(iobCobCalculatorPlugin.dataLock).thenReturn(Unit)
        `when`(profileFunction.getUnits()).thenReturn(Constants.MGDL)
    }

    @Test fun shouldRunTest() {
        `when`(iobCobCalculatorPlugin.bgReadings).thenReturn(generateValidBgData())
        var t = TriggerDelta(injector).units(Constants.MGDL).setValue(73.0, DeltaType.LONG_AVERAGE).comparator(Comparator.Compare.IS_EQUAL)
        Assert.assertFalse(t.shouldRun())
        Assert.assertEquals(DeltaType.LONG_AVERAGE, t.delta.deltaType)
        t = TriggerDelta(injector).units(Constants.MGDL).setValue(-2.0, DeltaType.SHORT_AVERAGE).comparator(Comparator.Compare.IS_EQUAL)
        Assert.assertFalse(t.shouldRun())
        Assert.assertEquals(DeltaType.SHORT_AVERAGE, t.delta.deltaType)
        t = TriggerDelta(injector).units(Constants.MGDL).setValue(-3.0, DeltaType.DELTA).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        Assert.assertTrue(t.shouldRun())
        Assert.assertEquals(DeltaType.DELTA, t.delta.deltaType)
        t = TriggerDelta(injector).units(Constants.MGDL).setValue(2.0, DeltaType.LONG_AVERAGE).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assert.assertTrue(t.shouldRun())
        t = TriggerDelta(injector).units(Constants.MGDL).setValue(2.0, DeltaType.LONG_AVERAGE).comparator(Comparator.Compare.IS_EQUAL)
        Assert.assertFalse(t.shouldRun())
        t = TriggerDelta(injector).units(Constants.MGDL).setValue(0.3, DeltaType.LONG_AVERAGE).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assert.assertTrue(t.shouldRun())
        t = TriggerDelta(injector).units(Constants.MGDL).setValue(0.1, DeltaType.LONG_AVERAGE).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        Assert.assertFalse(t.shouldRun())
        t = TriggerDelta(injector).units(Constants.MGDL).setValue(-0.5, DeltaType.LONG_AVERAGE).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        Assert.assertFalse(t.shouldRun())
        t = TriggerDelta(injector).units(Constants.MGDL).setValue(-0.2, DeltaType.LONG_AVERAGE).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assert.assertTrue(t.shouldRun())
        `when`(iobCobCalculatorPlugin.bgReadings).thenReturn(ArrayList())
        t = TriggerDelta(injector).units(Constants.MGDL).setValue(213.0, DeltaType.DELTA).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assert.assertFalse(t.shouldRun())
        t = TriggerDelta(injector).comparator(Comparator.Compare.IS_NOT_AVAILABLE)
        Assert.assertTrue(t.shouldRun())
    }

    @Test fun copyConstructorTest() {
        val t: TriggerDelta = TriggerDelta(injector).units(Constants.MGDL).setValue(213.0, DeltaType.DELTA).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        val t1 = t.duplicate() as TriggerDelta
        Assert.assertEquals(213.0, t1.delta.value, 0.01)
        Assert.assertEquals(Constants.MGDL, t1.units)
        Assert.assertEquals(DeltaType.DELTA, t.delta.deltaType)
        Assert.assertEquals(Comparator.Compare.IS_EQUAL_OR_LESSER, t.comparator.value)
    }

    private var deltaJson = "{\"data\":{\"comparator\":\"IS_EQUAL\",\"deltaType\":\"DELTA\",\"units\":\"mg/dl\",\"value\":4.1},\"type\":\"info.nightscout.androidaps.plugins.general.automation.triggers.TriggerDelta\"}"

    @Test
    fun toJSONTest() {
        val t: TriggerDelta = TriggerDelta(injector).units(Constants.MGDL).setValue(4.1, DeltaType.DELTA).comparator(Comparator.Compare.IS_EQUAL)
        Assert.assertEquals(deltaJson, t.toJSON())
    }

    @Test
    fun fromJSONTest() {
        val t: TriggerDelta = TriggerDelta(injector).units(Constants.MMOL).setValue(4.1, DeltaType.DELTA).comparator(Comparator.Compare.IS_EQUAL)
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerDelta
        Assert.assertEquals(Comparator.Compare.IS_EQUAL, t2.comparator.value)
        Assert.assertEquals(4.1, t2.delta.value, 0.01)
        Assert.assertEquals(Constants.MMOL, t2.units)
        Assert.assertEquals(DeltaType.DELTA, t2.delta.deltaType)
    }

    @Test fun iconTest() {
        Assert.assertEquals(Optional.of(R.drawable.ic_auto_delta), TriggerDelta(injector).icon())
    }

    @Test fun initializerTest() {
        val t = TriggerDelta(injector)
        Assert.assertTrue(t.units == Constants.MGDL)
    }

    private fun generateValidBgData(): List<BgReading> {
        val list: MutableList<BgReading> = ArrayList()
        list.add(BgReading(injector, NSSgv(JSONObject("{\"mgdl\":214,\"mills\":1514766900000,\"direction\":\"Flat\"}"))))
        list.add(BgReading(injector, NSSgv(JSONObject("{\"mgdl\":216,\"mills\":1514766600000,\"direction\":\"Flat\"}")))) // +2
        list.add(BgReading(injector, NSSgv(JSONObject("{\"mgdl\":219,\"mills\":1514766300000,\"direction\":\"Flat\"}")))) // +3
        list.add(BgReading(injector, NSSgv(JSONObject("{\"mgdl\":223,\"mills\":1514766000000,\"direction\":\"Flat\"}")))) // +4
        list.add(BgReading(injector, NSSgv(JSONObject("{\"mgdl\":222,\"mills\":1514765700000,\"direction\":\"Flat\"}"))))
        list.add(BgReading(injector, NSSgv(JSONObject("{\"mgdl\":224,\"mills\":1514765400000,\"direction\":\"Flat\"}"))))
        list.add(BgReading(injector, NSSgv(JSONObject("{\"mgdl\":226,\"mills\":1514765100000,\"direction\":\"Flat\"}"))))
        list.add(BgReading(injector, NSSgv(JSONObject("{\"mgdl\":228,\"mills\":1514764800000,\"direction\":\"Flat\"}"))))
        return list
    }
}