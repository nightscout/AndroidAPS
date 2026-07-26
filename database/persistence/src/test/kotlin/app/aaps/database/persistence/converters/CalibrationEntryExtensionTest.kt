package app.aaps.database.persistence.converters

import app.aaps.core.data.model.CAL
import app.aaps.core.data.model.IDs
import app.aaps.database.entities.CalibrationEntry
import app.aaps.database.entities.embedments.InterfaceIDs
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

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

        assertThat(back.id).isEqualTo(1L)
        assertThat(back.version).isEqualTo(2)
        assertThat(back.dateCreated).isEqualTo(3_000L)
        assertThat(back.isValid).isFalse()
        assertThat(back.referenceId).isEqualTo(4L)
        assertThat(back.timestamp).isEqualTo(1_000L)
        assertThat(back.utcOffset).isEqualTo(3_600_000L)
        assertThat(back.fingerstickMgdl).isEqualTo(123.4)
        assertThat(back.sensorMgdlAtPairing).isEqualTo(118.7)
        assertThat(back.ids.nightscoutId).isEqualTo("nsId")
        assertThat(back.ids.pumpSerial).isEqualTo("serial-1")
        assertThat(back.ids.pumpId).isEqualTo(20L)
        // Lossless mapping + value equality on data classes
        assertThat(back).isEqualTo(original)
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

        assertThat(back.id).isEqualTo(5L)
        assertThat(back.version).isEqualTo(6)
        assertThat(back.dateCreated).isEqualTo(7_000L)
        assertThat(back.isValid).isTrue()
        assertThat(back.referenceId).isEqualTo(8L)
        assertThat(back.timestamp).isEqualTo(2_000L)
        assertThat(back.utcOffset).isEqualTo(7_200_000L)
        assertThat(back.fingerstickMgdl).isEqualTo(99.5)
        assertThat(back.sensorMgdlAtPairing).isEqualTo(101.2)
        assertThat(back.interfaceIDs.nightscoutId).isEqualTo("eNsId")
        assertThat(back.interfaceIDs.pumpId).isEqualTo(21L)
        assertThat(back).isEqualTo(original)
    }
}
