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
    val ENTRIESPREF = "aaps-entries."
    val TREATMENTSPREF = "aaps-treatments."
    val PREPPEDPREF = "aaps-autotune."
    val SETTINGS = "settings.json"
    val PROFIL = "profil"
    val PUMPPROFILE = "pumpprofile.json"
    val TUNEDPROFILE = "newaapsprofile."
    val LOGPREF = "autotune."
    val ZIPPREF = "autotune_"
    var autotunePath: File? = null
    var autotuneSettings: File? = null


    /*****************************************************************************
     * Create autotune folder for all files created during an autotune session
     */
    fun createAutotuneFolder() {
        //create autotune subfolder for autotune files if not exists
        autotunePath = File(logDirectory, AUTOTUNEFOLDER)
        if (!(autotunePath!!.exists() && autotunePath!!.isDirectory)) {
            autotunePath!!.mkdir()
            log("Create $AUTOTUNEFOLDER subfolder in $logDirectory")
        }
        autotuneSettings = File(logDirectory, SETTINGSFOLDER)
        if (!(autotuneSettings!!.exists() && autotuneSettings!!.isDirectory)) {
            autotuneSettings!!.mkdir()
            log("Create $SETTINGSFOLDER subfolder in $logDirectory")
        }
    }

    /*****************************************************************************
     * between each run of autotune, clean autotune folder content
     */
    fun deleteAutotuneFiles() {
        for (file in autotunePath!!.listFiles()) {
            if (file.isFile) file.delete()
        }
        for (file in autotuneSettings!!.listFiles()) {
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
            createAutotunefile(resourceHelper.gs(R.string.autotune_tunedprofile_name) + ".json", tunedProfile.data.toString(2).replace("\\/", "/"), true)
        } catch (e: JSONException) {
        }
    }

    fun exportEntries(autotuneIob: AutotuneIob) {
        try {
            createAutotunefile(ENTRIESPREF + formatDate(Date(autotuneIob.startBG)) + ".json", autotuneIob.glucosetoJSON().toString(2).replace("\\/", "/"))
        } catch (e: JSONException) {
        }
    }

    fun exportTreatments(autotuneIob: AutotuneIob) {
        try {
            createAutotunefile(TREATMENTSPREF + formatDate(Date(autotuneIob.startBG)) + ".json", autotuneIob.nsHistorytoJSON().toString(2).replace("\\/", "/"))
        } catch (e: JSONException) {
        }
    }

    fun exportPreppedGlucose(preppedGlucose: PreppedGlucose) {
        createAutotunefile(PREPPEDPREF + formatDate(Date(preppedGlucose.from)) + ".json", preppedGlucose.toString(2))
    }

    fun exportResult(result: String) {
        createAutotunefile(RECOMMENDATIONS, result)
    }

    fun exportLogAndZip(lastRun: Date?, logString: String) {
        log("Create " + LOGPREF + DateUtil.toISOString(lastRun, "yyyy-MM-dd_HH-mm-ss", null) + ".log" + " file in " + AUTOTUNEFOLDER + " folder")
        createAutotunefile(LOGPREF + DateUtil.toISOString(lastRun, "yyyy-MM-dd_HH-mm-ss", null) + ".log", logString)
        zipAutotune(lastRun)
    }

    private fun createAutotunefile(fileName: String?, stringFile: String, isSettingFile: Boolean = false) {
        //var stringFile = stringFile
        if (fileName != null && !fileName.isEmpty()) {
            //if (stringFile.isEmpty()) stringFile = ""
            val autotuneFile = File(if (isSettingFile) autotuneSettings!!.absolutePath else autotunePath!!.absolutePath, fileName)
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
                val zipFileName = ZIPPREF + DateUtil.toISOString(lastRun, "yyyy-MM-dd_HH-mm-ss", null) + ".zip"
                val zipFile = File(logDirectory, zipFileName)
                val out = ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile)))
                if (autotunePath != null) zipDirectory(autotunePath!!, autotunePath!!.name, out)
                if (autotuneSettings != null) zipDirectory(autotuneSettings!!, autotuneSettings!!.name, out)
                out.flush()
                out.close()
                log("Create $zipFileName file in $logDirectory folder")
            } catch (e: IOException) {
            }
        }
    }

    private fun log(message: String) {
        autotunePlugin.atLog("[FS] $message")
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
                    var read: Int
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