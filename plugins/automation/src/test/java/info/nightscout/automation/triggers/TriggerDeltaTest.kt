package info.nightscout.automation.triggers

import com.google.common.base.Optional
import info.nightscout.automation.R
import info.nightscout.automation.elements.Comparator
import info.nightscout.automation.elements.InputDelta.DeltaType
import info.nightscout.database.entities.GlucoseValue
import info.nightscout.interfaces.GlucoseUnit
import info.nightscout.interfaces.iob.InMemoryGlucoseValue
import org.json.JSONObject
import org.junit.jupiter.api.Assertions
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
        `when`(autosensDataStore.getBucketedDataTableCopy()).thenReturn(generateValidBgData())
        var t = TriggerDelta(injector).units(GlucoseUnit.MGDL).setValue(73.0, DeltaType.LONG_AVERAGE).comparator(Comparator.Compare.IS_EQUAL)
        Assertions.assertFalse(t.shouldRun())
        Assertions.assertEquals(DeltaType.LONG_AVERAGE, t.delta.deltaType)
        t = TriggerDelta(injector).units(GlucoseUnit.MGDL).setValue(-2.0, DeltaType.SHORT_AVERAGE).comparator(Comparator.Compare.IS_EQUAL)
        Assertions.assertFalse(t.shouldRun())
        Assertions.assertEquals(DeltaType.SHORT_AVERAGE, t.delta.deltaType)
        t = TriggerDelta(injector).units(GlucoseUnit.MGDL).setValue(-3.0, DeltaType.DELTA).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        Assertions.assertTrue(t.shouldRun())
        Assertions.assertEquals(DeltaType.DELTA, t.delta.deltaType)
        t = TriggerDelta(injector).units(GlucoseUnit.MGDL).setValue(2.0, DeltaType.LONG_AVERAGE).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assertions.assertTrue(t.shouldRun())
        t = TriggerDelta(injector).units(GlucoseUnit.MGDL).setValue(2.0, DeltaType.LONG_AVERAGE).comparator(Comparator.Compare.IS_EQUAL)
        Assertions.assertFalse(t.shouldRun())
        t = TriggerDelta(injector).units(GlucoseUnit.MGDL).setValue(0.3, DeltaType.LONG_AVERAGE).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assertions.assertTrue(t.shouldRun())
        t = TriggerDelta(injector).units(GlucoseUnit.MGDL).setValue(0.1, DeltaType.LONG_AVERAGE).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        Assertions.assertFalse(t.shouldRun())
        t = TriggerDelta(injector).units(GlucoseUnit.MGDL).setValue(-0.5, DeltaType.LONG_AVERAGE).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        Assertions.assertFalse(t.shouldRun())
        t = TriggerDelta(injector).units(GlucoseUnit.MGDL).setValue(-0.2, DeltaType.LONG_AVERAGE).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assertions.assertTrue(t.shouldRun())
        `when`(autosensDataStore.getBucketedDataTableCopy()).thenReturn(ArrayList())
        t = TriggerDelta(injector).units(GlucoseUnit.MGDL).setValue(213.0, DeltaType.DELTA).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assertions.assertFalse(t.shouldRun())
        t = TriggerDelta(injector).comparator(Comparator.Compare.IS_NOT_AVAILABLE)
        Assertions.assertTrue(t.shouldRun())
    }

    @Test fun copyConstructorTest() {
        val t: TriggerDelta = TriggerDelta(injector).units(GlucoseUnit.MGDL).setValue(213.0, DeltaType.DELTA).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        val t1 = t.duplicate() as TriggerDelta
        Assertions.assertEquals(213.0, t1.delta.value, 0.01)
        Assertions.assertEquals(GlucoseUnit.MGDL, t1.units)
        Assertions.assertEquals(DeltaType.DELTA, t.delta.deltaType)
        Assertions.assertEquals(Comparator.Compare.IS_EQUAL_OR_LESSER, t.comparator.value)
    }

    private var deltaJson = "{\"data\":{\"comparator\":\"IS_EQUAL\",\"deltaType\":\"DELTA\",\"units\":\"mg/dl\",\"value\":4.1},\"type\":\"TriggerDelta\"}"

    @Test
    fun toJSONTest() {
        val t: TriggerDelta = TriggerDelta(injector).units(GlucoseUnit.MGDL).setValue(4.1, DeltaType.DELTA).comparator(Comparator.Compare.IS_EQUAL)
        Assertions.assertEquals(deltaJson, t.toJSON())
    }

    @Test
    fun fromJSONTest() {
        val t: TriggerDelta = TriggerDelta(injector).units(GlucoseUnit.MMOL).setValue(4.1, DeltaType.DELTA).comparator(Comparator.Compare.IS_EQUAL)
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerDelta
        Assertions.assertEquals(Comparator.Compare.IS_EQUAL, t2.comparator.value)
        Assertions.assertEquals(4.1, t2.delta.value, 0.01)
        Assertions.assertEquals(GlucoseUnit.MMOL, t2.units)
        Assertions.assertEquals(DeltaType.DELTA, t2.delta.deltaType)
    }

    @Test fun iconTest() {
        Assertions.assertEquals(Optional.of(R.drawable.ic_auto_delta), TriggerDelta(injector).icon())
    }

    @Test fun initializerTest() {
        val t = TriggerDelta(injector)
        Assertions.assertTrue(t.units == GlucoseUnit.MGDL)
    }

    private fun generateValidBgData(): MutableList<InMemoryGlucoseValue> {
        val list: MutableList<InMemoryGlucoseValue> = ArrayList()
        list.add(InMemoryGlucoseValue(value = 214.0, timestamp = 1514766900000, trendArrow = GlucoseValue.TrendArrow.FLAT))
        list.add(InMemoryGlucoseValue(value = 216.0, timestamp = 1514766600000, trendArrow = GlucoseValue.TrendArrow.FLAT))
        list.add(InMemoryGlucoseValue(value = 219.0, timestamp = 1514766300000, trendArrow = GlucoseValue.TrendArrow.FLAT))
        list.add(InMemoryGlucoseValue(value = 223.0, timestamp = 1514766000000, trendArrow = GlucoseValue.TrendArrow.FLAT))
        list.add(InMemoryGlucoseValue(value = 222.0, timestamp = 1514765700000, trendArrow = GlucoseValue.TrendArrow.FLAT))
        list.add(InMemoryGlucoseValue(value = 224.0, timestamp = 1514765400000, trendArrow = GlucoseValue.TrendArrow.FLAT))
        list.add(InMemoryGlucoseValue(value = 226.0, timestamp = 1514765100000, trendArrow = GlucoseValue.TrendArrow.FLAT))
        list.add(InMemoryGlucoseValue(value = 228.0, timestamp = 1514764800000, trendArrow = GlucoseValue.TrendArrow.FLAT))
        return list
    }
}