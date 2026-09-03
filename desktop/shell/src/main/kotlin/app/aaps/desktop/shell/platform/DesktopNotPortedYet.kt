package app.aaps.desktop.shell.platform

import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.autotune.Autotune
import app.aaps.core.interfaces.bgQualityCheck.BgQualityCheck
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.maintenance.CloudDirectoryInfo
import app.aaps.core.interfaces.maintenance.CloudDirectoryManager
import app.aaps.core.interfaces.maintenance.ExportConfig
import app.aaps.core.interfaces.maintenance.ExportPreparation
import app.aaps.core.interfaces.maintenance.ExportResult
import app.aaps.core.interfaces.maintenance.ImportDecryptResult
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
import app.aaps.core.interfaces.maintenance.Maintenance
import app.aaps.core.interfaces.maintenance.Prefs
import app.aaps.core.interfaces.maintenance.PrefsFile
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.rx.weardata.CwfData
import app.aaps.core.interfaces.workflow.CalculationSignalsEmitter
import app.aaps.ui.compose.history.HistoryScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
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
 * Import and export of settings.
 *
 * The largest thing still missing, and it is not "file dialogs": `ImportExportPrefsImpl` is 911 lines
 * over the Storage Access Framework and WorkManager, and the format under it,
 * `EncryptedPrefsFormat`, is another 296 over `Context`, `DocumentFile` and `org.json`.
 *
 * The format is the reason this refuses rather than getting a quick desktop version. A phone has to
 * read back what a desktop writes; a second implementation of an encrypted stored format is how the
 * two silently stop matching, and the failure would appear as "your backup will not import" long
 * after the export.
 *
 * Reads answer with nothing and writes throw, so nothing here can be mistaken for a completed
 * export. `DesktopPrefsFileInfo` does list the export folder, so the screen shows which files exist
 * and then refuses to open one - deliberately, because seeing the list is what confirms the folder
 * is right.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class DesktopImportExportPrefs @Inject constructor(
    private val aapsLogger: AAPSLogger
) : ImportExportPrefs {

    override fun isMasterPasswordSet(): Boolean = false
    override fun isMasterPasswordCorrect(password: String): Boolean =
        aapsLogger.failNotOnDesktopYet("ImportExportPrefs.isMasterPasswordCorrect")

    override fun prepareExport(): ExportPreparation? {
        aapsLogger.notOnDesktopYet("ImportExportPrefs.prepareExport")
        return null
    }

    override suspend fun executeExport(password: String): ExportResult =
        aapsLogger.failNotOnDesktopYet("ImportExportPrefs.executeExport")

    override suspend fun executeCsvExport(): ExportResult =
        aapsLogger.failNotOnDesktopYet("ImportExportPrefs.executeCsvExport")

    override fun exportSharedPreferencesNonInteractive(password: String): Boolean {
        // False, not true: the unattended export path checks this, and a "yes" would record a
        // backup that does not exist.
        aapsLogger.notOnDesktopYet("ImportExportPrefs.exportSharedPreferencesNonInteractive")
        return false
    }

    override fun exportCustomWatchface(customWatchface: CwfData, withDate: Boolean) =
        aapsLogger.notOnDesktopYet("ImportExportPrefs.exportCustomWatchface")

    override fun exportUserEntriesCsv() = aapsLogger.notOnDesktopYet("ImportExportPrefs.exportUserEntriesCsv")

    override fun exportApsResult(algorithm: String?, input: String, output: String?) =
        aapsLogger.notOnDesktopYet("ImportExportPrefs.exportApsResult")

    override fun cacheExportPassword(password: String): String =
        aapsLogger.failNotOnDesktopYet("ImportExportPrefs.cacheExportPassword")

    override fun getExportConfig(): ExportConfig = ExportConfig(
        isCloudActive = false, isCloudError = false, hasCloudCredentials = false,
        settingsLocal = false, settingsCloud = false, logEmail = false, logCloud = false,
        csvLocal = false, csvCloud = false, cloudDisplayName = null
    )

    override fun setSettingsLocalEnabled(enabled: Boolean) = aapsLogger.notOnDesktopYet("export options")
    override fun setSettingsCloudEnabled(enabled: Boolean) = aapsLogger.notOnDesktopYet("export options")
    override fun setLogEmailEnabled(enabled: Boolean) = aapsLogger.notOnDesktopYet("export options")
    override fun setLogCloudEnabled(enabled: Boolean) = aapsLogger.notOnDesktopYet("export options")
    override fun setCsvLocalEnabled(enabled: Boolean) = aapsLogger.notOnDesktopYet("export options")
    override fun setCsvCloudEnabled(enabled: Boolean) = aapsLogger.notOnDesktopYet("export options")

    override suspend fun getLocalImportFiles(): List<PrefsFile> {
        aapsLogger.notOnDesktopYet("ImportExportPrefs.getLocalImportFiles")
        return emptyList()
    }

    override suspend fun getCloudImportFiles(pageToken: String?): Pair<List<PrefsFile>, String?> {
        aapsLogger.notOnDesktopYet("ImportExportPrefs.getCloudImportFiles")
        return emptyList<PrefsFile>() to null
    }

    override suspend fun getCloudImportFileCount(): Int = 0

    override fun decryptImportFile(file: PrefsFile, password: String): ImportDecryptResult =
        ImportDecryptResult.Error("Importing settings is not implemented on desktop yet")

    override fun executeImport(prefs: Prefs) = aapsLogger.failNotOnDesktopYet("ImportExportPrefs.executeImport")

    override fun prepareImportedSettings() = aapsLogger.notOnDesktopYet("ImportExportPrefs.prepareImportedSettings")
}

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
 * Sending and pruning logs.
 *
 * Nothing Android-specific in principle - a desktop can zip a folder - but the Android version goes
 * through an email intent and the log directory layout, so it is a port rather than a rewrite.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class DesktopMaintenance @Inject constructor(
    private val aapsLogger: AAPSLogger
) : Maintenance {

    override suspend fun executeSendLogs(): ExportResult =
        aapsLogger.failNotOnDesktopYet("Maintenance.executeSendLogs")

    override fun deleteLogs(keep: Int) = aapsLogger.notOnDesktopYet("Maintenance.deleteLogs")
}

/**
 * Cloud backup, which is Google Drive behind an OAuth flow.
 *
 * Every read reports "not connected" and every action refuses, so the settings screen shows cloud
 * export as unavailable rather than offering a sign-in that cannot complete.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class DesktopCloudDirectoryManager @Inject constructor(
    private val aapsLogger: AAPSLogger
) : CloudDirectoryManager {

    override fun getCloudDirectoryInfo(): CloudDirectoryInfo =
        aapsLogger.failNotOnDesktopYet("CloudDirectoryManager.getCloudDirectoryInfo")

    override fun clearCloudSettings() = aapsLogger.notOnDesktopYet("CloudDirectoryManager.clearCloudSettings")

    override suspend fun deauthorizeAndClearCloudSettings(): Boolean {
        aapsLogger.notOnDesktopYet("CloudDirectoryManager.deauthorizeAndClearCloudSettings")
        return false
    }

    override fun resetExportToLocal() = aapsLogger.notOnDesktopYet("CloudDirectoryManager.resetExportToLocal")
    override fun enableAllCloudExport() = aapsLogger.notOnDesktopYet("CloudDirectoryManager.enableAllCloudExport")
    override fun enableLocalStorage() = aapsLogger.notOnDesktopYet("CloudDirectoryManager.enableLocalStorage")

    override suspend fun testConnection(): Boolean = false
    override suspend fun startAuth(): String? = null
    override suspend fun waitForAuthCode(timeoutMs: Long): String? = null
    override suspend fun completeAuth(authCode: String): Boolean = false
    override suspend fun setupCloudStorage(): Boolean = false
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
