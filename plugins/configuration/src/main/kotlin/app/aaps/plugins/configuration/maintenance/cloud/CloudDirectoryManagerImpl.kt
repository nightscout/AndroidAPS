package app.aaps.plugins.configuration.maintenance.cloud

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.maintenance.CloudDirectoryInfo
import app.aaps.core.interfaces.maintenance.CloudDirectoryManager
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.plugins.configuration.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudDirectoryManagerImpl @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val cloudStorageManager: CloudStorageManager,
    private val exportOptionsDialog: ExportOptionsDialog
) : CloudDirectoryManager {

    override fun getCloudDirectoryInfo(): CloudDirectoryInfo {
        val provider = cloudStorageManager.getProvider(StorageTypes.GOOGLE_DRIVE)
        val hasCredentials = provider?.hasValidCredentials() == true
        val hasConnectionError = cloudStorageManager.hasConnectionError()
        val isCloudActive = cloudStorageManager.isCloudStorageActive()

        val authorizedStatusText = when {
            !hasCredentials        -> ""
            hasConnectionError     -> provider?.let { rh.gs(it.reAuthRequiredTextResId) } ?: ""
            else                   -> provider?.let { rh.gs(it.authorizedTextResId) } ?: ""
        }

        return CloudDirectoryInfo(
            isCloudActive = isCloudActive,
            hasCredentials = hasCredentials,
            hasConnectionError = hasConnectionError,
            providerDisplayName = provider?.displayName ?: rh.gs(R.string.storage_google_drive),
            providerDescription = rh.gs(R.string.backup_to_google_drive),
            providerIconResId = provider?.iconResId ?: R.drawable.ic_google_drive,
            authorizedStatusText = authorizedStatusText,
            cloudPath = CloudConstants.CLOUD_PATH_EXPORT
        )
    }

    override fun clearCloudSettings() {
        cloudStorageManager.clearAllCredentials()
        cloudStorageManager.setActiveStorageType(StorageTypes.LOCAL)
        cloudStorageManager.clearConnectionError()
        exportOptionsDialog.resetToLocalSettings()
    }

    override fun resetExportToLocal() {
        exportOptionsDialog.resetToLocalSettings()
    }

    override fun enableAllCloudExport() {
        exportOptionsDialog.enableAllCloud()
    }

    override fun enableLocalStorage() {
        exportOptionsDialog.enableLocalStorage()
    }

    override suspend fun testConnection(): Boolean {
        return try {
            val provider = cloudStorageManager.getProvider(StorageTypes.GOOGLE_DRIVE)
            provider?.testConnection() == true
        } catch (e: Exception) {
            aapsLogger.error(LTag.CORE, "${CloudConstants.LOG_PREFIX} testConnection failed", e)
            false
        }
    }

    override suspend fun startAuth(): String? {
        return try {
            val provider = cloudStorageManager.getProvider(StorageTypes.GOOGLE_DRIVE)
            provider?.startAuth()
        } catch (e: Exception) {
            aapsLogger.error(LTag.CORE, "${CloudConstants.LOG_PREFIX} startAuth failed", e)
            null
        }
    }

    override suspend fun waitForAuthCode(timeoutMs: Long): String? {
        return try {
            val provider = cloudStorageManager.getProvider(StorageTypes.GOOGLE_DRIVE)
            provider?.waitForAuthCode(timeoutMs)
        } catch (e: Exception) {
            aapsLogger.error(LTag.CORE, "${CloudConstants.LOG_PREFIX} waitForAuthCode failed", e)
            null
        }
    }

    override suspend fun completeAuth(authCode: String): Boolean {
        return try {
            val provider = cloudStorageManager.getProvider(StorageTypes.GOOGLE_DRIVE)
            if (provider?.completeAuth(authCode) == true) {
                cloudStorageManager.setActiveStorageType(StorageTypes.GOOGLE_DRIVE)
                true
            } else false
        } catch (e: Exception) {
            aapsLogger.error(LTag.CORE, "${CloudConstants.LOG_PREFIX} completeAuth failed", e)
            false
        }
    }

    override suspend fun setupCloudStorage(): Boolean {
        return try {
            val provider = cloudStorageManager.getProvider(StorageTypes.GOOGLE_DRIVE) ?: return false
            cloudStorageManager.setActiveStorageType(StorageTypes.GOOGLE_DRIVE)
            val folderId = provider.getOrCreateFolderPath(CloudConstants.CLOUD_PATH_SETTINGS)
            if (!folderId.isNullOrEmpty()) provider.setSelectedFolderId(folderId)
            true
        } catch (e: Exception) {
            aapsLogger.error(LTag.CORE, "${CloudConstants.LOG_PREFIX} setupCloudStorage failed", e)
            false
        }
    }
}
