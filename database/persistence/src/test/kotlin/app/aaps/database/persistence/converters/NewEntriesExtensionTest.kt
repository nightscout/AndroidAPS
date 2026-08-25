package app.aaps.database.persistence.converters

import app.aaps.database.entities.GlucoseValue
import app.aaps.database.entities.HeartRate
import app.aaps.database.entities.data.NewEntries
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Tests for [NewEntries.fromDb] which maps the Room aggregate entity [NewEntries]
 * to the domain aggregate [app.aaps.core.data.model.NE].
 *
 * The mapping is forward-only and lossy: the entity fields `apsResults`,
 * `preferencesChanges`, `versionChanges` and `stepsCount` have no counterpart in
 * `NE`, so they are silently dropped. There is no `toDb()`, hence no round trip.
 * We therefore assert the forward direction: that each source list is routed to the
 * correct destination list and its elements are converted element-by-element.
 */
internal class NewEntriesExtensionTest {

    private fun emptyEntries() = NewEntries(
        apsResults = emptyList(),
        bolusCalculatorResults = emptyList(),
        boluses = emptyList(),
        carbs = emptyList(),
        effectiveProfileSwitches = emptyList(),
        extendedBoluses = emptyList(),
        glucoseValues = emptyList(),
        runningModes = emptyList(),
        preferencesChanges = emptyList(),
        profileSwitches = emptyList(),
        temporaryBasals = emptyList(),
        temporaryTarget = emptyList(),
        therapyEvents = emptyList(),
        totalDailyDoses = emptyList(),
        versionChanges = emptyList(),
        heartRates = emptyList(),
        stepsCount = emptyList()
    )

    @Test
    fun emptyEntriesMapToEmptyDomainLists() {
        val ne = emptyEntries().fromDb()

        assertThat(ne.bolusCalculatorResults).isEmpty()
        assertThat(ne.boluses).isEmpty()
        assertThat(ne.carbs).isEmpty()
        assertThat(ne.effectiveProfileSwitches).isEmpty()
        assertThat(ne.extendedBoluses).isEmpty()
        assertThat(ne.glucoseValues).isEmpty()
        assertThat(ne.runningModes).isEmpty()
        assertThat(ne.profileSwitches).isEmpty()
        assertThat(ne.temporaryBasals).isEmpty()
        assertThat(ne.temporaryTarget).isEmpty()
        assertThat(ne.therapyEvents).isEmpty()
        assertThat(ne.totalDailyDoses).isEmpty()
        assertThat(ne.heartRates).isEmpty()
    }

    @Test
    fun listsAreRoutedToTheMatchingDomainListAndConvertedElementwise() {
        val hr1 = HeartRate(
            id = 1L,
            duration = 60_000L,
            timestamp = 1_000L,
            beatsPerMinute = 72.0,
            device = "watch-A",
            utcOffset = 0L,
            version = 3,
            dateCreated = 10L,
            isValid = true,
            referenceId = null,
            interfaceIDs_backing = null
        )
        val hr2 = HeartRate(
            id = 2L,
            duration = 120_000L,
            timestamp = 2_000L,
            beatsPerMinute = 88.0,
            device = "watch-B",
            utcOffset = 0L,
            version = 4,
            dateCreated = 20L,
            isValid = false,
            referenceId = null,
            interfaceIDs_backing = null
        )
        val gv1 = GlucoseValue(
            id = 5L,
            version = 1,
            dateCreated = 30L,
            isValid = true,
            referenceId = null,
            timestamp = 3_000L,
            utcOffset = 0L,
            raw = 100.0,
            value = 123.0,
            trendArrow = GlucoseValue.TrendArrow.FLAT,
            noise = 0.5,
            sourceSensor = GlucoseValue.SourceSensor.RANDOM
        )

        val entries = emptyEntries().copy(
            heartRates = listOf(hr1, hr2),
            glucoseValues = listOf(gv1)
        )

        val ne = entries.fromDb()

        // heartRates list routed correctly, size and per-element fields preserved
        assertThat(ne.heartRates).hasSize(2)
        assertThat(ne.heartRates[0].id).isEqualTo(1L)
        assertThat(ne.heartRates[0].timestamp).isEqualTo(1_000L)
        assertThat(ne.heartRates[0].duration).isEqualTo(60_000L)
        assertThat(ne.heartRates[0].beatsPerMinute).isEqualTo(72.0)
        assertThat(ne.heartRates[0].device).isEqualTo("watch-A")
        assertThat(ne.heartRates[0].isValid).isTrue()
        assertThat(ne.heartRates[1].id).isEqualTo(2L)
        assertThat(ne.heartRates[1].beatsPerMinute).isEqualTo(88.0)
        assertThat(ne.heartRates[1].isValid).isFalse()

        // glucoseValues list routed correctly (not mixed up with any other list)
        assertThat(ne.glucoseValues).hasSize(1)
        assertThat(ne.glucoseValues[0].id).isEqualTo(5L)
        assertThat(ne.glucoseValues[0].value).isEqualTo(123.0)
        assertThat(ne.glucoseValues[0].timestamp).isEqualTo(3_000L)
        assertThat(ne.glucoseValues[0].raw).isEqualTo(100.0)
        assertThat(ne.glucoseValues[0].noise).isEqualTo(0.5)

        // every unrelated list stays empty (routing did not leak elements sideways)
        assertThat(ne.boluses).isEmpty()
        assertThat(ne.carbs).isEmpty()
        assertThat(ne.bolusCalculatorResults).isEmpty()
        assertThat(ne.effectiveProfileSwitches).isEmpty()
        assertThat(ne.extendedBoluses).isEmpty()
        assertThat(ne.runningModes).isEmpty()
        assertThat(ne.profileSwitches).isEmpty()
        assertThat(ne.temporaryBasals).isEmpty()
        assertThat(ne.temporaryTarget).isEmpty()
        assertThat(ne.therapyEvents).isEmpty()
        assertThat(ne.totalDailyDoses).isEmpty()
    }
}
