package app.aaps.implementation.maintenance

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LoggerUtils
import app.aaps.core.interfaces.maintenance.ExportResult
import app.aaps.core.interfaces.maintenance.FileListProvider
import app.aaps.core.interfaces.maintenance.Maintenance
import app.aaps.core.interfaces.nsclient.NSSettingsStatus
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.implementation.maintenance.cloud.CloudConstants
import app.aaps.implementation.maintenance.cloud.CloudStorageManager
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.Arrays
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MaintenanceImpl @Inject constructor(
    private val context: Context,
    private val rh: ResourceHelper,
    private val preferences: Preferences,
    private val nsSettingsStatus: NSSettingsStatus,
    private val aapsLogger: AAPSLogger,
    private val config: Config,
    private val fileListProvider: FileListProvider,
    private val loggerUtils: LoggerUtils,
    private val cloudStorageManager: CloudStorageManager,
    private val sp: SP
) : Maintenance {

    override suspend fun executeSendLogs(): ExportResult {
        val amount = preferences.get(IntKey.MaintenanceLogsAmount)
        val logs = getLogFiles(amount)
        val zipFile = fileListProvider.ensureTempDirExists()?.createFile("application/zip", constructName())
            ?: return ExportResult(localSuccess = false)
        val zip = zipLogs(zipFile, logs)

        val logEmail = sp.getBoolean(ExportPrefKeys.PREF_LOG_EMAIL_ENABLED, true)
        val logCloud = sp.getBoolean(ExportPrefKeys.PREF_LOG_CLOUD_ENABLED, false)
        val isCloudActive = cloudStorageManager.isCloudStorageActive()
        val cloudEnabled = logCloud && isCloudActive

        var emailSuccess: Boolean? = null
        var cloudSuccess: Boolean? = null

        if (logEmail || !cloudEnabled) {
            emailSuccess = try {
                val recipient = preferences.get(StringKey.MaintenanceEmail)
                val emailIntent = sendMail(zip.uri, recipient, "Log Export")
                context.startActivity(emailIntent)
                true
            } catch (e: Exception) {
                aapsLogger.error("Failed to launch email intent", e)
                false
            }
        }

        if (cloudEnabled) {
            cloudSuccess = performCloudLogUpload(zip)
        }

        return ExportResult(localSuccess = emailSuccess, cloudSuccess = cloudSuccess)
    }

    override fun deleteLogs(keep: Int) {
        val logDir = File(loggerUtils.logDirectory)
        val files = logDir.listFiles { _: File?, name: String ->
            (name.startsWith("AndroidAPS") && name.endsWith(".zip"))
        }
        val autotuneFiles = logDir.listFiles { _: File?, name: String ->
            (name.startsWith("autotune") && name.endsWith(".zip"))
        }
        val keepIndex = keep - 1
        if (autotuneFiles != null && autotuneFiles.isNotEmpty()) {
            Arrays.sort(autotuneFiles) { f1: File, f2: File -> f2.name.compareTo(f1.name) }
            var delAutotuneFiles = listOf(*autotuneFiles)
            if (keepIndex < delAutotuneFiles.size) {
                delAutotuneFiles = delAutotuneFiles.subList(keepIndex, delAutotuneFiles.size)
                for (file in delAutotuneFiles) {
                    file.delete()
                }
            }
        }
        if (files == null || files.isEmpty()) return
        Arrays.sort(files) { f1: File, f2: File -> f2.name.compareTo(f1.name) }
        var delFiles = listOf(*files)
        if (keepIndex < delFiles.size) {
            delFiles = delFiles.subList(keepIndex, delFiles.size)
            for (file in delFiles) {
                file.delete()
            }
        }
        val exportDir = fileListProvider.ensureTempDirExists()
        exportDir?.listFiles()?.let { expFiles ->
            for (file in expFiles) file.delete()
        }
    }

    private suspend fun performCloudLogUpload(zipFile: DocumentFile): Boolean {
        return try {
            val provider = cloudStorageManager.getActiveProvider() ?: return false
            val bytes = context.contentResolver.openInputStream(zipFile.uri)?.use { it.readBytes() } ?: return false
            provider.getOrCreateFolderPath(CloudConstants.CLOUD_PATH_LOGS)?.let { provider.setSelectedFolderId(it) }
            var uploadedFileId = provider.uploadFileToPath(zipFile.name ?: "logs.zip", bytes, "application/zip", CloudConstants.CLOUD_PATH_LOGS)
            if (uploadedFileId == null) {
                uploadedFileId = provider.uploadFile(zipFile.name ?: "logs.zip", bytes, "application/zip")
            }
            uploadedFileId != null
        } catch (e: Exception) {
            aapsLogger.error("Cloud log upload failed", e)
            false
        }
    }

    /**
     * Returns a list of log files. The number of returned logs is given via the amount parameter.
     * The log files are sorted by the name descending.
     */
    internal fun getLogFiles(amount: Int): List<File> {
        aapsLogger.debug("getting $amount logs from directory ${loggerUtils.logDirectory}")
        val logDir = File(loggerUtils.logDirectory)
        val files = logDir.listFiles { _: File?, name: String ->
            (name.startsWith("AndroidAPS")
                && (name.endsWith(".log")
                || name.endsWith(".zip") && !name.endsWith(loggerUtils.suffix)))
        } ?: emptyArray()
        Arrays.sort(files) { f1: File, f2: File -> f2.name.compareTo(f1.name) }
        val result = listOf(*files)
        val toIndex = if (amount > result.size) result.size else amount
        return result.subList(0, toIndex)
    }

    private fun zipLogs(zipFile: DocumentFile, files: List<File>): DocumentFile {
        aapsLogger.debug("creating zip ${zipFile.name}")
        try {
            zip(zipFile, files)
        } catch (e: IOException) {
            aapsLogger.error("Cannot retrieve zip", e)
        }
        return zipFile
    }

    /** AndroidAPS_LOG_ + Long Time + .log.zip */
    private fun constructName(): String =
        "AndroidAPS_LOG_" + System.currentTimeMillis() + loggerUtils.suffix

    private fun zip(zipFile: DocumentFile, files: List<File>) {
        val bufferSize = 2048
        val out = ZipOutputStream(BufferedOutputStream(FileOutputStream(context.contentResolver.openFileDescriptor(zipFile.uri, "w")?.fileDescriptor)))
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
        builder.append(rh.gs(config.appName) + " " + config.VERSION + System.lineSeparator())
        if (config.AAPSCLIENT) builder.append("NSCLIENT" + System.lineSeparator())
        builder.append("Build: " + config.BUILD_VERSION + System.lineSeparator())
        builder.append("Remote: " + config.REMOTE + System.lineSeparator())
        builder.append("Flavor: " + config.FLAVOR + config.BUILD_TYPE + System.lineSeparator())
        builder.append(rh.gs(app.aaps.core.ui.R.string.configbuilder_nightscoutversion_label) + " " + nsSettingsStatus.getVersion() + System.lineSeparator())
        if (config.isEngineeringMode()) builder.append(rh.gs(app.aaps.core.ui.R.string.engineering_mode_enabled))
        val body = builder.toString()
        aapsLogger.debug("sending email to $recipient with subject $subject")
        val emailIntent = Intent(Intent.ACTION_SEND)
        emailIntent.type = "text/plain"
        emailIntent.putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject)
        emailIntent.putExtra(Intent.EXTRA_TEXT, body)
        emailIntent.putExtra(Intent.EXTRA_STREAM, attachmentUri)
        emailIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return emailIntent
    }
}
