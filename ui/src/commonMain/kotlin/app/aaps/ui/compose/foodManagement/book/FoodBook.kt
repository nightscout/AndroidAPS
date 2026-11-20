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
    /** Added later: the comments of the foods. The POC database has no column for them. */
    val foodNotes: List<FoodNote> = emptyList(),
)

/**
 * A free comment on a food. It stays on the food: it is not copied into the dishes that use the
 * food, nor into the meal history.
 */
@Serializable
data class FoodNote(
    val foodId: Long,
    val text: String,
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
 * @param inOff false when OFF did not know the barcode: the food was typed by hand with it. "OFF does
 *   not know the product" is not "OFF has no photo": OFF can add the product later.
 */
@Serializable
data class OffLink(
    val foodId: Long,
    val barcode: String,
    val hasImage: Boolean,
    val imageUrl: String? = null,
    val inOff: Boolean = true,
)

@Serializable
enum class RecipeKind {

    /** Foods put together under one name, for example "Sweet yogurt" = 1 yogurt + 2 sugars. */
    DISH,

    /** A standard meal, made of foods and dishes, for example "Breakfast". */
    MEAL
}

/**
 * A saved dish or meal. Its values are always computed from the foods it uses, never stored.
 *
 * @param photo key of a photo taken by the user in [FoodImageStore] (a home-made dish, or the whole
 *   plate), or null. Added later: an older file reads as "no photo".
 */
@Serializable
data class Recipe(
    val id: String,
    val name: String,
    val kind: RecipeKind,
    val items: List<RecipeItem>,
    val photo: String? = null,
    /** A free comment on the dish or meal. Not copied into the meal history. */
    val note: String? = null,
    /** The category and subcategory of a dish, as for a food, to filter the dish list. Not used for meals. */
    val category: String? = null,
    val subCategory: String? = null,
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
    /**
     * True when the meal was saved as a dish or a standard meal after it was eaten, under its [name].
     * It was put together on the spot, so the history still shows its lines.
     */
    val savedLater: Boolean = false,
    /**
     * Key of the photo of the dish or meal sent, as it was then. The file is kept while a meal of the
     * history uses it, also when the dish is deleted or gets another photo.
     */
    val photo: String? = null,
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
    /** The dish of a dish line, so the meal can be saved again as a dish or meal. Null for older meals. */
    val recipeId: String? = null,
    /** For a dish line: key of the photo of the dish, as it was then. */
    val photo: String? = null,
    /** The values of [fat], [protein] and [energyKj] that are only the part known, see [Nutrition]. */
    val partial: Set<Nutrient> = emptySet(),
) {

    val nutrition: Nutrition get() = Nutrition(carbs, fat, protein, energyKj, partial)
}

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

/** The values of a food that can be missing: a food does not always have them. */
@Serializable
enum class Nutrient { FAT, PROTEIN, ENERGY }

/**
 * Nutrition of a quantity.
 *
 * Carbs are always known. Fat, protein and energy are not known for every food. A value is the sum of
 * the foods that have it, and null when none has it. When some foods have it and others do not, the
 * value is only the part known: it is in [partial], and the real value is at least this one. It is
 * shown as such (">12 g"). These values are only shown: they are never sent to a dialog.
 */
data class Nutrition(
    val carbs: Double,
    val fat: Double?,
    val protein: Double?,
    val energyKj: Double?,
    val partial: Set<Nutrient> = emptySet(),
) {

    operator fun plus(other: Nutrition): Nutrition {
        val missing = mutableSetOf<Nutrient>()
        missing += partial
        missing += other.partial
        fun add(nutrient: Nutrient, a: Double?, b: Double?): Double? {
            if (a == null || b == null) {
                if (a != null || b != null) missing += nutrient
                return a ?: b
            }
            return a + b
        }
        return Nutrition(
            carbs = carbs + other.carbs,
            fat = add(Nutrient.FAT, fat, other.fat),
            protein = add(Nutrient.PROTEIN, protein, other.protein),
            energyKj = add(Nutrient.ENERGY, energyKj, other.energyKj),
            partial = missing,
        )
    }

    operator fun times(factor: Double) = Nutrition(carbs * factor, fat?.times(factor), protein?.times(factor), energyKj?.times(factor), partial)

    /** The value of [nutrient] only when it is complete, for a file that must hold exact values. */
    fun exact(nutrient: Nutrient): Double? = if (nutrient in partial) null else valueOf(nutrient)

    fun valueOf(nutrient: Nutrient): Double? = when (nutrient) {
        Nutrient.FAT     -> fat
        Nutrient.PROTEIN -> protein
        Nutrient.ENERGY  -> energyKj
    }

    companion object {

        val ZERO = Nutrition(0.0, 0.0, 0.0, 0.0)

        /** The total of the lines. Empty lines give [ZERO]. */
        fun sum(lines: List<Nutrition>): Nutrition = lines.reduceOrNull { a, b -> a + b } ?: ZERO
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

    /**
     * The lines of a new dish or meal made from [meal] of the history. A food line keeps its food,
     * and a dish line its dish, while they are still here and the food is counted in the same unit.
     * An older line without them is found by its name, when only one food (in the same unit) or one
     * dish has it. [servings] is the unit of a dish line: such a line is never taken for a food.
     *
     * Any other line is kept as a line typed by hand with the carbs eaten: the new dish never counts
     * a line as 0 g, and never reads "50 g" of a food now counted in biscuits as "50 biscuits".
     */
    fun recipeItemsOf(meal: EatenMeal, foods: Map<Long, FD>, recipes: List<Recipe>, servings: String): List<RecipeItem> = meal.items.map { item ->
        if (item.estimated) return@map typedLine(item)
        if (item.recipeId == null && unitDiffers(item.unit, servings)) {
            val byId = item.foodId?.let { foods[it] }?.takeUnless { unitChanged(item.unit, it) }
            val food = byId ?: foods.values.filter { sameName(it.name, item.name) && !unitChanged(item.unit, it) }.singleOrNull()
            if (food != null) return@map RecipeItem(item.name, item.quantity, item.unit, foodId = food.id)
        } else {
            val dishes = recipes.filter { it.kind == RecipeKind.DISH }
            val dish = item.recipeId?.let { id -> dishes.firstOrNull { it.id == id } } ?: dishes.filter { sameName(it.name, item.name) }.singleOrNull()
            if (dish != null) return@map RecipeItem(item.name, item.quantity, item.unit, recipeId = dish.id)
        }
        typedLine(item)
    }

    /** A line typed by hand with the carbs of [item], as the meal sheet makes it. */
    private fun typedLine(item: EatenItem) = RecipeItem(item.name, quantity = 1.0, unit = "", estimatedCarbs = item.carbs)

    private fun unitChanged(unit: String, food: FD): Boolean = food.portion > 0 && unitDiffers(unit, food.unit)

    private fun sameName(a: String, b: String): Boolean = a.trim().equals(b.trim(), ignoreCase = true)

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
        val lines = recipe.items.map { item -> nutritionOf(item, foods, recipes, visiting + recipe.id) ?: return null }
        // Not started from ZERO: a 0 there would make "no line has fat" look like "fat known, 0 g".
        return Nutrition.sum(lines)
    }

    private fun nutritionOf(item: RecipeItem, foods: Map<Long, FD>, recipes: Map<String, Recipe>, visiting: List<String>): Nutrition? {
        item.estimatedCarbs?.let { return Nutrition(carbs = it, fat = null, protein = null, energyKj = null) }
        item.foodId?.let { id -> return foods[id]?.takeUnless { unitChanged(item, it) }?.let { nutritionOf(it, item.quantity) } }
        item.recipeId?.let { id -> return recipes[id]?.let { recipe -> nutritionOf(recipe, foods, recipes, visiting)?.times(item.quantity) } }
        return null
    }
}
