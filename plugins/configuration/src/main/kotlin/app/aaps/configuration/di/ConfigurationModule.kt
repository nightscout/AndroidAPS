package app.aaps.configuration.di

import app.aaps.configuration.AndroidPermissionImpl
import app.aaps.configuration.activities.SingleFragmentActivity
import app.aaps.configuration.configBuilder.ConfigBuilderFragment
import app.aaps.configuration.configBuilder.ConfigBuilderPlugin
import app.aaps.configuration.configBuilder.RunningConfigurationImpl
import app.aaps.configuration.maintenance.ImportExportPrefsImpl
import app.aaps.configuration.maintenance.MaintenanceFragment
import app.aaps.configuration.maintenance.PrefFileListProviderImpl
import app.aaps.configuration.maintenance.activities.CustomWatchfaceImportListActivity
import app.aaps.configuration.maintenance.activities.LogSettingActivity
import app.aaps.configuration.maintenance.activities.PrefImportListActivity
import app.aaps.configuration.maintenance.formats.EncryptedPrefsFormat
import dagger.Binds
import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.interfaces.AndroidPermission
import info.nightscout.interfaces.ConfigBuilder
import info.nightscout.interfaces.maintenance.ImportExportPrefs
import info.nightscout.interfaces.maintenance.PrefFileListProvider
import info.nightscout.sdk.interfaces.RunningConfiguration

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
    @ContributesAndroidInjector abstract fun contributesPrefImportListActivity(): PrefImportListActivity
    @ContributesAndroidInjector abstract fun contributesCustomWatchfaceImportListActivity(): CustomWatchfaceImportListActivity
    @ContributesAndroidInjector abstract fun encryptedPrefsFormatInjector(): EncryptedPrefsFormat
    @ContributesAndroidInjector abstract fun prefImportListProviderInjector(): PrefFileListProvider

    @Module
    interface Bindings {

        @Binds fun bindAndroidPermissionInterface(androidPermission: AndroidPermissionImpl): AndroidPermission
        @Binds fun bindPrefFileListProvider(prefFileListProviderImpl: PrefFileListProviderImpl): PrefFileListProvider
        @Binds fun bindRunningConfiguration(runningConfigurationImpl: RunningConfigurationImpl): RunningConfiguration
        @Binds fun bindConfigBuilderInterface(configBuilderPlugin: ConfigBuilderPlugin): ConfigBuilder
        @Binds fun bindImportExportPrefsInterface(importExportPrefs: ImportExportPrefsImpl): ImportExportPrefs
    }
}