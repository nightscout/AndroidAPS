package app.aaps.database.persistence.converters

import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.TDD
import app.aaps.database.entities.TotalDailyDose
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

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
        assertEquals(original, back)

        // Explicit key scalar assertions.
        assertEquals(1L, back.id)
        assertEquals(2, back.version)
        assertEquals(1_000L, back.dateCreated)
        assertTrue(back.isValid)
        assertEquals(3L, back.referenceId)
        assertEquals(2_000L, back.timestamp)
        assertEquals(3_600_000L, back.utcOffset)
        assertEquals(4.5, back.basalAmount)
        assertEquals(6.5, back.bolusAmount)
        assertEquals(11.0, back.totalAmount)
        assertEquals(42.0, back.carbs)
        assertEquals(3.25, back.carbInsulin)

        // Nested ids survive the round trip.
        assertEquals("nsSystemId", back.ids.nightscoutSystemId)
        assertEquals("nsId", back.ids.nightscoutId)
        assertEquals("serial-123", back.ids.pumpSerial)
        assertEquals(10L, back.ids.temporaryId)
        assertEquals(11L, back.ids.pumpId)
        assertEquals(12L, back.ids.startId)
        assertEquals(13L, back.ids.endId)
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

        assertEquals(5L, back.id)
        assertEquals(7, back.version)
        assertEquals(5_000L, back.dateCreated)
        assertFalse(back.isValid)
        assertEquals(9L, back.referenceId)
        assertEquals(6_000L, back.timestamp)
        assertEquals(7_200_000L, back.utcOffset)
        assertEquals(1.25, back.basalAmount)
        assertEquals(2.75, back.bolusAmount)
        assertEquals(4.0, back.totalAmount)
        assertEquals(18.0, back.carbs)
        assertEquals(1.5, back.carbInsulin)
        assertEquals("nsId2", back.interfaceIDs_backing?.nightscoutId)
        assertEquals("serial-999", back.interfaceIDs_backing?.pumpSerial)
        assertEquals(21L, back.interfaceIDs_backing?.pumpId)
    }
}
