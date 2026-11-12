package app.aaps.ui.compose.foodManagement.book

/**
 * The files of the food screen in the "exports" folder of the AAPS directory: the CSV files for a
 * spreadsheet, and the backup of the food POC.
 */
interface FoodExportFiles {

    /** False where the platform has no exports folder: the menu does not offer these actions. */
    val available: Boolean

    /**
     * Saves [text] as a new file. [name] is the end of the file name: the date and time come first,
     * so a new file never replaces an older one.
     */
    fun save(name: String, mimeType: String, text: String): FileResult

    /** The files of the exports folder whose name ends with [suffix], newest first. */
    fun list(suffix: String): List<ExportFile>

    /** The text of [file], or null when it cannot be read. */
    fun read(file: ExportFile): String?

    /**
     * Saves [entries] (entry name to content) as one new zip file, named like [save] does. The file
     * keeps the extension of [name], whatever the content is.
     */
    fun saveArchive(name: String, mimeType: String, entries: Map<String, ByteArray>): FileResult

    /** The entries of the zip [file], or null when it cannot be read or is too large. */
    fun readArchive(file: ExportFile): Map<String, ByteArray>?
}

/**
 * A file of the exports folder.
 *
 * @param name shown to the user. It starts with the date and time of the export.
 * @param ref where the platform finds the file again (a content URI on Android)
 */
data class ExportFile(val name: String, val ref: String)

enum class FileResult {

    SAVED,

    /** The user has not chosen the AAPS directory yet (Maintenance). */
    NO_FOLDER,

    FAILED
}

/** For a platform without an exports folder: the actions are not offered there. */
open class NoFoodExportFiles : FoodExportFiles {

    override val available: Boolean = false

    override fun save(name: String, mimeType: String, text: String): FileResult = FileResult.FAILED

    override fun list(suffix: String): List<ExportFile> = emptyList()

    override fun read(file: ExportFile): String? = null

    override fun saveArchive(name: String, mimeType: String, entries: Map<String, ByteArray>): FileResult = FileResult.FAILED

    override fun readArchive(file: ExportFile): Map<String, ByteArray>? = null
}
