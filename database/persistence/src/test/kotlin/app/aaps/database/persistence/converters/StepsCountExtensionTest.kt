package app.aaps.database.persistence.converters

import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.SC
import app.aaps.database.entities.StepsCount
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

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

        assertThat(back.id).isEqualTo(1L)
        assertThat(back.duration).isEqualTo(300_000L)
        assertThat(back.timestamp).isEqualTo(1_000L)
        assertThat(back.steps5min).isEqualTo(5)
        assertThat(back.steps10min).isEqualTo(10)
        assertThat(back.steps15min).isEqualTo(15)
        assertThat(back.steps30min).isEqualTo(30)
        assertThat(back.steps60min).isEqualTo(60)
        assertThat(back.steps180min).isEqualTo(180)
        assertThat(back.device).isEqualTo("watch")
        assertThat(back.utcOffset).isEqualTo(3_600_000L)
        assertThat(back.version).isEqualTo(2)
        assertThat(back.dateCreated).isEqualTo(2_000L)
        assertThat(back.isValid).isTrue()
        assertThat(back.referenceId).isEqualTo(99L)
    }

    @Test
    fun domainRoundTripPreservesNestedIds() {
        val original = sampleDomain()

        val back = original.toDb().fromDb()

        assertThat(back.ids).isEqualTo(sampleIds())
        assertThat(back.ids.pumpId).isEqualTo(222L)
        assertThat(back.ids.pumpSerial).isEqualTo("serial-123")
    }

    @Test
    fun domainRoundTripIsLossless() {
        val original = sampleDomain()

        val back = original.toDb().fromDb()

        assertThat(back).isEqualTo(original)
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

        assertThat(back.id).isEqualTo(7L)
        assertThat(back.duration).isEqualTo(600_000L)
        assertThat(back.timestamp).isEqualTo(5_000L)
        assertThat(back.steps5min).isEqualTo(1)
        assertThat(back.steps180min).isEqualTo(8)
        assertThat(back.device).isEqualTo("phone")
        assertThat(back.utcOffset).isEqualTo(7_200_000L)
        assertThat(back.version).isEqualTo(4)
        assertThat(back.dateCreated).isEqualTo(9_000L)
        assertThat(back.isValid).isFalse()
        assertThat(back.referenceId).isEqualTo(55L)
        assertThat(back.interfaceIDs_backing).isEqualTo(sampleIds().toDb())
    }
}
