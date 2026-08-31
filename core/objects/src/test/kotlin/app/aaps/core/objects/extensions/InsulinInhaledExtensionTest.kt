package app.aaps.core.objects.extensions

import app.aaps.core.data.model.ICfg
import app.aaps.core.interfaces.insulin.InsulinType
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.json.JSONObject
import org.junit.jupiter.api.Test

/**
 * [ICfg.isInhaled] is stored, not re-derived from the peak - see the field's KDoc. These tests lock
 * down the two things that has to get right: the flag survives every persistence round-trip, and an
 * entry written before the field existed still comes back with the correct identity.
 */
class InsulinInhaledExtensionTest {

    private fun afrezza(peakMinutes: Int) =
        ICfg(insulinLabel = "Afrezza", peak = peakMinutes, dia = 3.0, concentration = 1.0, isInhaled = true)

    // ---- InsulinType.isInhaledPeak -------------------------------------------------------------

    @Test fun `isInhaledPeak covers the whole inhaled range and nothing above it`() {
        // LIMIT_PEAK_INHALED = 10..30, LIMIT_PEAK = 35..120 - disjoint, so the peak decides alone.
        for (m in intArrayOf(10, 15, 20, 30))
            assertThat(InsulinType.isInhaledPeak(m * 60_000L)).isTrue()
        for (m in intArrayOf(35, 45, 55, 75, 120))
            assertThat(InsulinType.isInhaledPeak(m * 60_000L)).isFalse()
    }

    @Test fun `the Afrezza template seeds an inhaled iCfg`() {
        assertThat(InsulinType.OREF_INHALED_AFREZZA.iCfg.isInhaled).isTrue()
        assertThat(InsulinType.OREF_RAPID_ACTING.iCfg.isInhaled).isFalse()
    }

    // ---- catalogue round-trips -----------------------------------------------------------------

    @Test fun `kotlinx round-trip preserves the flag at a non-default peak`() {
        // Peak 30 is the case that used to break: fromPeak() only matches Afrezza's factory 15 min.
        val restored = ICfg.fromJsonObject(afrezza(peakMinutes = 30).toJsonObject())

        assertThat(restored.isInhaled).isTrue()
        assertThat(restored.peak).isEqualTo(30)
        assertThat(restored.dia).isEqualTo(3.0)
    }

    @Test fun `org-json round-trip preserves the flag at a non-default peak`() {
        val restored = ICfg.fromJson(JSONObject(afrezza(peakMinutes = 30).toJson().toString()))

        assertThat(restored.isInhaled).isTrue()
        assertThat(restored.peak).isEqualTo(30)
    }

    @Test fun `a non-inhaled insulin stays non-inhaled through a round-trip`() {
        val fiasp = ICfg(insulinLabel = "Fiasp", peak = 55, dia = 8.0, concentration = 1.0)
        assertThat(ICfg.fromJsonObject(fiasp.toJsonObject()).isInhaled).isFalse()
        assertThat(ICfg.fromJson(JSONObject(fiasp.toJson().toString())).isInhaled).isFalse()
    }

    // ---- legacy entries, written before the field existed --------------------------------------

    @Test fun `legacy kotlinx entry without the key is reconstructed from the peak`() {
        val legacyInhaled = Json.decodeFromString<JsonObject>(
            """{"insulinLabel":"Afrezza","insulinEndTime":10800000,"insulinPeakTime":1800000,"concentration":1.0}"""
        )
        val legacyInjected = Json.decodeFromString<JsonObject>(
            """{"insulinLabel":"Fiasp","insulinEndTime":28800000,"insulinPeakTime":3300000,"concentration":1.0}"""
        )

        assertThat(ICfg.fromJsonObject(legacyInhaled).isInhaled).isTrue()   // peak 30 min
        assertThat(ICfg.fromJsonObject(legacyInjected).isInhaled).isFalse() // peak 55 min
    }

    @Test fun `legacy org-json entry without the key is reconstructed from the peak`() {
        val legacyInhaled = JSONObject("""{"insulinLabel":"Afrezza","insulinEndTime":10800000,"insulinPeakTime":1800000}""")
        val legacyInjected = JSONObject("""{"insulinLabel":"Fiasp","insulinEndTime":28800000,"insulinPeakTime":3300000}""")

        assertThat(ICfg.fromJson(legacyInhaled).isInhaled).isTrue()
        assertThat(ICfg.fromJson(legacyInjected).isInhaled).isFalse()
    }

    // ---- equality / cloning --------------------------------------------------------------------

    @Test fun `isEqual and deepClone account for the flag`() {
        val inhaled = afrezza(peakMinutes = 30)
        val sameButInjected = inhaled.deepClone().also { it.isInhaled = false }

        assertThat(inhaled.deepClone().isInhaled).isTrue()
        assertThat(inhaled.isEqual(inhaled.deepClone())).isTrue()
        assertThat(inhaled.isEqual(sameButInjected)).isFalse()
    }
}
