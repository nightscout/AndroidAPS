package app.aaps.ui.compose.foodManagement

import app.aaps.core.data.model.FD
import app.aaps.ui.compose.foodManagement.openfoodfacts.OffProduct
import kotlin.math.roundToInt

/**
 * One food in the meal the user is putting together.
 *
 * PROOF OF CONCEPT. The meal being put together is kept in memory. It can be saved as a dish or a
 * standard meal, and a copy is kept when it is sent to the bolus wizard.
 *
 * @param key unique inside one meal, so the same food can be added twice
 * @param quantity in the unit of the food (g, ml, ...)
 * @param carbs grams of carbs for this quantity, not rounded
 * @param foodId the food this line comes from, so the meal can be saved as a dish or a meal
 * @param recipeId the dish this line comes from (quantity is then in servings)
 * @param estimated true for a line typed by hand: a name and the carbs the user estimated, without
 *   a food or a dish behind it. Its quantity and unit mean nothing and are not shown.
 * @param problem set when the line cannot be computed any more: its food or dish was deleted, or
 *   the unit of its food changed. Only found while a dish or a meal is changed, so the user can
 *   remove or correct the line. Its carbs are not known (0 here), so a dish or meal with such a line
 *   cannot be saved.
 */
data class MealItem(
    val key: Long,
    val name: String,
    val quantity: Double,
    val unit: String,
    val carbs: Double,
    val fat: Double? = null,
    val protein: Double? = null,
    val energyKj: Double? = null,
    val foodId: Long? = null,
    val recipeId: String? = null,
    val estimated: Boolean = false,
    val problem: LineProblem? = null,
)

/** Why a line of a saved dish or meal cannot be computed any more. */
enum class LineProblem {

    /** Its food or dish was deleted. */
    DELETED,

    /**
     * The unit of its food changed (100 g became 1 biscuit). The quantity of the line is in the old
     * unit, so "50 g" would be read as "50 biscuits": the user must set the quantity again.
     */
    UNIT_CHANGED
}

/**
 * The values of a food when the editor started to follow the portion: a new portion changes every
 * value in proportion.
 */
data class ScaleBase(val portion: Double, val carbs: Double?, val fat: Double?, val protein: Double?, val energy: Double?)

internal object MealMath {

    /**
     * Carbs of [quantity] of [food]. The food stores its carbs for one portion of `portion` units.
     * A food without a portion size (portion 0) is counted in portions instead.
     */
    fun carbsFor(food: FD, quantity: Double): Double =
        if (food.portion > 0) food.carbs * quantity / food.portion
        else food.carbs * quantity

    fun totalCarbs(items: List<MealItem>): Double = items.sumOf { it.carbs }

    /** The wizard takes whole grams. */
    fun wizardCarbs(items: List<MealItem>): Int = totalCarbs(items).roundToInt()

    /** Reads a typed number. Accepts "1,5" as well as "1.5". Null when it is not a positive number. */
    fun parseAmount(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }

    /**
     * Reads a nutrition value typed from the package: "14.8" or "14,8". The food keeps whole numbers,
     * so it is rounded (14.8 -> 15). Null when empty, negative or not a number.
     */
    fun parseNutrient(text: String): Int? =
        text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 && it.isFinite() }?.roundToInt()

    /** 80.0 shows as "80", 12.25 as "12.3". */
    fun formatAmount(value: Double): String {
        val tenths = (value * 10).roundToInt()
        return if (tenths % 10 == 0) (tenths / 10).toString() else (tenths / 10.0).toString()
    }

    /**
     * The food an OFF product becomes in the editor.
     *
     * With [perServing] and a serving known to OFF, one portion is that serving: "1 pot (125 g)" gives
     * portion 1 and unit "pot (125 g)", "125 g" gives portion 125 and unit "g". Otherwise one portion
     * is 100 g (or 100 ml).
     *
     * The food table stores whole grams, so the values are rounded here, once, from the exact OFF
     * values. The editor shows the exact values next to them, so the user can see the rounding.
     */
    fun foodValuesFrom(product: OffProduct, perServing: Boolean = true): OffFoodValues {
        val portion = (if (perServing) servingPortion(product) else null) ?: per100Portion(product)
        val nutrients = nutrientsFor(product, portion.portion, portion.gramsPerUnit)
        return OffFoodValues(
            name = if (product.brand != null) "${product.name} (${product.brand})" else product.name,
            portion = portion.portion,
            unit = portion.unit,
            gramsPerUnit = portion.gramsPerUnit,
            carbs = nutrients.carbs,
            fat = nutrients.fat,
            protein = nutrients.protein,
            energyKj = nutrients.energyKj,
        )
    }

    /** The serving OFF knows for this product, or null when OFF has no serving weight. */
    fun servingPortion(product: OffProduct): OffPortion? {
        val grams = product.servingQuantity ?: return null
        val text = product.servingSize?.trim().orEmpty()
        if (text.isEmpty() || WEIGHT_ONLY.matches(text)) return OffPortion(grams, baseUnit(product), 1.0)
        // "125 g (1 pot)" or "125 g (Pot)": the weight first, then what it is. Turned into "1 pot (125 g)".
        WEIGHT_THEN_LABEL.matchEntire(text)?.let { match ->
            val weight = match.groupValues[1].trim()
            val label = match.groupValues[2].trim()
            return countedPortion(label, grams, weight) ?: OffPortion(1.0, "$label ($weight)", grams)
        }
        return countedPortion(text, grams, weight = null) ?: OffPortion(1.0, text, grams)
    }

    /**
     * "1 pot (125 g)" or "3 biscuits": a count, then what is counted. The count becomes the portion,
     * so the meal can be counted in pots or biscuits. [weight], when known apart, is added to the unit.
     */
    private fun countedPortion(text: String, grams: Double, weight: String?): OffPortion? {
        val match = LEADING_COUNT.matchEntire(text) ?: return null
        val count = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val rest = match.groupValues[2].trim()
        if (count <= 0 || rest.isEmpty()) return null
        return OffPortion(count, if (weight != null) "$rest ($weight)" else rest, grams / count)
    }

    /**
     * Nutrition of [portion] units when one unit weighs [gramsPerUnit] g (or ml), from the exact
     * per-100 OFF values. A value OFF does not have stays null.
     */
    fun nutrientsFor(product: OffProduct, portion: Double, gramsPerUnit: Double): OffNutrients {
        val factor = portion * gramsPerUnit / 100.0
        return OffNutrients(
            carbs = product.carbsPer100?.let { (it * factor).roundToInt() },
            fat = product.fatPer100?.let { (it * factor).roundToInt() },
            protein = product.proteinPer100?.let { (it * factor).roundToInt() },
            energyKj = product.energyKjPer100?.let { (it * factor).roundToInt() },
        )
    }

    private fun per100Portion(product: OffProduct) = OffPortion(100.0, baseUnit(product), 1.0)

    private fun baseUnit(product: OffProduct) = if (product.isLiquid) "ml" else "g"

    /** "125 g", "125g", "12.5 cl": a weight or a volume and nothing else. */
    private val WEIGHT_ONLY = Regex("""^\d+(?:[.,]\d+)?\s*(?:mg|g|gr|kg|ml|cl|dl|l)?\.?$""", RegexOption.IGNORE_CASE)

    /** "125 g (1 pot)": a weight or a volume, then a label in brackets. */
    private val WEIGHT_THEN_LABEL = Regex("""^(\d+(?:[.,]\d+)?\s*(?:mg|g|gr|kg|ml|cl|dl|l)\.?)\s*\((.+)\)$""", RegexOption.IGNORE_CASE)

    /** "1 pot (125 g)", "3 biscuits (30 g)": a count, then what is counted. */
    private val LEADING_COUNT = Regex("""^(\d+(?:[.,]\d+)?)\s+(.+)$""")
}

/** One portion of an OFF food: [portion] units of [unit], each weighing [gramsPerUnit] g (or ml). */
internal data class OffPortion(val portion: Double, val unit: String, val gramsPerUnit: Double)

internal data class OffNutrients(val carbs: Int?, val fat: Int?, val protein: Int?, val energyKj: Int?)

internal data class OffFoodValues(
    val name: String,
    val portion: Double,
    val unit: String,
    val gramsPerUnit: Double,
    val carbs: Int?,
    val fat: Int?,
    val protein: Int?,
    val energyKj: Int?,
)
