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
 *
 * ## The layout is Android's
 *
 * `Documents` is the counterpart of the `AAPS` folder a phone writes to, and is laid out the same
 * way: settings exports in `preferences`, the user-entry CSV in `exports`, and the option marker
 * files in `extra`. A user copying a backup between a phone and an iPhone finds it in the same
 * relative place, and a folder full of loose files is not what either platform looks like.
 *
 * This used to write everything into `Documents` itself. Files written by that build are not listed
 * by this one - they are one folder up from where the import screen now looks. That is a deliberate
 * clean break rather than an accident: see [list].
 */
@OptIn(ExperimentalForeignApi::class)
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosPrefsFileAccess @Inject constructor(
    /**
     * Settings exports, the counterpart of Android's `AAPS/preferences`.
     *
     * A test points it somewhere of its own so it neither reads the user's exports nor leaves files
     * behind for the next test to list.
     */
    private val directory: String? = defaultDocumentsSubdirectory("preferences"),
    /** The CSV goes elsewhere, for the reason given on [write]. */
    private val csvDirectory: String? = defaultDocumentsSubdirectory("exports")
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

    /**
     * Writes into the folder Android would write this kind of file into.
     *
     * A phone splits them - `newPreferenceFile` creates in `AAPS/preferences` and `newExportCsvFile`
     * in `AAPS/exports` - so putting both in one folder would not be a mirror of a phone, and a user
     * copying a backup between the two would be looking in the wrong place. The name is what says
     * which: only the CSV is not a settings export, and it is the only `.csv` written.
     */
    override fun write(name: String, contents: String) {
        val target = (if (name.endsWith(".csv")) csvDirectory else directory) ?: error("no Documents directory")
        NSFileManager.defaultManager.createDirectoryAtPath(target, withIntermediateDirectories = true, attributes = null, error = null)
        val ok = NSString.create(string = contents).writeToFile("$target/$name", atomically = true, encoding = NSUTF8StringEncoding, error = null)
        if (!ok) error("could not write $name")
    }

    /**
     * The settings exports, from `preferences` only.
     *
     * Deliberately not also `Documents` itself, where an older build wrote them. Listing both would
     * mean a file the app can no longer produce keeps appearing next to ones it can, with nothing to
     * tell the two apart - and the fallback would have to be carried until someone could prove no
     * phone still had one, which is not a thing anyone can prove.
     */
    override fun list(): List<Pair<String, String>> {
        val manager = NSFileManager.defaultManager
        val directory = this.directory ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        val names = manager.contentsOfDirectoryAtPath(directory, null) as? List<String> ?: return emptyList()
        return names.filter { it.endsWith(".json") }.mapNotNull { name ->
            NSString.stringWithContentsOfFile(directory + "/" + name, encoding = NSUTF8StringEncoding, error = null)?.let { name to it }
        }
    }

    /** Where settings exports are, so a screen can tell the user where to look. */
    val path: String? get() = directory
}

/**
 * A named folder inside the app's own Documents directory, or null on a build that has none.
 *
 * Not created here. A build that never exports should leave nothing behind, and the folder is made
 * on the first write instead - unlike `extra`, which is created at start up because a user has to be
 * able to find it before anything has written to it.
 */
private fun defaultDocumentsSubdirectory(name: String): String? =
    (NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).firstOrNull() as? String)?.let { "$it/$name" }
