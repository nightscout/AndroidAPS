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

    override fun read(barcode: String): ByteArray? = fileOf(barcode)?.takeIf { it.exists() }?.readBytes()

    /** Written to a second file first, then renamed, so a crash never leaves half a photo. */
    override fun write(barcode: String, bytes: ByteArray) {
        val file = fileOf(barcode) ?: return
        folder.mkdirs()
        val temp = File(folder, "${file.name}.tmp")
        temp.writeBytes(bytes)
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
    }

    /** Digits only, so a barcode can never point outside the folder. */
    private fun fileOf(barcode: String): File? =
        if (barcode.isNotEmpty() && barcode.all { it in '0'..'9' }) File(folder, "$barcode.jpg") else null

    private companion object {

        const val FOLDER = "food_poc_images"
    }
}

internal actual fun decodeFoodImage(bytes: ByteArray): ImageBitmap? =
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
