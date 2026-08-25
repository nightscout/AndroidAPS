package app.aaps.database.persistence.converters

import app.aaps.core.data.model.DS
import app.aaps.core.data.model.IDs
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.database.entities.DeviceStatus
import app.aaps.database.entities.embedments.InterfaceIDs
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

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

        assertThat(back.id).isEqualTo(1L)
        assertThat(back.timestamp).isEqualTo(1_000L)
        assertThat(back.utcOffset).isEqualTo(3_600_000L)
        assertThat(back.device).isEqualTo("deviceValue")
        assertThat(back.pump).isEqualTo("pumpValue")
        assertThat(back.enacted).isEqualTo("enactedValue")
        assertThat(back.suggested).isEqualTo("suggestedValue")
        assertThat(back.iob).isEqualTo("iobValue")
        assertThat(back.uploaderBattery).isEqualTo(77)
        assertThat(back.isCharging).isEqualTo(true)
        assertThat(back.configuration).isEqualTo("configValue")
    }

    @Test
    fun domainRoundTripKeepsNestedIds() {
        val original = fullDomain()
        val back = original.toDb().fromDb()

        assertThat(back.ids).isEqualTo(domainIds())
        assertThat(back.ids.pumpType).isEqualTo(PumpType.GENERIC_AAPS)
        assertThat(back.ids.nightscoutId).isEqualTo("nsId")
        assertThat(back.ids.pumpId).isEqualTo(20L)
    }

    @Test
    fun domainRoundTripIsLossless() {
        val original = fullDomain()
        val back = original.toDb().fromDb()

        assertThat(back).isEqualTo(original)
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

        assertThat(back.id).isEqualTo(2L)
        assertThat(back.timestamp).isEqualTo(2_000L)
        assertThat(back.utcOffset).isEqualTo(7_200_000L)
        assertThat(back.device).isEqualTo("d")
        assertThat(back.pump).isEqualTo("p")
        assertThat(back.enacted).isEqualTo("e")
        assertThat(back.suggested).isEqualTo("s")
        assertThat(back.iob).isEqualTo("i")
        assertThat(back.uploaderBattery).isEqualTo(42)
        assertThat(back.isCharging).isEqualTo(false)
        assertThat(back.configuration).isEqualTo("c")
        assertThat(back.interfaceIDs_backing).isEqualTo(entityIds())
    }
}
