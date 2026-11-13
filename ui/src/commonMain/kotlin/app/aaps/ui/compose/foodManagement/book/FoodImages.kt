package app.aaps.ui.compose.foodManagement.book

import app.aaps.core.interfaces.concurrent.aapsIoDispatcher
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.ui.compose.foodManagement.openfoodfacts.CurrentImage
import app.aaps.ui.compose.foodManagement.openfoodfacts.OffImage
import app.aaps.ui.compose.foodManagement.openfoodfacts.OffProduct
import app.aaps.ui.compose.foodManagement.openfoodfacts.OpenFoodFactsRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The food photos: the product photos of the foods imported from Open Food Facts, and the photos
 * taken by the user ([FoodPhoto]). A photo of the user is shown instead of the OFF photo; the OFF
 * file is kept, so removing the photo of the user shows it again without a download.
 *
 * Nothing is added to the database: the food book keeps which product a food came from
 * ([OffLink]), and [FoodImageStore] keeps the photo file. When the file is missing (new phone,
 * restored backup, cleared data), it is downloaded again the next time the food is opened, but only
 * when the link says OFF has a photo: a product without one is never asked again.
 *
 * PROOF OF CONCEPT.
 */
@SingleIn(AppScope::class)
@Inject
class FoodImages(
    private val store: FoodImageStore,
    private val openFoodFacts: OpenFoodFactsRepository,
    private val foodBook: FoodBookRepository,
    private val aapsLogger: AAPSLogger,
) {

    /** One download at a time: opening the same food twice must not download twice. */
    private val mutex = Mutex()

    val available: Boolean get() = store.available

    /** Downloads the photo of a search result, to show it in the list. Not saved. */
    suspend fun download(product: OffProduct): ByteArray? {
        if (!store.available) return null
        val url = product.imageUrl ?: return null
        return (openFoodFacts.image(url) as? OffImage.Found)?.bytes
    }

    /**
     * Remembers that [foodId] was saved from [product], and saves its photo. [bytes] is the photo
     * already downloaded for the list, if any, so it is not downloaded twice.
     */
    suspend fun link(foodId: Long, product: OffProduct, bytes: ByteArray?) {
        foodBook.saveOffLink(OffLink(foodId = foodId, barcode = product.barcode, hasImage = product.imageUrl != null, imageUrl = product.imageUrl))
        if (!store.available || product.imageUrl == null) return
        val photo = bytes ?: download(product) ?: return
        write(product.barcode, photo)
    }

    /**
     * The photo to show for each food of [foodIds] that has one: the photo of the user, or else the
     * saved OFF photo. Nothing is downloaded.
     */
    suspend fun saved(foodIds: Collection<Long>): Map<Long, ByteArray> {
        if (!store.available) return emptyMap()
        foodBook.load()
        val book = foodBook.book.value
        val links = book.offLinks.filter { it.foodId in foodIds && it.hasImage }
        val photos = book.photos.filter { it.foodId in foodIds }
        return withContext(aapsIoDispatcher) {
            val byBarcode = links.map { it.barcode }.distinct().associateWith { read(it) }
            val off = links.mapNotNull { link -> byBarcode[link.barcode]?.let { link.foodId to it } }.toMap()
            off + photos.mapNotNull { photo -> read(photo.key)?.let { photo.foodId to it } }
        }
    }

    /** The photo the user took for [foodId], or null when there is none. */
    suspend fun userPhoto(foodId: Long): ByteArray? {
        if (!store.available) return null
        foodBook.load()
        val photo = foodBook.book.value.photos.firstOrNull { it.foodId == foodId } ?: return null
        return withContext(aapsIoDispatcher) { read(photo.key) }
    }

    /**
     * Sets [bytes] as the photo of the user for [foodId], or removes it when [bytes] is null. The file
     * of the old photo is deleted. The OFF photo is never touched.
     */
    suspend fun setUserPhoto(foodId: Long, bytes: ByteArray?) {
        if (!store.available) return
        foodBook.load()
        val old = foodBook.book.value.photos.firstOrNull { it.foodId == foodId }
        if (bytes != null) {
            val key = newUserKey()
            if (!write(key, bytes)) return
            foodBook.savePhoto(FoodPhoto(foodId, key))
        } else foodBook.removePhoto(foodId)
        old?.let { withContext(aapsIoDispatcher) { runCatching { store.delete(it.key) } } }
    }

    /**
     * The photo files of a backup, by key: the OFF photos and the photos of the user of the foods of
     * [foodIds], and the photos of the dishes, meals and history.
     */
    suspend fun filesFor(foodIds: Collection<Long>): Map<String, ByteArray> {
        if (!store.available) return emptyMap()
        foodBook.load()
        val book = foodBook.book.value
        val keys = book.offLinks.filter { it.foodId in foodIds && it.hasImage }.map { it.barcode } +
            book.photos.filter { it.foodId in foodIds }.map { it.key } +
            recipeKeysOf(book)
        return withContext(aapsIoDispatcher) { keys.distinct().mapNotNull { key -> read(key)?.let { key to it } }.toMap() }
    }

    /** The photos of [keys] found on the phone, by key. */
    suspend fun read(keys: Collection<String>): Map<String, ByteArray> {
        if (!store.available || keys.isEmpty()) return emptyMap()
        return withContext(aapsIoDispatcher) { keys.distinct().mapNotNull { key -> read(key)?.let { key to it } }.toMap() }
    }

    /**
     * Saves [bytes] as a new photo file for a dish or a meal and returns its key, or null when it was
     * not saved. The old photo is not deleted here: the history may still use it, see [deleteUnused].
     */
    suspend fun savePhoto(bytes: ByteArray): String? {
        if (!store.available) return null
        val key = newUserKey()
        return if (write(key, bytes)) key else null
    }

    /**
     * Deletes the photo files of [keys] that no dish, meal or meal of the history uses any more. A meal
     * of the history keeps the photo of its dish as it was when it was eaten.
     */
    suspend fun deleteUnused(keys: Collection<String>) {
        if (!store.available || keys.isEmpty()) return
        foodBook.load()
        val book = foodBook.book.value
        val used = recipeKeysOf(book).toSet() + book.photos.map { it.key }
        val unused = keys.filter { it.startsWith(USER_KEY_PREFIX) && it !in used }
        withContext(aapsIoDispatcher) { unused.forEach { key -> runCatching { store.delete(key) } } }
    }

    private fun recipeKeysOf(book: FoodBook): List<String> =
        book.recipes.mapNotNull { it.photo } + book.eaten.flatMap { meal -> listOfNotNull(meal.photo) + meal.items.mapNotNull { it.photo } }

    /**
     * Saves a photo file of a backup. A file already on the phone is never replaced. Returns false
     * when the file was not written (already there, a key that is not valid, or an error).
     */
    suspend fun restoreFile(key: String, bytes: ByteArray): Boolean {
        if (!store.available || !FoodImageStore.isValidKey(key)) return false
        if (withContext(aapsIoDispatcher) { read(key) } != null) return false
        return write(key, bytes)
    }

    /**
     * The OFF photo of [foodId]. When OFF has one but the file is missing, it is downloaded again. When
     * the address has changed, the product is read again to find the new one. Null when the food was
     * not imported, OFF has no photo, or OFF cannot be reached now (it is tried again next time).
     */
    suspend fun forFood(foodId: Long): ByteArray? = mutex.withLock {
        if (!store.available) return@withLock null
        foodBook.load()
        val link = foodBook.book.value.offLinks.firstOrNull { it.foodId == foodId } ?: return@withLock null
        withContext(aapsIoDispatcher) { read(link.barcode) }?.let { return@withLock it }
        if (!link.hasImage) return@withLock null

        val atOldAddress = link.imageUrl?.let { openFoodFacts.image(it) }
        if (atOldAddress is OffImage.Found) return@withLock atOldAddress.bytes.also { write(link.barcode, it) }
        // No answer: nothing is known, so nothing is changed. Tried again next time.
        if (atOldAddress == OffImage.Failed) return@withLock null

        when (val current = openFoodFacts.currentImageUrl(link.barcode)) {
            is CurrentImage.At   -> {
                val photo = (openFoodFacts.image(current.url) as? OffImage.Found)?.bytes
                foodBook.saveOffLink(link.copy(imageUrl = current.url))
                photo?.also { write(link.barcode, it) }
            }

            CurrentImage.None    -> {
                // OFF has no photo any more: never asked again for this food.
                foodBook.saveOffLink(link.copy(hasImage = false, imageUrl = null))
                null
            }

            CurrentImage.Unknown -> null
        }
    }

    private fun read(key: String): ByteArray? = runCatching { store.read(key) }.getOrNull()

    private suspend fun write(key: String, bytes: ByteArray): Boolean = withContext(aapsIoDispatcher) {
        runCatching { store.write(key, bytes) }
            .onFailure { e -> aapsLogger.error(LTag.UI, "Food POC: cannot save a food photo", e) }
            .isSuccess
    }

    /** A new key for each photo, so an old backup never brings back a photo under the name of a newer one. */
    @OptIn(ExperimentalUuidApi::class)
    private fun newUserKey(): String = USER_KEY_PREFIX + Uuid.random().toHexString().take(16)

    companion object {

        const val USER_KEY_PREFIX = "user_"
    }
}
