package app.aaps.database.persistence.converters

import app.aaps.core.data.model.DS
import app.aaps.core.data.model.IDs
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.database.entities.DeviceStatus
import app.aaps.database.entities.embedments.InterfaceIDs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

internal class DeviceStatusExtensionTest {

    private fun domainIds(): IDs =
        IDs(
            nightscoutSystemId = "nsSystem",
            nightscoutId = "nsId",
            pumpType = PumpType.GENERIC_AAPS,
            pumpSerial = "serial-123",
            temporaryId = 10L,
            pumpId = 20L,
            startId = 30L,
            endId = 40L
        )

    private fun entityIds(): InterfaceIDs =
        InterfaceIDs(
            nightscoutSystemId = "nsSystem",
            nightscoutId = "nsId",
            pumpType = InterfaceIDs.PumpType.GENERIC_AAPS,
            pumpSerial = "serial-123",
            temporaryId = 10L,
            pumpId = 20L,
            startId = 30L,
            endId = 40L
        )

    private fun fullDomain(): DS =
        DS(
            id = 1L,
            ids = domainIds(),
            timestamp = 1_000L,
            utcOffset = 3_600_000L,
            device = "deviceValue",
            pump = "pumpValue",
            enacted = "enactedValue",
            suggested = "suggestedValue",
            iob = "iobValue",
            uploaderBattery = 77,
            isCharging = true,
            configuration = "configValue"
        )

    @Test
    fun domainRoundTripKeepsScalarFields() {
        val original = fullDomain()
        val back = original.toDb().fromDb()

        assertEquals(1L, back.id)
        assertEquals(1_000L, back.timestamp)
        assertEquals(3_600_000L, back.utcOffset)
        assertEquals("deviceValue", back.device)
        assertEquals("pumpValue", back.pump)
        assertEquals("enactedValue", back.enacted)
        assertEquals("suggestedValue", back.suggested)
        assertEquals("iobValue", back.iob)
        assertEquals(77, back.uploaderBattery)
        assertEquals(true, back.isCharging)
        assertEquals("configValue", back.configuration)
    }

    @Test
    fun domainRoundTripKeepsNestedIds() {
        val original = fullDomain()
        val back = original.toDb().fromDb()

        assertEquals(domainIds(), back.ids)
        assertEquals(PumpType.GENERIC_AAPS, back.ids.pumpType)
        assertEquals("nsId", back.ids.nightscoutId)
        assertEquals(20L, back.ids.pumpId)
    }

    @Test
    fun domainRoundTripIsLossless() {
        val original = fullDomain()
        val back = original.toDb().fromDb()

        assertEquals(original, back)
    }

    @Test
    fun entityRoundTripKeepsScalarFields() {
        val original = DeviceStatus(
            id = 2L,
            interfaceIDs_backing = entityIds(),
            timestamp = 2_000L,
            utcOffset = 7_200_000L,
            device = "d",
            pump = "p",
            enacted = "e",
            suggested = "s",
            iob = "i",
            uploaderBattery = 42,
            isCharging = false,
            configuration = "c"
        )
        val back = original.fromDb().toDb()

        assertEquals(2L, back.id)
        assertEquals(2_000L, back.timestamp)
        assertEquals(7_200_000L, back.utcOffset)
        assertEquals("d", back.device)
        assertEquals("p", back.pump)
        assertEquals("e", back.enacted)
        assertEquals("s", back.suggested)
        assertEquals("i", back.iob)
        assertEquals(42, back.uploaderBattery)
        assertEquals(false, back.isCharging)
        assertEquals("c", back.configuration)
        assertEquals(entityIds(), back.interfaceIDs_backing)
    }
}
