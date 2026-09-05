package app.aaps.database.persistence.converters

import app.aaps.core.data.model.CAL
import app.aaps.core.data.model.IDs
import app.aaps.database.entities.CalibrationEntry
import app.aaps.database.entities.embedments.InterfaceIDs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

internal class CalibrationEntryExtensionTest {

    @Test
    fun domainRoundTripKeepsAllFields() {
        val original = CAL(
            id = 1L,
            version = 2,
            dateCreated = 3_000L,
            isValid = false,
            referenceId = 4L,
            ids = IDs(
                nightscoutSystemId = "nsSys",
                nightscoutId = "nsId",
                pumpType = null,
                pumpSerial = "serial-1",
                temporaryId = 10L,
                pumpId = 20L,
                startId = 30L,
                endId = 40L
            ),
            timestamp = 1_000L,
            utcOffset = 3_600_000L,
            fingerstickMgdl = 123.4,
            sensorMgdlAtPairing = 118.7
        )

        val back = original.toDb().fromDb()

        assertEquals(1L, back.id)
        assertEquals(2, back.version)
        assertEquals(3_000L, back.dateCreated)
        assertFalse(back.isValid)
        assertEquals(4L, back.referenceId)
        assertEquals(1_000L, back.timestamp)
        assertEquals(3_600_000L, back.utcOffset)
        assertEquals(123.4, back.fingerstickMgdl)
        assertEquals(118.7, back.sensorMgdlAtPairing)
        assertEquals("nsId", back.ids.nightscoutId)
        assertEquals("serial-1", back.ids.pumpSerial)
        assertEquals(20L, back.ids.pumpId)
        // Lossless mapping + value equality on data classes
        assertEquals(original, back)
    }

    @Test
    fun entityRoundTripKeepsAllFields() {
        val original = CalibrationEntry(
            id = 5L,
            version = 6,
            dateCreated = 7_000L,
            isValid = true,
            referenceId = 8L,
            interfaceIDs_backing = InterfaceIDs(
                nightscoutSystemId = "eSys",
                nightscoutId = "eNsId",
                pumpType = null,
                pumpSerial = "serial-2",
                temporaryId = 11L,
                pumpId = 21L,
                startId = 31L,
                endId = 41L
            ),
            timestamp = 2_000L,
            utcOffset = 7_200_000L,
            fingerstickMgdl = 99.5,
            sensorMgdlAtPairing = 101.2
        )

        val back = original.fromDb().toDb()

        assertEquals(5L, back.id)
        assertEquals(6, back.version)
        assertEquals(7_000L, back.dateCreated)
        assertTrue(back.isValid)
        assertEquals(8L, back.referenceId)
        assertEquals(2_000L, back.timestamp)
        assertEquals(7_200_000L, back.utcOffset)
        assertEquals(99.5, back.fingerstickMgdl)
        assertEquals(101.2, back.sensorMgdlAtPairing)
        assertEquals("eNsId", back.interfaceIDs.nightscoutId)
        assertEquals(21L, back.interfaceIDs.pumpId)
        assertEquals(original, back)
    }
}
