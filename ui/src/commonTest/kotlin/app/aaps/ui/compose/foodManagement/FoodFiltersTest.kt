package app.aaps.ui.compose.foodManagement

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The category filters of the food and dish lists, and the A to Z sort. */
class FoodFiltersTest {

    @Test
    fun allLetsEverythingThrough() {
        assertTrue(GroupFilter.All.matches(null))
        assertTrue(GroupFilter.All.matches(""))
        assertTrue(GroupFilter.All.matches("Fruit"))
    }

    @Test
    fun noneKeepsOnlyTheItemsWithoutAValue() {
        assertTrue(GroupFilter.None.matches(null))
        assertTrue(GroupFilter.None.matches(""))
        assertTrue(GroupFilter.None.matches("  "))
        assertFalse(GroupFilter.None.matches("Fruit"))
    }

    @Test
    fun aNamedFilterKeepsOnlyThatValue() {
        assertTrue(GroupFilter.Named("Fruit").matches("Fruit"))
        assertFalse(GroupFilter.Named("Fruit").matches("Dairy"))
        assertFalse(GroupFilter.Named("Fruit").matches(null))
    }

    @Test
    fun theChoicesAreSortedWithoutDoublesAndNoneOnlyWhenUseful() {
        val choices = GroupChoices.of(listOf("Fruit", null, "Dairy", "Fruit", " ", "Écrémé"))
        assertEquals(listOf("Dairy", "Écrémé", "Fruit"), choices.names)
        assertTrue(choices.hasNone)

        // Every item has a value: "None" would show an empty list.
        assertFalse(GroupChoices.of(listOf("Fruit", "Dairy")).hasNone)
        // No item has a value: "None" would be the same as "All".
        assertEquals(GroupChoices(), GroupChoices.of(listOf(null, "")))
    }

    @Test
    fun aToZIgnoresCaseAndAccents() {
        val names = listOf("zucchini", "Écrasé de pommes", "apple", "Œuf", "Banana")
        assertEquals(listOf("apple", "Banana", "Écrasé de pommes", "Œuf", "zucchini"), names.sortedBy { nameSortKey(it) })
        assertEquals("ecrase", nameSortKey("Écrasé"))
        assertEquals("oeuf", nameSortKey("Œuf"))
    }
}
