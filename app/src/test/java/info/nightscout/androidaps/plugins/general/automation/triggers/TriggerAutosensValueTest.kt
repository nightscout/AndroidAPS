package info.nightscout.androidaps.plugins.general.automation.triggers

import com.google.common.base.Optional
import info.nightscout.androidaps.R
import info.nightscout.androidaps.plugins.general.automation.elements.Comparator
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.data.AutosensData
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.androidaps.utils.DateUtil
import org.json.JSONObject
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(DateUtil::class, IobCobCalculatorPlugin::class)
class TriggerAutosensValueTest : TriggerTestBase() {

    var now = 1514766900000L
    @Test fun shouldRunTest() {
        `when`(sp.getDouble(Mockito.eq(R.string.key_openapsama_autosens_max), ArgumentMatchers.anyDouble())).thenReturn(1.2)
        `when`(sp.getDouble(Mockito.eq(R.string.key_openapsama_autosens_min), ArgumentMatchers.anyDouble())).thenReturn(0.7)
        `when`(iobCobCalculatorPlugin.getLastAutosensData("Automation trigger")).thenReturn(generateAutosensData())
        var t = TriggerAutosensValue(injector)
        t.autosens.value = 110.0
        t.comparator.value = Comparator.Compare.IS_EQUAL
        Assert.assertEquals(110.0, t.autosens.value, 0.01)
        Assert.assertEquals(Comparator.Compare.IS_EQUAL, t.comparator.value)
        Assert.assertFalse(t.shouldRun())
        t = TriggerAutosensValue(injector)
        t.autosens.value = 100.0
        t.comparator.value = Comparator.Compare.IS_EQUAL
        Assert.assertEquals(100.0, t.autosens.value, 0.01)
        Assert.assertTrue(t.shouldRun())
        t = TriggerAutosensValue(injector)
        t.autosens.value = 50.0
        t.comparator.value = Comparator.Compare.IS_EQUAL_OR_GREATER
        Assert.assertTrue(t.shouldRun())
        t = TriggerAutosensValue(injector)
        t.autosens.value = 310.0
        t.comparator.value = Comparator.Compare.IS_EQUAL_OR_LESSER
        Assert.assertTrue(t.shouldRun())
        t = TriggerAutosensValue(injector)
        t.autosens.value = 420.0
        t.comparator.value = Comparator.Compare.IS_EQUAL
        Assert.assertFalse(t.shouldRun())
        t = TriggerAutosensValue(injector)
        t.autosens.value = 390.0
        t.comparator.value = Comparator.Compare.IS_EQUAL_OR_LESSER
        Assert.assertTrue(t.shouldRun())
        t = TriggerAutosensValue(injector)
        t.autosens.value = 390.0
        t.comparator.value = Comparator.Compare.IS_EQUAL_OR_GREATER
        Assert.assertFalse(t.shouldRun())
        t = TriggerAutosensValue(injector)
        t.autosens.value = 20.0
        t.comparator.value = Comparator.Compare.IS_EQUAL_OR_GREATER
        Assert.assertTrue(t.shouldRun())
        t = TriggerAutosensValue(injector)
        t.autosens.value = 390.0
        t.comparator.value = Comparator.Compare.IS_EQUAL_OR_LESSER
        Assert.assertTrue(t.shouldRun())
        PowerMockito.`when`(iobCobCalculatorPlugin.getLastAutosensData("Automation trigger")).thenReturn(AutosensData(injector))
        t = TriggerAutosensValue(injector)
        t.autosens.value = 80.0
        t.comparator.value = Comparator.Compare.IS_EQUAL_OR_LESSER
        Assert.assertFalse(t.shouldRun())

        // Test autosensData == null and Comparator == IS_NOT_AVAILABLE
        PowerMockito.`when`(iobCobCalculatorPlugin.getLastAutosensData("Automation trigger")).thenReturn(null)
        t = TriggerAutosensValue(injector)
        t.comparator.value = Comparator.Compare.IS_NOT_AVAILABLE
        Assert.assertTrue(t.shouldRun())
    }

    @Test
    fun copyConstructorTest() {
        val t = TriggerAutosensValue(injector)
        t.autosens.value = 213.0
        t.comparator.value = Comparator.Compare.IS_EQUAL_OR_LESSER
        val t1 = t.duplicate() as TriggerAutosensValue
        Assert.assertEquals(213.0, t1.autosens.value, 0.01)
        Assert.assertEquals(Comparator.Compare.IS_EQUAL_OR_LESSER, t.comparator.value)
    }

    private var asJson = "{\"data\":{\"comparator\":\"IS_EQUAL\",\"value\":410},\"type\":\"info.nightscout.androidaps.plugins.general.automation.triggers.TriggerAutosensValue\"}"

    @Test
    fun toJSONTest() {
        val t = TriggerAutosensValue(injector)
        t.autosens.value = 410.0
        t.comparator.value = Comparator.Compare.IS_EQUAL
        Assert.assertEquals(asJson, t.toJSON())
    }

    @Test
    fun fromJSONTest() {
        val t = TriggerAutosensValue(injector)
        t.autosens.value = 410.0
        t.comparator.value = Comparator.Compare.IS_EQUAL
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerAutosensValue
        Assert.assertEquals(Comparator.Compare.IS_EQUAL, t2.comparator.value)
        Assert.assertEquals(410.0, t2.autosens.value, 0.01)
    }

    @Test fun iconTest() {
        Assert.assertEquals(Optional.of(R.drawable.`ic_as`), TriggerAutosensValue(injector).icon())
    }

    @Before
    fun mock() {
        PowerMockito.mockStatic(DateUtil::class.java)
        PowerMockito.`when`(DateUtil.now()).thenReturn(now)
    }

    private fun generateAutosensData(): AutosensData {
        return AutosensData(injector)
    }
}