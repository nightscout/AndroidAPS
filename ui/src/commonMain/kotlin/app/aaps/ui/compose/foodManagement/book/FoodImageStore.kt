package app.aaps.ui.compose.foodManagement.book

import androidx.compose.ui.graphics.ImageBitmap

/**
 * The product photos of the foods imported from Open Food Facts: one small file per barcode,
 * outside the database, so the database does not grow. Two foods made from the same product (per
 * serving and per 100 g) share one file.
 *
 * PROOF OF CONCEPT. A missing file is never an error: the photo is downloaded again.
 */
interface FoodImageStore {

    /** False where photos are not kept: no photo is downloaded or shown there. */
    val available: Boolean

    /** The photo of [barcode], or null when there is none. */
    fun read(barcode: String): ByteArray?

    fun write(barcode: String, bytes: ByteArray)
}

/** For a platform that does not keep photos: the food screen then works without them. */
open class NoFoodImageStore : FoodImageStore {

    override val available: Boolean = false

    override fun read(barcode: String): ByteArray? = null

    override fun write(barcode: String, bytes: ByteArray) = Unit
}

/** The photo as an image, or null when the bytes are not a picture this platform can read. */
internal expect fun decodeFoodImage(bytes: ByteArray): ImageBitmap?
