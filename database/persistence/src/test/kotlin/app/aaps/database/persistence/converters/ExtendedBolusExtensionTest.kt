package app.aaps.database.persistence.converters

import app.aaps.core.data.model.EB
import app.aaps.core.data.model.IDs
import app.aaps.database.entities.ExtendedBolus
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

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

        assertThat(back.id).isEqualTo(1L)
        assertThat(back.version).isEqualTo(2)
        assertThat(back.dateCreated).isEqualTo(3L)
        assertThat(back.isValid).isTrue()
        assertThat(back.referenceId).isEqualTo(4L)
        assertThat(back.timestamp).isEqualTo(1_000L)
        assertThat(back.utcOffset).isEqualTo(3_600_000L)
        assertThat(back.duration).isEqualTo(1_800_000L)
        assertThat(back.amount).isEqualTo(2.5)
        assertThat(back.isEmulatingTempBasal).isTrue()
        // nested IDs mapping round-trips losslessly (pumpType left null)
        assertThat(back.ids).isEqualTo(sampleIds())
        // EB has value equality and the mapping is lossless
        assertThat(back).isEqualTo(original)
    }

    @Test
    fun toDbCopiesAllFields() {
        val entity: ExtendedBolus = sampleEb().toDb()

        assertThat(entity.id).isEqualTo(1L)
        assertThat(entity.version).isEqualTo(2)
        assertThat(entity.dateCreated).isEqualTo(3L)
        assertThat(entity.isValid).isTrue()
        assertThat(entity.referenceId).isEqualTo(4L)
        assertThat(entity.timestamp).isEqualTo(1_000L)
        assertThat(entity.utcOffset).isEqualTo(3_600_000L)
        assertThat(entity.duration).isEqualTo(1_800_000L)
        assertThat(entity.amount).isEqualTo(2.5)
        assertThat(entity.isEmulatingTempBasal).isTrue()
        assertThat(entity.interfaceIDs_backing?.pumpSerial).isEqualTo("serial-123")
        assertThat(entity.interfaceIDs_backing?.pumpId).isEqualTo(20L)
    }

    @Test
    fun roundTripEntityToDomainAndBack() {
        val original: ExtendedBolus = sampleEb().toDb()
        val back = original.fromDb().toDb()

        assertThat(back.id).isEqualTo(1L)
        assertThat(back.version).isEqualTo(2)
        assertThat(back.dateCreated).isEqualTo(3L)
        assertThat(back.isValid).isTrue()
        assertThat(back.referenceId).isEqualTo(4L)
        assertThat(back.timestamp).isEqualTo(1_000L)
        assertThat(back.utcOffset).isEqualTo(3_600_000L)
        assertThat(back.duration).isEqualTo(1_800_000L)
        assertThat(back.amount).isEqualTo(2.5)
        assertThat(back.isEmulatingTempBasal).isTrue()
        assertThat(back.interfaceIDs_backing?.startId).isEqualTo(30L)
        assertThat(back.interfaceIDs_backing?.endId).isEqualTo(40L)
    }
}
