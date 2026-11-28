package app.aaps.ui.compose.foodManagement.book

import android.content.Context
import androidx.core.net.toUri
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.maintenance.FileListProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

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

    override fun save(name: String, mimeType: String, text: String): FileResult =
        write(name, mimeType) { out -> out.write(text.toByteArray(Charsets.UTF_8)) }

    override fun saveArchive(name: String, mimeType: String, entries: Map<String, ByteArray>): FileResult =
        write(name, mimeType) { out ->
            ZipOutputStream(out).use { zip ->
                entries.forEach { (entry, bytes) ->
                    zip.putNextEntry(ZipEntry(entry))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }

    /** Creates a new file in the exports folder, with the date and time first, and writes it. */
    private fun write(name: String, mimeType: String, content: (OutputStream) -> Unit): FileResult = try {
        val dir = fileListProvider.ensureExportDirExists()
        if (dir == null) FileResult.NO_FOLDER
        else {
            val time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'_'HHmmss"))
            val file = dir.createFile(mimeType, "${time}_$name")
            val written = file?.let { context.contentResolver.openOutputStream(it.uri)?.use(content) }
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

    /**
     * Reads every entry in memory. A food backup holds small photos only, so a file that grows past
     * [MAX_ARCHIVE_BYTES] once unzipped is refused: it is not a backup of this screen.
     */
    override fun readArchive(file: ExportFile): Map<String, ByteArray>? = try {
        context.contentResolver.openInputStream(file.ref.toUri())?.use { input ->
            ZipInputStream(input).use { zip ->
                val entries = mutableMapOf<String, ByteArray>()
                var total = 0L
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val bytes = ByteArrayOutputStream()
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_ARCHIVE_BYTES) error("Food backup too large")
                        bytes.write(buffer, 0, read)
                    }
                    entries[entry.name] = bytes.toByteArray()
                }
                entries
            }
        }
    } catch (e: Exception) {
        aapsLogger.error(LTag.UI, "Food backup archive cannot be read", e)
        null
    }

    override fun read(file: ExportFile): String? = try {
        context.contentResolver.openInputStream(file.ref.toUri())?.use { it.readBytes().toString(Charsets.UTF_8) }
    } catch (e: Exception) {
        aapsLogger.error(LTag.UI, "Food backup cannot be read", e)
        null
    }

    private companion object {

        const val BUFFER_SIZE = 64 * 1024
        const val MAX_ARCHIVE_BYTES = 200L * 1024 * 1024
    }
}
