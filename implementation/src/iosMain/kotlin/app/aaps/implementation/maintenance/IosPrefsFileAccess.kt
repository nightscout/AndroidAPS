package app.aaps.implementation.maintenance

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSLocale
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

/**
 * [PrefsFileAccess] over the app's Documents directory, which the Files app shows to the user.
 *
 * On Android the user picks a folder through the storage access framework and AAPS holds a permission
 * to it; iOS has no such picker for a folder the app then keeps writing to, and does not need one -
 * Documents is exposed by `UIFileSharingEnabled`, so the user can reach an export, copy it off the
 * phone, or drop one in to import.
 *
 * The database used to live in this directory and no longer does, which is what makes exposing it
 * safe: a user tidying up their files can no longer delete the treatment history by accident.
 */
@OptIn(ExperimentalForeignApi::class)
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosPrefsFileAccess @Inject constructor(
    /**
     * Where exports are kept. Defaults to the app's Documents directory, which is the one the Files
     * app shows; a test points it somewhere of its own so it neither reads the user's exports nor
     * leaves files behind for the next test to list.
     */
    private val directory: String? = defaultDocumentsDirectory()
) : PrefsFileAccess {

    /**
     * Pinned to `en_US_POSIX` and the Gregorian calendar on purpose.
     *
     * An `NSDateFormatter` otherwise follows the user's own calendar and digits, so the same pattern
     * gives a Buddhist year on a Thai phone and Eastern Arabic numerals on an Arabic one. A file name
     * is not something the user reads in their language - it is sorted, matched and compared with
     * files written by Android, which uses these digits.
     */
    private val nameFormatter = NSDateFormatter().apply {
        setLocale(NSLocale("en_US_POSIX"))
        setDateFormat("yyyy-MM-dd'_'HHmmss")
    }

    override fun newExportName(flavour: String): String = "${nameFormatter.stringFromDate(NSDate())}_$flavour.json"

    override fun newCsvName(): String = "${nameFormatter.stringFromDate(NSDate())}_UserEntry.csv"

    override fun write(name: String, contents: String) {
        val path = documentsPath(name) ?: error("no Documents directory")
        val ok = NSString.create(string = contents).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
        if (!ok) error("could not write $name")
    }

    override fun list(): List<Pair<String, String>> {
        val manager = NSFileManager.defaultManager
        val directory = documentsDirectory() ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        val names = manager.contentsOfDirectoryAtPath(directory, null) as? List<String> ?: return emptyList()
        return names.filter { it.endsWith(".json") }.mapNotNull { name ->
            NSString.stringWithContentsOfFile(directory + "/" + name, encoding = NSUTF8StringEncoding, error = null)?.let { name to it }
        }
    }

    private fun documentsDirectory(): String? = directory

    private fun documentsPath(name: String): String? = documentsDirectory()?.let { "$it/$name" }
}

/** The app's own Documents directory, or null on the rare build that has none. */
private fun defaultDocumentsDirectory(): String? =
    NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).firstOrNull() as? String
