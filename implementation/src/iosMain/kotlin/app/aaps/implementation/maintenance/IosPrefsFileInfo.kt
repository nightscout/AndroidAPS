package app.aaps.implementation.maintenance

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.maintenance.PrefsFile
import app.aaps.core.interfaces.maintenance.PrefsFileInfo
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Clock
import kotlin.time.Instant
import platform.Foundation.NSDate
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSRelativeDateTimeFormatter
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dateWithTimeIntervalSince1970

/**
 * What the maintenance and import screens need to know about exports on iOS.
 *
 * ## The date
 *
 * Android picks one of three of its own strings - "less than an hour ago", "3 hours ago", "5 days
 * ago" - and falls back to the plain date once an export is older than the window. iOS has no
 * reader for those strings yet, so the relative wording comes from `NSRelativeDateTimeFormatter`
 * instead, which the system localizes on its own. That is a smaller difference than it sounds:
 * the same window decides which form is used, so the switch happens at the same age on both.
 *
 * What is missing is the surrounding word. Android says "exported 3 days ago"; this says "3 days
 * ago". The label sits next to a calendar icon on a row about an export, so it still reads, but the
 * prefix should be added once iOS can read the app's own strings.
 *
 * ## The file list
 *
 * Real, and it comes from [PrefsFileLister] rather than from a second reader written here. This used
 * to answer with an empty list because the format and the metadata keys were Android only; both are
 * shared code now, so an iPhone reads the same exports a phone writes.
 *
 * The list matters beyond the import screen: the setup wizard asks it whether to offer importing at
 * all, so an empty answer used to mean a new iOS user was never offered their own backup.
 *
 * ## The directory
 *
 * Android has to ask: the user picks a folder through the Storage Access Framework, the permission
 * is persisted, and it can be revoked later. iOS has no such grant. Every app owns its Documents
 * directory and can always read and write it, so the only thing that can go wrong is the directory
 * not being there, which is what is actually checked.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosPrefsFileInfo @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val lister: PrefsFileLister,
    private val exportDirectory: String? = defaultDocumentsDirectory()
) : PrefsFileInfo {

    // Built once. The formatter is not cheap to create and the screen asks per listed file.
    private val relativeFormatter by lazy { NSRelativeDateTimeFormatter() }

    override fun formatExportedAgo(utcTime: String): String {
        val exported = ExportedAgo.parse(utcTime)
        if (exported == null) {
            aapsLogger.debug(LTag.UI, "Export timestamp cannot be read, showing it as is: $utcTime")
            return ExportedAgo.datePart(utcTime)
        }
        val now = Clock.System.now()
        if (!ExportedAgo.isRelative(exported, now)) return ExportedAgo.datePart(utcTime)
        return relativeFormatter.localizedStringForDate(exported.toNSDate(), relativeToDate = now.toNSDate())
    }

    override fun listPreferenceFiles(): MutableList<PrefsFile> = lister.list().toMutableList()

    override fun isDirectoryAccessGranted(): Boolean {
        val directory = exportDirectory
        if (directory == null) {
            aapsLogger.debug(LTag.UI, "No documents directory, so nothing can be exported")
            return false
        }
        return NSFileManager.defaultManager.isWritableFileAtPath(directory)
    }
}

private fun Instant.toNSDate(): NSDate = NSDate.dateWithTimeIntervalSince1970(toEpochMilliseconds() / 1000.0)

/**
 * The app's own Documents directory, the only place an iOS app may write without being given
 * anything. Null would mean iOS did not report one at all, which should not happen.
 */
private fun defaultDocumentsDirectory(): String? =
    NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).firstOrNull() as? String
