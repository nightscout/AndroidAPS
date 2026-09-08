package app.aaps.database.persistence.converters

import app.aaps.core.data.model.HR
import app.aaps.core.data.model.IDs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

internal class HeartRateExtensionTest {

    private fun sampleDomain(): HR = HR(
        id = 10L,
        duration = 60_000L,
        timestamp = 1_000L,
        beatsPerMinute = 72.5,
        device = "watch-device",
        utcOffset = 3_600_000L,
        version = 3,
        dateCreated = 2_000L,
        isValid = true,
        referenceId = 42L,
        ids = IDs(
            nightscoutSystemId = "ns-sys",
            nightscoutId = "ns-id",
            pumpSerial = "serial-1",
            temporaryId = 111L,
            pumpId = 222L,
            startId = 333L,
            endId = 444L
        )
    )

    @Test
    fun domainToDbCopiesAllFields() {
        val domain = sampleDomain()
        val entity = domain.toDb()

        assertEquals(10L, entity.id)
        assertEquals(60_000L, entity.duration)
        assertEquals(1_000L, entity.timestamp)
        assertEquals(72.5, entity.beatsPerMinute)
        assertEquals("watch-device", entity.device)
        assertEquals(3_600_000L, entity.utcOffset)
        assertEquals(3, entity.version)
        assertEquals(2_000L, entity.dateCreated)
        assertTrue(entity.isValid)
        assertEquals(42L, entity.referenceId)
        assertEquals("ns-id", entity.interfaceIDs_backing?.nightscoutId)
        assertEquals(222L, entity.interfaceIDs_backing?.pumpId)
    }

    @Test
    fun roundTripDomainToDbAndBack() {
        val original = sampleDomain()
        val back = original.toDb().fromDb()

        assertEquals(original.id, back.id)
        assertEquals(original.duration, back.duration)
        assertEquals(original.timestamp, back.timestamp)
        assertEquals(original.beatsPerMinute, back.beatsPerMinute)
        assertEquals(original.device, back.device)
        assertEquals(original.utcOffset, back.utcOffset)
        assertEquals(original.version, back.version)
        assertEquals(original.dateCreated, back.dateCreated)
        assertEquals(original.isValid, back.isValid)
        assertEquals(original.referenceId, back.referenceId)
        assertEquals(original.ids, back.ids)
        // HR is a value-equality data class and the mapping is lossless.
        assertEquals(original, back)
    }

    @Test
    fun roundTripDbToDomainAndBack() {
        val entity = sampleDomain().toDb()
        val back = entity.fromDb().toDb()

        assertEquals(entity.id, back.id)
        assertEquals(entity.duration, back.duration)
        assertEquals(entity.timestamp, back.timestamp)
        assertEquals(entity.beatsPerMinute, back.beatsPerMinute)
        assertEquals(entity.device, back.device)
        assertEquals(entity.utcOffset, back.utcOffset)
        assertEquals(entity.version, back.version)
        assertEquals(entity.dateCreated, back.dateCreated)
        assertEquals(entity.isValid, back.isValid)
        assertEquals(entity.referenceId, back.referenceId)
        assertEquals(entity.interfaceIDs_backing, back.interfaceIDs_backing)
        assertEquals(entity, back)
    }
}
