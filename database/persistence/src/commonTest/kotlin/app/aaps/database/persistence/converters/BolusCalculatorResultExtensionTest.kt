package app.aaps.database.persistence.converters

import app.aaps.core.data.model.BCR
import app.aaps.core.data.model.IDs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

internal class BolusCalculatorResultExtensionTest {

    private fun sampleIDs() = IDs(
        nightscoutSystemId = "nsSys",
        nightscoutId = "nsId",
        pumpType = null,
        pumpSerial = "serial-1",
        temporaryId = 11L,
        pumpId = 12L,
        startId = 13L,
        endId = 14L
    )

    private fun sampleDomain() = BCR(
        id = 1L,
        version = 2,
        dateCreated = 1_000L,
        isValid = true,
        referenceId = 3L,
        ids = sampleIDs(),
        timestamp = 2_000L,
        utcOffset = 3_600_000L,
        targetBGLow = 90.0,
        targetBGHigh = 110.0,
        isf = 45.0,
        ic = 8.0,
        bolusIOB = 1.5,
        wasBolusIOBUsed = true,
        basalIOB = 0.5,
        wasBasalIOBUsed = false,
        glucoseValue = 150.0,
        wasGlucoseUsed = true,
        glucoseDifference = 60.0,
        glucoseInsulin = 1.2,
        glucoseTrend = 5.0,
        wasTrendUsed = false,
        trendInsulin = 0.1,
        cob = 25.0,
        wasCOBUsed = true,
        cobInsulin = 3.1,
        carbs = 40.0,
        wereCarbsUsed = false,
        carbsInsulin = 5.0,
        otherCorrection = 0.25,
        wasSuperbolusUsed = true,
        superbolusInsulin = 2.0,
        wasTempTargetUsed = false,
        totalInsulin = 12.34,
        percentageCorrection = 90,
        profileName = "profile-A",
        note = "some note"
    )

    @Test
    fun roundTripDomainToDbAndBack() {
        val original = sampleDomain()

        val back = original.toDb().fromDb()

        // Data classes with value equality + lossless mapping -> full equality
        assertEquals(original, back)

        // Explicit key-field assertions (robust against embedded/backing differences)
        assertEquals(1L, back.id)
        assertEquals(2, back.version)
        assertEquals(1_000L, back.dateCreated)
        assertTrue(back.isValid)
        assertEquals(3L, back.referenceId)
        assertEquals(2_000L, back.timestamp)
        assertEquals(3_600_000L, back.utcOffset)
        assertEquals(90.0, back.targetBGLow)
        assertEquals(110.0, back.targetBGHigh)
        assertEquals(45.0, back.isf)
        assertEquals(8.0, back.ic)
        assertEquals(12.34, back.totalInsulin)
        assertEquals(90, back.percentageCorrection)
        assertEquals("profile-A", back.profileName)
        assertEquals("some note", back.note)

        // Nested IDs round-trips
        assertEquals("nsId", back.ids.nightscoutId)
        assertEquals("serial-1", back.ids.pumpSerial)
        assertEquals(11L, back.ids.temporaryId)
        assertEquals(12L, back.ids.pumpId)
        assertEquals(13L, back.ids.startId)
        assertEquals(14L, back.ids.endId)
    }

    @Test
    fun roundTripDbToDomainAndBack() {
        val entity = sampleDomain().toDb()

        val back = entity.fromDb().toDb()

        assertEquals(entity, back)
        assertEquals(1L, back.id)
        assertEquals(2_000L, back.timestamp)
        assertTrue(back.isValid)
        assertEquals(40.0, back.carbs)
        assertFalse(back.wereCarbsUsed)
        assertTrue(back.wasSuperbolusUsed)
        assertEquals("profile-A", back.profileName)
        assertEquals("nsId", back.interfaceIDs.nightscoutId)
        assertEquals(12L, back.interfaceIDs.pumpId)
    }

    @Test
    fun booleanFlagsMapDistinctly() {
        val back = sampleDomain().toDb().fromDb()

        assertTrue(back.wasBolusIOBUsed)
        assertFalse(back.wasBasalIOBUsed)
        assertTrue(back.wasGlucoseUsed)
        assertFalse(back.wasTrendUsed)
        assertTrue(back.wasCOBUsed)
        assertFalse(back.wereCarbsUsed)
        assertTrue(back.wasSuperbolusUsed)
        assertFalse(back.wasTempTargetUsed)
    }
}
