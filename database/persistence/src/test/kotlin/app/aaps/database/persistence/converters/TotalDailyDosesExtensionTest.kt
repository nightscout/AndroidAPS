package app.aaps.database.persistence.converters

import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.TDD
import app.aaps.database.entities.TotalDailyDose
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

internal class TotalDailyDosesExtensionTest {

    private fun sampleDomain(): TDD =
        TDD(
            id = 1L,
            version = 2,
            dateCreated = 1_000L,
            isValid = true,
            referenceId = 3L,
            ids = IDs(
                nightscoutSystemId = "nsSystemId",
                nightscoutId = "nsId",
                pumpType = null,
                pumpSerial = "serial-123",
                temporaryId = 10L,
                pumpId = 11L,
                startId = 12L,
                endId = 13L
            ),
            timestamp = 2_000L,
            utcOffset = 3_600_000L,
            basalAmount = 4.5,
            bolusAmount = 6.5,
            totalAmount = 11.0,
            carbs = 42.0,
            carbInsulin = 3.25
        )

    @Test
    fun roundTripDomainToDbAndBack() {
        val original = sampleDomain()

        val back = original.toDb().fromDb()

        // Value-equal data class with a lossless mapping (nested IDs round-trips too).
        assertThat(back).isEqualTo(original)

        // Explicit key scalar assertions.
        assertThat(back.id).isEqualTo(1L)
        assertThat(back.version).isEqualTo(2)
        assertThat(back.dateCreated).isEqualTo(1_000L)
        assertThat(back.isValid).isTrue()
        assertThat(back.referenceId).isEqualTo(3L)
        assertThat(back.timestamp).isEqualTo(2_000L)
        assertThat(back.utcOffset).isEqualTo(3_600_000L)
        assertThat(back.basalAmount).isEqualTo(4.5)
        assertThat(back.bolusAmount).isEqualTo(6.5)
        assertThat(back.totalAmount).isEqualTo(11.0)
        assertThat(back.carbs).isEqualTo(42.0)
        assertThat(back.carbInsulin).isEqualTo(3.25)

        // Nested ids survive the round trip.
        assertThat(back.ids.nightscoutSystemId).isEqualTo("nsSystemId")
        assertThat(back.ids.nightscoutId).isEqualTo("nsId")
        assertThat(back.ids.pumpSerial).isEqualTo("serial-123")
        assertThat(back.ids.temporaryId).isEqualTo(10L)
        assertThat(back.ids.pumpId).isEqualTo(11L)
        assertThat(back.ids.startId).isEqualTo(12L)
        assertThat(back.ids.endId).isEqualTo(13L)
    }

    @Test
    fun roundTripDbToDomainAndBack() {
        val entity = TotalDailyDose(
            id = 5L,
            version = 7,
            dateCreated = 5_000L,
            isValid = false,
            referenceId = 9L,
            interfaceIDs_backing = IDs(
                nightscoutId = "nsId2",
                pumpSerial = "serial-999",
                pumpId = 21L
            ).toDb(),
            timestamp = 6_000L,
            utcOffset = 7_200_000L,
            basalAmount = 1.25,
            bolusAmount = 2.75,
            totalAmount = 4.0,
            carbs = 18.0,
            carbInsulin = 1.5
        )

        val back = entity.fromDb().toDb()

        assertThat(back.id).isEqualTo(5L)
        assertThat(back.version).isEqualTo(7)
        assertThat(back.dateCreated).isEqualTo(5_000L)
        assertThat(back.isValid).isFalse()
        assertThat(back.referenceId).isEqualTo(9L)
        assertThat(back.timestamp).isEqualTo(6_000L)
        assertThat(back.utcOffset).isEqualTo(7_200_000L)
        assertThat(back.basalAmount).isEqualTo(1.25)
        assertThat(back.bolusAmount).isEqualTo(2.75)
        assertThat(back.totalAmount).isEqualTo(4.0)
        assertThat(back.carbs).isEqualTo(18.0)
        assertThat(back.carbInsulin).isEqualTo(1.5)
        assertThat(back.interfaceIDs_backing?.nightscoutId).isEqualTo("nsId2")
        assertThat(back.interfaceIDs_backing?.pumpSerial).isEqualTo("serial-999")
        assertThat(back.interfaceIDs_backing?.pumpId).isEqualTo(21L)
    }
}
