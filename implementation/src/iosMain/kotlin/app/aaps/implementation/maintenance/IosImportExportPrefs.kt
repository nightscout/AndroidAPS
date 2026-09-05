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
import app.aaps.core.interfaces.maintenance.PrefsFile
import app.aaps.core.interfaces.maintenance.PrefsMetadataKey
import app.aaps.core.interfaces.maintenance.Prefs
import app.aaps.core.interfaces.plugin.ActivePlugin
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
import app.aaps.implementation.maintenance.data.PrefsStatusImpl
import app.aaps.implementation.maintenance.formats.ExportMetadata
import app.aaps.implementation.maintenance.formats.PrefsFormatCodec
import app.aaps.implementation.maintenance.formats.PrefsTransfer
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSLocale
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile
import platform.UIKit.UIDevice

/**
 * Exporting and importing AAPS settings on iOS.
 *
 * ## What is real here and what is not
 *
 * Settings export and import are real, and they write the same files Android writes -
 * [PrefsFormatCodec] is the one implementation of the format now, so a file exported on a phone
 * opens on an iPhone and the other way round. What is not here is the cloud, the CSV export, the
 * watchface export and the APS result dump. Those are separate features rather than parts of this
 * one, and each says so rather than pretending to work: the cloud answers "not configured", which
 * is true, and the exports log that they are not implemented.
 *
 * ## Where the files go
 *
 * The app's own Documents directory. On Android the user picks a folder through the storage access
 * framework and AAPS holds a permission to it; iOS has no such picker for a folder the app then
 * keeps writing to, and does not need one - the app's Documents directory is exposed to the Files
 * app by `UIFileSharingEnabled`, so the user can reach an export, copy it off the phone, or drop one
 * in to import. That is the same shape of access with none of the permission machinery.
 *
 * The database used to live in this directory and no longer does, which is what makes exposing it
 * safe: a user tidying up their files can no longer delete the treatment history by accident.
 */
@Suppress("TooManyFunctions")
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosImportExportPrefs @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences,
    private val store: KeyValueStore,
    private val config: Config,
    private val dateUtil: DateUtil,
    private val activePlugin: ActivePlugin,
    private val passwordHasher: PasswordHasher,
    private val files: PrefsFileAccess,
    private val lister: PrefsFileLister,
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
     * Returns the password unchanged.
     *
     * Android keeps an export password for a while so a scheduled export can run unattended, in a
     * datastore that expires it. iOS has no unattended export to serve, so there is nothing to cache
     * and nothing to expire - and a password stored with no expiry to answer a need that does not
     * exist would be the worse answer.
     */
    override fun cacheExportPassword(password: String): String = password

    // ----- Where an export goes. There is no cloud on iOS, and this says so rather than failing -----

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

    /** No cloud on iOS, so these stay off however they are set. */
    override fun setSettingsCloudEnabled(enabled: Boolean) = notOnIos("setSettingsCloudEnabled")
    override fun setLogCloudEnabled(enabled: Boolean) = notOnIos("setLogCloudEnabled")
    override fun setCsvCloudEnabled(enabled: Boolean) = notOnIos("setCsvCloudEnabled")

    // ----- Export -----

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

    /** The platform finds the values; [ExportMetadata] decides what an export is stamped with. */
    private fun metadataForExport(): Map<PrefsMetadataKey, PrefMetadata> = ExportMetadata.forExport(
        deviceName = UIDevice.currentDevice.name,
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
    override fun exportCustomWatchface(customWatchface: CwfData, withDate: Boolean) = notOnIos("exportCustomWatchface")
    override fun exportUserEntriesCsv() = notOnIos("exportUserEntriesCsv")
    override suspend fun executeCsvExport(): ExportResult = ExportResult(localSuccess = false).also { notOnIos("executeCsvExport") }
    override fun exportApsResult(algorithm: String?, input: String, output: String?) = notOnIos("exportApsResult")

    private fun notOnIos(what: String) = aapsLogger.debug(LTag.CORE, "Not implemented on iOS yet: ImportExportPrefs.$what")
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
 * A fake of this in a test writes to a map instead of the Documents directory, which is what lets
 * the export and import path be tested without a simulator holding real files.
 */
interface PrefsFileAccess {

    /** The name a new export gets, in the same shape Android uses: `2026-09-05_143022_full.json`. */
    fun newExportName(flavour: String): String
    fun write(name: String, contents: String)
    fun list(): List<Pair<String, String>>
}

/** [PrefsFileAccess] over the app's Documents directory, which the Files app shows to the user. */
@OptIn(ExperimentalForeignApi::class)
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosPrefsFileAccess @Inject constructor(
    /**
     * Where exports are kept. Defaults to the app's Documents directory, which is the one the Files
     * app shows; a test points it somewhere of its own so it neither reads the user's exports nor
     * leaves files behind for the next test to list.
     */
    private val directory: String? = defaultDocumentsDirectory()
) : PrefsFileAccess {

    /**
     * Pinned to `en_US_POSIX` and the Gregorian calendar on purpose.
     *
     * An `NSDateFormatter` otherwise follows the user's own calendar and digits, so the same pattern
     * gives a Buddhist year on a Thai phone and Eastern Arabic numerals on an Arabic one. A file name
     * is not something the user reads in their language - it is sorted, matched and compared with
     * files written by Android, which uses these digits.
     */
    private val nameFormatter = NSDateFormatter().apply {
        setLocale(NSLocale("en_US_POSIX"))
        setDateFormat("yyyy-MM-dd'_'HHmmss")
    }

    override fun newExportName(flavour: String): String = "${nameFormatter.stringFromDate(NSDate())}_$flavour.json"

    override fun write(name: String, contents: String) {
        val path = documentsPath(name) ?: error("no Documents directory")
        val ok = NSString.create(string = contents).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
        if (!ok) error("could not write $name")
    }

    override fun list(): List<Pair<String, String>> {
        val manager = NSFileManager.defaultManager
        val directory = documentsDirectory() ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        val names = manager.contentsOfDirectoryAtPath(directory, null) as? List<String> ?: return emptyList()
        return names.filter { it.endsWith(".json") }.mapNotNull { name ->
            NSString.stringWithContentsOfFile(directory + "/" + name, encoding = NSUTF8StringEncoding, error = null)?.let { name to it }
        }
    }

    private fun documentsDirectory(): String? = directory

    private fun documentsPath(name: String): String? = documentsDirectory()?.let { "$it/$name" }
}

/** The app's own Documents directory, or null on the rare build that has none. */
private fun defaultDocumentsDirectory(): String? =
    NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).firstOrNull() as? String
