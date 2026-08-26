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

    // Not contributed to Metro: codegen fails with "No expected binding found for key
    // Provider<SecureEncrypt?>". Nothing here declares that nullable type - Metro builds the key that
    // way itself, and it does the same for other types elsewhere in this tree. Filed upstream as
    // https://github.com/ZacSweers/metro/issues/2731. Worth retrying when that is fixed.
    @Binds abstract fun bindImportExportPrefsInterface(impl: ImportExportPrefsImpl): ImportExportPrefs

    @Binds abstract fun bindPrefFileListProvider(impl: FileListProviderImpl): FileListProvider
    @Binds abstract fun bindMaintenanceInterface(impl: MaintenanceImpl): Maintenance

    @Binds @IntoSet abstract fun bindGoogleDriveProvider(provider: GoogleDriveProvider): CloudStorageProvider
}
