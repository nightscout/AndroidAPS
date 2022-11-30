package info.nightscout.plugins.general.autotune

import info.nightscout.androidaps.annotations.OpenForTesting
import info.nightscout.interfaces.logging.LoggerUtils
import info.nightscout.plugins.aps.R
import info.nightscout.plugins.general.autotune.data.ATProfile
import info.nightscout.plugins.general.autotune.data.PreppedGlucose
import info.nightscout.shared.interfaces.ResourceHelper
import org.json.JSONException
import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OpenForTesting
class AutotuneFS @Inject constructor(
    private val  rh: ResourceHelper,
    private val loggerUtils: LoggerUtils
    ) {

    val AUTOTUNEFOLDER = "autotune"
    val SETTINGSFOLDER = "settings"
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
    lateinit var autotunePath: File
    lateinit var autotuneSettings: File
    private var logString = ""
    val BUFFER_SIZE = 2048
    private val log = LoggerFactory.getLogger(AutotunePlugin::class.java)

    /*****************************************************************************
     * Create autotune folder for all files created during an autotune session
     *****************************************************************************/
    fun createAutotuneFolder() {
        //create autotune subfolder for autotune files if not exists
        autotunePath = File(loggerUtils.logDirectory, AUTOTUNEFOLDER)
        if (!(autotunePath.exists() && autotunePath.isDirectory)) {
            autotunePath.mkdir()
            log("Create $AUTOTUNEFOLDER subfolder in ${loggerUtils.logDirectory}")
        }
        autotuneSettings = File(loggerUtils.logDirectory, SETTINGSFOLDER)
        if (!(autotuneSettings.exists() && autotuneSettings.isDirectory)) {
            autotuneSettings.mkdir()
            log("Create $SETTINGSFOLDER subfolder in ${loggerUtils.logDirectory}")
        }
    }

    /*****************************************************************************
     * between each run of autotune, clean autotune folder content
     *****************************************************************************/
    fun deleteAutotuneFiles() {
        autotunePath.listFiles()?.let { listFiles ->
            for (file in listFiles) {
                if (file.isFile) file.delete()
            }
        }
        autotuneSettings.listFiles()?.let { listFiles ->
            for (file in listFiles) {
                if (file.isFile) file.delete()
            }
        }
        log("Delete previous Autotune files")
    }

    /*****************************************************************************
     * Create a JSON autotune files or settings files
     *****************************************************************************/
    fun exportSettings(settings: String) {
        createAutotunefile(SETTINGS, settings, true)
    }

    fun exportPumpProfile(profile: ATProfile) {
        createAutotunefile(PUMPPROFILE, profile.profiletoOrefJSON(), true)
        createAutotunefile(PUMPPROFILE, profile.profiletoOrefJSON())
    }

    fun exportTunedProfile(tunedProfile: ATProfile) {
        createAutotunefile(TUNEDPROFILE + formatDate(tunedProfile.from) + ".json", tunedProfile.profiletoOrefJSON())
        try {
            createAutotunefile(rh.gs(R.string.autotune_tunedprofile_name) + ".json", tunedProfile.profiletoOrefJSON(), true)
        } catch (e: JSONException) {
        }
    }

    fun exportEntries(autotuneIob: AutotuneIob) {
        try {
            createAutotunefile(ENTRIESPREF + formatDate(autotuneIob.startBG) + ".json", autotuneIob.glucoseToJSON())
        } catch (e: JSONException) {
        }
    }

    fun exportTreatments(autotuneIob: AutotuneIob) {
        try {
            createAutotunefile(TREATMENTSPREF + formatDate(autotuneIob.startBG) + ".json", autotuneIob.nsHistoryToJSON())
            createAutotunefile(AAPSBOLUSESPREF + formatDate(autotuneIob.startBG) + ".json", autotuneIob.bolusesToJSON())
        } catch (e: JSONException) {
        }
    }

    fun exportPreppedGlucose(preppedGlucose: PreppedGlucose) {
        createAutotunefile(PREPPEDPREF + formatDate(preppedGlucose.from) + ".json", preppedGlucose.toString(2))
    }

    fun exportResult(result: String) {
        createAutotunefile(RECOMMENDATIONS, result)
    }

    fun exportLog(lastRun: Long, index: Int = 0) {
        val suffix = if (index == 0) "" else "_" + index
        log("Create " + LOGPREF + formatDate(lastRun) + suffix + ".log" + " file in " + AUTOTUNEFOLDER + " folder")
        createAutotunefile(LOGPREF + formatDate(lastRun) + suffix + ".log", logString)
        logString = ""
    }

    fun exportLogAndZip(lastRun: Long) {
        log("Create " + LOGPREF + formatDate(lastRun) + ".log" + " file in " + AUTOTUNEFOLDER + " folder")
        createAutotunefile(LOGPREF + formatDate(lastRun) + ".log", logString)
        zipAutotune(lastRun)
        logString = ""
    }

    private fun createAutotunefile(fileName: String, stringFile: String, isSettingFile: Boolean = false) {
        val autotuneFile = File(if (isSettingFile) autotuneSettings.absolutePath else autotunePath.absolutePath, fileName)
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

    /**********************************************************************************
     * create a zip file with all autotune files and settings in autotune folder at the end of run
     **********************************************************************************/
    fun zipAutotune(lastRun: Long) {
        try {
            val zipFileName = ZIPPREF + formatDate(lastRun, true) + ".zip"
            val zipFile = File(loggerUtils.logDirectory, zipFileName)
            val out = ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile)))
            zipDirectory(autotunePath, autotunePath.name, out)
            zipDirectory(autotuneSettings, autotuneSettings.name, out)
            out.flush()
            out.close()
            log("Create $zipFileName file in ${loggerUtils.logDirectory} folder")
        } catch (e: IOException) {
            //log.error("Unhandled exception", e);
        }
    }

    private fun log(message: String) {
        atLog("[FS] $message")
    }

    fun atLog(message: String) {
        logString += "$message\n"
        log.debug(message)
    }

    private fun zipDirectory(folder: File, parentFolder: String, out: ZipOutputStream) {
        folder.listFiles()?.let { listFiles ->
            for (file in listFiles) {
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
                    }
                    out.closeEntry()
                } catch (e: IOException) {
                    //log.error("Unhandled exception", e);
                }
            }
        }
    }

    private fun formatDate(date: Long, dateHour: Boolean = false): String {
        val dateFormat = if (dateHour) SimpleDateFormat("yyyy-MM-dd_HH-mm-ss") else SimpleDateFormat("yyyy-MM-dd")
        return dateFormat.format(date)
    }
}