package app.aaps.ui.compose.foodManagement.book

import android.content.Context
import androidx.core.net.toUri
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.maintenance.FileListProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * The "exports" folder of the AAPS directory, next to the user entries export. The user chose that
 * directory in Maintenance; without it nothing is saved.
 */
@ContributesBinding(AppScope::class)
@Inject
class AndroidFoodExportFiles(
    private val context: Context,
    private val fileListProvider: FileListProvider,
    private val aapsLogger: AAPSLogger,
) : FoodExportFiles {

    override val available: Boolean = true

    override fun save(name: String, mimeType: String, text: String): FileResult = try {
        val dir = fileListProvider.ensureExportDirExists()
        if (dir == null) FileResult.NO_FOLDER
        else {
            val time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'_'HHmmss"))
            val file = dir.createFile(mimeType, "${time}_$name")
            val written = file?.let { context.contentResolver.openOutputStream(it.uri)?.use { out -> out.write(text.toByteArray(Charsets.UTF_8)) } }
            if (written == null) FileResult.FAILED else FileResult.SAVED
        }
    } catch (e: Exception) {
        aapsLogger.error(LTag.UI, "Food export failed", e)
        FileResult.FAILED
    }

    override fun list(suffix: String): List<ExportFile> = try {
        fileListProvider.ensureExportDirExists()?.listFiles().orEmpty()
            .mapNotNull { file -> file.name?.takeIf { it.endsWith(suffix) }?.let { ExportFile(it, file.uri.toString()) } }
            .sortedByDescending { it.name }
    } catch (e: Exception) {
        aapsLogger.error(LTag.UI, "Food export folder cannot be read", e)
        emptyList()
    }

    override fun read(file: ExportFile): String? = try {
        context.contentResolver.openInputStream(file.ref.toUri())?.use { it.readBytes().toString(Charsets.UTF_8) }
    } catch (e: Exception) {
        aapsLogger.error(LTag.UI, "Food backup cannot be read", e)
        null
    }
}
