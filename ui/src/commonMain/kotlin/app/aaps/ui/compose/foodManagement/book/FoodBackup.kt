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
    /** Added later in format 1: an older file reads as "no imported product". */
    val offLinks: List<OffLink> = emptyList(),
    /** Format 2: the photos taken by the user. Their files are in the archive, see [FoodBackupMath.IMAGE_FOLDER]. */
    val photos: List<FoodPhoto> = emptyList(),
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
data class BackupMerge(val recipes: List<Recipe>, val meals: List<EatenMeal>, val offLinks: List<OffLink> = emptyList(), val photos: List<FoodPhoto> = emptyList())

/**
 * Writes and reads backups, and decides what a restore adds.
 *
 * A restore never deletes or changes anything: it only adds what is missing. Database ids are not
 * the same on another phone or after a new install, so a food is found again by what it is (name,
 * portion, unit and carbs), and the dishes are pointed to the food found.
 */
internal object FoodBackupMath {

    /**
     * 1: one JSON file ([FILE_NAME]), without photos.
     * 2: an archive ([ARCHIVE_NAME]) with the JSON file and the photo files ([IMAGE_FOLDER]).
     */
    const val FORMAT = 2
    const val FILE_NAME = "FoodBackup.json"

    /**
     * A zip file with an extension of its own: Android does not know it, so it does not offer to
     * open it as a zip, and only AAPS lists it for a restore.
     */
    const val ARCHIVE_NAME = "FoodBackup.aapsfood"
    const val ARCHIVE_MIME_TYPE = "application/octet-stream"

    /** The folder of the photo files in the archive: "images/<key>.jpg". */
    const val IMAGE_FOLDER = "images/"
    const val IMAGE_EXTENSION = ".jpg"

    /** The archive entry name of the photo [key]. */
    fun imageEntry(key: String): String = IMAGE_FOLDER + key + IMAGE_EXTENSION

    /** The photo key of an archive entry, or null when the entry is not a photo with a valid key. */
    fun keyOfEntry(entry: String): String? =
        entry.takeIf { it.startsWith(IMAGE_FOLDER) && it.endsWith(IMAGE_EXTENSION) }
            ?.removePrefix(IMAGE_FOLDER)?.removeSuffix(IMAGE_EXTENSION)
            ?.takeIf { FoodImageStore.isValidKey(it) }

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
                offLinks = book.offLinks,
                photos = book.photos,
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
        // The product of a food follows the food found for it. A food that already has one keeps it.
        val linkedIds = book.offLinks.map { it.foodId }.toSet()
        val offLinks = backup.offLinks
            .mapNotNull { link -> foodIds[link.foodId]?.let { link.copy(foodId = it) } }
            .filter { it.foodId !in linkedIds }
            .distinctBy { it.foodId }
        // The same for the photo of the user: a food that already has one keeps it.
        val photoIds = book.photos.map { it.foodId }.toSet()
        val photos = backup.photos
            .mapNotNull { photo -> foodIds[photo.foodId]?.let { photo.copy(foodId = it) } }
            .filter { it.foodId !in photoIds }
            .distinctBy { it.foodId }
        return BackupMerge(recipes, meals, offLinks, photos)
    }

    private fun keyOf(name: String, portion: Double, unit: String, carbs: Int) = listOf(name.trim().lowercase(), portion.toString(), unit.trim().lowercase(), carbs.toString())
}
