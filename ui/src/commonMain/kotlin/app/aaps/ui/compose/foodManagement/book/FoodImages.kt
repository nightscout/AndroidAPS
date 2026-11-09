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

/**
 * The product photos of the foods imported from Open Food Facts.
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

    /** The saved photos of the foods in [foodIds] that have one. Nothing is downloaded. */
    suspend fun saved(foodIds: Collection<Long>): Map<Long, ByteArray> {
        if (!store.available) return emptyMap()
        foodBook.load()
        val links = foodBook.book.value.offLinks.filter { it.foodId in foodIds && it.hasImage }
        return withContext(aapsIoDispatcher) {
            val byBarcode = links.map { it.barcode }.distinct().associateWith { runCatching { store.read(it) }.getOrNull() }
            links.mapNotNull { link -> byBarcode[link.barcode]?.let { link.foodId to it } }.toMap()
        }
    }

    /**
     * The photo of [foodId]. When OFF has one but the file is missing, it is downloaded again. When
     * the address has changed, the product is read again to find the new one. Null when the food was
     * not imported, OFF has no photo, or OFF cannot be reached now (it is tried again next time).
     */
    suspend fun forFood(foodId: Long): ByteArray? = mutex.withLock {
        if (!store.available) return@withLock null
        foodBook.load()
        val link = foodBook.book.value.offLinks.firstOrNull { it.foodId == foodId } ?: return@withLock null
        withContext(aapsIoDispatcher) { runCatching { store.read(link.barcode) }.getOrNull() }?.let { return@withLock it }
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

    private suspend fun write(barcode: String, bytes: ByteArray) = withContext(aapsIoDispatcher) {
        runCatching { store.write(barcode, bytes) }
            .onFailure { e -> aapsLogger.error(LTag.UI, "Food POC: cannot save a product photo", e) }
    }
}
