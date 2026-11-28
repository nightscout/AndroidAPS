package app.aaps.ui.compose.foodManagement.book

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import java.io.File

/**
 * The food POC file, in the private files of the app. It is not part of the settings export or the
 * logs. Uninstalling the app, or clearing its data, removes it.
 */
@ContributesBinding(AppScope::class)
@Inject
class AndroidFoodBookStore(private val context: Context) : FoodBookStore {

    private val file: File get() = File(context.filesDir, FILE_NAME)

    override fun read(): String? = file.takeIf { it.exists() }?.readText()

    /** Written to a second file first, then renamed, so a crash never leaves half a file. */
    override fun write(text: String) {
        val temp = File(context.filesDir, "$FILE_NAME.tmp")
        temp.writeText(text)
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
    }

    private companion object {

        const val FILE_NAME = "food_poc_book.json"
    }
}
