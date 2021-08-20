package info.nightscout.androidaps.plugins.general.maintenance

import android.os.Environment
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.interfaces.ConfigInterface
import info.nightscout.androidaps.plugins.constraints.versionChecker.VersionCheckerUtils
import info.nightscout.androidaps.plugins.general.maintenance.formats.*
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.storage.Storage
import org.joda.time.DateTime
import org.joda.time.Days
import org.joda.time.Hours
import org.joda.time.LocalDateTime
import org.joda.time.format.DateTimeFormat
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class PrefFileListProvider @Inject constructor(
    private val resourceHelper: ResourceHelper,
    private val config: ConfigInterface,
    private val classicPrefsFormat: ClassicPrefsFormat,
    private val encryptedPrefsFormat: EncryptedPrefsFormat,
    private val storage: Storage,
    private val versionCheckerUtils: VersionCheckerUtils
) {

    companion object {

        private val path = File(Environment.getExternalStorageDirectory().toString())
        private val aapsPath = File(path, "AAPS" + File.separator + "preferences")
        private const val IMPORT_AGE_NOT_YET_OLD_DAYS = 60
    }

    /**
     * This function tries to list possible preference files from main SDCard root dir and AAPS/preferences dir
     * and tries to do quick assessment for preferences format plausibility.
     * It does NOT load full metadata or is 100% accurate - it tries to do QUICK detection, based on:
     *  - file name and extension
     *  - predicted file contents
     */
    fun listPreferenceFiles(loadMetadata: Boolean = false): MutableList<PrefsFile> {
        val prefFiles = mutableListOf<PrefsFile>()

        // searching rood dir for legacy files
        path.walk().maxDepth(1).filter { it.isFile && (it.name.endsWith(".json") || it.name.contains("Preferences")) }.forEach {
            val contents = storage.getFileContents(it)
            val detectedNew = encryptedPrefsFormat.isPreferencesFile(it, contents)
            val detectedOld = !detectedNew && classicPrefsFormat.isPreferencesFile(it, contents)
            if (detectedNew || detectedOld) {
                val formatHandler = if (detectedNew) PrefsFormatsHandler.ENCRYPTED else PrefsFormatsHandler.CLASSIC
                prefFiles.add(PrefsFile(it.name, it, path, PrefsImportDir.ROOT_DIR, formatHandler, metadataFor(loadMetadata, formatHandler, contents)))
            }
        }

        // searching dedicated dir, only for new JSON format
        aapsPath.walk().filter { it.isFile && it.name.endsWith(".json") }.forEach {
            val contents = storage.getFileContents(it)
            if (encryptedPrefsFormat.isPreferencesFile(it, contents)) {
                prefFiles.add(PrefsFile(it.name, it, aapsPath, PrefsImportDir.AAPS_DIR, PrefsFormatsHandler.ENCRYPTED, metadataFor(loadMetadata, PrefsFormatsHandler.ENCRYPTED, contents)))
            }
        }

        // we sort only if we have metadata to be used for that
        if (loadMetadata) {
            prefFiles.sortWith(
                compareByDescending<PrefsFile> { it.handler }
                    .thenBy { it.metadata[PrefsMetadataKey.AAPS_FLAVOUR]?.status }
                    .thenByDescending { it.metadata[PrefsMetadataKey.CREATED_AT]?.value }
            )
        }

        return prefFiles
    }

    private fun metadataFor(loadMetadata: Boolean, formatHandler: PrefsFormatsHandler, contents: String): PrefMetadataMap {
        if (!loadMetadata) {
            return mapOf()
        }
        return checkMetadata(when (formatHandler) {
            PrefsFormatsHandler.CLASSIC   -> classicPrefsFormat.loadMetadata(contents)
            PrefsFormatsHandler.ENCRYPTED -> encryptedPrefsFormat.loadMetadata(contents)
        })
    }

    fun legacyFile(): File {
        return File(path, resourceHelper.gs(R.string.app_name) + "Preferences")
    }

    fun ensureExportDirExists() {
        if (!aapsPath.exists()) {
            aapsPath.mkdirs()
        }
    }

    fun newExportFile(): File {
        val timeLocal = LocalDateTime.now().toString(DateTimeFormat.forPattern("yyyy-MM-dd'_'HHmmss"))
        return File(aapsPath, timeLocal + "_" + config.FLAVOR + ".json")
    }

    // check metadata for known issues, change their status and add info with explanations
    fun checkMetadata(metadata: Map<PrefsMetadataKey, PrefMetadata>): Map<PrefsMetadataKey, PrefMetadata> {
        val meta = metadata.toMutableMap()

        meta[PrefsMetadataKey.AAPS_FLAVOUR]?.let { flavour ->
            val flavourOfPrefs = flavour.value
            if (flavour.value != config.FLAVOR) {
                flavour.status = PrefsStatus.WARN
                flavour.info = resourceHelper.gs(R.string.metadata_warning_different_flavour, flavourOfPrefs, config.FLAVOR)
            }
        }

        meta[PrefsMetadataKey.DEVICE_MODEL]?.let { model ->
            if (model.value != config.currentDeviceModelString) {
                model.status = PrefsStatus.WARN
                model.info = resourceHelper.gs(R.string.metadata_warning_different_device)
            }
        }

        meta[PrefsMetadataKey.CREATED_AT]?.let { createdAt ->
            try {
                val date1 = DateTime.parse(createdAt.value)
                val date2 = DateTime.now()

                val daysOld = Days.daysBetween(date1.toLocalDate(), date2.toLocalDate()).days

                if (daysOld > IMPORT_AGE_NOT_YET_OLD_DAYS) {
                    createdAt.status = PrefsStatus.WARN
                    createdAt.info = resourceHelper.gs(R.string.metadata_warning_old_export, daysOld.toString())
                }
            } catch (e: Exception) {
                createdAt.status = PrefsStatus.WARN
                createdAt.info = resourceHelper.gs(R.string.metadata_warning_date_format)
            }
        }

        meta[PrefsMetadataKey.AAPS_VERSION]?.let { version ->
            val currentAppVer = versionCheckerUtils.versionDigits(config.VERSION_NAME)
            val metadataVer = versionCheckerUtils.versionDigits(version.value)

            if ((currentAppVer.size >= 2) && (metadataVer.size >= 2) && (abs(currentAppVer[1] - metadataVer[1]) > 1)) {
                version.status = PrefsStatus.WARN
                version.info = resourceHelper.gs(R.string.metadata_warning_different_version)
            }

            if ((currentAppVer.isNotEmpty()) && (metadataVer.isNotEmpty()) && (currentAppVer[0] != metadataVer[0])) {
                version.status = PrefsStatus.WARN
                version.info = resourceHelper.gs(R.string.metadata_urgent_different_version)
            }
        }

        return meta
    }

    fun formatExportedAgo(utcTime: String): String {
        val refTime = DateTime.now()
        val itTime = DateTime.parse(utcTime)
        val days = Days.daysBetween(itTime, refTime).days
        val hours = Hours.hoursBetween(itTime, refTime).hours

        return if (hours == 0) {
            resourceHelper.gs(R.string.exported_less_than_hour_ago)
        } else if ((hours < 24) && (hours > 0)) {
            resourceHelper.gs(R.string.exported_ago, resourceHelper.gq(R.plurals.hours, hours, hours))
        } else if ((days < IMPORT_AGE_NOT_YET_OLD_DAYS) && (days > 0)) {
            resourceHelper.gs(R.string.exported_ago, resourceHelper.gq(R.plurals.days, days, days))
        } else {
            resourceHelper.gs(R.string.exported_at, utcTime.substring(0, 10))
        }
    }

}