package app.aaps.implementation.maintenance.cloud

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.maintenance.CloudDirectoryInfo
import app.aaps.core.interfaces.maintenance.CloudDirectoryManager
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.icons.IcGoogleDrive
import app.aaps.implementation.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudDirectoryManagerImpl @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val preferences: Preferences,
    private val cloudStorageManager: CloudStorageManager
) : CloudDirectoryManager {

    override fun getCloudDirectoryInfo(): CloudDirectoryInfo {
        val provider = cloudStorageManager.getProvider(StorageTypes.GOOGLE_DRIVE)
        val hasCredentials = provider?.hasValidCredentials() == true
        val hasConnectionError = cloudStorageManager.hasConnectionError()
        val isCloudActive = cloudStorageManager.isCloudStorageActive()

        val authorizedStatusText = when {
            !hasCredentials    -> ""
            hasConnectionError -> provider.let { rh.gs(it.reAuthRequiredTextResId) }
            else               -> provider.let { rh.gs(it.authorizedTextResId) }
        }

        return CloudDirectoryInfo(
            isCloudActive = isCloudActive,
            hasCredentials = hasCredentials,
            hasConnectionError = hasConnectionError,
            providerDisplayName = provider?.displayName ?: rh.gs(R.string.storage_google_drive),
            providerDescription = rh.gs(R.string.backup_to_google_drive),
            providerIcon = provider?.icon ?: IcGoogleDrive,
            authorizedStatusText = authorizedStatusText,
            cloudPath = CloudConstants.CLOUD_PATH_EXPORT
        )
    }

    override fun clearCloudSettings() {
        cloudStorageManager.clearAllCredentials()
        cloudStorageManager.setActiveStorageType(StorageTypes.LOCAL)
        cloudStorageManager.clearConnectionError()
        resetToLocalSettings()
    }

    override fun resetExportToLocal() = resetToLocalSettings()

    override fun enableAllCloudExport() {
        aapsLogger.info(LTag.CORE, "${CloudConstants.LOG_PREFIX} ExportDestination: Enabling all cloud mode")
        preferences.put(BooleanNonKey.ExportAllCloudEnabled, true)
        preferences.put(BooleanNonKey.ExportLogEmailEnabled, false)
        preferences.put(BooleanNonKey.ExportLogCloudEnabled, true)
        preferences.put(BooleanNonKey.ExportSettingsLocalEnabled, true)
        preferences.put(BooleanNonKey.ExportSettingsCloudEnabled, true)
        preferences.put(BooleanNonKey.ExportCsvLocalEnabled, false)
        preferences.put(BooleanNonKey.ExportCsvCloudEnabled, true)
    }

    override fun enableLocalStorage() {
        aapsLogger.info(LTag.CORE, "${CloudConstants.LOG_PREFIX} ExportDestination: Enabling local storage")
        preferences.put(BooleanNonKey.ExportSettingsLocalEnabled, true)
    }

    private fun resetToLocalSettings() {
        aapsLogger.info(LTag.CORE, "${CloudConstants.LOG_PREFIX} ExportDestination: Resetting all settings to local/email mode")
        preferences.put(BooleanNonKey.ExportAllCloudEnabled, false)
        preferences.put(BooleanNonKey.ExportLogEmailEnabled, true)
        preferences.put(BooleanNonKey.ExportLogCloudEnabled, false)
        preferences.put(BooleanNonKey.ExportSettingsLocalEnabled, true)
        preferences.put(BooleanNonKey.ExportSettingsCloudEnabled, false)
        preferences.put(BooleanNonKey.ExportCsvLocalEnabled, true)
        preferences.put(BooleanNonKey.ExportCsvCloudEnabled, false)
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
