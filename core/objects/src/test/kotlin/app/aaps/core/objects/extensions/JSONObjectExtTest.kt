package app.aaps.core.objects.extensions

import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Covers the org.json [JSONObject] preference extensions: putIfThereIsValue skip rules + put/store round-trip. */
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
}
