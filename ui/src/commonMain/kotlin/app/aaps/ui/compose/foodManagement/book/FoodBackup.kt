package app.aaps.ui.compose.foodManagement.book

import app.aaps.core.data.model.FD
import app.aaps.core.data.model.IDs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

// PROOF OF CONCEPT, personal use. A backup of everything the food POC uses, so the POC can be tried
// on a real phone without losing the data. Format 3 is also the archive the final version reads:
// the data moves from the POC to the final version, and the final version has no code for the POC.
// In AAPS a backup normally goes through Nightscout: see the feature request.

/**
 * A backup as the POC uses it. Files of every format (1 to 3) are read into it.
 *
 * @param format version of the file it was read from. A newer layout is refused, not read wrong.
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

/**
 * A food as saved in the backup. [id] is its database id on the phone that made the backup.
 *
 * @param nightscoutId only in files of format 3, see [ArchiveFood.nightscoutId]
 */
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
    val nightscoutId: String? = null,
)

/**
 * The JSON file of format 3: the archive the final version reads.
 *
 * Each food carries its own barcode and photo, like the columns of the final food table. Format 2
 * kept them in lists next to the foods. A meal of the history keeps the time of its treatment, never
 * the database id of the treatment: that id means nothing on another phone.
 */
@Serializable
data class FoodArchive(
    val format: Int,
    val exportedAt: Long,
    val foods: List<ArchiveFood>,
    val recipes: List<Recipe>,
    val eaten: List<EatenMeal>,
)

/**
 * A food in a file of format 3. The values have decimals, as in the final database.
 *
 * @param id reference of the food inside the file only: dishes and meals point to it. It is the
 *   database id on the phone that wrote the file, and means nothing on another phone.
 * @param nightscoutId id of the food in Nightscout. Nightscout finds a food again only by this id:
 *   without it, a restored food is added a second time when Nightscout sends it again.
 * @param barcode the Open Food Facts product of the food. Its photo is "images/<barcode>.jpg".
 * @param noOffPhoto true when Open Food Facts has no photo for [barcode]: it is never asked again
 * @param notInOff true when Open Food Facts did not know [barcode]: the food was typed by hand with it
 * @param image key of the photo taken by the user. Its file is "images/<key>.jpg".
 * @param fiber not kept by the POC: always empty in a file of the POC
 * @param note not kept by the POC: always empty in a file of the POC
 */
@Serializable
data class ArchiveFood(
    val id: Long,
    val nightscoutId: String? = null,
    val name: String,
    val category: String? = null,
    val subCategory: String? = null,
    val portion: Double,
    val unit: String,
    val carbs: Double,
    val fat: Double? = null,
    val protein: Double? = null,
    val energy: Double? = null,
    val fiber: Double? = null,
    val gi: Int? = null,
    val barcode: String? = null,
    val noOffPhoto: Boolean = false,
    val notInOff: Boolean = false,
    val image: String? = null,
    val note: String? = null,
)

/** Only the format of a file, to know how to read the rest. */
@Serializable
private data class FileFormat(val format: Int)

/** What a restore adds to the food book: only what is not there yet. */
data class BackupMerge(val recipes: List<Recipe>, val meals: List<EatenMeal>, val offLinks: List<OffLink> = emptyList(), val photos: List<FoodPhoto> = emptyList())

/**
 * Writes and reads backups, and decides what a restore adds.
 *
 * A restore never deletes or changes anything: it only adds what is missing. Database ids are not
 * the same on another phone or after a new install, so a food is found again by its Nightscout id,
 * or else by what it is (name, portion, unit and carbs), and the dishes are pointed to the food found.
 */
internal object FoodBackupMath {

    /**
     * 1: one JSON file ([FILE_NAME]), without photos.
     * 2: an archive ([ARCHIVE_NAME]) with the JSON file and the photo files ([IMAGE_FOLDER]).
     * 3: the same archive. The JSON file is a [FoodArchive]: each food carries its barcode and its
     *    photo. This is the layout the final version reads.
     */
    const val FORMAT = 3
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

    /** The JSON file of a new archive, always in the last format. */
    fun encode(foods: List<FD>, book: FoodBook, now: Long): String {
        val links = book.offLinks.associateBy { it.foodId }
        val photos = book.photos.associateBy { it.foodId }
        return json.encodeToString(
            FoodArchive.serializer(),
            FoodArchive(
                format = FORMAT,
                exportedAt = now,
                foods = foods.map { food ->
                    val link = links[food.id]
                    ArchiveFood(
                        id = food.id,
                        nightscoutId = food.ids.nightscoutId,
                        name = food.name,
                        category = food.category,
                        subCategory = food.subCategory,
                        portion = food.portion,
                        unit = food.unit,
                        carbs = food.carbs.toDouble(),
                        fat = food.fat?.toDouble(),
                        protein = food.protein?.toDouble(),
                        energy = food.energy?.toDouble(),
                        gi = food.gi,
                        barcode = link?.barcode,
                        noOffPhoto = link?.hasImage == false,
                        notInOff = link?.inOff == false,
                        image = photos[food.id]?.key,
                    )
                },
                recipes = book.recipes,
                // A meal still waiting for its treatment was perhaps never entered: it is left out.
                // The treatment is found again by its time on the other phone.
                eaten = book.eaten.filter { it.validatedAt != null }.map { it.copy(treatmentId = null) },
            )
        )
    }

    /** The backup in [text], or null when it is not a backup this version can read. */
    fun decode(text: String): FoodBackup? = runCatching {
        when (json.decodeFromString(FileFormat.serializer(), text).format) {
            1, 2 -> json.decodeFromString(FoodBackup.serializer(), text)
            FORMAT -> fromArchive(json.decodeFromString(FoodArchive.serializer(), text))
            else -> null
        }
    }.getOrNull()

    /**
     * A file of format 3 as the POC uses it. The POC database keeps whole numbers, so the values are
     * rounded. The POC has no place for the fiber and the note: they are not read.
     */
    private fun fromArchive(archive: FoodArchive): FoodBackup = FoodBackup(
        format = archive.format,
        exportedAt = archive.exportedAt,
        foods = archive.foods.map {
            BackupFood(
                id = it.id,
                name = it.name,
                category = it.category,
                subCategory = it.subCategory,
                portion = it.portion,
                unit = it.unit,
                carbs = it.carbs.roundToInt(),
                fat = it.fat?.roundToInt(),
                protein = it.protein?.roundToInt(),
                energy = it.energy?.roundToInt(),
                gi = it.gi,
                nightscoutId = it.nightscoutId,
            )
        },
        recipes = archive.recipes,
        eaten = archive.eaten,
        // The address of the photo is not in the file: a missing photo reads the product again.
        offLinks = archive.foods.mapNotNull { food -> food.barcode?.let { OffLink(foodId = food.id, barcode = it, hasImage = !food.noOffPhoto, inOff = !food.notInOff) } },
        photos = archive.foods.mapNotNull { food -> food.image?.let { FoodPhoto(foodId = food.id, key = it) } },
    )

    /** The foods of [backup] not found in [existing], ready to be saved (new database rows). */
    fun missingFoods(backup: FoodBackup, existing: List<FD>): List<FD> =
        backup.foods
            .filter { find(it, existing) == null }
            .distinctBy { it.nightscoutId ?: keyOf(it) }
            .map {
                FD(
                    ids = IDs(nightscoutId = it.nightscoutId),
                    name = it.name, category = it.category, subCategory = it.subCategory, portion = it.portion, unit = it.unit,
                    carbs = it.carbs, fat = it.fat, protein = it.protein, energy = it.energy, gi = it.gi
                )
            }

    /**
     * The dishes, standard meals and eaten meals of [backup] that [book] does not have yet.
     *
     * A dish or meal is already there when it has the same id, or the same name and kind (names are
     * unique). A dish line is pointed to the food of [foods] that is the same as its backup food, or
     * to nothing when there is none: the dish then shows that its total is not known.
     */
    fun merge(backup: FoodBackup, foods: List<FD>, book: FoodBook): BackupMerge {
        val foodIds = backup.foods.associate { it.id to find(it, foods)?.id }

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

    /**
     * The food of [foods] that is [food]: the one with its Nightscout id, or else the one with the
     * same name, portion, unit and carbs. Nightscout can have renamed a food since the backup.
     */
    private fun find(food: BackupFood, foods: List<FD>): FD? =
        food.nightscoutId?.let { id -> foods.firstOrNull { it.ids.nightscoutId == id } }
            ?: foods.firstOrNull { keyOf(it.name, it.portion, it.unit, it.carbs) == keyOf(food) }

    private fun keyOf(food: BackupFood) = keyOf(food.name, food.portion, food.unit, food.carbs)

    private fun keyOf(name: String, portion: Double, unit: String, carbs: Int) = listOf(name.trim().lowercase(), portion.toString(), unit.trim().lowercase(), carbs.toString())
}
