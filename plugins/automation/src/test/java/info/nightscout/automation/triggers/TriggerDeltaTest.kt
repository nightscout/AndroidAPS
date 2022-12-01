package info.nightscout.automation.triggers

import com.google.common.base.Optional
import info.nightscout.automation.R
import info.nightscout.automation.elements.Comparator
import info.nightscout.automation.elements.InputDelta.DeltaType
import info.nightscout.database.entities.GlucoseValue
import info.nightscout.interfaces.GlucoseUnit
import org.json.JSONObject
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`

class TriggerDeltaTest : TriggerTestBase() {

    var now = 1514766900000L

    @BeforeEach
    fun mock() {
        `when`(dateUtil.now()).thenReturn(now)
        `when`(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
    }

    @Test fun shouldRunTest() {
        `when`(autosensDataStore.getBgReadingsDataTableCopy()).thenReturn(generateValidBgData())
        var t = TriggerDelta(injector).units(GlucoseUnit.MGDL).setValue(73.0, DeltaType.LONG_AVERAGE).comparator(Comparator.Compare.IS_EQUAL)
        Assert.assertFalse(t.shouldRun())
        Assert.assertEquals(DeltaType.LONG_AVERAGE, t.delta.deltaType)
        t = TriggerDelta(injector).units(GlucoseUnit.MGDL).setValue(-2.0, DeltaType.SHORT_AVERAGE).comparator(Comparator.Compare.IS_EQUAL)
        Assert.assertFalse(t.shouldRun())
        Assert.assertEquals(DeltaType.SHORT_AVERAGE, t.delta.deltaType)
        t = TriggerDelta(injector).units(GlucoseUnit.MGDL).setValue(-3.0, DeltaType.DELTA).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        Assert.assertTrue(t.shouldRun())
        Assert.assertEquals(DeltaType.DELTA, t.delta.deltaType)
        t = TriggerDelta(injector).units(GlucoseUnit.MGDL).setValue(2.0, DeltaType.LONG_AVERAGE).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assert.assertTrue(t.shouldRun())
        t = TriggerDelta(injector).units(GlucoseUnit.MGDL).setValue(2.0, DeltaType.LONG_AVERAGE).comparator(Comparator.Compare.IS_EQUAL)
        Assert.assertFalse(t.shouldRun())
        t = TriggerDelta(injector).units(GlucoseUnit.MGDL).setValue(0.3, DeltaType.LONG_AVERAGE).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assert.assertTrue(t.shouldRun())
        t = TriggerDelta(injector).units(GlucoseUnit.MGDL).setValue(0.1, DeltaType.LONG_AVERAGE).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        Assert.assertFalse(t.shouldRun())
        t = TriggerDelta(injector).units(GlucoseUnit.MGDL).setValue(-0.5, DeltaType.LONG_AVERAGE).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        Assert.assertFalse(t.shouldRun())
        t = TriggerDelta(injector).units(GlucoseUnit.MGDL).setValue(-0.2, DeltaType.LONG_AVERAGE).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assert.assertTrue(t.shouldRun())
        `when`(autosensDataStore.getBgReadingsDataTableCopy()).thenReturn(ArrayList())
        t = TriggerDelta(injector).units(GlucoseUnit.MGDL).setValue(213.0, DeltaType.DELTA).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assert.assertFalse(t.shouldRun())
        t = TriggerDelta(injector).comparator(Comparator.Compare.IS_NOT_AVAILABLE)
        Assert.assertTrue(t.shouldRun())
    }

    @Test fun copyConstructorTest() {
        val t: TriggerDelta = TriggerDelta(injector).units(GlucoseUnit.MGDL).setValue(213.0, DeltaType.DELTA).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        val t1 = t.duplicate() as TriggerDelta
        Assert.assertEquals(213.0, t1.delta.value, 0.01)
        Assert.assertEquals(GlucoseUnit.MGDL, t1.units)
        Assert.assertEquals(DeltaType.DELTA, t.delta.deltaType)
        Assert.assertEquals(Comparator.Compare.IS_EQUAL_OR_LESSER, t.comparator.value)
    }

    private var deltaJson = "{\"data\":{\"comparator\":\"IS_EQUAL\",\"deltaType\":\"DELTA\",\"units\":\"mg/dl\",\"value\":4.1},\"type\":\"TriggerDelta\"}"

    @Test
    fun toJSONTest() {
        val t: TriggerDelta = TriggerDelta(injector).units(GlucoseUnit.MGDL).setValue(4.1, DeltaType.DELTA).comparator(Comparator.Compare.IS_EQUAL)
        Assert.assertEquals(deltaJson, t.toJSON())
    }

    @Test
    fun fromJSONTest() {
        val t: TriggerDelta = TriggerDelta(injector).units(GlucoseUnit.MMOL).setValue(4.1, DeltaType.DELTA).comparator(Comparator.Compare.IS_EQUAL)
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerDelta
        Assert.assertEquals(Comparator.Compare.IS_EQUAL, t2.comparator.value)
        Assert.assertEquals(4.1, t2.delta.value, 0.01)
        Assert.assertEquals(GlucoseUnit.MMOL, t2.units)
        Assert.assertEquals(DeltaType.DELTA, t2.delta.deltaType)
    }

    @Test fun iconTest() {
        Assert.assertEquals(Optional.of(R.drawable.ic_auto_delta), TriggerDelta(injector).icon())
    }

    @Test fun initializerTest() {
        val t = TriggerDelta(injector)
        Assert.assertTrue(t.units == GlucoseUnit.MGDL)
    }

    private fun generateValidBgData(): List<GlucoseValue> {
        val list: MutableList<GlucoseValue> = ArrayList()
        list.add(GlucoseValue(raw = 0.0, noise = 0.0, value = 214.0, timestamp = 1514766900000, sourceSensor = GlucoseValue.SourceSensor.UNKNOWN, trendArrow = GlucoseValue.TrendArrow.FLAT))
        list.add(GlucoseValue(raw = 0.0, noise = 0.0, value = 216.0, timestamp = 1514766600000, sourceSensor = GlucoseValue.SourceSensor.UNKNOWN, trendArrow = GlucoseValue.TrendArrow.FLAT))
        list.add(GlucoseValue(raw = 0.0, noise = 0.0, value = 219.0, timestamp = 1514766300000, sourceSensor = GlucoseValue.SourceSensor.UNKNOWN, trendArrow = GlucoseValue.TrendArrow.FLAT))
        list.add(GlucoseValue(raw = 0.0, noise = 0.0, value = 223.0, timestamp = 1514766000000, sourceSensor = GlucoseValue.SourceSensor.UNKNOWN, trendArrow = GlucoseValue.TrendArrow.FLAT))
        list.add(GlucoseValue(raw = 0.0, noise = 0.0, value = 222.0, timestamp = 1514765700000, sourceSensor = GlucoseValue.SourceSensor.UNKNOWN, trendArrow = GlucoseValue.TrendArrow.FLAT))
        list.add(GlucoseValue(raw = 0.0, noise = 0.0, value = 224.0, timestamp = 1514765400000, sourceSensor = GlucoseValue.SourceSensor.UNKNOWN, trendArrow = GlucoseValue.TrendArrow.FLAT))
        list.add(GlucoseValue(raw = 0.0, noise = 0.0, value = 226.0, timestamp = 1514765100000, sourceSensor = GlucoseValue.SourceSensor.UNKNOWN, trendArrow = GlucoseValue.TrendArrow.FLAT))
        list.add(GlucoseValue(raw = 0.0, noise = 0.0, value = 228.0, timestamp = 1514764800000, sourceSensor = GlucoseValue.SourceSensor.UNKNOWN, trendArrow = GlucoseValue.TrendArrow.FLAT))
        return list
    }
}