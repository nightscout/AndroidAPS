package info.nightscout.automation.triggers

import com.google.common.truth.Truth.assertThat
import info.nightscout.automation.R
import info.nightscout.automation.elements.Comparator
import info.nightscout.automation.elements.InputDelta.DeltaType
import info.nightscout.database.entities.GlucoseValue
import info.nightscout.interfaces.GlucoseUnit
import info.nightscout.interfaces.iob.InMemoryGlucoseValue
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`

class TriggerDeltaTest : TriggerTestBase() {

    @BeforeEach
    fun mock() {
        now = 1514766900000L
        `when`(dateUtil.now()).thenReturn(now)
        `when`(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
    }

    @Test fun shouldRunTest() {
        `when`(autosensDataStore.getBucketedDataTableCopy()).thenReturn(generateValidBgData())
        var t = TriggerDelta(injector).units(GlucoseUnit.MGDL).setValue(73.0, DeltaType.LONG_AVERAGE).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isFalse()
        assertThat(t.delta.deltaType).isEqualTo(DeltaType.LONG_AVERAGE)
        t = TriggerDelta(injector).units(GlucoseUnit.MGDL).setValue(-2.0, DeltaType.SHORT_AVERAGE).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isFalse()
        assertThat(t.delta.deltaType).isEqualTo(DeltaType.SHORT_AVERAGE)
        t = TriggerDelta(injector).units(GlucoseUnit.MGDL).setValue(-3.0, DeltaType.DELTA).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        assertThat(t.shouldRun()).isTrue()
        assertThat(t.delta.deltaType).isEqualTo(DeltaType.DELTA)
        t = TriggerDelta(injector).units(GlucoseUnit.MGDL).setValue(2.0, DeltaType.LONG_AVERAGE).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerDelta(injector).units(GlucoseUnit.MGDL).setValue(2.0, DeltaType.LONG_AVERAGE).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isFalse()
        t = TriggerDelta(injector).units(GlucoseUnit.MGDL).setValue(0.3, DeltaType.LONG_AVERAGE).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerDelta(injector).units(GlucoseUnit.MGDL).setValue(0.1, DeltaType.LONG_AVERAGE).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        assertThat(t.shouldRun()).isFalse()
        t = TriggerDelta(injector).units(GlucoseUnit.MGDL).setValue(-0.5, DeltaType.LONG_AVERAGE).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        assertThat(t.shouldRun()).isFalse()
        t = TriggerDelta(injector).units(GlucoseUnit.MGDL).setValue(-0.2, DeltaType.LONG_AVERAGE).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.shouldRun()).isTrue()
        `when`(autosensDataStore.getBucketedDataTableCopy()).thenReturn(ArrayList())
        t = TriggerDelta(injector).units(GlucoseUnit.MGDL).setValue(213.0, DeltaType.DELTA).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.shouldRun()).isFalse()
        t = TriggerDelta(injector).comparator(Comparator.Compare.IS_NOT_AVAILABLE)
        assertThat(t.shouldRun()).isTrue()
    }

    @Test fun copyConstructorTest() {
        val t: TriggerDelta = TriggerDelta(injector).units(GlucoseUnit.MGDL).setValue(213.0, DeltaType.DELTA).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        val t1 = t.duplicate() as TriggerDelta
        assertThat( t1.delta.value).isWithin( 0.01).of(213.0)
        assertThat(t1.units).isEqualTo(GlucoseUnit.MGDL)
        assertThat(t.delta.deltaType).isEqualTo(DeltaType.DELTA)
        assertThat(t.comparator.value).isEqualTo(Comparator.Compare.IS_EQUAL_OR_LESSER)
    }

    private var deltaJson = "{\"data\":{\"comparator\":\"IS_EQUAL\",\"deltaType\":\"DELTA\",\"units\":\"mg/dl\",\"value\":4.1},\"type\":\"TriggerDelta\"}"

    @Test
    fun toJSONTest() {
        val t: TriggerDelta = TriggerDelta(injector).units(GlucoseUnit.MGDL).setValue(4.1, DeltaType.DELTA).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.toJSON()).isEqualTo(deltaJson)
    }

    @Test
    fun fromJSONTest() {
        val t: TriggerDelta = TriggerDelta(injector).units(GlucoseUnit.MMOL).setValue(4.1, DeltaType.DELTA).comparator(Comparator.Compare.IS_EQUAL)
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerDelta
        assertThat(t2.comparator.value).isEqualTo(Comparator.Compare.IS_EQUAL)
        assertThat( t2.delta.value).isWithin( 0.01).of(4.1)
        assertThat(t2.units).isEqualTo(GlucoseUnit.MMOL)
        assertThat(t2.delta.deltaType).isEqualTo(DeltaType.DELTA)
    }

    @Test fun iconTest() {
        assertThat(TriggerDelta(injector).icon()).hasValue(R.drawable.ic_auto_delta)
    }

    @Test fun initializerTest() {
        val t = TriggerDelta(injector)
        assertThat(t.units).isEqualTo(GlucoseUnit.MGDL)
    }

    private fun generateValidBgData(): MutableList<InMemoryGlucoseValue> {
        val list: MutableList<InMemoryGlucoseValue> = ArrayList()
        list.add(InMemoryGlucoseValue(value = 214.0, timestamp = 1514766900000, trendArrow = GlucoseValue.TrendArrow.FLAT, sourceSensor = GlucoseValue.SourceSensor.UNKNOWN))
        list.add(InMemoryGlucoseValue(value = 216.0, timestamp = 1514766600000, trendArrow = GlucoseValue.TrendArrow.FLAT, sourceSensor = GlucoseValue.SourceSensor.UNKNOWN))
        list.add(InMemoryGlucoseValue(value = 219.0, timestamp = 1514766300000, trendArrow = GlucoseValue.TrendArrow.FLAT, sourceSensor = GlucoseValue.SourceSensor.UNKNOWN))
        list.add(InMemoryGlucoseValue(value = 223.0, timestamp = 1514766000000, trendArrow = GlucoseValue.TrendArrow.FLAT, sourceSensor = GlucoseValue.SourceSensor.UNKNOWN))
        list.add(InMemoryGlucoseValue(value = 222.0, timestamp = 1514765700000, trendArrow = GlucoseValue.TrendArrow.FLAT, sourceSensor = GlucoseValue.SourceSensor.UNKNOWN))
        list.add(InMemoryGlucoseValue(value = 224.0, timestamp = 1514765400000, trendArrow = GlucoseValue.TrendArrow.FLAT, sourceSensor = GlucoseValue.SourceSensor.UNKNOWN))
        list.add(InMemoryGlucoseValue(value = 226.0, timestamp = 1514765100000, trendArrow = GlucoseValue.TrendArrow.FLAT, sourceSensor = GlucoseValue.SourceSensor.UNKNOWN))
        list.add(InMemoryGlucoseValue(value = 228.0, timestamp = 1514764800000, trendArrow = GlucoseValue.TrendArrow.FLAT, sourceSensor = GlucoseValue.SourceSensor.UNKNOWN))
        return list
    }
}
