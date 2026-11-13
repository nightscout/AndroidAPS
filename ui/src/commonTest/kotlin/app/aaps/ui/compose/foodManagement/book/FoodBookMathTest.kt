package app.aaps.ui.compose.foodManagement.book

import app.aaps.core.data.model.BCR
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.CA
import app.aaps.core.data.model.FD
import app.aaps.core.data.model.ICfg
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Dishes and meals are computed from their foods. The total goes to the bolus wizard. */
class FoodBookMathTest {

    private val yogurt = FD(id = 1, name = "Yogurt", portion = 1.0, unit = "pot", carbs = 6, fat = 4, protein = 4, energy = 300)
    private val sugar = FD(id = 2, name = "Sugar", portion = 1.0, unit = "piece", carbs = 5, fat = 0, protein = 0, energy = 84)
    private val bread = FD(id = 3, name = "Bread", portion = 100.0, unit = "g", carbs = 50)
    private val foods = listOf(yogurt, sugar, bread).associateBy { it.id }

    private val sweetYogurt = Recipe(
        id = "dish", name = "Sweet yogurt", kind = RecipeKind.DISH,
        items = listOf(RecipeItem("Yogurt", 1.0, "pot", foodId = 1), RecipeItem("Sugar", 2.0, "piece", foodId = 2))
    )
    private val breakfast = Recipe(
        id = "meal", name = "Breakfast", kind = RecipeKind.MEAL,
        items = listOf(RecipeItem("Bread", 80.0, "g", foodId = 3), RecipeItem("Sweet yogurt", 1.0, "servings", recipeId = "dish"))
    )
    private val recipes = listOf(sweetYogurt, breakfast).associateBy { it.id }

    @Test
    fun `a dish is the sum of its foods`() {
        val n = FoodBookMath.nutritionOf(sweetYogurt, foods, recipes)!!
        assertEquals(16.0, n.carbs, 0.0001) // 6 + 2 x 5
        assertEquals(4.0, n.fat!!, 0.0001)
        assertEquals(468.0, n.energyKj!!, 0.0001)
    }

    @Test
    fun `a meal counts the dishes it holds`() {
        // 80 g of bread is 40 g, plus the sweet yogurt 16 g.
        assertEquals(56.0, FoodBookMath.nutritionOf(breakfast, foods, recipes)!!.carbs, 0.0001)
    }

    @Test
    fun `servings of a dish multiply it`() {
        val item = RecipeItem("Sweet yogurt", 1.5, "servings", recipeId = "dish")
        assertEquals(24.0, FoodBookMath.nutritionOf(item, foods, recipes)!!.carbs, 0.0001)
    }

    @Test
    fun `a value missing on one food gives the part known, marked as partial`() {
        // Bread has no fat value: the fat of the breakfast is at least the fat of the yogurt.
        // The carbs are always complete.
        val n = FoodBookMath.nutritionOf(breakfast, foods, recipes)!!
        assertEquals(56.0, n.carbs, 0.0001) // 40 + 16
        assertEquals(4.0, n.fat!!, 0.0001)
        assertEquals(setOf(Nutrient.FAT, Nutrient.PROTEIN, Nutrient.ENERGY), n.partial)
        // A file that must hold exact values gets nothing.
        assertNull(n.exact(Nutrient.FAT))
    }

    @Test
    fun `a value no food has stays unknown, not 0 and not partial`() {
        val n = Nutrition.sum(listOf(FoodBookMath.nutritionOf(bread, 80.0), FoodBookMath.nutritionOf(bread, 20.0)))
        assertNull(n.fat)
        assertTrue(n.partial.isEmpty())
        assertEquals(Nutrition.ZERO, Nutrition.sum(emptyList()))
    }

    @Test
    fun `a partial value stays partial when it is added to complete ones or multiplied`() {
        val dish = Nutrition(10.0, fat = 2.0, protein = null, energyKj = null, partial = setOf(Nutrient.FAT))
        val food = FoodBookMath.nutritionOf(yogurt, 1.0)
        val n = (dish * 2.0) + food
        assertEquals(8.0, n.fat!!, 0.0001) // 2 x 2 + 4
        assertEquals(4.0, n.protein!!, 0.0001)
        assertEquals(setOf(Nutrient.FAT, Nutrient.PROTEIN, Nutrient.ENERGY), n.partial)
        assertEquals(food.fat, food.exact(Nutrient.FAT))
    }

    @Test
    fun `a deleted food makes the total unknown, never wrong`() {
        assertNull(FoodBookMath.nutritionOf(breakfast, foods - 3L, recipes))
        assertNull(FoodBookMath.nutritionOf(breakfast, foods, recipes - "dish"))
    }

    @Test
    fun `a line typed by hand counts its estimated carbs, its other values are not known`() {
        val lunch = Recipe(
            id = "lunch", name = "Lunch", kind = RecipeKind.MEAL,
            items = listOf(RecipeItem("Bread", 80.0, "g", foodId = 3), RecipeItem("Quiche", 1.0, "", estimatedCarbs = 35.0))
        )
        val n = FoodBookMath.nutritionOf(lunch, foods, recipes)!!
        assertEquals(75.0, n.carbs, 0.0001) // 40 + 35
        assertNull(n.fat)
        assertNull(n.energyKj)
    }

    @Test
    fun `a loop is found and gives no total`() {
        val looped = sweetYogurt.copy(items = sweetYogurt.items + RecipeItem("Breakfast", 1.0, "servings", recipeId = "meal"))
        val withLoop = recipes + ("dish" to looped)
        assertNull(FoodBookMath.nutritionOf(breakfast, foods, withLoop))
        assertTrue(FoodBookMath.makesLoop("dish", looped.items, withLoop))
        assertFalse(FoodBookMath.makesLoop("dish", sweetYogurt.items, recipes))
    }

    private fun bcr(id: Long, timestamp: Long, carbs: Double, note: String) = BCR(
        id = id, timestamp = timestamp, targetBGLow = 0.0, targetBGHigh = 0.0, isf = 0.0, ic = 0.0, bolusIOB = 0.0,
        wasBolusIOBUsed = false, basalIOB = 0.0, wasBasalIOBUsed = false, glucoseValue = 0.0, wasGlucoseUsed = false,
        glucoseDifference = 0.0, glucoseInsulin = 0.0, glucoseTrend = 0.0, wasTrendUsed = false, trendInsulin = 0.0,
        cob = 0.0, wasCOBUsed = false, cobInsulin = 0.0, carbs = carbs, wereCarbsUsed = true, carbsInsulin = 0.0,
        otherCorrection = 0.0, wasSuperbolusUsed = false, superbolusInsulin = 0.0, wasTempTargetUsed = false,
        totalInsulin = 2.5, percentageCorrection = 100, profileName = "", note = note
    )

    private fun eaten(id: String, timestamp: Long, carbs: Int, note: String, target: MealTarget = MealTarget.WIZARD) =
        EatenMeal(id = id, timestamp = timestamp, name = null, note = note, wizardCarbs = carbs, items = emptyList(), target = target)

    private fun carbsEntry(id: Long, created: Long, timestamp: Long, amount: Double, note: String?) =
        CA(id = id, dateCreated = created, timestamp = timestamp, duration = 0, amount = amount, notes = note)

    private fun link(meals: List<EatenMeal>, results: List<BCR>, carbs: List<CA> = emptyList()) =
        FoodBookMath.linkTreatments(meals, results, carbs, slackMs = 60_000, windowMs = 1_800_000)

    @Test
    fun `an eaten meal is linked to the calculation with its note`() {
        val meal = eaten("m", timestamp = 1_000_000, carbs = 56, note = "Bread 80 g (40 g)")
        val results = listOf(
            bcr(1, timestamp = 1_060_000, carbs = 56.0, note = "other"),
            bcr(2, timestamp = 1_120_000, carbs = 56.0, note = "Bread 80 g (40 g)"),
        )
        val link = link(listOf(meal), results)["m"]!!
        assertEquals(1_120_000, link.timestamp)
        assertTrue(link.sameNote)
        assertEquals(2L, link.bcr!!.id) // kept to show the calculation detail
    }

    @Test
    fun `without the note the same carbs give a weaker link`() {
        val meal = eaten("m", timestamp = 1_000_000, carbs = 56, note = "Bread")
        val link = link(listOf(meal), listOf(bcr(1, 1_060_000, 56.0, "changed")))["m"]!!
        assertFalse(link.sameNote)
    }

    @Test
    fun `a calculation too late or with other carbs is not linked, and one is used once`() {
        val first = eaten("a", timestamp = 1_000_000, carbs = 56, note = "x")
        val second = eaten("b", timestamp = 1_010_000, carbs = 56, note = "x")
        val results = listOf(bcr(1, 1_060_000, 56.0, "x"), bcr(2, 9_000_000, 56.0, "x"), bcr(3, 1_070_000, 30.0, "y"))
        val links = link(listOf(first, second), results)
        assertEquals(1_060_000, links["a"]!!.timestamp)
        assertNull(links["b"])
    }

    @Test
    fun `a meal sent to the carbs dialog is linked to its carbs entry, even dated in the past`() {
        val meal = eaten("m", timestamp = 10_000_000, carbs = 40, note = "Bread 80 g (40 g)", target = MealTarget.CARBS)
        // Created just after the meal was sent, but dated one hour earlier in the dialog.
        val entry = carbsEntry(7, created = 10_030_000, timestamp = 6_400_000, amount = 40.0, note = "Bread 80 g (40 g)")
        val found = link(listOf(meal), results = emptyList(), carbs = listOf(entry))["m"]!!
        assertEquals(6_400_000, found.timestamp)
        assertNull(found.insulin)
        assertNull(found.bcr)
        assertTrue(found.sameNote)
    }

    @Test
    fun `each meal looks only at its own kind of treatment`() {
        val wizardMeal = eaten("w", timestamp = 1_000_000, carbs = 40, note = "x")
        val carbsMeal = eaten("c", timestamp = 1_000_000, carbs = 40, note = "x", target = MealTarget.CARBS)
        // A carbs entry never confirms a wizard meal, and a bolus calculation never confirms a carbs meal.
        assertNull(link(listOf(wizardMeal), results = emptyList(), carbs = listOf(carbsEntry(1, 1_010_000, 1_010_000, 40.0, "x")))["w"])
        assertNull(link(listOf(carbsMeal), results = listOf(bcr(1, 1_010_000, 40.0, "x")))["c"])
    }

    @Test
    fun `a cancelled dialog leaves the meal without a treatment`() {
        val meal = eaten("m", timestamp = 1_000_000, carbs = 40, note = "x", target = MealTarget.CARBS)
        assertTrue(link(listOf(meal), results = emptyList(), carbs = emptyList()).isEmpty())
    }

    // ---- The bolus really given for a calculation ----

    private val iCfg = ICfg(insulinLabel = "test", peak = 75, dia = 5.0, concentration = 1.0)

    private fun bolus(id: Long, timestamp: Long, amount: Double, note: String?, type: BS.Type = BS.Type.NORMAL) =
        BS(id = id, timestamp = timestamp, amount = amount, type = type, notes = note, iCfg = iCfg)

    private fun given(results: List<BCR>, boluses: List<BS>) =
        FoodBookMath.bolusesGiven(results, boluses, slackMs = 60_000, noteWindowMs = 1_800_000, timeWindowMs = 300_000)

    @Test
    fun `the bolus with the note of the calculation is the one given, with its real amount`() {
        // Calculated 2.5 U; the user stopped the bolus at 1.8 U. A correction bolus came just before.
        val result = bcr(1, timestamp = 1_000_000, carbs = 40.0, note = "Bread 80 g (40 g)")
        val boluses = listOf(bolus(10, 1_010_000, 1.0, null), bolus(11, 1_200_000, 1.8, "Bread 80 g (40 g)"))
        assertEquals(1.8, given(listOf(result), boluses).getValue(1L).amount, 0.0001)
    }

    @Test
    fun `without the note only a normal bolus given soon after counts, never an SMB`() {
        val result = bcr(1, timestamp = 1_000_000, carbs = 40.0, note = "x")
        assertEquals(10L, given(listOf(result), listOf(bolus(9, 1_020_000, 0.3, null, BS.Type.SMB), bolus(10, 1_030_000, 2.5, null))).getValue(1L).id)
        // 20 minutes later and without the note: not this meal's bolus.
        assertTrue(given(listOf(result), listOf(bolus(10, 2_200_000, 2.5, null))).isEmpty())
    }

    @Test
    fun `a calculation without a bolus has none, and a bolus counts once`() {
        val first = bcr(1, timestamp = 1_000_000, carbs = 40.0, note = "x")
        val second = bcr(2, timestamp = 1_010_000, carbs = 40.0, note = "x")
        val result = given(listOf(first, second), listOf(bolus(10, 1_020_000, 2.5, "x")))
        assertEquals(10L, result.getValue(1L).id)
        assertNull(result[2L])
    }
}
