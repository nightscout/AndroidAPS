package app.aaps.database.persistence.converters

import app.aaps.core.data.model.FD
import app.aaps.core.data.model.IDs
import app.aaps.database.entities.Food
import app.aaps.database.entities.embedments.InterfaceIDs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

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
        assertEquals(original, back)

        // Explicit scalar assertions for clarity / regression safety.
        assertEquals(1L, back.id)
        assertEquals(2, back.version)
        assertEquals(1_000L, back.dateCreated)
        assertTrue(back.isValid)
        assertEquals(3L, back.referenceId)
        assertEquals("juice", back.name)
        assertEquals("drinks", back.category)
        assertEquals("fruit", back.subCategory)
        assertEquals(250.0, back.portion)
        assertEquals(12, back.carbs)
        assertEquals(4, back.fat)
        assertEquals(5, back.protein)
        assertEquals(600, back.energy)
        assertEquals("ml", back.unit)
        assertEquals(40, back.gi)

        // Nested IDs round-trips too.
        assertEquals("nsSys", back.ids.nightscoutSystemId)
        assertEquals("nsId", back.ids.nightscoutId)
        assertEquals("serial", back.ids.pumpSerial)
        assertEquals(10L, back.ids.temporaryId)
        assertEquals(11L, back.ids.pumpId)
        assertEquals(12L, back.ids.startId)
        assertEquals(13L, back.ids.endId)
    }

    @Test
    fun entityRoundTripPreservesAllFields() {
        val original = sampleEntity()
        val back = original.fromDb().toDb()

        assertEquals(original, back)

        assertEquals(21L, back.id)
        assertEquals(7, back.version)
        assertEquals(2_000L, back.dateCreated)
        assertFalse(back.isValid)
        assertEquals(22L, back.referenceId)
        assertEquals("apple", back.name)
        assertEquals("fruit", back.category)
        assertEquals("green", back.subCategory)
        assertEquals(100.0, back.portion)
        assertEquals(14, back.carbs)
        assertEquals(1, back.fat)
        assertEquals(2, back.protein)
        assertEquals(300, back.energy)
        assertEquals("g", back.unit)
        assertEquals(38, back.gi)

        assertEquals("eSys", back.interfaceIDs.nightscoutSystemId)
        assertEquals("eId", back.interfaceIDs.nightscoutId)
        assertEquals("eSerial", back.interfaceIDs.pumpSerial)
        assertEquals(30L, back.interfaceIDs.temporaryId)
        assertEquals(31L, back.interfaceIDs.pumpId)
        assertEquals(32L, back.interfaceIDs.startId)
        assertEquals(33L, back.interfaceIDs.endId)
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

        assertEquals(original, back)
        assertNull(back.category)
        assertNull(back.subCategory)
        assertNull(back.fat)
        assertNull(back.protein)
        assertNull(back.energy)
        assertNull(back.gi)
        assertNull(back.referenceId)
        assertEquals("g", back.unit)
    }
}
