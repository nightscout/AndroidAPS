package app.aaps.database.persistence.converters

import app.aaps.database.entities.GlucoseValue
import app.aaps.database.entities.HeartRate
import app.aaps.database.entities.data.NewEntries
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

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

        assertTrue((ne.bolusCalculatorResults).isEmpty())
        assertTrue((ne.boluses).isEmpty())
        assertTrue((ne.carbs).isEmpty())
        assertTrue((ne.effectiveProfileSwitches).isEmpty())
        assertTrue((ne.extendedBoluses).isEmpty())
        assertTrue((ne.glucoseValues).isEmpty())
        assertTrue((ne.runningModes).isEmpty())
        assertTrue((ne.profileSwitches).isEmpty())
        assertTrue((ne.temporaryBasals).isEmpty())
        assertTrue((ne.temporaryTarget).isEmpty())
        assertTrue((ne.therapyEvents).isEmpty())
        assertTrue((ne.totalDailyDoses).isEmpty())
        assertTrue((ne.heartRates).isEmpty())
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
        assertEquals(2, (ne.heartRates).size)
        assertEquals(1L, ne.heartRates[0].id)
        assertEquals(1_000L, ne.heartRates[0].timestamp)
        assertEquals(60_000L, ne.heartRates[0].duration)
        assertEquals(72.0, ne.heartRates[0].beatsPerMinute)
        assertEquals("watch-A", ne.heartRates[0].device)
        assertTrue(ne.heartRates[0].isValid)
        assertEquals(2L, ne.heartRates[1].id)
        assertEquals(88.0, ne.heartRates[1].beatsPerMinute)
        assertFalse(ne.heartRates[1].isValid)

        // glucoseValues list routed correctly (not mixed up with any other list)
        assertEquals(1, (ne.glucoseValues).size)
        assertEquals(5L, ne.glucoseValues[0].id)
        assertEquals(123.0, ne.glucoseValues[0].value)
        assertEquals(3_000L, ne.glucoseValues[0].timestamp)
        assertEquals(100.0, ne.glucoseValues[0].raw)
        assertEquals(0.5, ne.glucoseValues[0].noise)

        // every unrelated list stays empty (routing did not leak elements sideways)
        assertTrue((ne.boluses).isEmpty())
        assertTrue((ne.carbs).isEmpty())
        assertTrue((ne.bolusCalculatorResults).isEmpty())
        assertTrue((ne.effectiveProfileSwitches).isEmpty())
        assertTrue((ne.extendedBoluses).isEmpty())
        assertTrue((ne.runningModes).isEmpty())
        assertTrue((ne.profileSwitches).isEmpty())
        assertTrue((ne.temporaryBasals).isEmpty())
        assertTrue((ne.temporaryTarget).isEmpty())
        assertTrue((ne.therapyEvents).isEmpty())
        assertTrue((ne.totalDailyDoses).isEmpty())
    }
}
