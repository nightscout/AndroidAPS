package app.aaps.core.interfaces.maintenance

/**
 * Information about the current cloud directory configuration.
 * Provider-specific strings are passed as resolved strings to avoid
 * cross-module resource dependencies.
 */
data class CloudDirectoryInfo(
    val isCloudActive: Boolean,
    val hasCredentials: Boolean,
    val hasConnectionError: Boolean,
    val providerDisplayName: String,
    val providerDescription: String,
    val providerIconResId: Int,
    val authorizedStatusText: String,
    val cloudPath: String
)

/**
 * Abstraction for cloud directory management.
 * Lives in core/interfaces so the UI module can depend on it
 * without importing plugins/configuration directly.
 */
interface CloudDirectoryManager {

    fun getCloudDirectoryInfo(): CloudDirectoryInfo
    fun clearCloudSettings()
    fun resetExportToLocal()
    fun enableAllCloudExport()
    fun enableLocalStorage()
    suspend fun testConnection(): Boolean
    suspend fun startAuth(): String?
    suspend fun waitForAuthCode(timeoutMs: Long = 60000): String?
    suspend fun completeAuth(authCode: String): Boolean
    suspend fun setupCloudStorage(): Boolean
}
