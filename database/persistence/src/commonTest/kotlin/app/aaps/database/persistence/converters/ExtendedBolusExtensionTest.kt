package app.aaps.database.persistence.converters

import app.aaps.core.data.model.EB
import app.aaps.core.data.model.IDs
import app.aaps.database.entities.ExtendedBolus
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

internal class ExtendedBolusExtensionTest {

    private fun sampleIds() = IDs(
        nightscoutSystemId = "nsSystemId",
        nightscoutId = "nsId",
        pumpType = null,
        pumpSerial = "serial-123",
        temporaryId = 10L,
        pumpId = 20L,
        startId = 30L,
        endId = 40L
    )

    private fun sampleEb() = EB(
        id = 1L,
        version = 2,
        dateCreated = 3L,
        isValid = true,
        referenceId = 4L,
        ids = sampleIds(),
        timestamp = 1_000L,
        utcOffset = 3_600_000L,
        duration = 1_800_000L,
        amount = 2.5,
        isEmulatingTempBasal = true
    )

    @Test
    fun roundTripDomainToDbAndBack() {
        val original = sampleEb()
        val back = original.toDb().fromDb()

        assertEquals(1L, back.id)
        assertEquals(2, back.version)
        assertEquals(3L, back.dateCreated)
        assertTrue(back.isValid)
        assertEquals(4L, back.referenceId)
        assertEquals(1_000L, back.timestamp)
        assertEquals(3_600_000L, back.utcOffset)
        assertEquals(1_800_000L, back.duration)
        assertEquals(2.5, back.amount)
        assertTrue(back.isEmulatingTempBasal)
        // nested IDs mapping round-trips losslessly (pumpType left null)
        assertEquals(sampleIds(), back.ids)
        // EB has value equality and the mapping is lossless
        assertEquals(original, back)
    }

    @Test
    fun toDbCopiesAllFields() {
        val entity: ExtendedBolus = sampleEb().toDb()

        assertEquals(1L, entity.id)
        assertEquals(2, entity.version)
        assertEquals(3L, entity.dateCreated)
        assertTrue(entity.isValid)
        assertEquals(4L, entity.referenceId)
        assertEquals(1_000L, entity.timestamp)
        assertEquals(3_600_000L, entity.utcOffset)
        assertEquals(1_800_000L, entity.duration)
        assertEquals(2.5, entity.amount)
        assertTrue(entity.isEmulatingTempBasal)
        assertEquals("serial-123", entity.interfaceIDs_backing?.pumpSerial)
        assertEquals(20L, entity.interfaceIDs_backing?.pumpId)
    }

    @Test
    fun roundTripEntityToDomainAndBack() {
        val original: ExtendedBolus = sampleEb().toDb()
        val back = original.fromDb().toDb()

        assertEquals(1L, back.id)
        assertEquals(2, back.version)
        assertEquals(3L, back.dateCreated)
        assertTrue(back.isValid)
        assertEquals(4L, back.referenceId)
        assertEquals(1_000L, back.timestamp)
        assertEquals(3_600_000L, back.utcOffset)
        assertEquals(1_800_000L, back.duration)
        assertEquals(2.5, back.amount)
        assertTrue(back.isEmulatingTempBasal)
        assertEquals(30L, back.interfaceIDs_backing?.startId)
        assertEquals(40L, back.interfaceIDs_backing?.endId)
    }
}
