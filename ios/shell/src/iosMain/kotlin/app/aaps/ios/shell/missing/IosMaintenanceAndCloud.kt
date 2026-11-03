package app.aaps.ios.shell.missing

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.maintenance.ExportResult
import app.aaps.core.interfaces.maintenance.Maintenance
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController

/*
 * Maintenance and cloud export.
 *
 * Maintenance is real now - sending and deleting logs both work. Cloud export still sits on Google
 * Drive through its Android SDK, which iOS has no version of, so it reports "nothing here" rather
 * than guessing.
 *
 * Screen usage statistics used to be here too. They moved to `platform` because a client is not
 * meant to collect them at all, which makes an empty answer correct rather than unfinished.
 */

/** Sending logs through the iOS share sheet, and deleting the rotated ones. Both are real. */
@OptIn(ExperimentalForeignApi::class)
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class IosMaintenance(
    private val aapsLogger: AAPSLogger,
    /** Overridden in a test, so it neither reads the real log nor deletes it. */
    private val logDirectory: String? = defaultLogDirectory()
) : Maintenance {

    /**
     * Hands the log files to the iOS share sheet - mail, Files, AirDrop, whatever the user has.
     *
     * No mail composer and no zip. `MFMailComposeViewController` would force one way of sending and
     * needs an account set up; the share sheet asks the user instead. The files go as they are,
     * because the logger already bounds them at about 10 MB in total.
     *
     * Returns success once the sheet is on screen, which is what Android reports too: there
     * `startActivity` on the mail intent is the last thing it can see, so "sent" has always meant
     * "handed over", not "delivered".
     *
     * Presented on the main thread on purpose - [app.aaps.core.interfaces.maintenance.Maintenance]
     * is called from an IO dispatcher, and UIKit may only be touched from the main one.
     */
    override suspend fun executeSendLogs(): ExportResult = withContext(Dispatchers.Main) {
        val files = logFiles()
        if (files.isEmpty()) {
            aapsLogger.error(LTag.CORE, "There is no log file to send")
            return@withContext ExportResult(localSuccess = false)
        }
        val host = topViewController()
        if (host == null) {
            aapsLogger.error(LTag.CORE, "There is no screen to show the share sheet over")
            return@withContext ExportResult(localSuccess = false)
        }

        val share = UIActivityViewController(activityItems = files, applicationActivities = null)
        // An iPad shows this as a popover, and presenting one with nowhere to point it raises.
        share.popoverPresentationController?.let { popover ->
            host.view?.let { view ->
                popover.sourceView = view
                popover.sourceRect = view.bounds
            }
        }
        host.presentViewController(share, animated = true, completion = null)
        aapsLogger.debug(LTag.CORE, "Offered ${files.size} log file(s) to share")
        ExportResult(localSuccess = true)
    }

    /**
     * The live log first, then the rotated ones newest first - the same order [deleteLogs] keeps.
     *
     * The live file is included even though it is open for writing: `AAPSLoggerIos` appends whole
     * lines and closes the handle each time, so a copy taken now is complete up to the last line.
     * That is the whole point here - the interesting part of a log is usually its end.
     */
    private fun logFiles(): List<NSURL> {
        val directory = logDirectory ?: return emptyList()
        val manager = NSFileManager.defaultManager

        @Suppress("UNCHECKED_CAST")
        val names = manager.contentsOfDirectoryAtPath(directory, null) as? List<String> ?: return emptyList()

        val rotated = names
            .mapNotNull { name -> name.removePrefix("$LOG_NAME.").toIntOrNull()?.takeIf { name != it.toString() }?.let { it to name } }
            .sortedBy { (index, _) -> index }
            .map { (_, name) -> name }

        return (listOf(LOG_NAME) + rotated)
            .map { "$directory/$it" }
            .filter { manager.fileExistsAtPath(it) }
            .map { NSURL.fileURLWithPath(it) }
    }

    /**
     * The screen currently in front.
     *
     * Walking down from the root rather than presenting on the root itself: the maintenance sheet is
     * already open when this runs, and presenting on a controller that is itself covered does
     * nothing at all - silently, which is the worst way for this to fail. Same reasoning as
     * `IosAuthBrowser`.
     */
    private fun topViewController(): UIViewController? {
        var controller = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return null
        while (true) {
            controller = controller.presentedViewController ?: return controller
        }
    }

    /**
     * Removes rotated log files, keeping the newest [keep] of them.
     *
     * `AAPSLoggerIos` already bounds the log itself: at 5 MB it moves `aaps.log` to `aaps.log.1` and
     * keeps exactly one previous file, so the worst case on disk is about 10 MB and nothing here is
     * load bearing today. This exists for two reasons anyway. The periodic maintenance calls it every
     * cycle, and a call that logs an error every hour trains people to ignore errors. And the bound
     * is the *logger's* promise, not this one's: if it is ever changed to keep more, the housekeeping
     * that is supposed to clear them up should already work rather than be discovered missing.
     *
     * The live `aaps.log` is never touched - something is writing to it.
     */
    override fun deleteLogs(keep: Int) {
        val directory = logDirectory
        if (directory == null) {
            aapsLogger.debug(LTag.CORE, "No log directory, so there is nothing to delete")
            return
        }
        val manager = NSFileManager.defaultManager

        @Suppress("UNCHECKED_CAST")
        val names = manager.contentsOfDirectoryAtPath(directory, null) as? List<String> ?: return

        // Only the rotated ones - `aaps.log` itself is open for writing - and ordered newest first.
        // `.1` is the newest, because rotation *moves* the live file onto it; the convention every
        // rotating logger uses, and the opposite of what a plain name sort gives. Ordered by the
        // number rather than as text, so a hypothetical `.10` does not sort between `.1` and `.2`.
        val rotated = names
            .mapNotNull { name -> name.removePrefix("$LOG_NAME.").toIntOrNull()?.takeIf { name != it.toString() }?.let { it to name } }
            .sortedBy { (index, _) -> index }
            .map { (_, name) -> name }

        rotated.drop((keep - 1).coerceAtLeast(0)).forEach { name ->
            manager.removeItemAtPath("$directory/$name", null)
            aapsLogger.debug(LTag.CORE, "Deleted the old log file $name")
        }
    }

    private companion object {

        /** The name `AAPSLoggerIos` writes, and rotates as `aaps.log.1`. */
        private const val LOG_NAME = "aaps.log"
    }
}

/** Where `AAPSLoggerIos` puts the log: the app's Documents directory. */
@OptIn(ExperimentalForeignApi::class)
private fun defaultLogDirectory(): String? =
    NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).firstOrNull() as? String
