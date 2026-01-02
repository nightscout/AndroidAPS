package app.aaps.plugins.automation.triggers

import app.aaps.core.keys.DoubleKey
import app.aaps.implementation.iob.AutosensDataObject
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.Comparator
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.skyscreamer.jsonassert.JSONAssert

class TriggerAutosensValueTest : TriggerTestBase() {

    @Test fun shouldRunTest() {
        whenever(preferences.get(DoubleKey.AutosensMax)).thenReturn(1.2)
        whenever(preferences.get(DoubleKey.AutosensMin)).thenReturn(0.7)
        whenever(autosensDataStore.getLastAutosensData(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(generateAutosensData())
        var t = TriggerAutosensValue(injector)
        t.autosens.value = 110.0
        t.comparator.value = Comparator.Compare.IS_EQUAL
        assertThat(t.autosens.value).isWithin(0.01).of(110.0)
        assertThat(t.comparator.value).isEqualTo(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isFalse()
        t = TriggerAutosensValue(injector)
        t.autosens.value = 100.0
        t.comparator.value = Comparator.Compare.IS_EQUAL
        assertThat(t.autosens.value).isWithin(0.01).of(100.0)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerAutosensValue(injector)
        t.autosens.value = 50.0
        t.comparator.value = Comparator.Compare.IS_EQUAL_OR_GREATER
        assertThat(t.shouldRun()).isTrue()
        t = TriggerAutosensValue(injector)
        t.autosens.value = 310.0
        t.comparator.value = Comparator.Compare.IS_EQUAL_OR_LESSER
        assertThat(t.shouldRun()).isTrue()
        t = TriggerAutosensValue(injector)
        t.autosens.value = 420.0
        t.comparator.value = Comparator.Compare.IS_EQUAL
        assertThat(t.shouldRun()).isFalse()
        t = TriggerAutosensValue(injector)
        t.autosens.value = 390.0
        t.comparator.value = Comparator.Compare.IS_EQUAL_OR_LESSER
        assertThat(t.shouldRun()).isTrue()
        t = TriggerAutosensValue(injector)
        t.autosens.value = 390.0
        t.comparator.value = Comparator.Compare.IS_EQUAL_OR_GREATER
        assertThat(t.shouldRun()).isFalse()
        t = TriggerAutosensValue(injector)
        t.autosens.value = 20.0
        t.comparator.value = Comparator.Compare.IS_EQUAL_OR_GREATER
        assertThat(t.shouldRun()).isTrue()
        t = TriggerAutosensValue(injector)
        t.autosens.value = 390.0
        t.comparator.value = Comparator.Compare.IS_EQUAL_OR_LESSER
        assertThat(t.shouldRun()).isTrue()
        whenever(autosensDataStore.getLastAutosensData(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(AutosensDataObject(aapsLogger, preferences, dateUtil))
        t = TriggerAutosensValue(injector)
        t.autosens.value = 80.0
        t.comparator.value = Comparator.Compare.IS_EQUAL_OR_LESSER
        assertThat(t.shouldRun()).isFalse()

        // Test autosensData == null and Comparator == IS_NOT_AVAILABLE
        whenever(autosensDataStore.getLastAutosensData(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(null)
        t = TriggerAutosensValue(injector)
        t.comparator.value = Comparator.Compare.IS_NOT_AVAILABLE
        assertThat(t.shouldRun()).isTrue()
    }

    @Test
    fun copyConstructorTest() {
        val t = TriggerAutosensValue(injector)
        t.autosens.value = 213.0
        t.comparator.value = Comparator.Compare.IS_EQUAL_OR_LESSER
        val t1 = t.duplicate() as TriggerAutosensValue
        assertThat(t1.autosens.value).isWithin(0.01).of(213.0)
        assertThat(t.comparator.value).isEqualTo(Comparator.Compare.IS_EQUAL_OR_LESSER)
    }

    private var asJson = "{\"data\":{\"comparator\":\"IS_EQUAL\",\"value\":410},\"type\":\"TriggerAutosensValue\"}"

    @Test
    fun toJSONTest() {
        val t = TriggerAutosensValue(injector)
        t.autosens.value = 410.0
        t.comparator.value = Comparator.Compare.IS_EQUAL
        JSONAssert.assertEquals(asJson, t.toJSON(), true)
    }

    @Test
    fun fromJSONTest() {
        val t = TriggerAutosensValue(injector)
        t.autosens.value = 410.0
        t.comparator.value = Comparator.Compare.IS_EQUAL
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerAutosensValue
        assertThat(t2.comparator.value).isEqualTo(Comparator.Compare.IS_EQUAL)
        assertThat(t2.autosens.value).isWithin(0.01).of(410.0)
    }

    @Test fun iconTest() {
        assertThat(TriggerAutosensValue(injector).icon().get()).isEqualTo(R.drawable.ic_as)
    }

    private fun generateAutosensData() = AutosensDataObject(aapsLogger, preferences, dateUtil)
}
