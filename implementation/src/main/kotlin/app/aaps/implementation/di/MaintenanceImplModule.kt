package app.aaps.implementation.di

import app.aaps.core.interfaces.maintenance.CloudStorageProvider
import app.aaps.core.interfaces.maintenance.FileListProvider
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
import app.aaps.core.interfaces.maintenance.Maintenance
import app.aaps.implementation.maintenance.FileListProviderImpl
import app.aaps.implementation.maintenance.ImportExportPrefsImpl
import app.aaps.implementation.maintenance.MaintenanceImpl
import app.aaps.implementation.maintenance.cloud.providers.googledrive.GoogleDriveProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class MaintenanceImplModule {

    // CsvExportWorker and ApsResultExportWorker migrated to @HiltWorker (constructed by HiltWorkerFactory).

    @Binds @IntoSet abstract fun bindGoogleDriveProvider(provider: GoogleDriveProvider): CloudStorageProvider
}
