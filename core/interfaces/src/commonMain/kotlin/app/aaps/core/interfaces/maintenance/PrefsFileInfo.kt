package app.aaps.core.interfaces.maintenance

/**
 * The two things a maintenance or import screen needs to know about the export store.
 *
 * Split out of `FileListProvider` so those screens can live in shared code. The rest of that
 * interface is unavoidably Android - `DocumentFile` from the Storage Access Framework and
 * `java.io.File` - but nothing on this pair is: a formatted string and a flag. Keeping the screens
 * off the larger interface is what lets them compile for every target.
 */
interface PrefsFileInfo {

    /**
     * Turns the UTC timestamp an export was written at into text for the user, such as how long ago
     * it happened. Platform specific because the wording is localized.
     */
    fun formatExportedAgo(utcTime: String): String

    /**
     * Whether the user has picked an export directory and it is still readable.
     *
     * False is a normal state, not an error: it is what the screen shows a "choose a folder" prompt
     * for. On Android the permission can also be revoked after being granted.
     */
    fun isDirectoryAccessGranted(): Boolean
}
