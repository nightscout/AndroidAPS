package app.aaps.database.persistence.converters

import app.aaps.core.data.ue.Sources
import app.aaps.database.entities.UserEntry
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

internal class SourcesExtensionTest {

    @Test
    fun everyDomainValueRoundTripsThroughDb() {
        // toDb().fromDb() must return the original for every domain enum value
        Sources.entries.forEach { source ->
            assertEquals(source, source.toDb().fromDb())
        }
    }

    @Test
    fun everyEntityValueRoundTripsThroughDomain() {
        // fromDb().toDb() must return the original for every entity enum value
        UserEntry.Sources.entries.forEach { source ->
            assertEquals(source, source.fromDb().toDb())
        }
    }

    @Test
    fun mapsRepresentativeValuesByName() {
        assertEquals(UserEntry.Sources.TreatmentDialog, Sources.TreatmentDialog.toDb())
        assertEquals(Sources.TreatmentDialog, UserEntry.Sources.TreatmentDialog.fromDb())

        assertEquals(UserEntry.Sources.Unknown, Sources.Unknown.toDb())
        assertEquals(Sources.Unknown, UserEntry.Sources.Unknown.fromDb())

        assertEquals(UserEntry.Sources.Garmin, Sources.Garmin.toDb())
        assertEquals(Sources.Garmin, UserEntry.Sources.Garmin.fromDb())
    }

    @Test
    fun bothEnumsHaveTheSameNumberOfValues() {
        // The mapping is 1:1 by name in both directions; guard against a value being added
        // to one enum without the converter/other enum being updated.
        assertEquals(UserEntry.Sources.entries.size, Sources.entries.size)
    }
}
