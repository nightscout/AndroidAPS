package app.aaps.database.persistence.converters

import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.SC
import app.aaps.database.entities.StepsCount
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

internal class StepsCountExtensionTest {

    private fun sampleIds() = IDs(
        nightscoutSystemId = "nsSystem",
        nightscoutId = "nsId",
        pumpType = null, // PumpType round-trip is covered by its own converter test
        pumpSerial = "serial-123",
        temporaryId = 111L,
        pumpId = 222L,
        startId = 333L,
        endId = 444L
    )

    private fun sampleDomain() = SC(
        id = 1L,
        duration = 300_000L,
        timestamp = 1_000L,
        steps5min = 5,
        steps10min = 10,
        steps15min = 15,
        steps30min = 30,
        steps60min = 60,
        steps180min = 180,
        device = "watch",
        utcOffset = 3_600_000L,
        version = 2,
        dateCreated = 2_000L,
        isValid = true,
        referenceId = 99L,
        ids = sampleIds()
    )

    @Test
    fun domainRoundTripKeepsScalarFields() {
        val original = sampleDomain()

        val back = original.toDb().fromDb()

        assertEquals(1L, back.id)
        assertEquals(300_000L, back.duration)
        assertEquals(1_000L, back.timestamp)
        assertEquals(5, back.steps5min)
        assertEquals(10, back.steps10min)
        assertEquals(15, back.steps15min)
        assertEquals(30, back.steps30min)
        assertEquals(60, back.steps60min)
        assertEquals(180, back.steps180min)
        assertEquals("watch", back.device)
        assertEquals(3_600_000L, back.utcOffset)
        assertEquals(2, back.version)
        assertEquals(2_000L, back.dateCreated)
        assertTrue(back.isValid)
        assertEquals(99L, back.referenceId)
    }

    @Test
    fun domainRoundTripPreservesNestedIds() {
        val original = sampleDomain()

        val back = original.toDb().fromDb()

        assertEquals(sampleIds(), back.ids)
        assertEquals(222L, back.ids.pumpId)
        assertEquals("serial-123", back.ids.pumpSerial)
    }

    @Test
    fun domainRoundTripIsLossless() {
        val original = sampleDomain()

        val back = original.toDb().fromDb()

        assertEquals(original, back)
    }

    @Test
    fun entityRoundTripKeepsScalarFields() {
        val original = StepsCount(
            id = 7L,
            duration = 600_000L,
            timestamp = 5_000L,
            steps5min = 1,
            steps10min = 2,
            steps15min = 3,
            steps30min = 4,
            steps60min = 6,
            steps180min = 8,
            device = "phone",
            utcOffset = 7_200_000L,
            version = 4,
            dateCreated = 9_000L,
            isValid = false,
            referenceId = 55L,
            interfaceIDs_backing = sampleIds().toDb()
        )

        val back = original.fromDb().toDb()

        assertEquals(7L, back.id)
        assertEquals(600_000L, back.duration)
        assertEquals(5_000L, back.timestamp)
        assertEquals(1, back.steps5min)
        assertEquals(8, back.steps180min)
        assertEquals("phone", back.device)
        assertEquals(7_200_000L, back.utcOffset)
        assertEquals(4, back.version)
        assertEquals(9_000L, back.dateCreated)
        assertFalse(back.isValid)
        assertEquals(55L, back.referenceId)
        assertEquals(sampleIds().toDb(), back.interfaceIDs_backing)
    }
}
