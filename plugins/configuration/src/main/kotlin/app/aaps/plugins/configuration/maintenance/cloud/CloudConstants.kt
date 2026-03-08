package app.aaps.plugins.configuration.maintenance.cloud

/**
 * Centralized management of cloud-related constants.
 * 
 * This object contains constants that are shared across all cloud storage providers.
 */
object CloudConstants {
    // Log prefix for cloud-related logs
    const val LOG_PREFIX = "[Cloud]"
    
    // Cloud storage paths - these are logical paths that providers should map to their structure
    const val CLOUD_PATH_EXPORT = "/AAPS/export"
    const val CLOUD_PATH_SETTINGS = "${CLOUD_PATH_EXPORT}/preferences"
    const val CLOUD_PATH_LOGS = "${CLOUD_PATH_EXPORT}/logs"
    const val CLOUD_PATH_USER_ENTRIES = "${CLOUD_PATH_EXPORT}/user_entries"
    
    // Activity request codes
    const val CLOUD_IMPORT_REQUEST_CODE = 1001
    
    // SharedPreferences keys (provider-agnostic)
    // Use the same key as GoogleDriveManager for backward compatibility
    const val PREF_CLOUD_STORAGE_TYPE = "google_drive_storage_type"
    
    // Default page size for file listing
    const val DEFAULT_PAGE_SIZE = 5
}

/**
 * Storage type constants.
 * Each cloud provider should have a unique type identifier.
 */
object StorageTypes {
    const val LOCAL = "local"
    const val GOOGLE_DRIVE = "google_drive"
    
    // Future cloud storage providers:
    // const val DROPBOX = "dropbox"
    // const val ONEDRIVE = "onedrive"
    // const val AZURE_BLOB = "azure_blob"
    // const val AWS_S3 = "aws_s3"
    
    /**
     * Get all available storage types
     */
    val ALL_TYPES = listOf(LOCAL, GOOGLE_DRIVE)
    
    /**
     * Get all cloud storage types (excluding local)
     */
    val CLOUD_TYPES = listOf(GOOGLE_DRIVE)
    
    /**
     * Check if the given storage type is a cloud storage type
     */
    fun isCloudStorage(storageType: String): Boolean {
        return storageType in CLOUD_TYPES
    }
    
    /**
     * Check if the given storage type is valid
     */
    fun isValidStorageType(storageType: String): Boolean {
        return storageType in ALL_TYPES
    }
}
