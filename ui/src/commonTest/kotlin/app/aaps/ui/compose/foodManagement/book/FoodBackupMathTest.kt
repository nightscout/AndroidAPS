package app.aaps.ui.compose.foodManagement.book

import app.aaps.core.data.model.FD
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A restore only adds what is missing, and points dishes to the foods of this phone. */
class FoodBackupMathTest {

    // On the phone that made the backup: bread is food 3, yogurt food 4.
    private val bread = FD(id = 3, name = "Bread", portion = 100.0, unit = "g", carbs = 50)
    private val yogurt = FD(id = 4, name = "Yogurt", portion = 1.0, unit = "pot", carbs = 6)
    private val toast = Recipe("dish-1", "Toast", RecipeKind.DISH, listOf(RecipeItem("Bread", 40.0, "g", foodId = 3)))
    private val breakfast = Recipe(
        "meal-1", "Breakfast", RecipeKind.MEAL,
        listOf(RecipeItem("Toast", 1.0, "servings", recipeId = "dish-1"), RecipeItem("Yogurt", 1.0, "pot", foodId = 4))
    )
    private val eaten = EatenMeal("eaten-1", timestamp = 1000, name = null, note = "n", wizardCarbs = 26, items = emptyList(), validatedAt = 1100, treatmentId = 9)

    private fun backup(): FoodBackup =
        assertNotNull(FoodBackupMath.decode(FoodBackupMath.encode(listOf(bread, yogurt), FoodBook(listOf(toast, breakfast), listOf(eaten)), now = 5000)))

    @Test
    fun `a backup is read back as written`() {
        val backup = backup()
        assertEquals(5000, backup.exportedAt)
        assertEquals(listOf("Bread", "Yogurt"), backup.foods.map { it.name })
        assertEquals(listOf(toast, breakfast), backup.recipes)
        assertEquals(listOf(eaten), backup.eaten)
    }

    @Test
    fun `a newer format or another file is refused`() {
        val newer = FoodBackupMath.encode(emptyList(), FoodBook(), 0).replace("\"format\": 1", "\"format\": 2")
        assertNull(FoodBackupMath.decode(newer))
        assertNull(FoodBackupMath.decode("{\"name\":\"not a backup\"}"))
        assertNull(FoodBackupMath.decode("not json"))
    }

    @Test
    fun `only the foods not found by name portion unit and carbs are added`() {
        // Here bread exists with another id and other capitals; yogurt does not exist.
        val here = listOf(FD(id = 50, name = " bread", portion = 100.0, unit = "G", carbs = 50))

        val missing = FoodBackupMath.missingFoods(backup(), here)

        assertEquals(listOf("Yogurt"), missing.map { it.name })
        assertEquals(0, missing.single().id)
    }

    @Test
    fun `the product of an imported food follows the food found on this phone`() {
        val yogurtLink = OffLink(foodId = 4, barcode = "3017620422003", hasImage = true, imageUrl = "https://images.openfoodfacts.org/a.jpg")
        val text = FoodBackupMath.encode(listOf(bread, yogurt), FoodBook(offLinks = listOf(yogurtLink)), now = 5000)
        val backup = assertNotNull(FoodBackupMath.decode(text))
        // Here yogurt is food 51; bread is food 50 and already has a product of its own.
        val here = listOf(FD(id = 50, name = "Bread", portion = 100.0, unit = "g", carbs = 50), FD(id = 51, name = "Yogurt", portion = 1.0, unit = "pot", carbs = 6))
        val breadLink = OffLink(foodId = 50, barcode = "111", hasImage = false)

        val merge = FoodBackupMath.merge(backup, here, FoodBook(offLinks = listOf(breadLink)))

        assertEquals(listOf(yogurtLink.copy(foodId = 51)), merge.offLinks)
    }

    @Test
    fun `a backup made before the products were kept is still read`() {
        val old = FoodBackupMath.encode(listOf(bread), FoodBook(), now = 1).replace(Regex(",\\s*\"offLinks\": \\[\\s*]"), "")
        assertTrue(!old.contains("offLinks"))
        assertEquals(emptyList(), assertNotNull(FoodBackupMath.decode(old)).offLinks)
    }

    @Test
    fun `dishes point to the foods of this phone`() {
        val here = listOf(FD(id = 50, name = "Bread", portion = 100.0, unit = "g", carbs = 50), FD(id = 51, name = "Yogurt", portion = 1.0, unit = "pot", carbs = 6))

        val merge = FoodBackupMath.merge(backup(), here, FoodBook())

        assertEquals(50L, merge.recipes.single { it.id == "dish-1" }.items.single().foodId)
        val meal = merge.recipes.single { it.id == "meal-1" }
        assertEquals("dish-1", meal.items[0].recipeId)
        assertEquals(51L, meal.items[1].foodId)
        assertEquals(listOf(eaten), merge.meals)
    }

    @Test
    fun `a food not found leaves the line without food, so the total is shown as not known`() {
        val merge = FoodBackupMath.merge(backup(), emptyList(), FoodBook())

        assertNull(merge.recipes.single { it.id == "dish-1" }.items.single().foodId)
    }

    @Test
    fun `what is already here is not added again`() {
        // Same toast under another id, same breakfast id, same eaten meal.
        val book = FoodBook(
            recipes = listOf(Recipe("dish-9", "toast ", RecipeKind.DISH, emptyList()), breakfast),
            eaten = listOf(eaten),
        )

        val merge = FoodBackupMath.merge(backup(), listOf(bread), book)

        assertTrue(merge.recipes.isEmpty())
        assertTrue(merge.meals.isEmpty())
    }

    @Test
    fun `a dish line points to the dish already here under another id`() {
        val book = FoodBook(recipes = listOf(Recipe("dish-9", "Toast", RecipeKind.DISH, emptyList())))

        val merge = FoodBackupMath.merge(backup(), listOf(bread, yogurt), book)

        assertEquals("dish-9", merge.recipes.single { it.id == "meal-1" }.items[0].recipeId)
    }
}
