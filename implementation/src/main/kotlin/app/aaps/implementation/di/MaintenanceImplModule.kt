package app.aaps.implementation.di

import app.aaps.core.interfaces.maintenance.CloudStorageProvider
import app.aaps.core.interfaces.maintenance.FileListProvider
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
import app.aaps.core.interfaces.maintenance.Maintenance
import app.aaps.implementation.maintenance.FileListProviderImpl
import app.aaps.implementation.maintenance.ImportExportPrefsImpl
import app.aaps.implementation.maintenance.MaintenanceImpl
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

    // CsvExportWorker and ApsResultExportWorker migrated to @HiltWorker (constructed by HiltWorkerFactory).
    @ContributesAndroidInjector abstract fun encryptedPrefsFormatInjector(): EncryptedPrefsFormat

    // Not contributed to Metro: it takes a nullable `Provider<SecureEncrypt?>`, and Metro 1.4.2 crashes
    // in codegen on a nullable provider key - "No expected binding found for key
    // Provider<SecureEncrypt?>". Worth retrying when that is fixed upstream.
    @Binds abstract fun bindImportExportPrefsInterface(impl: ImportExportPrefsImpl): ImportExportPrefs

    @Binds abstract fun bindPrefFileListProvider(impl: FileListProviderImpl): FileListProvider
    @Binds abstract fun bindMaintenanceInterface(impl: MaintenanceImpl): Maintenance

    @Binds @IntoSet abstract fun bindGoogleDriveProvider(provider: GoogleDriveProvider): CloudStorageProvider
}
