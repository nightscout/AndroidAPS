package app.aaps.implementation.maintenance

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.maintenance.ExportConfig
import app.aaps.core.interfaces.maintenance.ExportPreparation
import app.aaps.core.interfaces.maintenance.ExportResult
import app.aaps.core.interfaces.maintenance.ImportDecryptResult
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
import app.aaps.core.interfaces.maintenance.PrefMetadata
import app.aaps.core.interfaces.maintenance.Prefs
import app.aaps.core.interfaces.maintenance.PrefsFile
import app.aaps.core.interfaces.maintenance.PrefsMetadataKey
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.protection.ExportPasswordDataStore
import app.aaps.core.interfaces.protection.PasswordHasher
import app.aaps.core.interfaces.protection.SecureEncrypt
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.weardata.CwfData
import app.aaps.core.interfaces.sharedPreferences.KeyValueStore
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.crypto.platformCryptoPrimitives
import app.aaps.implementation.maintenance.formats.ExportMetadata
import app.aaps.implementation.maintenance.formats.PrefsFormatCodec
import app.aaps.implementation.maintenance.formats.PrefsTransfer
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Exporting and importing AAPS settings, for every platform that keeps its exports in a folder.
 *
 * This was `IosImportExportPrefs`, and moving it here is the point: exactly one line of it was ever
 * about iOS - the device name - while the rest is the format, the crypto and the orchestration, all
 * of which are the same everywhere. Desktop refused this feature for a long time on the grounds that
 * a second implementation of an encrypted stored format is how two platforms silently stop matching.
 * That was right, and the answer is not to write a second one but to have no second one: there is a
 * single [PrefsFormatCodec], and the only thing a platform supplies is [PrefsFileAccess].
 *
 * Android keeps its own `ImportExportPrefsImpl`, which is a different job - the Storage Access
 * Framework, WorkManager, cloud providers and CSV export - and not something to force into this.
 *
 * ## What this deliberately does not do
 *
 * No cloud, no CSV export, no watchface export, no APS result dump. Each says so in the log rather
 * than returning a shape that looks like success, and `getExportConfig` reports the cloud as
 * inactive, which is true rather than a placeholder.
 */
@Suppress("TooManyFunctions")
class LocalImportExportPrefs(
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences,
    store: KeyValueStore,
    private val config: Config,
    private val dateUtil: DateUtil,
    private val activePlugin: ActivePlugin,
    private val passwordHasher: PasswordHasher,
    private val files: PrefsFileAccess,
    private val lister: PrefsFileLister,
    private val exportPasswordDataStore: ExportPasswordDataStore,
    secureEncrypt: SecureEncrypt,
    textResolver: TextResolver
) : ImportExportPrefs {

    private val codec = PrefsFormatCodec(platformCryptoPrimitives(), textResolver, secureEncrypt)
    private val transfer = PrefsTransfer(codec, store) { preferences.isExportableKey(it) }

    // ----- The master password, which is what an export is encrypted with -----

    override fun isMasterPasswordSet(): Boolean =
        !preferences.getIfExists(StringKey.ProtectionMasterPassword).isNullOrEmpty()

    override fun isMasterPasswordCorrect(password: String): Boolean {
        val masterHash = preferences.getIfExists(StringKey.ProtectionMasterPassword)
        return !masterHash.isNullOrEmpty() && passwordHasher.checkPassword(password, masterHash)
    }

    /**
     * Keeps the export password so a scheduled export can run without asking for it.
     *
     * The same store Android uses. `ActionSettingsExport` is an automation action in `commonMain`, so
     * a rule that exports on a schedule can be built on any platform, and it needs the password to
     * still be there when it fires.
     */
    override fun cacheExportPassword(password: String): String =
        exportPasswordDataStore.putPasswordToDataStore(password)

    // ----- Where an export goes. There is no cloud here, and this says so rather than failing -----

    override fun getExportConfig(): ExportConfig = ExportConfig(
        isCloudActive = false,
        isCloudError = false,
        hasCloudCredentials = false,
        settingsLocal = preferences.get(BooleanNonKey.ExportSettingsLocalEnabled),
        settingsCloud = false,
        logEmail = preferences.get(BooleanNonKey.ExportLogEmailEnabled),
        logCloud = false,
        csvLocal = preferences.get(BooleanNonKey.ExportCsvLocalEnabled),
        csvCloud = false,
        cloudDisplayName = null
    )

    override fun setSettingsLocalEnabled(enabled: Boolean) = preferences.put(BooleanNonKey.ExportSettingsLocalEnabled, enabled)
    override fun setCsvLocalEnabled(enabled: Boolean) = preferences.put(BooleanNonKey.ExportCsvLocalEnabled, enabled)
    override fun setLogEmailEnabled(enabled: Boolean) = preferences.put(BooleanNonKey.ExportLogEmailEnabled, enabled)

    /** No cloud here, so these stay off however they are set. */
    override fun setSettingsCloudEnabled(enabled: Boolean) = notHere("setSettingsCloudEnabled")
    override fun setLogCloudEnabled(enabled: Boolean) = notHere("setLogCloudEnabled")
    override fun setCsvCloudEnabled(enabled: Boolean) = notHere("setCsvCloudEnabled")

    // ----- Export -----

    /**
     * The name is generated once and carried, not generated again at write time.
     *
     * Both used to call `newExportName`, seconds apart, so the confirmation dialog could name a file
     * that did not match the one on disk - the names carry a timestamp to the second.
     */
    override fun prepareExport(): ExportPreparation =
        ExportPreparation(fileName = files.newExportName(config.FLAVOR), cachedPassword = null)

    override suspend fun executeExport(password: String): ExportResult =
        ExportResult(localSuccess = writeExport(files.newExportName(config.FLAVOR), password))

    override fun exportSharedPreferencesNonInteractive(password: String): Boolean =
        writeExport(files.newExportName(config.FLAVOR), password)

    private fun writeExport(name: String, password: String): Boolean =
        try {
            files.write(name, transfer.exportContents(metadataForExport(), password))
            aapsLogger.info(LTag.CORE, "Settings exported to $name")
            true
        } catch (e: Throwable) {
            aapsLogger.error(LTag.CORE, "Settings export to $name failed: ${e.message}")
            false
        }

    /** The platform finds the device name; [ExportMetadata] decides what an export is stamped with. */
    private fun metadataForExport(): Map<PrefsMetadataKey, PrefMetadata> = ExportMetadata.forExport(
        deviceName = platformDeviceName(),
        createdAt = dateUtil.toISOString(dateUtil.now()),
        version = config.VERSION_NAME,
        flavour = config.FLAVOR,
        deviceModel = config.currentDeviceModelString
    )

    // ----- Import -----

    override suspend fun getLocalImportFiles(): List<PrefsFile> = lister.list()

    override fun decryptImportFile(file: PrefsFile, password: String): ImportDecryptResult =
        transfer.importResult(file.content, password, config.isEngineeringMode())
            .also { if (it is ImportDecryptResult.Error) aapsLogger.error(LTag.CORE, "Reading ${file.name} failed: ${it.message}") }

    override fun executeImport(prefs: Prefs) {
        activePlugin.beforeImport()
        transfer.applyImported(prefs)
        activePlugin.afterImport()
    }

    override fun prepareImportedSettings() {
        preferences.put(BooleanNonKey.GeneralSetupWizardProcessed, true)
    }

    // ----- Not this feature. Each says so rather than looking like it worked -----

    override suspend fun getCloudImportFiles(pageToken: String?): Pair<List<PrefsFile>, String?> = emptyList<PrefsFile>() to null
    override suspend fun getCloudImportFileCount(): Int = 0
    override fun exportCustomWatchface(customWatchface: CwfData, withDate: Boolean) = notHere("exportCustomWatchface")
    override fun exportUserEntriesCsv() = notHere("exportUserEntriesCsv")
    override suspend fun executeCsvExport(): ExportResult = ExportResult(localSuccess = false).also { notHere("executeCsvExport") }
    override fun exportApsResult(algorithm: String?, input: String, output: String?) = notHere("exportApsResult")

    private fun notHere(what: String) = aapsLogger.debug(LTag.CORE, "Not implemented on this platform: ImportExportPrefs.$what")
}

/**
 * The exports sitting in the directory, with enough metadata for a list to show them.
 *
 * Shared because two callers need the same answer: the import screen, and the setup wizard's
 * decision about whether to offer importing at all. A second copy of this would be the kind that
 * quietly disagrees - the wizard offering a file the import screen then refuses, or the other way
 * round.
 */
@SingleIn(AppScope::class)
class PrefsFileLister @Inject constructor(
    private val files: PrefsFileAccess,
    secureEncrypt: SecureEncrypt,
    textResolver: TextResolver
) {

    private val codec = PrefsFormatCodec(platformCryptoPrimitives(), textResolver, secureEncrypt)

    /** Only files that really are exports. Anything else in the directory is somebody else's. */
    fun list(): List<PrefsFile> = files.list().mapNotNull { (name, contents) ->
        if (!codec.looksLikePreferences(contents)) null
        else PrefsFile(name = name, content = contents, metadata = codec.decodeMetadata(contents))
    }
}

/**
 * The file half of an export, kept apart from the format so both can be tested on their own.
 *
 * A fake of this in a test writes to a map instead of a real directory, which is what lets the export
 * and import path be tested without a simulator or a temp folder holding real files.
 */
interface PrefsFileAccess {

    /** The name a new export gets, in the same shape Android uses: `2026-09-05_143022_full.json`. */
    fun newExportName(flavour: String): String
    fun write(name: String, contents: String)
    fun list(): List<Pair<String, String>>
}
