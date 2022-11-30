package info.nightscout.automation.triggers

import com.google.common.base.Optional
import info.nightscout.automation.elements.Comparator
import info.nightscout.database.entities.GlucoseValue
import info.nightscout.interfaces.GlucoseUnit
import org.json.JSONObject
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`

class TriggerBgTest : TriggerTestBase() {

    var now = 1514766900000L

    @BeforeEach
    fun prepare() {
        `when`(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
        `when`(dateUtil.now()).thenReturn(now)
    }

    @Test
    fun shouldRunTest() {
        `when`(autosensDataStore.getBgReadingsDataTableCopy()).thenReturn(generateOneCurrentRecordBgData())
        var t: TriggerBg = TriggerBg(injector).setUnits(GlucoseUnit.MMOL).setValue(4.1).comparator(Comparator.Compare.IS_EQUAL)
        Assert.assertFalse(t.shouldRun())
        t = TriggerBg(injector).setUnits(GlucoseUnit.MGDL).setValue(214.0).comparator(Comparator.Compare.IS_EQUAL)
        Assert.assertTrue(t.shouldRun())
        t = TriggerBg(injector).setUnits(GlucoseUnit.MGDL).setValue(214.0).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        Assert.assertTrue(t.shouldRun())
        t = TriggerBg(injector).setUnits(GlucoseUnit.MGDL).setValue(214.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assert.assertTrue(t.shouldRun())
        t = TriggerBg(injector).setUnits(GlucoseUnit.MGDL).setValue(215.0).comparator(Comparator.Compare.IS_EQUAL)
        Assert.assertFalse(t.shouldRun())
        t = TriggerBg(injector).setUnits(GlucoseUnit.MGDL).setValue(215.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assert.assertTrue(t.shouldRun())
        t = TriggerBg(injector).setUnits(GlucoseUnit.MGDL).setValue(215.0).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        Assert.assertFalse(t.shouldRun())
        t = TriggerBg(injector).setUnits(GlucoseUnit.MGDL).setValue(213.0).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        Assert.assertTrue(t.shouldRun())
        t = TriggerBg(injector).setUnits(GlucoseUnit.MGDL).setValue(213.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assert.assertFalse(t.shouldRun())
        `when`(autosensDataStore.getBgReadingsDataTableCopy()).thenReturn(ArrayList())
        t = TriggerBg(injector).setUnits(GlucoseUnit.MGDL).setValue(213.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assert.assertFalse(t.shouldRun())
        t = TriggerBg(injector).comparator(Comparator.Compare.IS_NOT_AVAILABLE)
        Assert.assertTrue(t.shouldRun())
    }

    @Test
    fun copyConstructorTest() {
        val t: TriggerBg = TriggerBg(injector).setUnits(GlucoseUnit.MGDL).setValue(213.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        val t1 = t.duplicate() as TriggerBg
        Assert.assertEquals(213.0, t1.bg.value, 0.01)
        Assert.assertEquals(GlucoseUnit.MGDL, t1.bg.units)
        Assert.assertEquals(Comparator.Compare.IS_EQUAL_OR_LESSER, t.comparator.value)
    }

    private var bgJson = "{\"data\":{\"comparator\":\"IS_EQUAL\",\"bg\":4.1,\"units\":\"mmol\"},\"type\":\"TriggerBg\"}"

    @Test
    fun toJSONTest() {
        val t: TriggerBg = TriggerBg(injector).setUnits(GlucoseUnit.MMOL).setValue(4.1).comparator(Comparator.Compare.IS_EQUAL)
        Assert.assertEquals(bgJson, t.toJSON())
    }

    @Test
    fun fromJSONTest() {
        val t: TriggerBg = TriggerBg(injector).setUnits(GlucoseUnit.MMOL).setValue(4.1).comparator(Comparator.Compare.IS_EQUAL)
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerBg
        Assert.assertEquals(Comparator.Compare.IS_EQUAL, t2.comparator.value)
        Assert.assertEquals(4.1, t2.bg.value, 0.01)
        Assert.assertEquals(GlucoseUnit.MMOL, t2.bg.units)
    }

    @Test
    fun iconTest() {
        Assert.assertEquals(Optional.of(info.nightscout.core.main.R.drawable.ic_cp_bgcheck), TriggerBg(injector).icon())
    }

    private fun generateOneCurrentRecordBgData(): List<GlucoseValue> {
        val list: MutableList<GlucoseValue> = ArrayList()
        list.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 214.0,
                timestamp = now - 1,
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        return list
    }
}