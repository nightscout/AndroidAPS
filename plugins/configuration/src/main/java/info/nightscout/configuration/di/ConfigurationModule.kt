package info.nightscout.configuration.di

import dagger.Binds
import dagger.Module
import info.nightscout.configuration.configBuilder.ConfigBuilderPlugin
import info.nightscout.configuration.configBuilder.RunningConfigurationImpl
import info.nightscout.interfaces.ConfigBuilder
import info.nightscout.interfaces.configBuilder.RunningConfiguration

@Module(
    includes = [
        SetupWizardModule::class
    ]
)
open class ConfigurationModule {

    @Module
    interface Bindings {

        @Binds fun bindRunningConfiguration(runningConfigurationImpl: RunningConfigurationImpl): RunningConfiguration
        @Binds fun bindConfigBuilderInterface(configBuilderPlugin: ConfigBuilderPlugin): ConfigBuilder
    }
}