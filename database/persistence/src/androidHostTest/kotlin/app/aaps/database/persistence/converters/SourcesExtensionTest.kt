package app.aaps.database.persistence.converters

import app.aaps.core.data.ue.Sources
import app.aaps.database.entities.UserEntry
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

internal class SourcesExtensionTest {

    @Test
    fun everyDomainValueRoundTripsThroughDb() {
        // toDb().fromDb() must return the original for every domain enum value
        Sources.entries.forEach { source ->
            assertThat(source.toDb().fromDb()).isEqualTo(source)
        }
    }

    @Test
    fun everyEntityValueRoundTripsThroughDomain() {
        // fromDb().toDb() must return the original for every entity enum value
        UserEntry.Sources.entries.forEach { source ->
            assertThat(source.fromDb().toDb()).isEqualTo(source)
        }
    }

    @Test
    fun mapsRepresentativeValuesByName() {
        assertThat(Sources.TreatmentDialog.toDb()).isEqualTo(UserEntry.Sources.TreatmentDialog)
        assertThat(UserEntry.Sources.TreatmentDialog.fromDb()).isEqualTo(Sources.TreatmentDialog)

        assertThat(Sources.Unknown.toDb()).isEqualTo(UserEntry.Sources.Unknown)
        assertThat(UserEntry.Sources.Unknown.fromDb()).isEqualTo(Sources.Unknown)

        assertThat(Sources.Garmin.toDb()).isEqualTo(UserEntry.Sources.Garmin)
        assertThat(UserEntry.Sources.Garmin.fromDb()).isEqualTo(Sources.Garmin)
    }

    @Test
    fun bothEnumsHaveTheSameNumberOfValues() {
        // The mapping is 1:1 by name in both directions; guard against a value being added
        // to one enum without the converter/other enum being updated.
        assertThat(Sources.entries.size).isEqualTo(UserEntry.Sources.entries.size)
    }
}
