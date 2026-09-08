package app.aaps.desktop.shell.platform

import app.aaps.core.interfaces.autotune.Autotune
import app.aaps.core.interfaces.bgQualityCheck.BgQualityCheck
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.maintenance.ExportResult
import app.aaps.core.interfaces.maintenance.Maintenance
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.workflow.CalculationSignalsEmitter
import app.aaps.implementation.maintenance.DesktopFolders
import app.aaps.ui.compose.history.HistoryScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Real features that are not ported to desktop yet, refusing rather than pretending.
 *
 * Unlike the neighbouring files, none of these is absent by nature - each is code that could run on
 * a JVM and has simply not been moved. They are grouped so the size of what is left is visible in
 * one place, and each says what it would take.
 */

/**
 * Autotune, which is arithmetic sitting in the wrong source set.
 *
 * `AutotunePlugin` is about 2,700 lines over treatment history and nothing in it is really Android -
 * the blockers are `Calendar`/`TimeZone` and a file dump. It should be **ported rather than written
 * again**, and the date maths deserves golden vectors first, because what it computes is basal, ISF
 * and ICR.
 *
 * A run reports failure rather than doing nothing quietly. "Autotune did not work" is recoverable;
 * "autotune finished and changed nothing" would be a lie about insulin settings.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class DesktopAutotune @Inject constructor(
    private val aapsLogger: AAPSLogger
) : Autotune {

    override var lastRunSuccess: Boolean = false
    override var calculationRunning: Boolean = false

    override suspend fun aapsAutotune(daysBack: Int, autoSwitch: Boolean, profileToTune: String, weekDays: BooleanArray?) {
        lastRunSuccess = false
        aapsLogger.notOnDesktopYet("Autotune.aapsAutotune")
    }

    override fun atLog(message: String) = aapsLogger.notOnDesktopYet("Autotune.atLog: $message")
}

/**
 * Glucose data quality, which the overview shows as a chip and the loop reads.
 *
 * `UNKNOWN` is the one state that claims nothing. Answering `FIVE_MIN_DATA` would tell the loop the
 * data is clean when nothing checked it, and the whole point of this class is to notice doubled or
 * flat readings.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class DesktopBgQualityCheck @Inject constructor(
    private val aapsLogger: AAPSLogger
) : BgQualityCheck {

    override var state: BgQualityCheck.State = BgQualityCheck.State.UNKNOWN
    override var message: String = ""

    private val _stateFlow = MutableStateFlow(BgQualityCheck.State.UNKNOWN)
    override val stateFlow: StateFlow<BgQualityCheck.State> = _stateFlow

    override fun stateDescription(): String {
        aapsLogger.notOnDesktopYet("BgQualityCheck.stateDescription")
        return ""
    }
}

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
class DesktopMaintenance @Inject constructor(
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


/**
 * The history browser's own calculation scope.
 *
 * Refuses outright, for the reason the Apple side gives: this must be a **second** set of
 * calculation state, separate from the live loop's. Handing back the loop's own objects would let a
 * user scrolling through history overwrite the numbers the loop is dosing from.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class DesktopHistoryScope @Inject constructor(
    private val aapsLogger: AAPSLogger
) : HistoryScope {

    override val overviewData: OverviewData get() = refuse()
    override val signals: CalculationSignalsEmitter get() = refuse()
    override val cache: OverviewDataCache get() = refuse()
    override val iobCobCalculator: IobCobCalculator get() = refuse()

    override fun onDestroy() = aapsLogger.notOnDesktopYet("HistoryScope.onDestroy")

    private fun refuse(): Nothing = aapsLogger.failNotOnDesktopYet(
        "HistoryScope needs a scope of its own on desktop - sharing the loop's calculation state would corrupt it"
    )
}
