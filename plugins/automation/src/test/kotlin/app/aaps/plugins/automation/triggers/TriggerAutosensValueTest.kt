package app.aaps.plugins.automation.triggers

import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.Comparator
import app.aaps.plugins.main.iob.iobCobCalculator.data.AutosensDataObject
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.Mockito.`when`

class TriggerAutosensValueTest : TriggerTestBase() {

    @Test fun shouldRunTest() {
        `when`(sp.getDouble(Mockito.eq(app.aaps.core.utils.R.string.key_openapsama_autosens_max), ArgumentMatchers.anyDouble())).thenReturn(1.2)
        `when`(sp.getDouble(Mockito.eq(app.aaps.core.utils.R.string.key_openapsama_autosens_min), ArgumentMatchers.anyDouble())).thenReturn(0.7)
        `when`(autosensDataStore.getLastAutosensData(anyObject(), anyObject(), anyObject())).thenReturn(generateAutosensData())
        var t = TriggerAutosensValue(injector)
        t.autosens.value = 110.0
        t.comparator.value = Comparator.Compare.IS_EQUAL
        Assertions.assertEquals(110.0, t.autosens.value, 0.01)
        Assertions.assertEquals(Comparator.Compare.IS_EQUAL, t.comparator.value)
        Assertions.assertFalse(t.shouldRun())
        t = TriggerAutosensValue(injector)
        t.autosens.value = 100.0
        t.comparator.value = Comparator.Compare.IS_EQUAL
        Assertions.assertEquals(100.0, t.autosens.value, 0.01)
        Assertions.assertTrue(t.shouldRun())
        t = TriggerAutosensValue(injector)
        t.autosens.value = 50.0
        t.comparator.value = Comparator.Compare.IS_EQUAL_OR_GREATER
        Assertions.assertTrue(t.shouldRun())
        t = TriggerAutosensValue(injector)
        t.autosens.value = 310.0
        t.comparator.value = Comparator.Compare.IS_EQUAL_OR_LESSER
        Assertions.assertTrue(t.shouldRun())
        t = TriggerAutosensValue(injector)
        t.autosens.value = 420.0
        t.comparator.value = Comparator.Compare.IS_EQUAL
        Assertions.assertFalse(t.shouldRun())
        t = TriggerAutosensValue(injector)
        t.autosens.value = 390.0
        t.comparator.value = Comparator.Compare.IS_EQUAL_OR_LESSER
        Assertions.assertTrue(t.shouldRun())
        t = TriggerAutosensValue(injector)
        t.autosens.value = 390.0
        t.comparator.value = Comparator.Compare.IS_EQUAL_OR_GREATER
        Assertions.assertFalse(t.shouldRun())
        t = TriggerAutosensValue(injector)
        t.autosens.value = 20.0
        t.comparator.value = Comparator.Compare.IS_EQUAL_OR_GREATER
        Assertions.assertTrue(t.shouldRun())
        t = TriggerAutosensValue(injector)
        t.autosens.value = 390.0
        t.comparator.value = Comparator.Compare.IS_EQUAL_OR_LESSER
        Assertions.assertTrue(t.shouldRun())
        `when`(autosensDataStore.getLastAutosensData(anyObject(), anyObject(), anyObject())).thenReturn(AutosensDataObject(injector))
        t = TriggerAutosensValue(injector)
        t.autosens.value = 80.0
        t.comparator.value = Comparator.Compare.IS_EQUAL_OR_LESSER
        Assertions.assertFalse(t.shouldRun())

        // Test autosensData == null and Comparator == IS_NOT_AVAILABLE
        `when`(autosensDataStore.getLastAutosensData(anyObject(), anyObject(), anyObject())).thenReturn(null)
        t = TriggerAutosensValue(injector)
        t.comparator.value = Comparator.Compare.IS_NOT_AVAILABLE
        Assertions.assertTrue(t.shouldRun())
    }

    @Test
    fun copyConstructorTest() {
        val t = TriggerAutosensValue(injector)
        t.autosens.value = 213.0
        t.comparator.value = Comparator.Compare.IS_EQUAL_OR_LESSER
        val t1 = t.duplicate() as TriggerAutosensValue
        Assertions.assertEquals(213.0, t1.autosens.value, 0.01)
        Assertions.assertEquals(Comparator.Compare.IS_EQUAL_OR_LESSER, t.comparator.value)
    }

    private var asJson = "{\"data\":{\"comparator\":\"IS_EQUAL\",\"value\":410},\"type\":\"TriggerAutosensValue\"}"

    @Test
    fun toJSONTest() {
        val t = TriggerAutosensValue(injector)
        t.autosens.value = 410.0
        t.comparator.value = Comparator.Compare.IS_EQUAL
        Assertions.assertEquals(asJson, t.toJSON())
    }

    @Test
    fun fromJSONTest() {
        val t = TriggerAutosensValue(injector)
        t.autosens.value = 410.0
        t.comparator.value = Comparator.Compare.IS_EQUAL
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerAutosensValue
        Assertions.assertEquals(Comparator.Compare.IS_EQUAL, t2.comparator.value)
        Assertions.assertEquals(410.0, t2.autosens.value, 0.01)
    }

    @Test fun iconTest() {
        assertThat(TriggerAutosensValue(injector).icon().get()).isEqualTo(R.drawable.ic_as)
    }

    private fun generateAutosensData(): AutosensDataObject {
        return AutosensDataObject(injector)
    }
}