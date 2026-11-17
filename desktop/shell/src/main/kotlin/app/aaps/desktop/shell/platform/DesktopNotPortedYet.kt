package app.aaps.desktop.shell.platform

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.maintenance.ExportResult
import app.aaps.core.interfaces.maintenance.Maintenance
import app.aaps.implementation.maintenance.DesktopFolders
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.io.File

/**
 * Real features that are not ported to desktop yet, refusing rather than pretending.
 *
 * Unlike the neighbouring files, none of these is absent by nature - each is code that could run on
 * a JVM and has simply not been moved. They are grouped so the size of what is left is visible in
 * one place, and each says what it would take.
 */

/**
 * Sending and pruning logs. Pruning works; sending does not.
 *
 * `deleteLogs` is real, and matches the iOS one. `executeSendLogs` still refuses: nothing about
 * zipping a folder is Android-specific, but the Android version ends in an email intent, and where a
 * desktop should send a log - a mail client, a save dialog, a folder it opens - is a question about
 * the product rather than about the port.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class DesktopMaintenance(
    private val aapsLogger: AAPSLogger,
    /**
     * The live log, whose siblings are the rotated ones. Overridden in a test so it neither reads
     * nor deletes anything in the user's real data directory.
     */
    private val logFile: File = DesktopFolders.log
) : Maintenance {

    override suspend fun executeSendLogs(): ExportResult =
        aapsLogger.failNotOnDesktopYet("Maintenance.executeSendLogs")

    /**
     * Removes rotated log files, keeping the newest [keep] of them.
     *
     * `AAPSLoggerDesktop` bounds the log itself: at 5 MB it moves `aaps.log` to `aaps.log.1` and
     * keeps exactly one previous file, so the worst case on disk is about 10 MB and nothing here is
     * load bearing today. It exists anyway for the reason the iOS one does - `PeriodicMaintenance`
     * calls this every cycle, and a call that logs an error every hour trains people to ignore
     * errors - and because the bound is the *logger's* promise, not this one's. If it ever keeps
     * more, the housekeeping should already work rather than be discovered missing.
     *
     * The live `aaps.log` is never touched: something is writing to it.
     */
    override fun deleteLogs(keep: Int) {
        val directory = logFile.parentFile ?: return
        // Only the rotated ones, ordered newest first. `.1` is the newest, because rotation *moves*
        // the live file onto it - the convention every rotating logger uses, and the opposite of
        // what a plain name sort gives. Ordered by the number so a hypothetical `.10` does not sort
        // between `.1` and `.2`.
        val rotated = directory.listFiles()
            ?.filter { it.isFile }
            ?.mapNotNull { candidate ->
                candidate.name.removePrefix("${logFile.name}.").toIntOrNull()
                    ?.takeIf { candidate.name != logFile.name }
                    ?.let { it to candidate }
            }
            ?.sortedBy { (index, _) -> index }
            ?.map { (_, candidate) -> candidate }
            .orEmpty()

        rotated.drop((keep - 1).coerceAtLeast(0)).forEach { old ->
            if (old.delete()) aapsLogger.debug(LTag.CORE, "Deleted the old log file ${old.name}")
        }
    }
}
