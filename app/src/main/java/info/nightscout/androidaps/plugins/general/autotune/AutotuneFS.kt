package info.nightscout.androidaps.plugins.general.autotune

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.plugins.general.autotune.data.ATProfile
import info.nightscout.androidaps.plugins.general.autotune.data.PreppedGlucose
import info.nightscout.androidaps.plugins.general.maintenance.LoggerUtils
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.json.JSONException
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

//@Singleton
class AutotuneFS @Inject constructor(private val injector: HasAndroidInjector) {
    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var  sp: SP
    @Inject lateinit var  autotunePlugin: AutotunePlugin
    @Inject lateinit var  dateUtil: DateUtil
    @Inject lateinit var  resourceHelper: ResourceHelper

    val logDirectory = LoggerUtils.getLogDirectory()
    val AUTOTUNEFOLDER = "autotune"
    val SETTINGSFOLDER = "settings"
    val RECOMMENDATIONS = "autotune_recommendations.log"
    val ENTRIES_PREF = "aaps-entries."
    val TREATMENTS_PREF = "aaps-treatments."
    val PREPPED_PREF = "aaps-autotune."
    val SETTINGS = "settings.json"
    val PROFIL = "profil"
    val PUMPPROFILE = "pumpprofile.json"
    val TUNEDPROFILE = "newaapsprofile."
    val LOG_PREF = "autotune."
    val ZIP_PREF = "autotune_"
    var autotune_path: File? = null
    var autotune_settings: File? = null


    /*****************************************************************************
     * Create autotune folder for all files created during an autotune session
     */
    fun createAutotuneFolder() {
        //create autotune subfolder for autotune files if not exists
        autotune_path = File(logDirectory, AUTOTUNEFOLDER)
        if (!(autotune_path!!.exists() && autotune_path!!.isDirectory)) {
            autotune_path!!.mkdir()
            log("Create $AUTOTUNEFOLDER subfolder in $logDirectory")
        }
        autotune_settings = File(logDirectory, SETTINGSFOLDER)
        if (!(autotune_settings!!.exists() && autotune_settings!!.isDirectory)) {
            autotune_settings!!.mkdir()
            log("Create $SETTINGSFOLDER subfolder in $logDirectory")
        }
    }

    /*****************************************************************************
     * between each run of autotune, clean autotune folder content
     */
    fun deleteAutotuneFiles() {
        for (file in autotune_path!!.listFiles()) {
            if (file.isFile) file.delete()
        }
        for (file in autotune_settings!!.listFiles()) {
            if (file.isFile) file.delete()
        }
        log("Delete previous Autotune files")
    }

    /*****************************************************************************
     * Create a JSON autotune files or settings files
     */
    fun exportSettings(settings: String) {
        createAutotunefile(SETTINGS, settings, true)
    }

    fun exportPumpProfile(profile: ATProfile) {
        createAutotunefile(PUMPPROFILE, profile.profiletoOrefJSON(), true)
        createAutotunefile(PUMPPROFILE, profile.profiletoOrefJSON())
    }

    fun exportTunedProfile(tunedProfile: ATProfile) {
        createAutotunefile(TUNEDPROFILE + formatDate(Date(tunedProfile.from)) + ".json", tunedProfile.profiletoOrefJSON())
        try {
            createAutotunefile(resourceHelper!!.gs(R.string.autotune_tunedprofile_name) + ".json", tunedProfile.data.toString(2).replace("\\/", "/"), true)
        } catch (e: JSONException) {
        }
    }

    fun exportEntries(autotuneIob: AutotuneIob) {
        try {
            createAutotunefile(ENTRIES_PREF + formatDate(Date(autotuneIob.startBG)) + ".json", autotuneIob.glucosetoJSON().toString(2).replace("\\/", "/"))
        } catch (e: JSONException) {
        }
    }

    fun exportTreatments(autotuneIob: AutotuneIob) {
        try {
            createAutotunefile(TREATMENTS_PREF + formatDate(Date(autotuneIob.startBG)) + ".json", autotuneIob.nsHistorytoJSON().toString(2).replace("\\/", "/"))
        } catch (e: JSONException) {
        }
    }

    fun exportPreppedGlucose(preppedGlucose: PreppedGlucose) {
        createAutotunefile(PREPPED_PREF + formatDate(Date(preppedGlucose.from)) + ".json", preppedGlucose.toString(2))
    }

    fun exportResult(result: String) {
        createAutotunefile(RECOMMENDATIONS, result)
    }

    fun exportLogAndZip(lastRun: Date?, logString: String) {
        log("Create " + LOG_PREF + DateUtil.toISOString(lastRun, "yyyy-MM-dd_HH-mm-ss", null) + ".log" + " file in " + AUTOTUNEFOLDER + " folder")
        createAutotunefile(LOG_PREF + DateUtil.toISOString(lastRun, "yyyy-MM-dd_HH-mm-ss", null) + ".log", logString)
        zipAutotune(lastRun)
    }

    private fun createAutotunefile(fileName: String?, stringFile: String, isSettingFile: Boolean = false) {
        var stringFile = stringFile
        if (fileName != null && !fileName.isEmpty()) {
            if (stringFile.isEmpty()) stringFile = ""
            val autotuneFile = File(if (isSettingFile) autotune_settings!!.absolutePath else autotune_path!!.absolutePath, fileName)
            try {
                val fw = FileWriter(autotuneFile)
                val pw = PrintWriter(fw)
                pw.println(stringFile)
                pw.close()
                fw.close()
                log("Create " + fileName + " file in " + (if (isSettingFile) SETTINGSFOLDER else AUTOTUNEFOLDER) + " folder")
            } catch (e: FileNotFoundException) {
                //log.error("Unhandled exception", e);
            } catch (e: IOException) {
                //log.error("Unhandled exception", e);
            }
        }
    }

    private fun profilName(daterun: Date?): String {
        var strdate = ""
        var prefixe = "aaps-"
        if (daterun != null) {
            prefixe = "aaps-new"
            strdate = "." + formatDate(daterun)
        }
        return "$prefixe$PROFIL$strdate.json"
    }

    /**********************************************************************************
     * create a zip file with all autotune files and settings in autotune folder at the end of run
     */
    fun zipAutotune(lastRun: Date?) {
        if (lastRun != null) {
            try {
                val zipFileName = ZIP_PREF + DateUtil.toISOString(lastRun, "yyyy-MM-dd_HH-mm-ss", null) + ".zip"
                val zipFile = File(logDirectory, zipFileName)
                val out = ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile)))
                if (autotune_path != null) zipDirectory(autotune_path!!, autotune_path!!.name, out)
                if (autotune_settings != null) zipDirectory(autotune_settings!!, autotune_settings!!.name, out)
                out.flush()
                out.close()
                log("Create $zipFileName file in $logDirectory folder")
            } catch (e: IOException) {
            }
        }
    }

    private fun log(message: String) {
        autotunePlugin!!.atLog("[FS] $message")
    }

    companion object {
        const val BUFFER_SIZE = 2048
        private fun zipDirectory(folder: File, parentFolder: String, out: ZipOutputStream) {
            for (file in folder.listFiles()) {
                if (file.isDirectory) {
                    zipDirectory(file, parentFolder + "/" + file.name, out)
                    continue
                }
                try {
                    out.putNextEntry(ZipEntry(parentFolder + "/" + file.name))
                    val bis = BufferedInputStream(FileInputStream(file))
                    //long bytesRead = 0;
                    val bytesIn = ByteArray(BUFFER_SIZE)
                    var read = 0
                    while (bis.read(bytesIn).also { read = it } != -1) {
                        out.write(bytesIn, 0, read)
                        //bytesRead += read;
                    }
                    out.closeEntry()
                } catch (e: IOException) {
                }
            }
        }

        fun formatDate(date: Date?): String {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd")
            return dateFormat.format(date)
        }
    }

    init {
        injector.androidInjector().inject(this)
    }
}