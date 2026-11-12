package app.aaps.ui.compose.foodManagement.book

import app.aaps.core.data.model.BCR
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.CA
import app.aaps.core.data.model.FD
import app.aaps.ui.compose.foodManagement.LineProblem
import kotlinx.serialization.Serializable
import kotlin.math.abs

// PROOF OF CONCEPT (POC 2 and 3). Dishes, meals and the meals eaten are kept in one JSON file, apart
// from the AAPS database, so trying them can never touch the real data. The final storage is to be
// designed once, from what these POCs teach.

/** Everything the food POC stores. */
@Serializable
data class FoodBook(
    val recipes: List<Recipe> = emptyList(),
    val eaten: List<EatenMeal> = emptyList(),
    val offLinks: List<OffLink> = emptyList(),
    /** Added later: an older file reads as "no photo taken". */
    val photos: List<FoodPhoto> = emptyList(),
)

/**
 * A photo taken by the user for a food. It is shown instead of the Open Food Facts photo, which is
 * kept: removing the photo of the user shows the OFF photo again, without downloading it.
 *
 * @param key name of the photo file in [FoodImageStore], for example "user_3f9a0c1e"
 */
@Serializable
data class FoodPhoto(
    val foodId: Long,
    val key: String,
)

/**
 * A food imported from Open Food Facts, with the product it came from. Kept here because the POC
 * does not change the database (the final version has a barcode column on the food).
 *
 * The photo itself is a small file named after the barcode, outside the database. It is not saved
 * in a backup: [hasImage] and [imageUrl] are enough to download it again.
 *
 * @param hasImage false when OFF has no photo for this product: nothing is downloaded then
 * @param imageUrl where the photo was. OFF changes it when a photo is replaced, so a missing photo
 *   reads the product again to get the address of today.
 */
@Serializable
data class OffLink(
    val foodId: Long,
    val barcode: String,
    val hasImage: Boolean,
    val imageUrl: String? = null,
)

@Serializable
enum class RecipeKind {

    /** Foods put together under one name, for example "Sweet yogurt" = 1 yogurt + 2 sugars. */
    DISH,

    /** A standard meal, made of foods and dishes, for example "Breakfast". */
    MEAL
}

/** A saved dish or meal. Its values are always computed from the foods it uses, never stored. */
@Serializable
data class Recipe(
    val id: String,
    val name: String,
    val kind: RecipeKind,
    val items: List<RecipeItem>,
)

/**
 * One line of a dish or a meal: a food, another dish, or a line typed by hand.
 *
 * @param name kept so the line can still be shown when its food or dish has been deleted
 * @param quantity for a food, in the unit of the food (g, ml, pot...). For a dish, in servings: 1 is
 *   the whole dish.
 * @param unit shown with the quantity
 * @param estimatedCarbs set only for a line typed by hand ("home-made quiche"): the carbs the user
 *   estimated. Such a line has no food and no dish, and its fat, protein and energy are not known.
 */
@Serializable
data class RecipeItem(
    val name: String,
    val quantity: Double,
    val unit: String,
    val foodId: Long? = null,
    val recipeId: String? = null,
    val estimatedCarbs: Double? = null,
)

/** Where a meal was sent from the food screen. */
@Serializable
enum class MealTarget {

    /** The bolus wizard: it saves a bolus calculation with the note. */
    WIZARD,

    /** The carbs dialog: it saves a carbs entry with the note. No insulin. */
    CARBS
}

/**
 * A meal sent to the bolus wizard or to the carbs dialog, with a copy of every value at that time.
 * Changing a food or a standard meal later does not change what was eaten.
 *
 * The meal is kept "waiting" until the treatment it was sent for is found in the database. A meal
 * whose treatment never comes (the dialog was cancelled) is removed, so the history only lists
 * meals that were really entered. Once found, the meal keeps the id of its treatment and follows
 * it: when that treatment is deleted in the treatments history, the meal is removed too.
 *
 * @param timestamp when the user opened the dialog with this meal
 * @param note the note given to the dialog. Used to find the treatment that came from it.
 * @param validatedAt time of the treatment found for this meal, or null while it is still waiting
 * @param treatmentId database id of that treatment: a bolus calculation for [MealTarget.WIZARD], a
 *   carbs entry for [MealTarget.CARBS]. Null while waiting, and for meals saved by an older POC.
 */
@Serializable
data class EatenMeal(
    val id: String,
    val timestamp: Long,
    val name: String?,
    val note: String,
    val wizardCarbs: Int,
    val items: List<EatenItem>,
    val target: MealTarget = MealTarget.WIZARD,
    val validatedAt: Long? = null,
    val treatmentId: Long? = null,
    /** True when a saved meal was changed before it was sent: the history then shows its lines. */
    val changed: Boolean = false,
)

/** @param estimated true for a line typed by hand: its carbs are the estimate of the user. */
@Serializable
data class EatenItem(
    val name: String,
    val quantity: Double,
    val unit: String,
    val carbs: Double,
    val fat: Double?,
    val protein: Double?,
    val energyKj: Double?,
    val estimated: Boolean = false,
    /** The food of the line, to show its photo. Null for older meals, dishes and lines typed by hand. */
    val foodId: Long? = null,
)

/**
 * The treatment found for an eaten meal.
 *
 * @param id database id of the bolus calculation or of the carbs entry
 * @param insulin the insulin of the bolus calculation, or null for a carbs entry
 * @param bcr the bolus calculation, to show its detail. Null for a carbs entry.
 * @param sameNote true when the treatment has the meal note. False when only the carbs and the time
 *   match, which is a weaker link.
 */
data class TreatmentLink(val id: Long, val timestamp: Long, val insulin: Double?, val carbs: Double, val sameNote: Boolean, val bcr: BCR? = null)

/** Nutrition of a quantity. A value is null when one of the foods does not have it. */
data class Nutrition(
    val carbs: Double,
    val fat: Double?,
    val protein: Double?,
    val energyKj: Double?,
) {

    operator fun plus(other: Nutrition) = Nutrition(
        carbs = carbs + other.carbs,
        fat = sum(fat, other.fat),
        protein = sum(protein, other.protein),
        energyKj = sum(energyKj, other.energyKj),
    )

    operator fun times(factor: Double) = Nutrition(carbs * factor, fat?.times(factor), protein?.times(factor), energyKj?.times(factor))

    private fun sum(a: Double?, b: Double?): Double? = if (a == null || b == null) null else a + b

    companion object {

        val ZERO = Nutrition(0.0, 0.0, 0.0, 0.0)
    }
}

/**
 * Computes dishes and meals from the foods they use.
 *
 * A dish can hold another dish, so the computation walks down. It stops on a loop (a dish that holds
 * itself, maybe through another one) and on a food or dish that does not exist any more: the answer
 * is then null, and the user must see that the total is not known. A wrong total is never given.
 */
internal object FoodBookMath {

    /** How deep dishes may hold dishes. Far more than any real meal needs. */
    private const val MAX_DEPTH = 8

    fun nutritionOf(food: FD, quantity: Double): Nutrition {
        val factor = if (food.portion > 0) quantity / food.portion else quantity
        return Nutrition(
            carbs = food.carbs * factor,
            fat = food.fat?.let { it * factor },
            protein = food.protein?.let { it * factor },
            energyKj = food.energy?.let { it * factor },
        )
    }

    fun nutritionOf(recipe: Recipe, foods: Map<Long, FD>, recipes: Map<String, Recipe>): Nutrition? =
        nutritionOf(recipe, foods, recipes, visiting = emptyList())

    fun nutritionOf(item: RecipeItem, foods: Map<Long, FD>, recipes: Map<String, Recipe>): Nutrition? =
        nutritionOf(item, foods, recipes, visiting = emptyList())

    /**
     * True when the unit of [food] is not the unit [item] was saved with: "50 g" of a food now counted
     * in biscuits. A food without a portion size is counted in portions, its unit is not used.
     */
    fun unitChanged(item: RecipeItem, food: FD): Boolean = food.portion > 0 && unitDiffers(item.unit, food.unit)

    /** "g" and " G " are the same unit. */
    fun unitDiffers(a: String, b: String): Boolean = a.trim().lowercase() != b.trim().lowercase()

    /**
     * Why the total of [recipe] is not known, looking into the dishes it uses: a deleted food or dish
     * first, then a changed unit. Null when nothing is wrong.
     */
    fun problemOf(recipe: Recipe, foods: Map<Long, FD>, recipes: Map<String, Recipe>, depth: Int = 0): LineProblem? {
        if (depth > MAX_DEPTH) return null
        var found: LineProblem? = null
        for (item in recipe.items) {
            val problem = when {
                item.estimatedCarbs != null -> null
                item.foodId != null         -> {
                    val food = foods[item.foodId]
                    when {
                        food == null              -> LineProblem.DELETED
                        unitChanged(item, food) -> LineProblem.UNIT_CHANGED
                        else                      -> null
                    }
                }

                item.recipeId != null       -> {
                    val child = recipes[item.recipeId]
                    if (child == null) LineProblem.DELETED else problemOf(child, foods, recipes, depth + 1)
                }

                else                        -> LineProblem.DELETED
            }
            if (problem == LineProblem.DELETED) return problem
            if (problem != null) found = problem
        }
        return found
    }

    /** True when [items], saved as recipe [id], would hold that recipe again somewhere below. */
    fun makesLoop(id: String, items: List<RecipeItem>, recipes: Map<String, Recipe>): Boolean {
        fun holds(itemList: List<RecipeItem>, depth: Int): Boolean = itemList.any { item ->
            val child = item.recipeId ?: return@any false
            child == id || depth >= MAX_DEPTH || holds(recipes[child]?.items.orEmpty(), depth + 1)
        }
        return holds(items, 0)
    }

    /**
     * Finds, for each eaten meal, the treatment saved for it: a bolus calculation for a meal sent to
     * the wizard, a carbs entry for a meal sent to the carbs dialog. The treatment must be made
     * between [slackMs] before and [windowMs] after the meal was sent, with the same note, or else
     * with the same carbs. Each treatment is given to one meal only, the oldest meal first.
     *
     * A carbs entry is placed at the time chosen in the dialog, which can be in the past or the
     * future, so for those the time it was created is used when it is known.
     */
    fun linkTreatments(meals: List<EatenMeal>, results: List<BCR>, carbs: List<CA>, slackMs: Long, windowMs: Long): Map<String, TreatmentLink> {
        val usedResults = mutableSetOf<Long>()
        val usedCarbs = mutableSetOf<Long>()
        val links = mutableMapOf<String, TreatmentLink>()
        for (meal in meals.sortedBy { it.timestamp }) {
            val range = (meal.timestamp - slackMs)..(meal.timestamp + windowMs)
            when (meal.target) {
                MealTarget.WIZARD -> {
                    val window = results.filter { it.id !in usedResults && it.timestamp in range }
                    val byNote = window.firstOrNull { it.note == meal.note }
                    val match = byNote ?: window.firstOrNull { abs(it.carbs - meal.wizardCarbs) < 0.5 } ?: continue
                    usedResults += match.id
                    links[meal.id] = linkOf(meal, match)
                }

                MealTarget.CARBS  -> {
                    val window = carbs.filter { it.id !in usedCarbs && createdAt(it) in range }
                    val byNote = window.firstOrNull { it.notes == meal.note }
                    val match = byNote ?: window.firstOrNull { abs(it.amount - meal.wizardCarbs) < 0.5 } ?: continue
                    usedCarbs += match.id
                    links[meal.id] = linkOf(meal, match)
                }
            }
        }
        return links
    }

    /** The link of [meal] to the bolus calculation made for it. */
    fun linkOf(meal: EatenMeal, result: BCR) = TreatmentLink(result.id, result.timestamp, result.totalInsulin, result.carbs, sameNote = result.note == meal.note, bcr = result)

    /** The link of [meal] to the carbs entry made for it. */
    fun linkOf(meal: EatenMeal, carbs: CA) = TreatmentLink(carbs.id, carbs.timestamp, insulin = null, carbs = carbs.amount, sameNote = carbs.notes == meal.note)

    private fun createdAt(carbs: CA): Long = if (carbs.dateCreated > 0) carbs.dateCreated else carbs.timestamp

    /**
     * The bolus really given for each bolus calculation, by calculation id. What counts for the user
     * and the care team is what was given: the user can change the amount or stop the bolus.
     *
     * The database does not link a calculation to its bolus. The bolus of the wizard carries the
     * same note, so a normal bolus (not an SMB, not priming) with that note within [noteWindowMs]
     * after the calculation is it. Without a note match (a pump that loses the note), only a normal
     * bolus within [timeWindowMs] counts, the nearest one. A bolus is given to one calculation only.
     * A calculation without a bolus (cancelled, or 0 U) is not in the result.
     */
    fun bolusesGiven(results: List<BCR>, boluses: List<BS>, slackMs: Long, noteWindowMs: Long, timeWindowMs: Long): Map<Long, BS> {
        val normal = boluses.filter { it.isValid && it.type == BS.Type.NORMAL }
        val used = mutableSetOf<Long>()
        val given = mutableMapOf<Long, BS>()
        for (result in results.sortedBy { it.timestamp }) {
            val free = normal.filter { it.id !in used && it.timestamp >= result.timestamp - slackMs }
            val byNote = free.firstOrNull { result.note.isNotEmpty() && it.notes == result.note && it.timestamp <= result.timestamp + noteWindowMs }
            val match = byNote ?: free.filter { it.timestamp <= result.timestamp + timeWindowMs }.minByOrNull { abs(it.timestamp - result.timestamp) } ?: continue
            used += match.id
            given[result.id] = match
        }
        return given
    }

    private fun nutritionOf(recipe: Recipe, foods: Map<Long, FD>, recipes: Map<String, Recipe>, visiting: List<String>): Nutrition? {
        if (recipe.id in visiting || visiting.size >= MAX_DEPTH) return null
        var total = Nutrition.ZERO
        for (item in recipe.items) total += nutritionOf(item, foods, recipes, visiting + recipe.id) ?: return null
        return total
    }

    private fun nutritionOf(item: RecipeItem, foods: Map<Long, FD>, recipes: Map<String, Recipe>, visiting: List<String>): Nutrition? {
        item.estimatedCarbs?.let { return Nutrition(carbs = it, fat = null, protein = null, energyKj = null) }
        item.foodId?.let { id -> return foods[id]?.takeUnless { unitChanged(item, it) }?.let { nutritionOf(it, item.quantity) } }
        item.recipeId?.let { id -> return recipes[id]?.let { recipe -> nutritionOf(recipe, foods, recipes, visiting)?.times(item.quantity) } }
        return null
    }
}
