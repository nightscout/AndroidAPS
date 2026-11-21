package app.aaps.plugins.aps.autotune

import app.aaps.core.data.time.SystemTimeZone
import app.aaps.core.interfaces.autotune.AutotuneFileStore
import app.aaps.core.interfaces.autotune.AutotuneFolder
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.plugins.aps.ApsStrings
import app.aaps.plugins.aps.autotune.data.ATProfile
import app.aaps.plugins.aps.autotune.data.PreppedGlucose
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * The files of an Autotune run, for comparing it with oref0 autotune on a computer, and the log of the
 * run. The names and the text are decided here; [fileStore] only writes them.
 */
@SingleIn(AppScope::class)
@Inject
class AutotuneFS(
    private val rh: TextResolver,
    private val fileStore: AutotuneFileStore,
    private val aapsLogger: AAPSLogger
) {

    val RECOMMENDATIONS = "autotune_recommendations.log"
    val ENTRIESPREF = "aaps-entries."
    val TREATMENTSPREF = "aaps-treatments."
    val AAPSBOLUSESPREF = "aaps-boluses."
    val PREPPEDPREF = "aaps-autotune."
    val SETTINGS = "settings.json"
    val PUMPPROFILE = "pumpprofile.json"
    val TUNEDPROFILE = "newaapsprofile."
    val LOGPREF = "autotune."
    val ZIPPREF = "autotune_"
    private var logString = ""

    /*****************************************************************************
     * Create autotune folder for all files created during an autotune session
     *****************************************************************************/
    fun createAutotuneFolder() {
        //create autotune subfolder for autotune files if not exists
        for (folder in AutotuneFolder.entries)
            if (fileStore.createFolder(folder)) log("Create ${folder.folderName} subfolder in ${fileStore.directory}")
    }

    /*****************************************************************************
     * between each run of autotune, clean autotune folder content
     *****************************************************************************/
    fun deleteAutotuneFiles() {
        for (folder in AutotuneFolder.entries) fileStore.deleteFiles(folder)
        log("Delete previous Autotune files")
    }

    /*****************************************************************************
     * Create a JSON autotune files or settings files
     *****************************************************************************/
    fun exportSettings(settings: String) {
        createAutotunefile(SETTINGS, settings, true)
    }

    fun exportPumpProfile(profile: ATProfile) {
        createAutotunefile(PUMPPROFILE, profile.profileToOrefJSON(), true)
        createAutotunefile(PUMPPROFILE, profile.profileToOrefJSON())
    }

    fun exportTunedProfile(tunedProfile: ATProfile) {
        createAutotunefile(TUNEDPROFILE + formatDate(tunedProfile.from) + ".json", tunedProfile.profileToOrefJSON())
        createAutotunefile(rh.gs(ApsStrings.autotune_tunedprofile_name) + ".json", tunedProfile.profileToOrefJSON(), true)
    }

    // A value JSON cannot hold leaves the file out, as the JSONException of org.json did.
    fun exportEntries(autotuneIob: AutotuneIob) {
        try {
            createAutotunefile(ENTRIESPREF + formatDate(autotuneIob.startBG) + ".json", autotuneIob.glucoseToJSON())
        } catch (_: NonFiniteNumberException) {
        }
    }

    fun exportTreatments(autotuneIob: AutotuneIob) {
        try {
            createAutotunefile(TREATMENTSPREF + formatDate(autotuneIob.startBG) + ".json", autotuneIob.nsHistoryToJSON())
            createAutotunefile(AAPSBOLUSESPREF + formatDate(autotuneIob.startBG) + ".json", autotuneIob.bolusesToJSON())
        } catch (_: NonFiniteNumberException) {
        }
    }

    fun exportPreppedGlucose(preppedGlucose: PreppedGlucose) {
        createAutotunefile(PREPPEDPREF + formatDate(preppedGlucose.from) + ".json", preppedGlucose.toString(2))
    }

    fun exportResult(result: String) {
        createAutotunefile(RECOMMENDATIONS, result)
    }

    fun exportLog(lastRun: Long, index: Int = 0) {
        val suffix = if (index == 0) "" else "_$index"
        log("Create " + LOGPREF + formatDate(lastRun) + suffix + ".log" + " file in " + AutotuneFolder.RUN.folderName + " folder")
        createAutotunefile(LOGPREF + formatDate(lastRun) + suffix + ".log", logString)
        logString = ""
    }

    fun exportLogAndZip(lastRun: Long) {
        log("Create " + LOGPREF + formatDate(lastRun) + ".log" + " file in " + AutotuneFolder.RUN.folderName + " folder")
        createAutotunefile(LOGPREF + formatDate(lastRun) + ".log", logString)
        zipAutotune(lastRun)
        logString = ""
    }

    private fun createAutotunefile(fileName: String, stringFile: String, isSettingFile: Boolean = false) {
        val folder = if (isSettingFile) AutotuneFolder.SETTINGS else AutotuneFolder.RUN
        if (fileStore.write(folder, fileName, stringFile))
            log("Create " + fileName + " file in " + folder.folderName + " folder")
    }

    /**********************************************************************************
     * create a zip file with all autotune files and settings in autotune folder at the end of run
     **********************************************************************************/
    fun zipAutotune(lastRun: Long) {
        val zipFileName = ZIPPREF + formatDate(lastRun, true) + ".zip"
        if (fileStore.zip(zipFileName))
            log("Create $zipFileName file in ${fileStore.directory} folder")
    }

    private fun log(message: String) {
        atLog("[FS] $message")
    }

    fun atLog(message: String) {
        logString += "$message\n"
        aapsLogger.debug(LTag.AUTOTUNE, message)
    }

    /** `yyyy-MM-dd`, or `yyyy-MM-dd_HH-mm-ss` with [dateHour], in the phone's time zone. */
    private fun formatDate(date: Long, dateHour: Boolean = false): String {
        val time = Instant.fromEpochMilliseconds(date).toLocalDateTime(SystemTimeZone.current())
        fun two(value: Int) = value.toString().padStart(2, '0')
        val day = time.year.toString().padStart(4, '0') + "-" + two(time.month.number) + "-" + two(time.day)
        return if (dateHour) day + "_" + two(time.hour) + "-" + two(time.minute) + "-" + two(time.second) else day
    }
}
