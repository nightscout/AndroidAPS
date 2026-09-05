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
    private val directory: File = defaultExportDirectory()
) : PrefsFileAccess {

    /**
     * Fixed to [Locale.ROOT], for the reason the iOS formatter is pinned to `en_US_POSIX`: a file
     * name is sorted, matched and compared against files written by a phone, not read in the user's
     * language. A locale-sensitive formatter would give a different calendar or different digits.
     */
    private val nameFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'_'HHmmss", Locale.ROOT)

    override fun newExportName(flavour: String): String = "${LocalDateTime.now().format(nameFormatter)}_$flavour.json"

    override fun write(name: String, contents: String) {
        if (!directory.exists() && !directory.mkdirs()) error("could not create ${directory.path}")
        File(directory, name).writeText(contents)
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

/** `~/AAPS/exports`, the same shape on every desktop AAPS runs on. */
private fun defaultExportDirectory(): File =
    File(System.getProperty("user.home") ?: ".", "AAPS" + File.separator + "exports")
