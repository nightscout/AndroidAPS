package app.aaps.ui.compose.foodManagement.book

import app.aaps.core.data.model.FD
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// PROOF OF CONCEPT, personal use. A backup of everything the food POC uses, so the POC can be tried
// on a real phone without losing the data, and so the data can be moved later into the final
// storage. In AAPS a backup normally goes through Nightscout: see the feature request.

/**
 * One backup file.
 *
 * @param format version of this layout. A newer layout is refused, not read wrong.
 * @param foods the foods of the database. Dishes point to them by [BackupFood.id].
 */
@Serializable
data class FoodBackup(
    val format: Int,
    val exportedAt: Long,
    val foods: List<BackupFood>,
    val recipes: List<Recipe>,
    val eaten: List<EatenMeal>,
)

/** A food as saved in the backup. [id] is its database id on the phone that made the backup. */
@Serializable
data class BackupFood(
    val id: Long,
    val name: String,
    val category: String? = null,
    val subCategory: String? = null,
    val portion: Double,
    val unit: String,
    val carbs: Int,
    val fat: Int? = null,
    val protein: Int? = null,
    val energy: Int? = null,
    val gi: Int? = null,
)

/** What a restore adds to the food book: only what is not there yet. */
data class BackupMerge(val recipes: List<Recipe>, val meals: List<EatenMeal>)

/**
 * Writes and reads backups, and decides what a restore adds.
 *
 * A restore never deletes or changes anything: it only adds what is missing. Database ids are not
 * the same on another phone or after a new install, so a food is found again by what it is (name,
 * portion, unit and carbs), and the dishes are pointed to the food found.
 */
internal object FoodBackupMath {

    const val FORMAT = 1
    const val FILE_NAME = "FoodBackup.json"

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun encode(foods: List<FD>, book: FoodBook, now: Long): String =
        json.encodeToString(
            FoodBackup.serializer(),
            FoodBackup(
                format = FORMAT,
                exportedAt = now,
                foods = foods.map { BackupFood(it.id, it.name, it.category, it.subCategory, it.portion, it.unit, it.carbs, it.fat, it.protein, it.energy, it.gi) },
                recipes = book.recipes,
                eaten = book.eaten,
            )
        )

    /** The backup in [text], or null when it is not a backup this version can read. */
    fun decode(text: String): FoodBackup? =
        runCatching { json.decodeFromString(FoodBackup.serializer(), text) }.getOrNull()?.takeIf { it.format in 1..FORMAT }

    /** The foods of [backup] not found in [existing], ready to be saved (new database rows). */
    fun missingFoods(backup: FoodBackup, existing: List<FD>): List<FD> {
        val known = existing.map { keyOf(it.name, it.portion, it.unit, it.carbs) }.toSet()
        return backup.foods
            .filter { keyOf(it.name, it.portion, it.unit, it.carbs) !in known }
            .distinctBy { keyOf(it.name, it.portion, it.unit, it.carbs) }
            .map { FD(name = it.name, category = it.category, subCategory = it.subCategory, portion = it.portion, unit = it.unit, carbs = it.carbs, fat = it.fat, protein = it.protein, energy = it.energy, gi = it.gi) }
    }

    /**
     * The dishes, standard meals and eaten meals of [backup] that [book] does not have yet.
     *
     * A dish or meal is already there when it has the same id, or the same name and kind (names are
     * unique). A dish line is pointed to the food of [foods] that is the same as its backup food, or
     * to nothing when there is none: the dish then shows that its total is not known.
     */
    fun merge(backup: FoodBackup, foods: List<FD>, book: FoodBook): BackupMerge {
        val foodIdByKey = foods.associate { keyOf(it.name, it.portion, it.unit, it.carbs) to it.id }
        val foodIds = backup.foods.associate { it.id to foodIdByKey[keyOf(it.name, it.portion, it.unit, it.carbs)] }

        val bookIds = book.recipes.map { it.id }.toSet()
        val bookByName = book.recipes.associateBy { it.name.trim().lowercase() to it.kind }
        // A backup dish already there under another id: lines that use it point to the one there.
        val recipeIds = backup.recipes.associate { recipe -> recipe.id to (bookByName[recipe.name.trim().lowercase() to recipe.kind]?.id ?: recipe.id) }

        val recipes = backup.recipes
            .filter { it.id !in bookIds && (it.name.trim().lowercase() to it.kind) !in bookByName }
            .map { recipe ->
                recipe.copy(items = recipe.items.map { item ->
                    item.copy(foodId = item.foodId?.let { foodIds[it] }, recipeId = item.recipeId?.let { recipeIds[it] ?: it })
                })
            }
        val eatenIds = book.eaten.map { it.id }.toSet()
        val meals = backup.eaten.filter { it.id !in eatenIds }
        return BackupMerge(recipes, meals)
    }

    private fun keyOf(name: String, portion: Double, unit: String, carbs: Int) = listOf(name.trim().lowercase(), portion.toString(), unit.trim().lowercase(), carbs.toString())
}
