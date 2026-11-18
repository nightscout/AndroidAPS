package app.aaps.ui.compose.foodManagement.book

import app.aaps.core.data.model.FD
import app.aaps.core.data.model.IDs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A backup is written in format 3, the archive the final version reads, and files of format 1 and 2
 * are still read. A restore only adds what is missing, and points dishes to the foods of this phone.
 */
class FoodBackupMathTest {

    // On the phone that made the backup: bread is food 3 (from Nightscout), yogurt food 4.
    private val bread = FD(id = 3, ids = IDs(nightscoutId = NS_BREAD), name = "Bread", portion = 100.0, unit = "g", carbs = 50)
    private val yogurt = FD(id = 4, name = "Yogurt", portion = 1.0, unit = "pot", carbs = 6)
    private val toast = Recipe("dish-1", "Toast", RecipeKind.DISH, listOf(RecipeItem("Bread", 40.0, "g", foodId = 3)))
    private val breakfast = Recipe(
        "meal-1", "Breakfast", RecipeKind.MEAL,
        listOf(RecipeItem("Toast", 1.0, "servings", recipeId = "dish-1"), RecipeItem("Yogurt", 1.0, "pot", foodId = 4))
    )
    private val eaten = EatenMeal("eaten-1", timestamp = 1000, name = null, note = "n", wizardCarbs = 26, items = emptyList(), validatedAt = 1100, treatmentId = 9)

    /** The meal as it is in a backup: the id of its treatment is left out. */
    private val eatenInBackup = eaten.copy(treatmentId = null)

    private fun backup(): FoodBackup =
        assertNotNull(FoodBackupMath.decode(FoodBackupMath.encode(listOf(bread, yogurt), FoodBook(listOf(toast, breakfast), listOf(eaten)), now = 5000)))

    @Test
    fun `a backup is read back as written`() {
        val backup = backup()
        assertEquals(3, backup.format)
        assertEquals(5000, backup.exportedAt)
        assertEquals(listOf("Bread", "Yogurt"), backup.foods.map { it.name })
        assertEquals(listOf(NS_BREAD, null), backup.foods.map { it.nightscoutId })
        assertEquals(listOf(toast, breakfast), backup.recipes)
        assertEquals(listOf(eatenInBackup), backup.eaten)
    }

    @Test
    fun `each food carries its product and its photo, the treatment id is left out`() {
        val book = FoodBook(
            eaten = listOf(eaten),
            offLinks = listOf(OffLink(foodId = 4, barcode = BARCODE, hasImage = true, imageUrl = "https://images.openfoodfacts.org/a.jpg"), OffLink(foodId = 3, barcode = "111", hasImage = false)),
            photos = listOf(FoodPhoto(foodId = 4, key = "user_aaa")),
        )

        val text = FoodBackupMath.encode(listOf(bread, yogurt), book, now = 5000)
        val backup = assertNotNull(FoodBackupMath.decode(text))

        // No lists next to the foods, no address of a photo, no database id of a treatment.
        assertFalse(text.contains("offLinks"))
        assertFalse(text.contains("photos"))
        assertFalse(text.contains("imageUrl"))
        assertFalse(text.contains("treatmentId"))
        assertEquals(listOf(OffLink(foodId = 3, barcode = "111", hasImage = false), OffLink(foodId = 4, barcode = BARCODE, hasImage = true)), backup.offLinks)
        assertEquals(listOf(FoodPhoto(foodId = 4, key = "user_aaa")), backup.photos)
    }

    @Test
    fun `a barcode that Open Food Facts did not know stays marked as such`() {
        val book = FoodBook(offLinks = listOf(OffLink(foodId = 4, barcode = "2000000000017", hasImage = false, inOff = false), OffLink(foodId = 3, barcode = BARCODE, hasImage = false)))

        val text = FoodBackupMath.encode(listOf(bread, yogurt), book, now = 5000)
        val backup = assertNotNull(FoodBackupMath.decode(text))

        // Written only when true: a file of the final version without it is read as "known".
        assertEquals(1, Regex("notInOff").findAll(text).count())
        assertEquals(
            listOf(OffLink(foodId = 3, barcode = BARCODE, hasImage = false), OffLink(foodId = 4, barcode = "2000000000017", hasImage = false, inOff = false)),
            backup.offLinks
        )
    }

    @Test
    fun `the comments of foods and dishes are in the backup, a food here keeps its own`() {
        val dish = toast.copy(note = "With butter")
        val book = FoodBook(recipes = listOf(dish), foodNotes = listOf(FoodNote(foodId = 3, text = "Whole wheat"), FoodNote(foodId = 4, text = "Plain")))

        val backup = assertNotNull(FoodBackupMath.decode(FoodBackupMath.encode(listOf(bread, yogurt), book, now = 5000)))

        assertEquals(listOf(FoodNote(3, "Whole wheat"), FoodNote(4, "Plain")), backup.foodNotes)
        assertEquals("With butter", backup.recipes.single().note)
        // Bread is food 50 here and has no comment; yogurt is food 51 here and keeps its own.
        val here = listOf(FD(id = 50, ids = IDs(nightscoutId = NS_BREAD), name = "Bread", portion = 100.0, unit = "g", carbs = 50), FD(id = 51, name = "Yogurt", portion = 1.0, unit = "pot", carbs = 6))
        val merge = FoodBackupMath.merge(backup, here, FoodBook(foodNotes = listOf(FoodNote(51, "Mine"))))
        assertEquals(listOf(FoodNote(50, "Whole wheat")), merge.foodNotes)
    }

    @Test
    fun `a meal still waiting for its treatment is not in the backup`() {
        val waiting = eaten.copy(id = "eaten-2", validatedAt = null, treatmentId = null)

        val text = FoodBackupMath.encode(emptyList(), FoodBook(eaten = listOf(eaten, waiting)), now = 5000)

        assertEquals(listOf(eatenInBackup), assertNotNull(FoodBackupMath.decode(text)).eaten)
    }

    @Test
    fun `a newer format or another file is refused`() {
        val newer = FoodBackupMath.encode(emptyList(), FoodBook(), 0).replace("\"format\": 3", "\"format\": 4")
        assertTrue(newer.contains("\"format\": 4"))
        assertNull(FoodBackupMath.decode(newer))
        assertNull(FoodBackupMath.decode("{\"name\":\"not a backup\"}"))
        assertNull(FoodBackupMath.decode("not json"))
    }

    @Test
    fun `a file of the final version is read, its decimals rounded for the POC`() {
        val final = """
            {
              "format": 3,
              "exportedAt": 7,
              "foods": [
                { "id": 12, "nightscoutId": "ns-1", "name": "Yogurt", "portion": 125.0, "unit": "g", "carbs": 14.8, "fat": 3.4,
                  "fiber": 0.5, "barcode": "$BARCODE", "image": "user_aaa", "note": "plain", "fieldOfLater": 1 }
              ],
              "recipes": [],
              "eaten": []
            }
        """.trimIndent()

        val backup = assertNotNull(FoodBackupMath.decode(final))

        val food = backup.foods.single()
        assertEquals(15, food.carbs)
        assertEquals(3, food.fat)
        assertEquals("ns-1", food.nightscoutId)
        assertEquals(listOf(OffLink(foodId = 12, barcode = BARCODE, hasImage = true)), backup.offLinks)
        assertEquals(listOf(FoodPhoto(foodId = 12, key = "user_aaa")), backup.photos)
    }

    @Test
    fun `a backup of format 2 is still read`() {
        val old = """
            {
              "format": 2,
              "exportedAt": 7,
              "foods": [ { "id": 4, "name": "Yogurt", "portion": 1.0, "unit": "pot", "carbs": 6 } ],
              "recipes": [],
              "eaten": [],
              "offLinks": [ { "foodId": 4, "barcode": "$BARCODE", "hasImage": true, "imageUrl": "https://images.openfoodfacts.org/a.jpg" } ],
              "photos": [ { "foodId": 4, "key": "user_aaa" } ]
            }
        """.trimIndent()

        val backup = assertNotNull(FoodBackupMath.decode(old))

        assertEquals(2, backup.format)
        assertEquals(listOf("Yogurt"), backup.foods.map { it.name })
        assertEquals(listOf(OffLink(foodId = 4, barcode = BARCODE, hasImage = true, imageUrl = "https://images.openfoodfacts.org/a.jpg")), backup.offLinks)
        assertEquals(listOf(FoodPhoto(foodId = 4, key = "user_aaa")), backup.photos)
    }

    @Test
    fun `a backup of format 1, without products and photos, is still read`() {
        val old = """
            { "format": 1, "exportedAt": 1, "foods": [ { "id": 3, "name": "Bread", "portion": 100.0, "unit": "g", "carbs": 50 } ], "recipes": [], "eaten": [] }
        """.trimIndent()

        val backup = assertNotNull(FoodBackupMath.decode(old))

        assertEquals(1, backup.format)
        assertEquals(emptyList(), backup.offLinks)
        assertEquals(emptyList(), backup.photos)
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
    fun `a food is found again by its Nightscout id, also when Nightscout renamed it`() {
        val here = listOf(FD(id = 60, ids = IDs(nightscoutId = NS_BREAD), name = "Wholemeal bread", portion = 100.0, unit = "g", carbs = 48), yogurt)

        assertTrue(FoodBackupMath.missingFoods(backup(), here).isEmpty())
        assertEquals(60L, FoodBackupMath.merge(backup(), here, FoodBook()).recipes.single { it.id == "dish-1" }.items.single().foodId)
    }

    @Test
    fun `a food added by a restore keeps its Nightscout id, so Nightscout does not add it again`() {
        val missing = FoodBackupMath.missingFoods(backup(), emptyList())

        assertEquals(NS_BREAD, missing.single { it.name == "Bread" }.ids.nightscoutId)
        assertNull(missing.single { it.name == "Yogurt" }.ids.nightscoutId)
    }

    @Test
    fun `the product of an imported food follows the food found on this phone`() {
        val yogurtLink = OffLink(foodId = 4, barcode = BARCODE, hasImage = true, imageUrl = "https://images.openfoodfacts.org/a.jpg")
        val text = FoodBackupMath.encode(listOf(bread, yogurt), FoodBook(offLinks = listOf(yogurtLink)), now = 5000)
        val backup = assertNotNull(FoodBackupMath.decode(text))
        // Here yogurt is food 51; bread is food 50 and already has a product of its own.
        val here = listOf(FD(id = 50, name = "Bread", portion = 100.0, unit = "g", carbs = 50), FD(id = 51, name = "Yogurt", portion = 1.0, unit = "pot", carbs = 6))
        val breadLink = OffLink(foodId = 50, barcode = "111", hasImage = false)

        val merge = FoodBackupMath.merge(backup, here, FoodBook(offLinks = listOf(breadLink)))

        // The address of the photo is not in the backup: a missing photo reads the product again.
        assertEquals(listOf(yogurtLink.copy(foodId = 51, imageUrl = null)), merge.offLinks)
    }

    @Test
    fun `the photo of the user follows the food found on this phone, and a food keeps its own`() {
        val yogurtPhoto = FoodPhoto(foodId = 4, key = "user_aaa")
        val breadPhoto = FoodPhoto(foodId = 3, key = "user_bbb")
        val text = FoodBackupMath.encode(listOf(bread, yogurt), FoodBook(photos = listOf(yogurtPhoto, breadPhoto)), now = 5000)
        val backup = assertNotNull(FoodBackupMath.decode(text))
        // Here yogurt is food 51; bread is food 50 and already has a photo taken on this phone.
        val here = listOf(FD(id = 50, name = "Bread", portion = 100.0, unit = "g", carbs = 50), FD(id = 51, name = "Yogurt", portion = 1.0, unit = "pot", carbs = 6))
        val herePhoto = FoodPhoto(foodId = 50, key = "user_ccc")

        val merge = FoodBackupMath.merge(backup, here, FoodBook(photos = listOf(herePhoto)))

        assertEquals(listOf(yogurtPhoto.copy(foodId = 51)), merge.photos)
    }

    @Test
    fun `photo entries of the archive are found by their key`() {
        assertEquals("images/user_aaa.jpg", FoodBackupMath.imageEntry("user_aaa"))
        assertEquals("user_aaa", FoodBackupMath.keyOfEntry("images/user_aaa.jpg"))
        assertEquals(BARCODE, FoodBackupMath.keyOfEntry("images/$BARCODE.jpg"))
        assertNull(FoodBackupMath.keyOfEntry(FoodBackupMath.FILE_NAME))
        assertNull(FoodBackupMath.keyOfEntry("images/../settings.jpg"))
        assertNull(FoodBackupMath.keyOfEntry("images/sub/a.jpg"))
    }

    @Test
    fun `dishes point to the foods of this phone`() {
        val here = listOf(FD(id = 50, name = "Bread", portion = 100.0, unit = "g", carbs = 50), FD(id = 51, name = "Yogurt", portion = 1.0, unit = "pot", carbs = 6))

        val merge = FoodBackupMath.merge(backup(), here, FoodBook())

        assertEquals(50L, merge.recipes.single { it.id == "dish-1" }.items.single().foodId)
        val meal = merge.recipes.single { it.id == "meal-1" }
        assertEquals("dish-1", meal.items[0].recipeId)
        assertEquals(51L, meal.items[1].foodId)
        assertEquals(listOf(eatenInBackup), merge.meals)
    }

    @Test
    fun `the lines of a restored meal point to the foods and dishes of this phone`() {
        val meal = eaten.copy(
            id = "eaten-7",
            items = listOf(
                EatenItem("Bread", 40.0, "g", 20.0, fat = null, protein = null, energyKj = null, foodId = 3),
                EatenItem("Toast", 1.0, "servings", 20.0, fat = null, protein = null, energyKj = null, recipeId = "dish-1"),
                EatenItem("Ham", 50.0, "g", 1.0, fat = null, protein = null, energyKj = null, foodId = 77),
            )
        )
        val backup = assertNotNull(FoodBackupMath.decode(FoodBackupMath.encode(listOf(bread), FoodBook(listOf(toast), listOf(meal)), now = 5000)))
        val here = listOf(FD(id = 50, ids = IDs(nightscoutId = NS_BREAD), name = "Bread", portion = 100.0, unit = "g", carbs = 50))
        val book = FoodBook(recipes = listOf(Recipe("dish-9", "Toast", RecipeKind.DISH, emptyList())))

        val items = FoodBackupMath.merge(backup, here, book).meals.single().items

        // Bread is food 50 here, toast is dish 9 here, and food 77 was not in the backup: no food.
        assertEquals(listOf(50L, null, null), items.map { it.foodId })
        assertEquals(listOf(null, "dish-9", null), items.map { it.recipeId })
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

    private companion object {

        const val NS_BREAD = "ns-bread"
        const val BARCODE = "3017620422003"
    }
}
