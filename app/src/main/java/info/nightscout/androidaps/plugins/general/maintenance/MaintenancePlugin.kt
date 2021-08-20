package info.nightscout.androidaps.plugins.general.maintenance

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.BuildConfig
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.general.nsclient.data.NSSettingsStatus
import info.nightscout.androidaps.utils.buildHelper.BuildHelper
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import java.io.*
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MaintenancePlugin @Inject constructor(
    injector: HasAndroidInjector,
    private val context: Context,
    resourceHelper: ResourceHelper,
    private val sp: SP,
    private val nsSettingsStatus: NSSettingsStatus,
    aapsLogger: AAPSLogger,
    private val buildHelper: BuildHelper,
    private val config: Config
) : PluginBase(PluginDescription()
    .mainType(PluginType.GENERAL)
    .fragmentClass(MaintenanceFragment::class.java.name)
    .alwaysVisible(false)
    .alwaysEnabled(true)
    .pluginIcon(R.drawable.ic_maintenance)
    .pluginName(R.string.maintenance)
    .shortName(R.string.maintenance_shortname)
    .preferencesId(R.xml.pref_maintenance)
    .description(R.string.description_maintenance),
    aapsLogger, resourceHelper, injector
) {

    fun sendLogs() {
        val recipient = sp.getString(R.string.key_maintenance_logs_email, "logs@androidaps.org")
        val amount = sp.getInt(R.string.key_maintenance_logs_amount, 2)
        val logDirectory = LoggerUtils.getLogDirectory()
        val logs = getLogFiles(logDirectory, amount)
        val zipDir = context.getExternalFilesDir("exports")
        val zipFile = File(zipDir, constructName())
        aapsLogger.debug("zipFile: ${zipFile.absolutePath}")
        val zip = zipLogs(zipFile, logs)
        val attachmentUri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".fileprovider", zip)
        val emailIntent: Intent = this.sendMail(attachmentUri, recipient, "Log Export")
        aapsLogger.debug("sending emailIntent")
        context.startActivity(emailIntent)
    }

    //todo replace this with a call on startup of the application, specifically to remove
    // unnecessary garbage from the log exports
    fun deleteLogs() {
        LoggerUtils.getLogDirectory()?.let { logDirectory ->
            val logDir = File(logDirectory)
            val files = logDir.listFiles { _: File?, name: String ->
                (name.startsWith("AndroidAPS") && name.endsWith(".zip"))
            }
            Arrays.sort(files) { f1: File, f2: File -> f1.name.compareTo(f2.name) }
            var delFiles = listOf(*files)
            val amount = sp.getInt(R.string.key_logshipper_amount, 2)
            val keepIndex = amount - 1
            if (keepIndex < delFiles.size) {
                delFiles = delFiles.subList(keepIndex, delFiles.size)
                for (file in delFiles) {
                    file.delete()
                }
            }
            val exportDir = File(logDirectory, "exports")
            if (exportDir.exists()) {
                val expFiles = exportDir.listFiles()
                for (file in expFiles) {
                    file.delete()
                }
                exportDir.delete()
            }
        }
    }

    /**
     * returns a list of log files. The number of returned logs is given via the amount
     * parameter.
     *
     * The log files are sorted by the name descending.
     *
     * @param directory
     * @param amount
     * @return
     */
    fun getLogFiles(directory: String, amount: Int): List<File> {
        aapsLogger.debug("getting $amount logs from directory $directory")
        val logDir = File(directory)
        val files = logDir.listFiles { _: File?, name: String ->
            (name.startsWith("AndroidAPS")
                && (name.endsWith(".log")
                || name.endsWith(".zip") && !name.endsWith(LoggerUtils.SUFFIX)))
        }
        Arrays.sort(files) { f1: File, f2: File -> f2.name.compareTo(f1.name) }
        val result = listOf(*files)
        var toIndex = amount
        if (toIndex > result.size) {
            toIndex = result.size
        }
        aapsLogger.debug("returning sublist 0 to $toIndex")
        return result.subList(0, toIndex)
    }

    fun zipLogs(zipFile: File, files: List<File>): File {
        aapsLogger.debug("creating zip ${zipFile.absolutePath}")
        try {
            zip(zipFile, files)
        } catch (e: IOException) {
            aapsLogger.error("Cannot retrieve zip", e)
        }
        return zipFile
    }

    /**
     * construct the name of zip file which is used to export logs.
     *
     * The name is constructed using the following scheme:
     * AndroidAPS_LOG_ + Long Time + .log.zip
     *
     * @return
     */
    private fun constructName(): String {
        return "AndroidAPS_LOG_" + Date().time + LoggerUtils.SUFFIX
    }

    private fun zip(zipFile: File?, files: List<File>) {
        val bufferSize = 2048
        val out = ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile)))
        for (file in files) {
            val data = ByteArray(bufferSize)
            FileInputStream(file).use { fileInputStream ->
                BufferedInputStream(fileInputStream, bufferSize).use { origin ->
                    val entry = ZipEntry(file.name)
                    out.putNextEntry(entry)
                    var count: Int
                    while (origin.read(data, 0, bufferSize).also { count = it } != -1) {
                        out.write(data, 0, count)
                    }
                }
            }
        }
        out.close()
    }

    @Suppress("SameParameterValue")
    private fun sendMail(attachmentUri: Uri, recipient: String, subject: String): Intent {
        val builder = StringBuilder()
        builder.append("ADD TIME OF EVENT HERE: " + System.lineSeparator())
        builder.append("ADD ISSUE DESCRIPTION OR GITHUB ISSUE REFERENCE NUMBER: " + System.lineSeparator())
        builder.append("-------------------------------------------------------" + System.lineSeparator())
        builder.append("(Please remember this will send only very recent logs." + System.lineSeparator())
        builder.append("If you want to provide logs for event older than a few hours," + System.lineSeparator())
        builder.append("you have to do it manually)" + System.lineSeparator())
        builder.append("-------------------------------------------------------" + System.lineSeparator())
        builder.append(resourceHelper.gs(R.string.app_name) + " " + BuildConfig.VERSION + System.lineSeparator())
        if (config.NSCLIENT) builder.append("NSCLIENT" + System.lineSeparator())
        builder.append("Build: " + BuildConfig.BUILDVERSION + System.lineSeparator())
        builder.append("Remote: " + BuildConfig.REMOTE + System.lineSeparator())
        builder.append("Flavor: " + BuildConfig.FLAVOR + BuildConfig.BUILD_TYPE + System.lineSeparator())
        builder.append(resourceHelper.gs(R.string.configbuilder_nightscoutversion_label) + " " + nsSettingsStatus.nightscoutVersionName + System.lineSeparator())
        if (buildHelper.isEngineeringMode()) builder.append(resourceHelper.gs(R.string.engineering_mode_enabled))
        return sendMail(attachmentUri, recipient, subject, builder.toString())
    }

    /**
     * send a mail with the given file to the recipients with the given subject.
     *
     * the returned intent should be used to really send the mail using
     *
     * startActivity(Intent.createChooser(emailIntent , "Send email..."));
     *
     * @param attachmentUri
     * @param recipient
     * @param subject
     * @param body
     *
     * @return
     */
    private fun sendMail(attachmentUri: Uri, recipient: String, subject: String, body: String): Intent {
        aapsLogger.debug("sending email to $recipient with subject $subject")
        val emailIntent = Intent(Intent.ACTION_SEND)
        emailIntent.type = "text/plain"
        emailIntent.putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject)
        emailIntent.putExtra(Intent.EXTRA_TEXT, body)
        aapsLogger.debug("put path $attachmentUri")
        emailIntent.putExtra(Intent.EXTRA_STREAM, attachmentUri)
        emailIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return emailIntent
    }

    override fun preprocessPreferences(preferenceFragment: PreferenceFragmentCompat) {
        super.preprocessPreferences(preferenceFragment)
        val encryptSwitch = preferenceFragment.findPreference(resourceHelper.gs(R.string.key_maintenance_encrypt_exported_prefs)) as SwitchPreference?
            ?: return
        encryptSwitch.isVisible = buildHelper.isEngineeringMode()
        encryptSwitch.isEnabled = buildHelper.isEngineeringMode()
    }

}