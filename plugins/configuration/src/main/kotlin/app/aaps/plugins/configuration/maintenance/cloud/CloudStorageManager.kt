package app.aaps.plugins.configuration.maintenance.cloud

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.sharedPreferences.SP
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cloud Storage Manager - Factory class for managing cloud storage providers.
 * 
 * This class provides a unified interface for accessing cloud storage providers.
 * It manages provider registration, selection, and lifecycle.
 * 
 * The providers are automatically registered via Dagger multi-binding.
 * To add a new cloud provider:
 * 1. Create a class that implements CloudStorageProvider
 * 2. Add a @Binds @IntoSet binding in CloudStorageModule
 * 3. Add the storage type to StorageTypes
 * 
 * Usage:
 * ```
 * // Get the active provider (based on user settings)
 * val provider = cloudStorageManager.getActiveProvider()
 * 
 * // Get a specific provider by type
 * val googleDrive = cloudStorageManager.getProvider(StorageTypes.GOOGLE_DRIVE)
 * 
 * // List all available providers
 * val providers = cloudStorageManager.getAvailableProviders()
 * ```
 */
@Singleton
class CloudStorageManager @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val sp: SP,
    /**
     * Set of all cloud storage providers, injected via Dagger multi-binding.
     * This allows adding new providers without modifying this class.
     */
    cloudStorageProviders: Set<@JvmSuppressWildcards CloudStorageProvider>
) {

    companion object {
        private const val LOG_PREFIX = "[CloudStorageManager]"
    }

    /**
     * Map of storage type to provider instance
     */
    private val providers: Map<String, CloudStorageProvider>

    init {
        // Build provider map from the injected set
        providers = cloudStorageProviders.associateBy { it.storageType }
        
        aapsLogger.info(LTag.CORE, "$LOG_PREFIX Initialized with ${providers.size} provider(s): ${providers.keys.joinToString()}")
    }

    /**
     * Get the currently active provider based on user settings.
     * @return The active CloudStorageProvider, or null if local storage is selected
     */
    fun getActiveProvider(): CloudStorageProvider? {
        val activeType = sp.getString(CloudConstants.PREF_CLOUD_STORAGE_TYPE, StorageTypes.LOCAL)
        
        if (activeType == StorageTypes.LOCAL) {
            return null
        }
        
        return providers[activeType]
    }

    /**
     * Get a specific provider by its storage type.
     * @param storageType The storage type identifier (e.g., "google_drive")
     * @return The provider, or null if not found
     */
    fun getProvider(storageType: String): CloudStorageProvider? {
        return providers[storageType]
    }

    /**
     * Get all available cloud storage providers.
     * @return List of all registered providers
     */
    fun getAvailableProviders(): List<CloudStorageProvider> {
        return providers.values.toList()
    }

    /**
     * Get all cloud storage types that have valid credentials.
     * @return List of storage types with valid credentials
     */
    fun getAuthenticatedProviders(): List<CloudStorageProvider> {
        return providers.values.filter { it.hasValidCredentials() }
    }

    /**
     * Check if any cloud provider has valid credentials.
     * @return true if at least one provider is authenticated
     */
    fun hasAnyCloudCredentials(): Boolean {
        return providers.values.any { it.hasValidCredentials() }
    }

    /**
     * Get the currently selected storage type.
     * @return The storage type identifier
     */
    fun getActiveStorageType(): String {
        return sp.getString(CloudConstants.PREF_CLOUD_STORAGE_TYPE, StorageTypes.LOCAL)
    }

    /**
     * Set the active storage type.
     * @param storageType The storage type to set
     */
    fun setActiveStorageType(storageType: String) {
        if (StorageTypes.isValidStorageType(storageType)) {
            sp.putString(CloudConstants.PREF_CLOUD_STORAGE_TYPE, storageType)
            aapsLogger.info(LTag.CORE, "$LOG_PREFIX Active storage type set to: $storageType")
        } else {
            aapsLogger.warn(LTag.CORE, "$LOG_PREFIX Invalid storage type: $storageType")
        }
    }

    /**
     * Check if cloud storage is currently active.
     * @return true if the active storage type is a cloud type
     */
    fun isCloudStorageActive(): Boolean {
        return StorageTypes.isCloudStorage(getActiveStorageType())
    }

    /**
     * Check if the active provider has a connection error.
     * @return true if there's a connection error
     */
    fun hasConnectionError(): Boolean {
        return getActiveProvider()?.hasConnectionError() ?: false
    }

    /**
     * Clear connection error on the active provider.
     */
    fun clearConnectionError() {
        getActiveProvider()?.clearConnectionError()
    }

    /**
     * Clear credentials for all providers.
     * Use this for a complete sign-out from all cloud services.
     */
    fun clearAllCredentials() {
        providers.values.forEach { provider ->
            try {
                provider.clearCredentials()
                aapsLogger.info(LTag.CORE, "$LOG_PREFIX Cleared credentials for: ${provider.storageType}")
            } catch (e: Exception) {
                aapsLogger.error(LTag.CORE, "$LOG_PREFIX Error clearing credentials for ${provider.storageType}", e)
            }
        }
    }

    /**
     * Get the display name for a storage type.
     * @param storageType The storage type
     * @return Human-readable display name
     */
    fun getDisplayName(storageType: String): String {
        return providers[storageType]?.displayName ?: storageType
    }

    /**
     * Get the icon resource ID for a storage type.
     * @param storageType The storage type
     * @return Drawable resource ID, or 0 if not found
     */
    fun getIconResId(storageType: String): Int {
        return providers[storageType]?.iconResId ?: 0
    }
}
