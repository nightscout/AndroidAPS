package app.aaps.database.persistence.converters

import app.aaps.core.data.model.BCR
import app.aaps.core.data.model.IDs
import app.aaps.database.entities.BolusCalculatorResult
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

internal class BolusCalculatorResultExtensionTest {

    private fun sampleIDs() = IDs(
        nightscoutSystemId = "nsSys",
        nightscoutId = "nsId",
        pumpType = null,
        pumpSerial = "serial-1",
        temporaryId = 11L,
        pumpId = 12L,
        startId = 13L,
        endId = 14L
    )

    private fun sampleDomain() = BCR(
        id = 1L,
        version = 2,
        dateCreated = 1_000L,
        isValid = true,
        referenceId = 3L,
        ids = sampleIDs(),
        timestamp = 2_000L,
        utcOffset = 3_600_000L,
        targetBGLow = 90.0,
        targetBGHigh = 110.0,
        isf = 45.0,
        ic = 8.0,
        bolusIOB = 1.5,
        wasBolusIOBUsed = true,
        basalIOB = 0.5,
        wasBasalIOBUsed = false,
        glucoseValue = 150.0,
        wasGlucoseUsed = true,
        glucoseDifference = 60.0,
        glucoseInsulin = 1.2,
        glucoseTrend = 5.0,
        wasTrendUsed = false,
        trendInsulin = 0.1,
        cob = 25.0,
        wasCOBUsed = true,
        cobInsulin = 3.1,
        carbs = 40.0,
        wereCarbsUsed = false,
        carbsInsulin = 5.0,
        otherCorrection = 0.25,
        wasSuperbolusUsed = true,
        superbolusInsulin = 2.0,
        wasTempTargetUsed = false,
        totalInsulin = 12.34,
        percentageCorrection = 90,
        profileName = "profile-A",
        note = "some note"
    )

    @Test
    fun roundTripDomainToDbAndBack() {
        val original = sampleDomain()

        val back = original.toDb().fromDb()

        // Data classes with value equality + lossless mapping -> full equality
        assertThat(back).isEqualTo(original)

        // Explicit key-field assertions (robust against embedded/backing differences)
        assertThat(back.id).isEqualTo(1L)
        assertThat(back.version).isEqualTo(2)
        assertThat(back.dateCreated).isEqualTo(1_000L)
        assertThat(back.isValid).isTrue()
        assertThat(back.referenceId).isEqualTo(3L)
        assertThat(back.timestamp).isEqualTo(2_000L)
        assertThat(back.utcOffset).isEqualTo(3_600_000L)
        assertThat(back.targetBGLow).isEqualTo(90.0)
        assertThat(back.targetBGHigh).isEqualTo(110.0)
        assertThat(back.isf).isEqualTo(45.0)
        assertThat(back.ic).isEqualTo(8.0)
        assertThat(back.totalInsulin).isEqualTo(12.34)
        assertThat(back.percentageCorrection).isEqualTo(90)
        assertThat(back.profileName).isEqualTo("profile-A")
        assertThat(back.note).isEqualTo("some note")

        // Nested IDs round-trips
        assertThat(back.ids.nightscoutId).isEqualTo("nsId")
        assertThat(back.ids.pumpSerial).isEqualTo("serial-1")
        assertThat(back.ids.temporaryId).isEqualTo(11L)
        assertThat(back.ids.pumpId).isEqualTo(12L)
        assertThat(back.ids.startId).isEqualTo(13L)
        assertThat(back.ids.endId).isEqualTo(14L)
    }

    @Test
    fun roundTripDbToDomainAndBack() {
        val entity = sampleDomain().toDb()

        val back = entity.fromDb().toDb()

        assertThat(back).isEqualTo(entity)
        assertThat(back.id).isEqualTo(1L)
        assertThat(back.timestamp).isEqualTo(2_000L)
        assertThat(back.isValid).isTrue()
        assertThat(back.carbs).isEqualTo(40.0)
        assertThat(back.wereCarbsUsed).isFalse()
        assertThat(back.wasSuperbolusUsed).isTrue()
        assertThat(back.profileName).isEqualTo("profile-A")
        assertThat(back.interfaceIDs.nightscoutId).isEqualTo("nsId")
        assertThat(back.interfaceIDs.pumpId).isEqualTo(12L)
    }

    @Test
    fun booleanFlagsMapDistinctly() {
        val back = sampleDomain().toDb().fromDb()

        assertThat(back.wasBolusIOBUsed).isTrue()
        assertThat(back.wasBasalIOBUsed).isFalse()
        assertThat(back.wasGlucoseUsed).isTrue()
        assertThat(back.wasTrendUsed).isFalse()
        assertThat(back.wasCOBUsed).isTrue()
        assertThat(back.wereCarbsUsed).isFalse()
        assertThat(back.wasSuperbolusUsed).isTrue()
        assertThat(back.wasTempTargetUsed).isFalse()
    }
}
