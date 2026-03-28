package app.aaps.plugins.configuration.di

import app.aaps.plugins.configuration.maintenance.cloud.CloudStorageProvider
import app.aaps.plugins.configuration.maintenance.cloud.providers.googledrive.GoogleDriveProvider
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoSet

/**
 * Dagger module for cloud storage providers.
 * 
 * This module uses multi-binding to automatically collect all CloudStorageProvider
 * implementations. Adding a new cloud provider is as simple as:
 * 1. Create a class implementing CloudStorageProvider
 * 2. Add a new @Binds @IntoSet method here
 * 
 * Runtime Provider Selection:
 * - All providers registered here are injected into CloudStorageManager as a Set
 * - CloudStorageManager stores them in a map keyed by storageType
 * - User selects which provider to use via SharedPreferences (PREF_CLOUD_STORAGE_TYPE)
 * - CloudStorageManager.getActiveProvider() returns the provider based on user's selection
 * - This allows runtime switching without code changes
 * 
 * The CloudStorageManager will automatically receive all registered providers
 * without needing to know about specific implementations at compile time.
 */
@Module
abstract class CloudStorageModule {

    /**
     * Bind GoogleDriveProvider into the set of cloud storage providers.
     */
    @Binds
    @IntoSet
    abstract fun bindGoogleDriveProvider(provider: GoogleDriveProvider): CloudStorageProvider

    // Future providers can be added here:
    // @Binds @IntoSet abstract fun bindDropboxProvider(provider: DropboxProvider): CloudStorageProvider
    // @Binds @IntoSet abstract fun bindOneDriveProvider(provider: OneDriveProvider): CloudStorageProvider
}
