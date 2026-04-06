package app.aaps.implementation.di

import app.aaps.core.interfaces.maintenance.CloudDirectoryManager
import app.aaps.core.interfaces.maintenance.CloudStorageProvider
import app.aaps.core.interfaces.maintenance.FileListProvider
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
import app.aaps.core.interfaces.maintenance.Maintenance
import app.aaps.implementation.maintenance.FileListProviderImpl
import app.aaps.implementation.maintenance.ImportExportPrefsImpl
import app.aaps.implementation.maintenance.MaintenanceImpl
import app.aaps.implementation.maintenance.cloud.CloudDirectoryManagerImpl
import app.aaps.implementation.maintenance.cloud.providers.googledrive.GoogleDriveProvider
import app.aaps.implementation.maintenance.formats.EncryptedPrefsFormat
import dagger.Binds
import dagger.Module
import dagger.android.ContributesAndroidInjector
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class MaintenanceImplModule {

    @ContributesAndroidInjector abstract fun contributesCsvExportWorker(): ImportExportPrefsImpl.CsvExportWorker
    @ContributesAndroidInjector abstract fun contributesApsResultExportWorker(): ImportExportPrefsImpl.ApsResultExportWorker
    @ContributesAndroidInjector abstract fun encryptedPrefsFormatInjector(): EncryptedPrefsFormat
    @ContributesAndroidInjector abstract fun prefImportListProviderInjector(): FileListProvider

    @Binds abstract fun bindPrefFileListProvider(impl: FileListProviderImpl): FileListProvider
    @Binds abstract fun bindImportExportPrefsInterface(impl: ImportExportPrefsImpl): ImportExportPrefs
    @Binds abstract fun bindMaintenanceInterface(impl: MaintenanceImpl): Maintenance
    @Binds abstract fun bindCloudDirectoryManager(impl: CloudDirectoryManagerImpl): CloudDirectoryManager

    @Binds @IntoSet abstract fun bindGoogleDriveProvider(provider: GoogleDriveProvider): CloudStorageProvider
}
