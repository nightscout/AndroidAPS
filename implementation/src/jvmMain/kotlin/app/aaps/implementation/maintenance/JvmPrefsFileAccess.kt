package app.aaps.implementation.maintenance

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * [PrefsFileAccess] over a folder on the machine.
 *
 * A desktop has no storage access framework to ask and no Files app to expose a container through,
 * so the folder is simply a known path under the user's home. It is created on first write rather
 * than at start up, so a desktop that never exports leaves nothing behind.
 *
 * `AAPS` in the home directory rather than a hidden dot-folder: an export is something the user is
 * meant to find, copy to another machine, and hand back to a phone.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class JvmPrefsFileAccess @Inject constructor(
    /** Overridden in a test so it neither reads the user's exports nor leaves files behind. */
    private val directory: File = DesktopFolders.preferences,
    /** The CSV goes elsewhere, for the reason given on [write]. */
    private val csvDirectory: File = DesktopFolders.exports
) : PrefsFileAccess {

    /**
     * Fixed to [Locale.ROOT], for the reason the iOS formatter is pinned to `en_US_POSIX`: a file
     * name is sorted, matched and compared against files written by a phone, not read in the user's
     * language. A locale-sensitive formatter would give a different calendar or different digits.
     */
    private val nameFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'_'HHmmss", Locale.ROOT)

    override fun newExportName(flavour: String): String = "${LocalDateTime.now().format(nameFormatter)}_$flavour.json"

    override fun newCsvName(): String = "${LocalDateTime.now().format(nameFormatter)}_UserEntry.csv"

    /**
     * Writes into the folder Android would write this kind of file into.
     *
     * A phone splits them - `newPreferenceFile` creates in `AAPS/preferences` and `newExportCsvFile`
     * in `AAPS/exports` - so a desktop that put both in one folder would not be a mirror of a phone,
     * and a user copying a backup between the two would be looking in the wrong place. The name is
     * what says which: only the CSV is not a settings export, and it is the only `.csv` written.
     */
    override fun write(name: String, contents: String) {
        val target = if (name.endsWith(".csv")) csvDirectory else directory
        if (!target.exists() && !target.mkdirs()) error("could not create ${target.path}")
        File(target, name).writeText(contents)
    }

    override fun list(): List<Pair<String, String>> =
        directory.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".json") }
            // A directory the user can open is a directory the user can put anything in, so a file
            // that cannot be read as text is skipped rather than failing the whole listing.
            ?.mapNotNull { file -> runCatching { file.name to file.readText() }.getOrNull() }
            ?: emptyList()

    /** Where the folder is, so the settings screen can tell the user where to look. */
    val path: String get() = directory.path
}

/**
 * The `AAPS` folder in the user's home, laid out the way Android lays out its own.
 *
 * Two folders, and the split is not arbitrary:
 *
 * - **`~/AAPS`** is the user's. Exports and the marker files someone creates by hand live here, and
 *   it is the counterpart of the `Documents/AAPS` folder a phone writes to. `FileListProviderImpl`
 *   names the same three subfolders.
 * - **`~/.aaps`** is the app's, and is the counterpart of Android's private data directory. The
 *   database, the keys, the log and the preference store are there and no user is meant to go
 *   looking.
 *
 * Defined once, and in `commonMain`'s module rather than in the desktop shell, because both sides of
 * every one of these paths live in different modules and have already drifted apart once: settings
 * were written to `~/AAPS/exports` while the screen listed `~/.aaps/exports`, a folder that did not
 * exist. The export reported success and was then invisible to the app that had just written it.
 */
object DesktopFolders {

    /** The user-visible root, the counterpart of `Documents/AAPS` on a phone. */
    val root: File get() = File(System.getProperty("user.home") ?: ".", "AAPS")

    /** Settings exports, matching Android's `newPreferenceFile`, which writes to `AAPS/preferences`. */
    val preferences: File get() = File(root, "preferences")

    /** Everything else that is exported - the user-entry CSV, and watchfaces on a phone. */
    val exports: File get() = File(root, "exports")

    /**
     * Marker files that switch options on, read by `DesktopClientConfig.isEnabled`.
     *
     * The same folder Android reads through `ensureExtraDirExists`, so `engineering_mode` is created
     * in the same relative place on both. It is the user who creates these by hand, which is the
     * whole reason they belong under the visible root and not in the data directory.
     */
    val extra: File get() = File(root, "extra")
}
