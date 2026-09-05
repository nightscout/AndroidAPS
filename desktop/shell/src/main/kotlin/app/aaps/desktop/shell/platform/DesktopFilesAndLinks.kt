package app.aaps.desktop.shell.platform

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.maintenance.PrefsFile
import app.aaps.core.interfaces.maintenance.PrefsFileInfo
import app.aaps.core.interfaces.plugin.PermissionGroup
import app.aaps.core.interfaces.plugin.PluginPermissions
import app.aaps.core.interfaces.ui.UrlOpener
import app.aaps.implementation.maintenance.DesktopFolders
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.awt.Desktop
import java.io.File
import java.net.URI
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The three things a desktop does better than a phone, not worse.
 *
 * Grouped because they are the opposite of the refusals next door: a JVM has a browser launcher, a
 * plain filesystem and no runtime permission model, so all three are answered properly.
 */

/**
 * Opens a link in the user's browser.
 *
 * `Desktop.browse` is the portable way and it works on Windows, macOS and most Linux desktops. Where
 * it is unavailable - a headless session, or a Linux box with no `xdg-open` - the URL is logged so
 * the user can still copy it, rather than nothing happening at all.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class DesktopUrlOpener @Inject constructor(
    private val aapsLogger: AAPSLogger
) : UrlOpener {

    override fun open(url: String) {
        val opened = runCatching {
            if (!Desktop.isDesktopSupported()) return@runCatching false
            val desktop = Desktop.getDesktop()
            if (!desktop.isSupported(Desktop.Action.BROWSE)) return@runCatching false
            desktop.browse(URI(url))
            true
        }.getOrDefault(false)
        if (!opened) aapsLogger.error(LTag.CORE, "Could not open a browser; the link was: $url")
    }
}

/**
 * Nothing to ask for.
 *
 * A JVM has no runtime permission model, so both lists are genuinely empty - this is a true answer
 * rather than a stub's shrug, and the permissions screen correctly shows nothing outstanding.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class DesktopPluginPermissions @Inject constructor() : PluginPermissions {

    override fun collectMissingPermissions(): List<PermissionGroup> = emptyList()

    override fun collectAllPermissions(): List<PermissionGroup> = emptyList()
}

/**
 * Lists exported preference files from the AAPS folder.
 *
 * Android reaches its export directory through the Storage Access Framework, which is why
 * `FileListProvider` is full of `DocumentFile`. A desktop just has a directory, so this reads it
 * directly and needs no granting step - [isDirectoryAccessGranted] is true because there is nothing
 * to grant.
 *
 * The files are only listed here. Reading one back is `ImportExportPrefs`, which desktop now has
 * through the shared `LocalImportExportPrefs`, so a file listed here can also be opened.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class DesktopPrefsFileInfo @Inject constructor(
    private val aapsLogger: AAPSLogger
) : PrefsFileInfo {

    // Asked for rather than spelled out again. This used to be its own ".aaps/exports" literal while
    // JvmPrefsFileAccess wrote to "AAPS/exports", so every export succeeded and none was ever listed.
    private val exportDir = DesktopFolders.preferences

    override fun listPreferenceFiles(): MutableList<PrefsFile> {
        val files = runCatching {
            exportDir.listFiles { f: File -> f.isFile }?.sortedByDescending { it.lastModified() }.orEmpty()
        }.onFailure { aapsLogger.error(LTag.CORE, "Cannot list ${exportDir.path}: ${it.message}") }
            .getOrDefault(emptyList())
        // Content is read because PrefsFile carries it; metadata is left empty because it comes
        // from EncryptedPrefsFormat, which is not ported. So the list shows real file names with no
        // details beside them - blank rather than invented.
        return files.map {
            PrefsFile(name = it.name, content = runCatching { it.readText() }.getOrDefault(""), metadata = emptyMap())
        }.toMutableList()
    }

    /** True: a desktop reads its own folder without asking anyone. */
    override fun isDirectoryAccessGranted(): Boolean = true

    /**
     * How long ago an export was written, as a rough "3 d" or "5 h".
     *
     * Deliberately coarse and deliberately not localised here: the same shape the Apple side settled
     * on. An unparseable timestamp gives an empty string rather than "now", which would be a lie
     * about how old a backup is.
     */
    override fun formatExportedAgo(utcTime: String): String {
        val then = runCatching { Instant.parse(utcTime) }.getOrNull() ?: return ""
        val minutes = (Clock.System.now() - then).inWholeMinutes
        return when {
            minutes < 1    -> "0 min"
            minutes < 60   -> "$minutes min"
            minutes < 1440 -> "${minutes / 60} h"
            else           -> "${minutes / 1440} d"
        }
    }
}
