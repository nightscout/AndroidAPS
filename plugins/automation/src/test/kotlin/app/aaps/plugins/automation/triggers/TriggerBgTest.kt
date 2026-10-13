package app.aaps.plugins.automation.triggers

import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.plugins.automation.elements.Comparator
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.skyscreamer.jsonassert.JSONAssert

class TriggerBgTest : TriggerTestBase() {

    @BeforeEach
    fun prepare() {
        whenever(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
    }

    @Test
    fun shouldRunTest() {
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(generateOneCurrentRecordBgData())
        var t: TriggerBg = TriggerBg(injector).setUnits(GlucoseUnit.MMOL).setValue(4.1).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isFalse()
        t = TriggerBg(injector).setUnits(GlucoseUnit.MGDL).setValue(214.0).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerBg(injector).setUnits(GlucoseUnit.MGDL).setValue(214.0).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerBg(injector).setUnits(GlucoseUnit.MGDL).setValue(214.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerBg(injector).setUnits(GlucoseUnit.MGDL).setValue(215.0).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isFalse()
        t = TriggerBg(injector).setUnits(GlucoseUnit.MGDL).setValue(215.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerBg(injector).setUnits(GlucoseUnit.MGDL).setValue(215.0).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        assertThat(t.shouldRun()).isFalse()
        t = TriggerBg(injector).setUnits(GlucoseUnit.MGDL).setValue(213.0).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerBg(injector).setUnits(GlucoseUnit.MGDL).setValue(213.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.shouldRun()).isFalse()
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(ArrayList())
        t = TriggerBg(injector).setUnits(GlucoseUnit.MGDL).setValue(213.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.shouldRun()).isFalse()
        t = TriggerBg(injector).comparator(Comparator.Compare.IS_NOT_AVAILABLE)
        assertThat(t.shouldRun()).isTrue()
    }

    @Test
    fun copyConstructorTest() {
        val t: TriggerBg = TriggerBg(injector).setUnits(GlucoseUnit.MGDL).setValue(213.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        val t1 = t.duplicate() as TriggerBg
        assertThat(t1.bg.value).isWithin(0.01).of(213.0)
        assertThat(t1.bg.units).isEqualTo(GlucoseUnit.MGDL)
        assertThat(t.comparator.value).isEqualTo(Comparator.Compare.IS_EQUAL_OR_LESSER)
    }

    private var bgJson = "{\"data\":{\"comparator\":\"IS_EQUAL\",\"bg\":4.1,\"units\":\"mmol\"},\"type\":\"TriggerBg\"}"

    @Test
    fun toJSONTest() {
        val t: TriggerBg = TriggerBg(injector).setUnits(GlucoseUnit.MMOL).setValue(4.1).comparator(Comparator.Compare.IS_EQUAL)
        JSONAssert.assertEquals(bgJson, t.toJSON(), true)
    }

    @Test
    fun fromJSONTest() {
        val t: TriggerBg = TriggerBg(injector).setUnits(GlucoseUnit.MMOL).setValue(4.1).comparator(Comparator.Compare.IS_EQUAL)
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerBg
        assertThat(t2.comparator.value).isEqualTo(Comparator.Compare.IS_EQUAL)
        assertThat(t2.bg.value).isWithin(0.01).of(4.1)
        assertThat(t2.bg.units).isEqualTo(GlucoseUnit.MMOL)
    }

    @Test
    fun iconTest() {
        assertThat(TriggerBg(injector).icon().get()).isEqualTo(app.aaps.core.objects.R.drawable.ic_cp_bgcheck)
    }

    private fun generateOneCurrentRecordBgData(): MutableList<InMemoryGlucoseValue> {
        val list: MutableList<InMemoryGlucoseValue> = ArrayList()
        list.add(InMemoryGlucoseValue(value = 214.0, timestamp = now - 1, trendArrow = TrendArrow.FLAT, sourceSensor = SourceSensor.UNKNOWN))
        return list
    }
}
