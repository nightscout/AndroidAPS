package info.nightscout.configuration.di

import dagger.Binds
import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.configuration.AndroidPermissionImpl
import info.nightscout.configuration.configBuilder.ConfigBuilderFragment
import info.nightscout.configuration.configBuilder.ConfigBuilderPlugin
import info.nightscout.configuration.configBuilder.RunningConfigurationImpl
import info.nightscout.configuration.maintenance.ImportExportPrefsImpl
import info.nightscout.configuration.maintenance.PrefFileListProviderImpl
import info.nightscout.configuration.maintenance.activities.PrefImportListActivity
import info.nightscout.configuration.maintenance.formats.EncryptedPrefsFormat
import info.nightscout.interfaces.AndroidPermission
import info.nightscout.interfaces.ConfigBuilder
import info.nightscout.interfaces.configBuilder.RunningConfiguration
import info.nightscout.interfaces.maintenance.ImportExportPrefs
import info.nightscout.interfaces.maintenance.PrefFileListProvider

@Module(
    includes = [
        SetupWizardModule::class
    ]
)
abstract class ConfigurationModule {

    @ContributesAndroidInjector abstract fun contributesConfigBuilderFragment(): ConfigBuilderFragment
    @ContributesAndroidInjector abstract fun contributesCsvExportWorker(): ImportExportPrefsImpl.CsvExportWorker
    @ContributesAndroidInjector abstract fun contributesPrefImportListActivity(): PrefImportListActivity
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