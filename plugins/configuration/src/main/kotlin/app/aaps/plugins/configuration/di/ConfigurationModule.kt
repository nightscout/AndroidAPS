package app.aaps.plugins.configuration.di

import app.aaps.core.interfaces.androidPermissions.AndroidPermission
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.maintenance.FileListProvider
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
import app.aaps.core.nssdk.interfaces.RunningConfiguration
import app.aaps.plugins.configuration.AndroidPermissionImpl
import app.aaps.plugins.configuration.activities.SingleFragmentActivity
import app.aaps.plugins.configuration.configBuilder.ConfigBuilderFragment
import app.aaps.plugins.configuration.configBuilder.ConfigBuilderPlugin
import app.aaps.plugins.configuration.configBuilder.RunningConfigurationImpl
import app.aaps.plugins.configuration.maintenance.FileListProviderImpl
import app.aaps.plugins.configuration.maintenance.ImportExportPrefsImpl
import app.aaps.plugins.configuration.maintenance.MaintenanceFragment
import app.aaps.plugins.configuration.maintenance.activities.CustomWatchfaceImportListActivity
import app.aaps.plugins.configuration.maintenance.activities.LogSettingActivity
import app.aaps.plugins.configuration.maintenance.activities.PrefImportListActivity
import app.aaps.plugins.configuration.maintenance.formats.EncryptedPrefsFormat
import dagger.Binds
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Suppress("unused")
@Module(
    includes = [
        ConfigurationModule.Bindings::class,
        SetupWizardModule::class
    ]
)
abstract class ConfigurationModule {

    @ContributesAndroidInjector abstract fun contributesSingleFragmentActivity(): SingleFragmentActivity
    @ContributesAndroidInjector abstract fun contributesLogSettingActivity(): LogSettingActivity
    @ContributesAndroidInjector abstract fun contributesMaintenanceFragment(): MaintenanceFragment
    @ContributesAndroidInjector abstract fun contributesConfigBuilderFragment(): ConfigBuilderFragment
    @ContributesAndroidInjector abstract fun contributesCsvExportWorker(): ImportExportPrefsImpl.CsvExportWorker
    @ContributesAndroidInjector abstract fun contributesApsResultExportWorker(): ImportExportPrefsImpl.ApsResultExportWorker
    @ContributesAndroidInjector abstract fun contributesPrefImportListActivity(): PrefImportListActivity
    @ContributesAndroidInjector abstract fun contributesCustomWatchfaceImportListActivity(): CustomWatchfaceImportListActivity
    @ContributesAndroidInjector abstract fun encryptedPrefsFormatInjector(): EncryptedPrefsFormat
    @ContributesAndroidInjector abstract fun prefImportListProviderInjector(): FileListProvider

    @Module
    interface Bindings {

        @Binds fun bindAndroidPermissionInterface(androidPermission: AndroidPermissionImpl): AndroidPermission
        @Binds fun bindPrefFileListProvider(prefFileListProviderImpl: FileListProviderImpl): FileListProvider
        @Binds fun bindRunningConfiguration(runningConfigurationImpl: RunningConfigurationImpl): RunningConfiguration
        @Binds fun bindConfigBuilderInterface(configBuilderPlugin: ConfigBuilderPlugin): ConfigBuilder
        @Binds fun bindImportExportPrefsInterface(importExportPrefs: ImportExportPrefsImpl): ImportExportPrefs
    }
}