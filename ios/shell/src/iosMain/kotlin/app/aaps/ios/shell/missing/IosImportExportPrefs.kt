package app.aaps.ios.shell.missing

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.maintenance.ExportConfig
import app.aaps.core.interfaces.maintenance.ExportPreparation
import app.aaps.core.interfaces.maintenance.ExportResult
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
import app.aaps.core.interfaces.maintenance.ImportDecryptResult
import app.aaps.core.interfaces.maintenance.Prefs
import app.aaps.core.interfaces.maintenance.PrefsFile
import app.aaps.core.interfaces.rx.weardata.CwfData
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Placeholder, and the largest gap in this package by a distance: 24 methods, and the Android class
 * behind them is the document picker, the encrypted file format and the CSV writer.
 *
 * The split below is on purpose:
 *
 * - **Reads that describe the current state** answer "nothing configured", which is true - iOS has
 *   no export set up, so no cloud, no local files, no master password.
 * - **Anything that would export, import or decrypt** throws. There is no safe placeholder for
 *   writing the user's settings out or reading someone's settings in, and a method that quietly
 *   did nothing would look like an export that worked.
 *
 * Nothing here is worth finishing piecemeal. It needs the same treatment the other big ones got:
 * the rules to commonMain, the file access behind a platform port, and then a `UIDocumentPicker`
 * on this side.
 */
@Suppress("TooManyFunctions")
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosImportExportPrefs @Inject constructor(
    private val aapsLogger: AAPSLogger
) : ImportExportPrefs {

    // ----- Reads that can answer honestly: nothing is set up -----

    override fun isMasterPasswordSet(): Boolean {
        aapsLogger.notOnIosYet("ImportExportPrefs.isMasterPasswordSet")
        return false
    }

    override fun getExportConfig(): ExportConfig {
        aapsLogger.notOnIosYet("ImportExportPrefs.getExportConfig")
        return ExportConfig(
            isCloudActive = false,
            isCloudError = false,
            hasCloudCredentials = false,
            settingsLocal = false,
            settingsCloud = false,
            logEmail = false,
            logCloud = false,
            csvLocal = false,
            csvCloud = false,
            cloudDisplayName = null
        )
    }

    override suspend fun getLocalImportFiles(): List<PrefsFile> {
        aapsLogger.notOnIosYet("ImportExportPrefs.getLocalImportFiles")
        return emptyList()
    }

    override suspend fun getCloudImportFiles(pageToken: String?): Pair<List<PrefsFile>, String?> {
        aapsLogger.notOnIosYet("ImportExportPrefs.getCloudImportFiles")
        return emptyList<PrefsFile>() to null
    }

    override suspend fun getCloudImportFileCount(): Int {
        aapsLogger.notOnIosYet("ImportExportPrefs.getCloudImportFileCount")
        return 0
    }

    /** Nothing to prepare, so nothing is offered. Null is the interface's "file creation failed". */
    override fun prepareExport(): ExportPreparation? {
        aapsLogger.notOnIosYet("ImportExportPrefs.prepareExport")
        return null
    }

    // ----- Toggles: recorded in the log, stored nowhere -----

    override fun setSettingsLocalEnabled(enabled: Boolean) = aapsLogger.notOnIosYet("ImportExportPrefs.setSettingsLocalEnabled")
    override fun setSettingsCloudEnabled(enabled: Boolean) = aapsLogger.notOnIosYet("ImportExportPrefs.setSettingsCloudEnabled")
    override fun setLogEmailEnabled(enabled: Boolean) = aapsLogger.notOnIosYet("ImportExportPrefs.setLogEmailEnabled")
    override fun setLogCloudEnabled(enabled: Boolean) = aapsLogger.notOnIosYet("ImportExportPrefs.setLogCloudEnabled")
    override fun setCsvLocalEnabled(enabled: Boolean) = aapsLogger.notOnIosYet("ImportExportPrefs.setCsvLocalEnabled")
    override fun setCsvCloudEnabled(enabled: Boolean) = aapsLogger.notOnIosYet("ImportExportPrefs.setCsvCloudEnabled")

    override fun prepareImportRestart() = aapsLogger.notOnIosYet("ImportExportPrefs.prepareImportRestart")

    override fun exportCustomWatchface(customWatchface: CwfData, withDate: Boolean) =
        aapsLogger.notOnIosYet("ImportExportPrefs.exportCustomWatchface")

    override fun exportUserEntriesCsv() = aapsLogger.notOnIosYet("ImportExportPrefs.exportUserEntriesCsv")

    override fun exportApsResult(algorithm: String?, input: String, output: String?) =
        aapsLogger.notOnIosYet("ImportExportPrefs.exportApsResult")

    // ----- Anything that moves the user's settings: refuse rather than pretend -----

    override fun exportSharedPreferencesNonInteractive(password: String): Boolean =
        aapsLogger.failNotOnIosYet("ImportExportPrefs.exportSharedPreferencesNonInteractive")

    override suspend fun executeCsvExport(): ExportResult =
        aapsLogger.failNotOnIosYet("ImportExportPrefs.executeCsvExport")

    override suspend fun executeExport(password: String): ExportResult =
        aapsLogger.failNotOnIosYet("ImportExportPrefs.executeExport")

    override fun cacheExportPassword(password: String): String =
        aapsLogger.failNotOnIosYet("ImportExportPrefs.cacheExportPassword")

    override fun isMasterPasswordCorrect(password: String): Boolean =
        aapsLogger.failNotOnIosYet("ImportExportPrefs.isMasterPasswordCorrect")

    override fun decryptImportFile(file: PrefsFile, password: String): ImportDecryptResult =
        aapsLogger.failNotOnIosYet("ImportExportPrefs.decryptImportFile")

    override fun executeImport(prefs: Prefs) =
        aapsLogger.failNotOnIosYet("ImportExportPrefs.executeImport")
}
