package app.aaps.plugins.configuration.maintenance.cloud.providers.googledrive

import android.content.Context
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.plugins.configuration.R
import app.aaps.plugins.configuration.maintenance.cloud.CloudConstants
import app.aaps.plugins.configuration.maintenance.cloud.CloudFile
import app.aaps.plugins.configuration.maintenance.cloud.CloudFileListResult
import app.aaps.plugins.configuration.maintenance.cloud.CloudFolder
import app.aaps.plugins.configuration.maintenance.cloud.CloudStorageProvider
import app.aaps.plugins.configuration.maintenance.cloud.StorageTypes
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Drive implementation of CloudStorageProvider.
 * 
 * This class adapts GoogleDriveManager to the CloudStorageProvider interface,
 * enabling the unified cloud storage architecture.
 */
@Singleton
class GoogleDriveProvider @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val sp: SP,
    private val rxBus: RxBus,
    private val context: Context,
    private val googleDriveManager: GoogleDriveManager
) : CloudStorageProvider {

    companion object {
        private const val LOG_PREFIX = "[GoogleDriveProvider]"
    }

    private fun gLog(message: String) {
        aapsLogger.info(LTag.CORE, "$LOG_PREFIX $message")
    }

    // ==================== Provider Identity ====================

    override val storageType: String = StorageTypes.GOOGLE_DRIVE

    override val displayName: String
        get() = rh.gs(R.string.storage_google_drive)

    override val iconResId: Int = R.drawable.ic_google_drive

    override val authorizedTextResId: Int = R.string.google_drive_authorized

    override val reAuthRequiredTextResId: Int = R.string.google_drive_reauth_required

    // ==================== Authentication ====================

    override suspend fun startAuth(): String? {
        gLog("startAuth")
        return googleDriveManager.startPKCEAuth()
    }

    override suspend fun completeAuth(authCode: String): Boolean {
        gLog("completeAuth")
        return googleDriveManager.exchangeCodeForTokens(authCode)
    }

    override fun hasValidCredentials(): Boolean {
        return googleDriveManager.hasValidRefreshToken()
    }

    override fun clearCredentials() {
        gLog("clearCredentials")
        googleDriveManager.clearGoogleDriveSettings()
    }

    override suspend fun getValidAccessToken(): String? {
        return googleDriveManager.getValidAccessToken()
    }

    // ==================== Connection ====================

    override suspend fun testConnection(): Boolean {
        gLog("testConnection")
        return googleDriveManager.testConnection()
    }

    override fun hasConnectionError(): Boolean {
        return googleDriveManager.hasConnectionError()
    }

    override fun clearConnectionError() {
        googleDriveManager.clearConnectionError()
    }

    // ==================== Folder Operations ====================

    override suspend fun getOrCreateFolderPath(path: String): String? {
        gLog("getOrCreateFolderPath: $path")
        return googleDriveManager.getOrCreateFolderPath(path)
    }

    override suspend fun createFolder(name: String, parentId: String): String? {
        gLog("createFolder: $name under $parentId")
        return googleDriveManager.createFolder(name, parentId)
    }

    override suspend fun listFolders(parentId: String): List<CloudFolder> {
        gLog("listFolders: $parentId")
        return googleDriveManager.listFolders(parentId).map { driveFolder ->
            CloudFolder(
                id = driveFolder.id,
                name = driveFolder.name
            )
        }
    }

    // ==================== File Operations ====================

    override suspend fun uploadFileToPath(
        fileName: String,
        content: ByteArray,
        mimeType: String,
        path: String
    ): String? {
        gLog("uploadFileToPath: $fileName to $path")
        return googleDriveManager.uploadFileToPath(fileName, content, mimeType, path)
    }

    override suspend fun uploadFile(
        fileName: String,
        content: ByteArray,
        mimeType: String
    ): String? {
        gLog("uploadFile: $fileName")
        return googleDriveManager.uploadFile(fileName, content, mimeType)
    }

    override suspend fun downloadFile(fileId: String): ByteArray? {
        gLog("downloadFile: $fileId")
        return googleDriveManager.downloadFile(fileId)
    }

    override suspend fun listSettingsFiles(
        pageSize: Int,
        pageToken: String?
    ): CloudFileListResult {
        gLog("listSettingsFiles: pageSize=$pageSize")
        val result = googleDriveManager.listSettingsFilesPaged(pageToken, pageSize)
        
        return CloudFileListResult(
            files = result.files.map { driveFile ->
                CloudFile(
                    id = driveFile.id,
                    name = driveFile.name,
                    mimeType = guessMimeType(driveFile.name)
                )
            },
            nextPageToken = result.nextPageToken,
            totalCount = result.totalCount ?: -1
        )
    }

    // ==================== Selected Folder ====================

    override fun getSelectedFolderId(): String {
        return googleDriveManager.getSelectedFolderId()
    }

    override fun setSelectedFolderId(folderId: String) {
        gLog("setSelectedFolderId: $folderId")
        googleDriveManager.setSelectedFolderId(folderId)
    }

    // ==================== OAuth Helpers ====================

    /**
     * Wait for OAuth authorization code (used during PKCE auth flow)
     */
    override suspend fun waitForAuthCode(timeoutMs: Long): String? {
        return googleDriveManager.waitForAuthCode(timeoutMs)
    }

    /**
     * Count settings files in the selected folder
     */
    override suspend fun countSettingsFiles(): Int {
        return googleDriveManager.countSettingsFiles()
    }

    /**
     * Guess MIME type based on file extension
     */
    private fun guessMimeType(fileName: String): String {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".json") -> "application/json"
            lower.endsWith(".csv") -> "text/csv"
            lower.endsWith(".zip") -> "application/zip"
            else -> "application/octet-stream"
        }
    }
}
