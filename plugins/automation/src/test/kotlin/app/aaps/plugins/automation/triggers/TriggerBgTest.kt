package app.aaps.plugins.automation.triggers

import app.aaps.core.interfaces.db.GlucoseUnit
import app.aaps.core.interfaces.iob.InMemoryGlucoseValue
import app.aaps.database.entities.GlucoseValue
import app.aaps.plugins.automation.elements.Comparator
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`

class TriggerBgTest : TriggerTestBase() {

    @BeforeEach
    fun prepare() {
        `when`(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
    }

    @Test
    fun shouldRunTest() {
        `when`(autosensDataStore.getBucketedDataTableCopy()).thenReturn(generateOneCurrentRecordBgData())
        var t: TriggerBg = TriggerBg(injector).setUnits(GlucoseUnit.MMOL).setValue(4.1).comparator(Comparator.Compare.IS_EQUAL)
        Assertions.assertFalse(t.shouldRun())
        t = TriggerBg(injector).setUnits(GlucoseUnit.MGDL).setValue(214.0).comparator(Comparator.Compare.IS_EQUAL)
        Assertions.assertTrue(t.shouldRun())
        t = TriggerBg(injector).setUnits(GlucoseUnit.MGDL).setValue(214.0).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        Assertions.assertTrue(t.shouldRun())
        t = TriggerBg(injector).setUnits(GlucoseUnit.MGDL).setValue(214.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assertions.assertTrue(t.shouldRun())
        t = TriggerBg(injector).setUnits(GlucoseUnit.MGDL).setValue(215.0).comparator(Comparator.Compare.IS_EQUAL)
        Assertions.assertFalse(t.shouldRun())
        t = TriggerBg(injector).setUnits(GlucoseUnit.MGDL).setValue(215.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assertions.assertTrue(t.shouldRun())
        t = TriggerBg(injector).setUnits(GlucoseUnit.MGDL).setValue(215.0).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        Assertions.assertFalse(t.shouldRun())
        t = TriggerBg(injector).setUnits(GlucoseUnit.MGDL).setValue(213.0).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        Assertions.assertTrue(t.shouldRun())
        t = TriggerBg(injector).setUnits(GlucoseUnit.MGDL).setValue(213.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assertions.assertFalse(t.shouldRun())
        `when`(autosensDataStore.getBucketedDataTableCopy()).thenReturn(ArrayList())
        t = TriggerBg(injector).setUnits(GlucoseUnit.MGDL).setValue(213.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assertions.assertFalse(t.shouldRun())
        t = TriggerBg(injector).comparator(Comparator.Compare.IS_NOT_AVAILABLE)
        Assertions.assertTrue(t.shouldRun())
    }

    @Test
    fun copyConstructorTest() {
        val t: TriggerBg = TriggerBg(injector).setUnits(GlucoseUnit.MGDL).setValue(213.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        val t1 = t.duplicate() as TriggerBg
        Assertions.assertEquals(213.0, t1.bg.value, 0.01)
        Assertions.assertEquals(GlucoseUnit.MGDL, t1.bg.units)
        Assertions.assertEquals(Comparator.Compare.IS_EQUAL_OR_LESSER, t.comparator.value)
    }

    private var bgJson = "{\"data\":{\"comparator\":\"IS_EQUAL\",\"bg\":4.1,\"units\":\"mmol\"},\"type\":\"TriggerBg\"}"

    @Test
    fun toJSONTest() {
        val t: TriggerBg = TriggerBg(injector).setUnits(GlucoseUnit.MMOL).setValue(4.1).comparator(Comparator.Compare.IS_EQUAL)
        Assertions.assertEquals(bgJson, t.toJSON())
    }

    @Test
    fun fromJSONTest() {
        val t: TriggerBg = TriggerBg(injector).setUnits(GlucoseUnit.MMOL).setValue(4.1).comparator(Comparator.Compare.IS_EQUAL)
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerBg
        Assertions.assertEquals(Comparator.Compare.IS_EQUAL, t2.comparator.value)
        Assertions.assertEquals(4.1, t2.bg.value, 0.01)
        Assertions.assertEquals(GlucoseUnit.MMOL, t2.bg.units)
    }

    @Test
    fun iconTest() {
        assertThat(TriggerBg(injector).icon().get()).isEqualTo(app.aaps.core.main.R.drawable.ic_cp_bgcheck)
    }

    private fun generateOneCurrentRecordBgData(): MutableList<InMemoryGlucoseValue> {
        val list: MutableList<InMemoryGlucoseValue> = ArrayList()
        list.add(InMemoryGlucoseValue(value = 214.0, timestamp = now - 1, trendArrow = GlucoseValue.TrendArrow.FLAT, sourceSensor = GlucoseValue.SourceSensor.UNKNOWN))
        return list
    }
}