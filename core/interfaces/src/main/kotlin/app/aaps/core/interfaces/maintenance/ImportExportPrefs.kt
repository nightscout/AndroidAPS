package app.aaps.core.interfaces.maintenance

import android.content.Context
import androidx.fragment.app.FragmentActivity
import app.aaps.core.interfaces.rx.weardata.CwfData
import org.json.JSONObject

/** Where to send the export. */
enum class ExportDestination {

    LOCAL, CLOUD, BOTH
}

/**
 * Snapshot of the current export-options configuration.
 * Read once, then used to drive UI labels and export routing.
 */
data class ExportConfig(
    val isCloudActive: Boolean,
    val isCloudError: Boolean,
    val hasCloudCredentials: Boolean,
    val settingsLocal: Boolean,
    val settingsCloud: Boolean,
    val logEmail: Boolean,
    val logCloud: Boolean,
    val csvLocal: Boolean,
    val csvCloud: Boolean,
    val cloudDisplayName: String?
)

/**
 * Per-destination result of an export.
 * `null` means the destination was not attempted.
 */
data class ExportResult(
    val localSuccess: Boolean? = null,
    val cloudSuccess: Boolean? = null
)

/**
 * Result of preparing an export: file is ready, and optionally a cached password is available.
 */
data class ExportPreparation(
    val fileName: String,
    val cachedPassword: String?,
    val destination: ExportDestination = ExportDestination.LOCAL,
    val cloudDisplayName: String? = null
)

interface ImportExportPrefs {

    fun exportCustomWatchface(customWatchface: CwfData, withDate: Boolean = true)
    fun exportSharedPreferences(activity: FragmentActivity)
    fun exportSharedPreferencesNonInteractive(context: Context, password: String): Boolean
    fun exportUserEntriesCsv(context: Context)
    suspend fun executeCsvExport(): ExportResult
    fun exportApsResult(algorithm: String?, input: JSONObject, output: JSONObject?)

    // Compose export support — discrete steps, no UI

    /** Check if master password has been configured */
    fun isMasterPasswordSet(): Boolean

    /** Prepare export file and check for cached password. Returns null if file creation fails. */
    fun prepareExport(): ExportPreparation?

    /** Execute the actual export with the given password. Returns per-destination results. */
    suspend fun executeExport(password: String): ExportResult

    /** Cache the password for future exports. Returns the (possibly transformed) password to use. */
    fun cacheExportPassword(password: String): String

    /** Get a snapshot of the current export configuration. */
    fun getExportConfig(): ExportConfig

    /** Toggle export destination preferences (for inline FilterChips). */
    fun setSettingsLocalEnabled(enabled: Boolean)
    fun setSettingsCloudEnabled(enabled: Boolean)
    fun setLogEmailEnabled(enabled: Boolean)
    fun setLogCloudEnabled(enabled: Boolean)
    fun setCsvLocalEnabled(enabled: Boolean)
    fun setCsvCloudEnabled(enabled: Boolean)

    // Compose import support — discrete steps, no UI

    /** Get list of local preference files. */
    suspend fun getLocalImportFiles(): List<PrefsFile>

    /** Get a page of cloud preference files. Returns (files, nextPageToken). */
    suspend fun getCloudImportFiles(pageToken: String?): Pair<List<PrefsFile>, String?>

    /** Get total count of settings files in cloud. */
    suspend fun getCloudImportFileCount(): Int

    /** Attempt to decrypt a preference file with the given password. */
    fun decryptImportFile(file: PrefsFile, password: String): ImportDecryptResult

    /** Write the decrypted prefs to SharedPreferences and call plugin hooks. */
    fun executeImport(prefs: Prefs)

    /** Send events needed before app restart after import. */
    fun prepareImportRestart()
}

/** Result of attempting to decrypt a preference file for import. */
sealed interface ImportDecryptResult {

    data class Success(
        val prefs: Prefs,
        val importOk: Boolean,
        val importPossible: Boolean
    ) : ImportDecryptResult

    data object WrongPassword : ImportDecryptResult
    data class Error(val message: String) : ImportDecryptResult
}