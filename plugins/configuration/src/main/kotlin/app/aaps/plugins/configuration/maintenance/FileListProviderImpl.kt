package app.aaps.plugins.configuration.maintenance

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.maintenance.FileListProvider
import app.aaps.core.interfaces.maintenance.PrefMetadata
import app.aaps.core.interfaces.maintenance.PrefsFile
import app.aaps.core.interfaces.maintenance.PrefsMetadataKey
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.weardata.CwfFile
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.storage.Storage
import app.aaps.core.interfaces.versionChecker.VersionCheckerUtils
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.plugins.configuration.R
import app.aaps.plugins.configuration.maintenance.data.PrefMetadataMap
import app.aaps.plugins.configuration.maintenance.data.PrefsStatusImpl
import app.aaps.plugins.configuration.maintenance.formats.EncryptedPrefsFormat
import app.aaps.shared.impl.weardata.ZipWatchfaceFormat
import dagger.Lazy
import dagger.Reusable
import org.joda.time.DateTime
import org.joda.time.Days
import org.joda.time.Hours
import org.joda.time.LocalDateTime
import org.joda.time.format.DateTimeFormat
import java.io.File
import javax.inject.Inject
import kotlin.math.abs

@Suppress("SpellCheckingInspection")
@Reusable
class FileListProviderImpl @Inject constructor(
    private val rh: ResourceHelper,
    private val config: Lazy<Config>,
    private val encryptedPrefsFormat: EncryptedPrefsFormat,
    private val storage: Storage,
    private val versionCheckerUtils: VersionCheckerUtils,
    private val preferences: Lazy<Preferences>,
    private val context: Context,
    private val rxBus: RxBus
) : FileListProvider {

    private val documentsPath get() = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "AAPS")
    override val resultPath get() = File(documentsPath, File.separator + "results")

    val preferencesPath = "preferences"
    val exportsPath = "exports"
    val tempPath = "temp"
    val extraPath = "extra"

    companion object {

        private const val IMPORT_AGE_NOT_YET_OLD_DAYS = 60
    }

    /**
     * This function tries to list possible preference files from AAPS/preferences dir
     * and tries to do quick assessment for preferences format plausibility.
     */
    override fun listPreferenceFiles(): MutableList<PrefsFile> {
        val prefFiles = mutableListOf<PrefsFile>()

        try {
            // searching dedicated dir, only for new JSON format
            ensurePreferenceDirExists()?.listFiles()?.filter { it.isFile && it.name?.endsWith(".json") == true }?.forEach {
                val content = storage.getFileContents(context.contentResolver, it)
                if (encryptedPrefsFormat.isPreferencesFile(it, content)) {
                    prefFiles.add(PrefsFile(it.name ?: "UNKNOWN", content, metadataFor(content)))
                }
            }
        } catch (_: SecurityException) {
            ToastUtils.errorToast(context, rh.gs(R.string.error_accessing_filesystem_select_aaps_directory_properly))
            return mutableListOf()
        }

        val filtered = prefFiles
            .filter { it.metadata[PrefsMetadataKeyImpl.AAPS_FLAVOUR]?.status != null }
            .filter { (it.metadata[PrefsMetadataKeyImpl.AAPS_FLAVOUR]?.status as PrefsStatusImpl) != PrefsStatusImpl.ERROR }
            .toMutableList()
        filtered.sortWith(
            compareByDescending<PrefsFile> { it.metadata[PrefsMetadataKeyImpl.AAPS_FLAVOUR]?.status as PrefsStatusImpl }
                .thenByDescending { it.metadata[PrefsMetadataKeyImpl.CREATED_AT]?.value }
        )
        return filtered
    }

    override fun listCustomWatchfaceFiles(): MutableList<CwfFile> {
        val customWatchfaceFiles = mutableListOf<CwfFile>()
        val customWatchfaceAuthorization = preferences.get().get(BooleanKey.WearCustomWatchfaceAuthorization)

        ensureExportDirExists()?.listFiles()?.filter { it.isFile && it.name?.endsWith(ZipWatchfaceFormat.CWF_EXTENSION) == true }?.forEach {
            try {
                storage.getBinaryFileContents(context.contentResolver, it)?.let { content ->
                    ZipWatchfaceFormat.loadCustomWatchface(content, it?.name ?: "", customWatchfaceAuthorization)?.also { customWatchface ->
                        customWatchfaceFiles.add(customWatchface)
                    }
                }
            } catch (_: SecurityException) {
                ToastUtils.errorToast(context, rh.gs(R.string.error_accessing_filesystem_select_aaps_directory_properly))
                return mutableListOf()
            }
        }

        if (customWatchfaceFiles.isEmpty()) {
            try {
                val assetFiles = context.assets.list("") ?: arrayOf()
                for (assetFileName in assetFiles) {
                    if (assetFileName.endsWith(ZipWatchfaceFormat.CWF_EXTENSION)) {
                        val assetByteArray = context.assets.open(assetFileName).readBytes()
                        ZipWatchfaceFormat.loadCustomWatchface(assetByteArray, assetFileName, customWatchfaceAuthorization)?.also { customWatchface ->
                            customWatchfaceFiles.add(customWatchface)
                            rxBus.send(EventData.ActionGetCustomWatchface(EventData.ActionSetCustomWatchface(customWatchface.cwfData), exportFile = true, withDate = false))
                        }
                    }
                }
            } catch (_: Exception) {
                // Handle any exceptions that may occur while accessing assets
            }
        }

        return customWatchfaceFiles
    }

    private fun metadataFor(contents: String): PrefMetadataMap =
        checkMetadata(encryptedPrefsFormat.loadMetadata(contents))

    override fun ensurePreferenceDirExists(): DocumentFile? {
        val prefUri = preferences.get().getIfExists(StringKey.AapsDirectoryUri) ?: return null
        val uri = Uri.parse(prefUri)
        val baseDir = DocumentFile.fromTreeUri(context, uri)
        val files = baseDir?.listFiles()
        return files?.firstOrNull { it.name == preferencesPath } ?: baseDir?.createDirectory(preferencesPath)
    }

    override fun ensureExportDirExists(): DocumentFile? {
        val prefUri = preferences.get().getIfExists(StringKey.AapsDirectoryUri) ?: return null
        val uri = Uri.parse(prefUri)
        val baseDir = DocumentFile.fromTreeUri(context, uri)
        val files = baseDir?.listFiles()
        return files?.firstOrNull { it.name == exportsPath } ?: baseDir?.createDirectory(exportsPath)
    }

    override fun ensureTempDirExists(): DocumentFile? {
        val prefUri = preferences.get().getIfExists(StringKey.AapsDirectoryUri) ?: return null
        val uri = Uri.parse(prefUri)
        val baseDir = DocumentFile.fromTreeUri(context, uri)
        val files = baseDir?.listFiles()
        return files?.firstOrNull { it.name == tempPath } ?: baseDir?.createDirectory(tempPath)
    }

    override fun ensureExtraDirExists(): DocumentFile? {
        val prefUri = preferences.get().getIfExists(StringKey.AapsDirectoryUri) ?: return null
        val uri = Uri.parse(prefUri)
        val baseDir = DocumentFile.fromTreeUri(context, uri)
        val files = baseDir?.listFiles()
        return files?.firstOrNull { it.name == extraPath } ?: baseDir?.createDirectory(extraPath)
    }

    override fun ensureResultDirExists(): File {
        if (!resultPath.exists()) {
            resultPath.mkdirs()
        }
        return resultPath
    }

    override fun newPreferenceFile(): DocumentFile? {
        val timeLocal = LocalDateTime.now().toString(DateTimeFormat.forPattern("yyyy-MM-dd'_'HHmmss"))
        val dir = ensurePreferenceDirExists()
        return dir?.createFile("application/json", timeLocal + "_" + config.get().FLAVOR)
    }

    override fun newExportCsvFile(): DocumentFile? {
        val timeLocal = LocalDateTime.now().toString(DateTimeFormat.forPattern("yyyy-MM-dd'_'HHmmss"))
        val dir = ensureExportDirExists()
        return dir?.createFile("application/csv", timeLocal + "_UserEntry.csv")
    }

    override fun newCwfFile(filename: String, withDate: Boolean): DocumentFile? {
        val timeLocal = LocalDateTime.now().toString(DateTimeFormat.forPattern("yyyy-MM-dd'_'HHmmss"))
        val dir = ensureExportDirExists()
        return dir?.createFile("application/${ZipWatchfaceFormat.CWF_EXTENSION}", if (withDate) "${filename}_$timeLocal" else filename)
    }

    override fun newResultFile(): File {
        val timeLocal = LocalDateTime.now().toString(DateTimeFormat.forPattern("yyyy-MM-dd'_'HHmmss"))
        return File(resultPath, "$timeLocal.json")
    }

    // check metadata for known issues, change their status and add info with explanations
    override fun checkMetadata(metadata: Map<PrefsMetadataKey, PrefMetadata>): Map<PrefsMetadataKey, PrefMetadata> {
        val meta = metadata.toMutableMap()

        meta[PrefsMetadataKeyImpl.AAPS_FLAVOUR]?.let { flavour ->
            val flavourOfPrefs = flavour.value
            if (flavour.value != config.get().FLAVOR) {
                flavour.status = PrefsStatusImpl.WARN
                flavour.info = rh.gs(R.string.metadata_warning_different_flavour, flavourOfPrefs, config.get().FLAVOR)
            }
        }

        meta[PrefsMetadataKeyImpl.DEVICE_MODEL]?.let { model ->
            if (model.value != config.get().currentDeviceModelString) {
                model.status = PrefsStatusImpl.WARN
                model.info = rh.gs(R.string.metadata_warning_different_device)
            }
        }

        meta[PrefsMetadataKeyImpl.CREATED_AT]?.let { createdAt ->
            try {
                val date1 = DateTime.parse(createdAt.value)
                val date2 = DateTime.now()

                val daysOld = Days.daysBetween(date1.toLocalDate(), date2.toLocalDate()).days

                if (daysOld > IMPORT_AGE_NOT_YET_OLD_DAYS) {
                    createdAt.status = PrefsStatusImpl.WARN
                    createdAt.info = rh.gs(R.string.metadata_warning_old_export, daysOld.toString())
                }
            } catch (_: Exception) {
                createdAt.status = PrefsStatusImpl.WARN
                createdAt.info = rh.gs(R.string.metadata_warning_date_format)
            }
        }

        meta[PrefsMetadataKeyImpl.AAPS_VERSION]?.let { version ->
            val currentAppVer = versionCheckerUtils.versionDigits(config.get().VERSION_NAME)
            val metadataVer = versionCheckerUtils.versionDigits(version.value)

            if ((currentAppVer.size >= 2) && (metadataVer.size >= 2) && (abs(currentAppVer[1] - metadataVer[1]) > 1)) {
                version.status = PrefsStatusImpl.WARN
                version.info = rh.gs(R.string.metadata_warning_different_version)
            }

            if ((currentAppVer.isNotEmpty()) && (metadataVer.isNotEmpty()) && (currentAppVer[0] != metadataVer[0])) {
                version.status = PrefsStatusImpl.WARN
                version.info = rh.gs(R.string.metadata_urgent_different_version)
            }
        }

        return meta
    }

    override fun formatExportedAgo(utcTime: String): String {
        val refTime = DateTime.now()
        val itTime = DateTime.parse(utcTime)
        val days = Days.daysBetween(itTime, refTime).days
        val hours = Hours.hoursBetween(itTime, refTime).hours

        return if (hours == 0) {
            rh.gs(R.string.exported_less_than_hour_ago)
        } else if ((hours < 24) && (hours > 0)) {
            rh.gs(R.string.exported_ago, rh.gq(app.aaps.core.ui.R.plurals.hours, hours, hours))
        } else if ((days < IMPORT_AGE_NOT_YET_OLD_DAYS) && (days > 0)) {
            rh.gs(R.string.exported_ago, rh.gq(app.aaps.core.ui.R.plurals.days, days, days))
        } else {
            rh.gs(R.string.exported_at, utcTime.substring(0, 10))
        }
    }

}