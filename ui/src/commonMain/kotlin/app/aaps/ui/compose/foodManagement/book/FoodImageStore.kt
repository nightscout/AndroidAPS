package app.aaps.ui.compose.foodManagement.book

import androidx.compose.ui.graphics.ImageBitmap

/**
 * The food photos, one small file per key, outside the database, so the database does not grow.
 *
 * - A photo from Open Food Facts has the barcode as key. Two foods made from the same product (per
 *   serving and per 100 g) share one file.
 * - A photo taken by the user has a key of its own ([FoodPhoto.key]).
 *
 * A key has only letters, digits and "_" ([isValidKey]): it can never point outside the folder.
 *
 * PROOF OF CONCEPT. A missing OFF file is never an error: the photo is downloaded again.
 */
interface FoodImageStore {

    /** False where photos are not kept: no photo is downloaded or shown there. */
    val available: Boolean

    /** The photo of [key], or null when there is none. */
    fun read(key: String): ByteArray?

    fun write(key: String, bytes: ByteArray)

    fun delete(key: String)

    companion object {

        fun isValidKey(key: String): Boolean = key.isNotEmpty() && key.length <= 64 && key.all { (it.isLetterOrDigit() && it.code < 128) || it == '_' }
    }
}

/** For a platform that does not keep photos: the food screen then works without them. */
open class NoFoodImageStore : FoodImageStore {

    override val available: Boolean = false

    override fun read(key: String): ByteArray? = null

    override fun write(key: String, bytes: ByteArray) = Unit

    override fun delete(key: String) = Unit
}

/** The photo as an image, or null when the bytes are not a picture this platform can read. */
internal expect fun decodeFoodImage(bytes: ByteArray): ImageBitmap?
