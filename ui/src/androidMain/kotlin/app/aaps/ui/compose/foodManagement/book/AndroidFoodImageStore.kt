package app.aaps.ui.compose.foodManagement.book

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import java.io.File

/**
 * The product photos, in a folder of the private files of the app. Not in the settings export, not
 * in the logs. Uninstalling the app, or clearing its data, removes them.
 */
@ContributesBinding(AppScope::class, binding = binding<FoodImageStore>())
@Inject
class AndroidFoodImageStore(private val context: Context) : FoodImageStore {

    private val folder: File get() = File(context.filesDir, FOLDER)

    override val available: Boolean = true

    override fun read(key: String): ByteArray? = fileOf(key)?.takeIf { it.exists() }?.readBytes()

    /** Written to a second file first, then renamed, so a crash never leaves half a photo. */
    override fun write(key: String, bytes: ByteArray) {
        val file = fileOf(key) ?: return
        folder.mkdirs()
        val temp = File(folder, "${file.name}.tmp")
        temp.writeBytes(bytes)
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
    }

    override fun delete(key: String) {
        fileOf(key)?.delete()
    }

    /** The existing OFF photos keep their name: "<barcode>.jpg". */
    private fun fileOf(key: String): File? = if (FoodImageStore.isValidKey(key)) File(folder, "$key.jpg") else null

    private companion object {

        const val FOLDER = "food_poc_images"
    }
}

internal actual fun decodeFoodImage(bytes: ByteArray): ImageBitmap? =
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
