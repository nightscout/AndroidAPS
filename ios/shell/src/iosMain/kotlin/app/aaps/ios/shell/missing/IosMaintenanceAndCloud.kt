package app.aaps.ios.shell.missing

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.maintenance.CloudDirectoryInfo
import app.aaps.core.interfaces.maintenance.CloudDirectoryManager
import app.aaps.core.interfaces.maintenance.ExportResult
import app.aaps.core.interfaces.maintenance.Maintenance
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/*
 * Maintenance and cloud export.
 *
 * Both sit on top of things iOS has no version of yet - the log directory and mail composer, and
 * Google Drive through its Android SDK - so both report "nothing here" rather than guessing.
 *
 * Screen usage statistics used to be here too. They moved to `platform` because a client is not
 * meant to collect them at all, which makes an empty answer correct rather than unfinished.
 */

/** Sending logs needs a log directory and a mail composer; neither is wired up on iOS. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosMaintenance @Inject constructor(
    private val aapsLogger: AAPSLogger
) : Maintenance {

    override suspend fun executeSendLogs(): ExportResult =
        aapsLogger.failNotOnIosYet("Maintenance.executeSendLogs")

    override fun deleteLogs(keep: Int) = aapsLogger.notOnIosYet("Maintenance.deleteLogs")
}

/**
 * Cloud export. Reported as inactive with no credentials, which every screen already handles - it is
 * the same state an Android user sees before connecting a cloud account.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosCloudDirectoryManager @Inject constructor(
    private val aapsLogger: AAPSLogger
) : CloudDirectoryManager {

    override fun getCloudDirectoryInfo(): CloudDirectoryInfo {
        aapsLogger.notOnIosYet("CloudDirectoryManager.getCloudDirectoryInfo")
        return CloudDirectoryInfo(
            isCloudActive = false,
            hasCredentials = false,
            hasConnectionError = false,
            providerDisplayName = "",
            providerDescription = "",
            providerIcon = Icons.Default.CloudOff,
            authorizedStatusText = "",
            cloudPath = ""
        )
    }

    override fun clearCloudSettings() = aapsLogger.notOnIosYet("CloudDirectoryManager.clearCloudSettings")
    override fun resetExportToLocal() = aapsLogger.notOnIosYet("CloudDirectoryManager.resetExportToLocal")
    override fun enableAllCloudExport() = aapsLogger.notOnIosYet("CloudDirectoryManager.enableAllCloudExport")
    override fun enableLocalStorage() = aapsLogger.notOnIosYet("CloudDirectoryManager.enableLocalStorage")

    override suspend fun deauthorizeAndClearCloudSettings(): Boolean {
        aapsLogger.notOnIosYet("CloudDirectoryManager.deauthorizeAndClearCloudSettings")
        return false
    }

    override suspend fun testConnection(): Boolean {
        aapsLogger.notOnIosYet("CloudDirectoryManager.testConnection")
        return false
    }

    override suspend fun startAuth(): String? {
        aapsLogger.notOnIosYet("CloudDirectoryManager.startAuth")
        return null
    }

    override suspend fun waitForAuthCode(timeoutMs: Long): String? {
        aapsLogger.notOnIosYet("CloudDirectoryManager.waitForAuthCode")
        return null
    }

    override suspend fun completeAuth(authCode: String): Boolean {
        aapsLogger.notOnIosYet("CloudDirectoryManager.completeAuth")
        return false
    }

    override suspend fun setupCloudStorage(): Boolean {
        aapsLogger.notOnIosYet("CloudDirectoryManager.setupCloudStorage")
        return false
    }
}
