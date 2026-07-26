package app.aaps.database.persistence.converters

import app.aaps.core.data.model.HR
import app.aaps.core.data.model.IDs
import app.aaps.database.entities.HeartRate
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

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

        assertThat(entity.id).isEqualTo(10L)
        assertThat(entity.duration).isEqualTo(60_000L)
        assertThat(entity.timestamp).isEqualTo(1_000L)
        assertThat(entity.beatsPerMinute).isEqualTo(72.5)
        assertThat(entity.device).isEqualTo("watch-device")
        assertThat(entity.utcOffset).isEqualTo(3_600_000L)
        assertThat(entity.version).isEqualTo(3)
        assertThat(entity.dateCreated).isEqualTo(2_000L)
        assertThat(entity.isValid).isTrue()
        assertThat(entity.referenceId).isEqualTo(42L)
        assertThat(entity.interfaceIDs_backing?.nightscoutId).isEqualTo("ns-id")
        assertThat(entity.interfaceIDs_backing?.pumpId).isEqualTo(222L)
    }

    @Test
    fun roundTripDomainToDbAndBack() {
        val original = sampleDomain()
        val back = original.toDb().fromDb()

        assertThat(back.id).isEqualTo(original.id)
        assertThat(back.duration).isEqualTo(original.duration)
        assertThat(back.timestamp).isEqualTo(original.timestamp)
        assertThat(back.beatsPerMinute).isEqualTo(original.beatsPerMinute)
        assertThat(back.device).isEqualTo(original.device)
        assertThat(back.utcOffset).isEqualTo(original.utcOffset)
        assertThat(back.version).isEqualTo(original.version)
        assertThat(back.dateCreated).isEqualTo(original.dateCreated)
        assertThat(back.isValid).isEqualTo(original.isValid)
        assertThat(back.referenceId).isEqualTo(original.referenceId)
        assertThat(back.ids).isEqualTo(original.ids)
        // HR is a value-equality data class and the mapping is lossless.
        assertThat(back).isEqualTo(original)
    }

    @Test
    fun roundTripDbToDomainAndBack() {
        val entity = sampleDomain().toDb()
        val back = entity.fromDb().toDb()

        assertThat(back.id).isEqualTo(entity.id)
        assertThat(back.duration).isEqualTo(entity.duration)
        assertThat(back.timestamp).isEqualTo(entity.timestamp)
        assertThat(back.beatsPerMinute).isEqualTo(entity.beatsPerMinute)
        assertThat(back.device).isEqualTo(entity.device)
        assertThat(back.utcOffset).isEqualTo(entity.utcOffset)
        assertThat(back.version).isEqualTo(entity.version)
        assertThat(back.dateCreated).isEqualTo(entity.dateCreated)
        assertThat(back.isValid).isEqualTo(entity.isValid)
        assertThat(back.referenceId).isEqualTo(entity.referenceId)
        assertThat(back.interfaceIDs_backing).isEqualTo(entity.interfaceIDs_backing)
        assertThat(back).isEqualTo(entity)
    }
}
