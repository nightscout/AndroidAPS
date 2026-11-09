package app.aaps.ui.compose.foodManagement

import app.aaps.core.data.model.FD
import app.aaps.ui.compose.foodManagement.openfoodfacts.OffProduct
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The carbs of a meal: this number goes to the bolus wizard, so each rule is pinned here. */
class MealMathTest {

    private fun food(carbs: Int, portion: Double, unit: String = "g") =
        FD(name = "x", portion = portion, carbs = carbs, unit = unit)

    @Test
    fun `carbs scale with the quantity`() {
        // 50 g carbs in 100 g of bread: 80 g of bread is 40 g carbs.
        assertEquals(40.0, MealMath.carbsFor(food(carbs = 50, portion = 100.0), 80.0), 0.0001)
        // 12 g carbs in a 250 ml bowl: half a bowl is 6 g.
        assertEquals(6.0, MealMath.carbsFor(food(carbs = 12, portion = 250.0, unit = "ml"), 125.0), 0.0001)
    }

    @Test
    fun `a food without a portion size is counted in portions`() {
        assertEquals(30.0, MealMath.carbsFor(food(carbs = 15, portion = 0.0), 2.0), 0.0001)
    }

    @Test
    fun `the total is rounded only once, at the end`() {
        val items = listOf(
            MealItem(key = 1, name = "a", quantity = 1.0, unit = "g", carbs = 10.4),
            MealItem(key = 2, name = "b", quantity = 1.0, unit = "g", carbs = 10.4),
        )
        assertEquals(20.8, MealMath.totalCarbs(items), 0.0001)
        assertEquals(21, MealMath.wizardCarbs(items)) // not 10 + 10
    }

    @Test
    fun `typed amounts accept a comma and refuse zero, negative and text`() {
        assertEquals(1.5, MealMath.parseAmount("1,5"))
        assertEquals(80.0, MealMath.parseAmount(" 80 "))
        assertNull(MealMath.parseAmount("0"))
        assertNull(MealMath.parseAmount("-5"))
        assertNull(MealMath.parseAmount("abc"))
        assertNull(MealMath.parseAmount(""))
    }

    @Test
    fun `nutrition values typed from the package are rounded, not lost`() {
        assertEquals(15, MealMath.parseNutrient("14.8"))
        assertEquals(2, MealMath.parseNutrient("1,7"))
        assertEquals(0, MealMath.parseNutrient("0.4"))
        assertEquals(0, MealMath.parseNutrient("0"))
        assertEquals(12, MealMath.parseNutrient(" 12 "))
        assertNull(MealMath.parseNutrient("-1"))
        assertNull(MealMath.parseNutrient("abc"))
        assertNull(MealMath.parseNutrient(""))
    }

    @Test
    fun `amounts are shown with at most one decimal`() {
        assertEquals("80", MealMath.formatAmount(80.0))
        assertEquals("4.7", MealMath.formatAmount(4.66))
        assertEquals("0", MealMath.formatAmount(0.0))
    }

    @Test
    fun `an OFF product becomes a 100 g food with rounded values`() {
        val product = OffProduct(
            barcode = "1", name = "Lait", brand = "Ferme", carbsPer100 = 4.7, sugarsPer100 = 4.7, fiberPer100 = null,
            fatPer100 = 1.5, proteinPer100 = 3.2, energyKjPer100 = 196.0, isLiquid = true
        )
        val values = MealMath.foodValuesFrom(product)
        assertEquals("Lait (Ferme)", values.name)
        assertEquals(100.0, values.portion)
        assertEquals("ml", values.unit)
        assertEquals(5, values.carbs)
        assertEquals(2, values.fat)
        assertEquals(196, values.energyKj)
    }

    @Test
    fun `missing OFF carbs stay empty instead of becoming zero`() {
        val product = OffProduct(
            barcode = "1", name = "X", brand = null, carbsPer100 = null, sugarsPer100 = null, fiberPer100 = null,
            fatPer100 = null, proteinPer100 = null, energyKjPer100 = null, isLiquid = false
        )
        val values = MealMath.foodValuesFrom(product)
        assertNull(values.carbs)
        assertEquals("X", values.name)
        assertEquals("g", values.unit)
    }

    private fun yogurt(servingSize: String?, servingQuantity: Double?) = OffProduct(
        barcode = "1", name = "Yaourt", brand = null, carbsPer100 = 8.8, sugarsPer100 = null, fiberPer100 = null,
        fatPer100 = 3.2, proteinPer100 = 4.0, energyKjPer100 = 300.0, isLiquid = false,
        servingSize = servingSize, servingQuantity = servingQuantity
    )

    @Test
    fun `a counted serving becomes the portion, with values for that serving`() {
        val values = MealMath.foodValuesFrom(yogurt("1 pot (125 g)", 125.0))
        assertEquals(1.0, values.portion)
        assertEquals("pot (125 g)", values.unit)
        assertEquals(125.0, values.gramsPerUnit)
        assertEquals(11, values.carbs) // 8.8 g per 100 g, so 11 g for 125 g
        assertEquals(375, values.energyKj)
    }

    @Test
    fun `a serving that is only a weight becomes a portion in grams`() {
        val values = MealMath.foodValuesFrom(yogurt("125 g", 125.0))
        assertEquals(125.0, values.portion)
        assertEquals("g", values.unit)
        assertEquals(1.0, values.gramsPerUnit)
        assertEquals(11, values.carbs)
    }

    @Test
    fun `a serving of several pieces counts in pieces`() {
        val portion = MealMath.servingPortion(yogurt("3 biscuits (30 g)", 30.0))!!
        assertEquals(3.0, portion.portion)
        assertEquals("biscuits (30 g)", portion.unit)
        assertEquals(10.0, portion.gramsPerUnit, 0.0001)
    }

    @Test
    fun `a weight written first, with a label, becomes a counted portion`() {
        val pot = MealMath.servingPortion(yogurt("125 g (1 pot)", 125.0))!!
        assertEquals(1.0, pot.portion)
        assertEquals("pot (125 g)", pot.unit)
        assertEquals(125.0, pot.gramsPerUnit)

        val plain = MealMath.servingPortion(yogurt("125 g (Pot)", 125.0))!!
        assertEquals(1.0, plain.portion)
        assertEquals("Pot (125 g)", plain.unit)
    }

    @Test
    fun `without a serving weight, or when asked, the portion is 100 g`() {
        assertEquals(100.0, MealMath.foodValuesFrom(yogurt("1 pot", null)).portion)
        val per100 = MealMath.foodValuesFrom(yogurt("1 pot (125 g)", 125.0), perServing = false)
        assertEquals(100.0, per100.portion)
        assertEquals("g", per100.unit)
        assertEquals(9, per100.carbs)
    }

    @Test
    fun `values for a portion come from the exact values, rounded once`() {
        // 2 pots of 125 g: 22 g carbs (8.8 x 2.5), not 2 x 11 from an already rounded value.
        val nutrients = MealMath.nutrientsFor(yogurt(null, null), portion = 2.0, gramsPerUnit = 125.0)
        assertEquals(22, nutrients.carbs)
        assertEquals(8, nutrients.fat)
    }
}
