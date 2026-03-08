package app.aaps.plugins.configuration.maintenance.cloud

/**
 * Abstract interface for cloud storage providers.
 * 
 * This interface defines the contract that all cloud storage providers must implement,
 * enabling easy extension to support additional cloud services like Dropbox, Azure, OneDrive, etc.
 * 
 * Usage:
 * - Implement this interface for each cloud storage provider
 * - Register the implementation in CloudStorageManager
 * - The application code uses CloudStorageManager to interact with the active provider
 */
interface CloudStorageProvider {
    
    /**
     * Unique identifier for this storage type (e.g., "google_drive", "dropbox", "azure_blob")
     */
    val storageType: String
    
    /**
     * Human-readable display name for this provider
     */
    val displayName: String
    
    /**
     * Drawable resource ID for the provider's icon
     */
    val iconResId: Int
    
    /**
     * String resource ID for "authorized" status text (e.g., "Google Drive Authorized")
     */
    val authorizedTextResId: Int
    
    /**
     * String resource ID for "re-authorization required" status text
     */
    val reAuthRequiredTextResId: Int
    
    // ==================== Authentication ====================
    
    /**
     * Start the authentication flow.
     * @return The authorization URL to open in browser, or null if auth is not needed
     */
    suspend fun startAuth(): String?
    
    /**
     * Complete the authentication flow with the received auth code.
     * @param authCode The authorization code received from the OAuth callback
     * @return true if authentication was successful
     */
    suspend fun completeAuth(authCode: String): Boolean
    
    /**
     * Check if there are valid stored credentials.
     * @return true if valid credentials exist
     */
    fun hasValidCredentials(): Boolean
    
    /**
     * Clear all stored credentials and settings.
     */
    fun clearCredentials()
    
    /**
     * Get a valid access token, refreshing if necessary.
     * @return Valid access token or null if unable to obtain one
     */
    suspend fun getValidAccessToken(): String?
    
    // ==================== Connection ====================
    
    /**
     * Test the connection to the cloud service.
     * @return true if connection is successful
     */
    suspend fun testConnection(): Boolean
    
    /**
     * Check if there's a connection error.
     * @return true if there's an active connection error
     */
    fun hasConnectionError(): Boolean
    
    /**
     * Clear the connection error state.
     */
    fun clearConnectionError()
    
    // ==================== Folder Operations ====================
    
    /**
     * Get or create a folder path in the cloud storage.
     * @param path The folder path (e.g., "AAPS/export/settings")
     * @return The folder ID or null if failed
     */
    suspend fun getOrCreateFolderPath(path: String): String?
    
    /**
     * Create a single folder.
     * @param name Folder name
     * @param parentId Parent folder ID (use "root" for root folder)
     * @return The created folder ID or null if failed
     */
    suspend fun createFolder(name: String, parentId: String = "root"): String?
    
    /**
     * List folders in the specified parent folder.
     * @param parentId Parent folder ID (use "root" for root folder)
     * @return List of folders
     */
    suspend fun listFolders(parentId: String = "root"): List<CloudFolder>
    
    // ==================== File Operations ====================
    
    /**
     * Upload a file to the specified path.
     * @param fileName File name
     * @param content File content as bytes
     * @param mimeType MIME type of the file
     * @param path Target folder path
     * @return The uploaded file ID or null if failed
     */
    suspend fun uploadFileToPath(
        fileName: String,
        content: ByteArray,
        mimeType: String,
        path: String
    ): String?
    
    /**
     * Upload a file to the currently selected folder.
     * @param fileName File name
     * @param content File content as bytes
     * @param mimeType MIME type of the file
     * @return The uploaded file ID or null if failed
     */
    suspend fun uploadFile(
        fileName: String,
        content: ByteArray,
        mimeType: String
    ): String?
    
    /**
     * Download a file by its ID.
     * @param fileId The file ID
     * @return File content as bytes or null if failed
     */
    suspend fun downloadFile(fileId: String): ByteArray?
    
    /**
     * List settings files (JSON files) in the selected folder.
     * @param pageSize Maximum number of files to return
     * @param pageToken Page token for pagination
     * @return List of settings files
     */
    suspend fun listSettingsFiles(
        pageSize: Int = 10,
        pageToken: String? = null
    ): CloudFileListResult
    
    // ==================== Selected Folder ====================
    
    /**
     * Get the currently selected folder ID.
     * @return The selected folder ID or empty string if none selected
     */
    fun getSelectedFolderId(): String
    
    /**
     * Set the selected folder ID.
     * @param folderId The folder ID to select
     */
    fun setSelectedFolderId(folderId: String)
    
    // ==================== OAuth Helpers ====================
    
    /**
     * Wait for OAuth authorization code (used during OAuth/PKCE auth flow).
     * Providers that use browser-based auth should implement this.
     * @param timeoutMs Maximum time to wait in milliseconds
     * @return The authorization code, or null if timeout/cancelled
     */
    suspend fun waitForAuthCode(timeoutMs: Long = 60000): String? = null
    
    /**
     * Count the number of settings files in the selected folder.
     * @return Number of settings files
     */
    suspend fun countSettingsFiles(): Int = 0
}
