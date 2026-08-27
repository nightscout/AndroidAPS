package app.aaps.database.persistence.converters

import app.aaps.core.data.model.FD
import app.aaps.core.data.model.IDs
import app.aaps.database.entities.Food
import app.aaps.database.entities.embedments.InterfaceIDs
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

internal class FoodExtensionTest {

    private fun sampleDomain(): FD =
        FD(
            id = 1L,
            version = 2,
            dateCreated = 1_000L,
            isValid = true,
            referenceId = 3L,
            ids = IDs(
                nightscoutSystemId = "nsSys",
                nightscoutId = "nsId",
                pumpSerial = "serial",
                temporaryId = 10L,
                pumpId = 11L,
                startId = 12L,
                endId = 13L
            ),
            name = "juice",
            category = "drinks",
            subCategory = "fruit",
            portion = 250.0,
            carbs = 12,
            fat = 4,
            protein = 5,
            energy = 600,
            unit = "ml",
            gi = 40
        )

    private fun sampleEntity(): Food =
        Food(
            id = 21L,
            version = 7,
            dateCreated = 2_000L,
            isValid = false,
            referenceId = 22L,
            interfaceIDs_backing = InterfaceIDs(
                nightscoutSystemId = "eSys",
                nightscoutId = "eId",
                pumpSerial = "eSerial",
                temporaryId = 30L,
                pumpId = 31L,
                startId = 32L,
                endId = 33L
            ),
            name = "apple",
            category = "fruit",
            subCategory = "green",
            portion = 100.0,
            carbs = 14,
            fat = 1,
            protein = 2,
            energy = 300,
            unit = "g",
            gi = 38
        )

    @Test
    fun domainRoundTripPreservesAllFields() {
        val original = sampleDomain()
        val back = original.toDb().fromDb()

        // Value equality holds because FD is a data class and the mapping is lossless.
        assertThat(back).isEqualTo(original)

        // Explicit scalar assertions for clarity / regression safety.
        assertThat(back.id).isEqualTo(1L)
        assertThat(back.version).isEqualTo(2)
        assertThat(back.dateCreated).isEqualTo(1_000L)
        assertThat(back.isValid).isTrue()
        assertThat(back.referenceId).isEqualTo(3L)
        assertThat(back.name).isEqualTo("juice")
        assertThat(back.category).isEqualTo("drinks")
        assertThat(back.subCategory).isEqualTo("fruit")
        assertThat(back.portion).isEqualTo(250.0)
        assertThat(back.carbs).isEqualTo(12)
        assertThat(back.fat).isEqualTo(4)
        assertThat(back.protein).isEqualTo(5)
        assertThat(back.energy).isEqualTo(600)
        assertThat(back.unit).isEqualTo("ml")
        assertThat(back.gi).isEqualTo(40)

        // Nested IDs round-trips too.
        assertThat(back.ids.nightscoutSystemId).isEqualTo("nsSys")
        assertThat(back.ids.nightscoutId).isEqualTo("nsId")
        assertThat(back.ids.pumpSerial).isEqualTo("serial")
        assertThat(back.ids.temporaryId).isEqualTo(10L)
        assertThat(back.ids.pumpId).isEqualTo(11L)
        assertThat(back.ids.startId).isEqualTo(12L)
        assertThat(back.ids.endId).isEqualTo(13L)
    }

    @Test
    fun entityRoundTripPreservesAllFields() {
        val original = sampleEntity()
        val back = original.fromDb().toDb()

        assertThat(back).isEqualTo(original)

        assertThat(back.id).isEqualTo(21L)
        assertThat(back.version).isEqualTo(7)
        assertThat(back.dateCreated).isEqualTo(2_000L)
        assertThat(back.isValid).isFalse()
        assertThat(back.referenceId).isEqualTo(22L)
        assertThat(back.name).isEqualTo("apple")
        assertThat(back.category).isEqualTo("fruit")
        assertThat(back.subCategory).isEqualTo("green")
        assertThat(back.portion).isEqualTo(100.0)
        assertThat(back.carbs).isEqualTo(14)
        assertThat(back.fat).isEqualTo(1)
        assertThat(back.protein).isEqualTo(2)
        assertThat(back.energy).isEqualTo(300)
        assertThat(back.unit).isEqualTo("g")
        assertThat(back.gi).isEqualTo(38)

        assertThat(back.interfaceIDs.nightscoutSystemId).isEqualTo("eSys")
        assertThat(back.interfaceIDs.nightscoutId).isEqualTo("eId")
        assertThat(back.interfaceIDs.pumpSerial).isEqualTo("eSerial")
        assertThat(back.interfaceIDs.temporaryId).isEqualTo(30L)
        assertThat(back.interfaceIDs.pumpId).isEqualTo(31L)
        assertThat(back.interfaceIDs.startId).isEqualTo(32L)
        assertThat(back.interfaceIDs.endId).isEqualTo(33L)
    }

    @Test
    fun nullableFieldsRoundTripAsNull() {
        val original = FD(
            id = 5L,
            name = "water",
            portion = 500.0,
            carbs = 0
        )
        val back = original.toDb().fromDb()

        assertThat(back).isEqualTo(original)
        assertThat(back.category).isNull()
        assertThat(back.subCategory).isNull()
        assertThat(back.fat).isNull()
        assertThat(back.protein).isNull()
        assertThat(back.energy).isNull()
        assertThat(back.gi).isNull()
        assertThat(back.referenceId).isNull()
        assertThat(back.unit).isEqualTo("g")
    }
}
