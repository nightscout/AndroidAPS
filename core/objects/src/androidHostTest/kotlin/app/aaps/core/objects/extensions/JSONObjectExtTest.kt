package app.aaps.core.objects.extensions

import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Covers the [JSONObject] preference extensions (putIfThereIsValue skip rules + put/store round-trip)
 * and their kotlinx twins, including [with], which replaced writing into an already built document.
 */
class JSONObjectExtTest {

    @Test
    fun putIfThereIsValue_writesNonZeroSkipsZeroAndNull() {
        val j = JSONObject()
            .putIfThereIsValue("i", 5)
            .putIfThereIsValue("iz", 0)
            .putIfThereIsValue("inull", null as Int?)
            .putIfThereIsValue("l", 5L)
            .putIfThereIsValue("lz", 0L)
            .putIfThereIsValue("d", 1.5)
            .putIfThereIsValue("dz", 0.0)
            .putIfThereIsValue("s", "x")
            .putIfThereIsValue("se", "")
        assertThat(j.getInt("i")).isEqualTo(5)
        assertThat(j.has("iz")).isFalse()
        assertThat(j.has("inull")).isFalse()
        assertThat(j.getLong("l")).isEqualTo(5L)
        assertThat(j.has("lz")).isFalse()
        assertThat(j.getDouble("d")).isEqualTo(1.5)
        assertThat(j.has("dz")).isFalse()
        assertThat(j.getString("s")).isEqualTo("x")
        assertThat(j.has("se")).isFalse()
    }

    @Test
    fun putFromPreferences_writesEachType() {
        val prefs = mock<Preferences>()
        whenever(prefs.get(IntKey.OverviewCarbsButtonIncrement1)).thenReturn(7)
        whenever(prefs.get(DoubleKey.OverviewInsulinButtonIncrement1)).thenReturn(2.5)
        whenever(prefs.get(UnitDoubleKey.OverviewLowMark)).thenReturn(80.0)
        whenever(prefs.get(StringNonKey.QuickWizard)).thenReturn("qw")
        whenever(prefs.get(BooleanNonKey.GeneralSetupWizardProcessed)).thenReturn(true)

        val j = JSONObject()
            .put(IntKey.OverviewCarbsButtonIncrement1, prefs)
            .put(DoubleKey.OverviewInsulinButtonIncrement1, prefs)
            .put(UnitDoubleKey.OverviewLowMark, prefs)
            .put(StringNonKey.QuickWizard, prefs)
            .put(BooleanNonKey.GeneralSetupWizardProcessed, prefs)

        assertThat(j.getInt(IntKey.OverviewCarbsButtonIncrement1.key)).isEqualTo(7)
        assertThat(j.getDouble(DoubleKey.OverviewInsulinButtonIncrement1.key)).isEqualTo(2.5)
        assertThat(j.getDouble(UnitDoubleKey.OverviewLowMark.key)).isEqualTo(80.0)
        assertThat(j.getString(StringNonKey.QuickWizard.key)).isEqualTo("qw")
        assertThat(j.getBoolean(BooleanNonKey.GeneralSetupWizardProcessed.key)).isTrue()
    }

    @Test
    fun storeToPreferences_readsEachPresentKey() {
        val prefs = mock<Preferences>()
        JSONObject()
            .put(IntKey.OverviewCarbsButtonIncrement1.key, 3)
            .put(DoubleKey.OverviewInsulinButtonIncrement1.key, 1.1)
            .put(UnitDoubleKey.OverviewLowMark.key, 72.0)
            .put(StringNonKey.QuickWizard.key, "z")
            .put(BooleanNonKey.GeneralSetupWizardProcessed.key, true)
            .store(IntKey.OverviewCarbsButtonIncrement1, prefs)
            .store(DoubleKey.OverviewInsulinButtonIncrement1, prefs)
            .store(UnitDoubleKey.OverviewLowMark, prefs)
            .store(StringNonKey.QuickWizard, prefs)
            .store(BooleanNonKey.GeneralSetupWizardProcessed, prefs)

        verify(prefs).put(IntKey.OverviewCarbsButtonIncrement1, 3)
        verify(prefs).put(DoubleKey.OverviewInsulinButtonIncrement1, 1.1)
        verify(prefs).put(UnitDoubleKey.OverviewLowMark, 72.0)
        verify(prefs).put(StringNonKey.QuickWizard, "z")
        verify(prefs).put(BooleanNonKey.GeneralSetupWizardProcessed, true)
    }

    @Test
    fun store_skipsAbsentKey() {
        val prefs = mock<Preferences>()
        JSONObject().store(IntKey.OverviewCarbsButtonIncrement1, prefs) // key not present → no-op
        // nothing to verify beyond no exception; getInt on absent key would have thrown inside store
    }

    // region kotlinx twins

    @Test
    fun kotlinxPutIfThereIsValue_writesNonZeroSkipsZeroAndNull() {
        val j = buildJsonObject {
            putIfThereIsValue("i", 5)
            putIfThereIsValue("iz", 0)
            putIfThereIsValue("inull", null as Int?)
            putIfThereIsValue("l", 5L)
            putIfThereIsValue("lz", 0L)
            putIfThereIsValue("d", 1.5)
            putIfThereIsValue("dz", 0.0)
            putIfThereIsValue("s", "x")
            putIfThereIsValue("se", "")
        }
        assertThat(j.getValue("i").jsonPrimitive.int).isEqualTo(5)
        assertThat(j.containsKey("iz")).isFalse()
        assertThat(j.containsKey("inull")).isFalse()
        assertThat(j.getValue("l").jsonPrimitive.long).isEqualTo(5L)
        assertThat(j.containsKey("lz")).isFalse()
        assertThat(j.getValue("d").jsonPrimitive.double).isEqualTo(1.5)
        assertThat(j.containsKey("dz")).isFalse()
        assertThat(j.getValue("s").jsonPrimitive.content).isEqualTo("x")
        assertThat(j.containsKey("se")).isFalse()
    }

    /**
     * [with] replaces code that used to keep writing into a finished document, so it has to behave the
     * same way a repeated `put` did: entries add, and a repeat of an existing name replaces it.
     */
    @Test
    fun with_addsEntries() {
        val base = buildJsonObject { put("a", 1) }

        val merged = base.with { put("b", 2) }

        assertThat(merged.getValue("a").jsonPrimitive.int).isEqualTo(1)
        assertThat(merged.getValue("b").jsonPrimitive.int).isEqualTo(2)
    }

    @Test
    fun with_laterEntryWinsOverExistingOne() {
        val base = buildJsonObject { put("a", 1) }

        val merged = base.with { put("a", 99) }

        assertThat(merged.getValue("a").jsonPrimitive.int).isEqualTo(99)
        assertThat(merged).hasSize(1)
    }

    /** The source is a value: merging must hand back a new document and leave the old one alone. */
    @Test
    fun with_leavesTheSourceUntouched() {
        val base = buildJsonObject { put("a", 1) }

        base.with { put("b", 2) }

        assertThat(base.containsKey("b")).isFalse()
        assertThat(base).hasSize(1)
    }

    @Test
    fun with_addingNothingGivesAnEqualDocument() {
        val base = buildJsonObject { put("a", 1); put("b", "x") }

        assertThat(base.with { }).isEqualTo(base)
    }

    /** Nested values survive the copy - the merge is shallow, so the sub-document is carried over as is. */
    @Test
    fun with_keepsNestedDocuments() {
        val base = buildJsonObject { putJsonObject("inner") { put("deep", 7) } }

        val merged = base.with { put("a", 1) }

        assertThat(merged.getValue("inner").jsonObject.getValue("deep").jsonPrimitive.int).isEqualTo(7)
    }

    // endregion
}
