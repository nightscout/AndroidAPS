package info.nightscout.configuration.maintenance

import android.content.Context
import android.os.Environment
import dagger.Lazy
import dagger.Reusable
import info.nightscout.androidaps.annotations.OpenForTesting
import info.nightscout.configuration.maintenance.formats.EncryptedPrefsFormat
import info.nightscout.core.main.R
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.maintenance.PrefFileListProvider
import info.nightscout.interfaces.maintenance.PrefMetadata
import info.nightscout.interfaces.maintenance.PrefMetadataMap
import info.nightscout.interfaces.maintenance.PrefsFile
import info.nightscout.interfaces.maintenance.PrefsImportDir
import info.nightscout.interfaces.maintenance.PrefsMetadataKey
import info.nightscout.interfaces.maintenance.PrefsStatus
import info.nightscout.interfaces.storage.Storage
import info.nightscout.interfaces.versionChecker.VersionCheckerUtils
import info.nightscout.shared.interfaces.ResourceHelper
import org.joda.time.DateTime
import org.joda.time.Days
import org.joda.time.Hours
import org.joda.time.LocalDateTime
import org.joda.time.format.DateTimeFormat
import java.io.File
import javax.inject.Inject
import kotlin.math.abs

@Suppress("SpellCheckingInspection")
@OpenForTesting
@Reusable
class PrefFileListProviderImpl @Inject constructor(
    private val rh: ResourceHelper,
    private val config: Lazy<Config>,
    private val encryptedPrefsFormat: EncryptedPrefsFormat,
    private val storage: Storage,
    private val versionCheckerUtils: VersionCheckerUtils,
    context: Context
) : PrefFileListProvider {
    private val path = File(Environment.getExternalStorageDirectory().toString())
    private val aapsPath = File(path, "AAPS" + File.separator + "preferences")
    private val exportsPath = File(path, "AAPS" + File.separator + "exports")
    private val tempPath = File(path, "AAPS" + File.separator + "temp")
    private val extraPath = File(path, "AAPS" + File.separator + "extra")
    override val logsPath: String = File(path, "AAPS" + File.separator + "logs" + File.separator + context.packageName).absolutePath

    companion object {

        private const val IMPORT_AGE_NOT_YET_OLD_DAYS = 60
    }

    /**
     * This function tries to list possible preference files from main SDCard root dir and AAPS/preferences dir
     * and tries to do quick assessment for preferences format plausibility.
     * It does NOT load full metadata or is 100% accurate - it tries to do QUICK detection, based on:
     *  - file name and extension
     *  - predicted file contents
     */
    override fun listPreferenceFiles(loadMetadata: Boolean): MutableList<PrefsFile> {
        val prefFiles = mutableListOf<PrefsFile>()

        // searching rood dir for legacy files
        path.walk().maxDepth(1).filter { it.isFile && (it.name.endsWith(".json") || it.name.contains("Preferences")) }.forEach {
            val contents = storage.getFileContents(it)
            val detectedNew = encryptedPrefsFormat.isPreferencesFile(it, contents)
            if (detectedNew) {
                prefFiles.add(PrefsFile(it.name, it, path, PrefsImportDir.ROOT_DIR, metadataFor(loadMetadata, contents)))
            }
        }

        // searching dedicated dir, only for new JSON format
        aapsPath.walk().filter { it.isFile && it.name.endsWith(".json") }.forEach {
            val contents = storage.getFileContents(it)
            if (encryptedPrefsFormat.isPreferencesFile(it, contents)) {
                prefFiles.add(PrefsFile(it.name, it, aapsPath, PrefsImportDir.AAPS_DIR, metadataFor(loadMetadata, contents)))
            }
        }

        // we sort only if we have metadata to be used for that
        if (loadMetadata) {
            prefFiles.sortWith(
                compareByDescending<PrefsFile> { it.metadata[PrefsMetadataKey.AAPS_FLAVOUR]?.status }
                    .thenByDescending { it.metadata[PrefsMetadataKey.CREATED_AT]?.value }
            )
        }

        return prefFiles
    }

    private fun metadataFor(loadMetadata: Boolean, contents: String): PrefMetadataMap {
        if (!loadMetadata) {
            return mapOf()
        }
        return checkMetadata(encryptedPrefsFormat.loadMetadata(contents))
    }

    override fun ensureExportDirExists(): File {
        if (!aapsPath.exists()) {
            aapsPath.mkdirs()
        }
        if (!exportsPath.exists()) {
            exportsPath.mkdirs()
        }
        return exportsPath
    }

    override fun ensureTempDirExists(): File {
        if (!tempPath.exists()) {
            tempPath.mkdirs()
        }
        return tempPath
    }

    override fun ensureExtraDirExists(): File {
        if (!extraPath.exists()) {
            extraPath.mkdirs()
        }
        return extraPath
    }

    override fun newExportFile(): File {
        val timeLocal = LocalDateTime.now().toString(DateTimeFormat.forPattern("yyyy-MM-dd'_'HHmmss"))
        return File(aapsPath, timeLocal + "_" + config.get().FLAVOR + ".json")
    }

    override fun newExportCsvFile(): File {
        val timeLocal = LocalDateTime.now().toString(DateTimeFormat.forPattern("yyyy-MM-dd'_'HHmmss"))
        return File(exportsPath, timeLocal + "_UserEntry.csv")
    }

    // check metadata for known issues, change their status and add info with explanations
    override fun checkMetadata(metadata: Map<PrefsMetadataKey, PrefMetadata>): Map<PrefsMetadataKey, PrefMetadata> {
        val meta = metadata.toMutableMap()

        meta[PrefsMetadataKey.AAPS_FLAVOUR]?.let { flavour ->
            val flavourOfPrefs = flavour.value
            if (flavour.value != config.get().FLAVOR) {
                flavour.status = PrefsStatus.WARN
                flavour.info = rh.gs(R.string.metadata_warning_different_flavour, flavourOfPrefs, config.get().FLAVOR)
            }
        }

        meta[PrefsMetadataKey.DEVICE_MODEL]?.let { model ->
            if (model.value != config.get().currentDeviceModelString) {
                model.status = PrefsStatus.WARN
                model.info = rh.gs(R.string.metadata_warning_different_device)
            }
        }

        meta[PrefsMetadataKey.CREATED_AT]?.let { createdAt ->
            try {
                val date1 = DateTime.parse(createdAt.value)
                val date2 = DateTime.now()

                val daysOld = Days.daysBetween(date1.toLocalDate(), date2.toLocalDate()).days

                if (daysOld > IMPORT_AGE_NOT_YET_OLD_DAYS) {
                    createdAt.status = PrefsStatus.WARN
                    createdAt.info = rh.gs(R.string.metadata_warning_old_export, daysOld.toString())
                }
            } catch (e: Exception) {
                createdAt.status = PrefsStatus.WARN
                createdAt.info = rh.gs(R.string.metadata_warning_date_format)
            }
        }

        meta[PrefsMetadataKey.AAPS_VERSION]?.let { version ->
            val currentAppVer = versionCheckerUtils.versionDigits(config.get().VERSION_NAME)
            val metadataVer = versionCheckerUtils.versionDigits(version.value)

            if ((currentAppVer.size >= 2) && (metadataVer.size >= 2) && (abs(currentAppVer[1] - metadataVer[1]) > 1)) {
                version.status = PrefsStatus.WARN
                version.info = rh.gs(R.string.metadata_warning_different_version)
            }

            if ((currentAppVer.isNotEmpty()) && (metadataVer.isNotEmpty()) && (currentAppVer[0] != metadataVer[0])) {
                version.status = PrefsStatus.WARN
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
            rh.gs(R.string.exported_ago, rh.gq(R.plurals.hours, hours, hours))
        } else if ((days < IMPORT_AGE_NOT_YET_OLD_DAYS) && (days > 0)) {
            rh.gs(R.string.exported_ago, rh.gq(R.plurals.days, days, days))
        } else {
            rh.gs(R.string.exported_at, utcTime.substring(0, 10))
        }
    }

}