package app.aaps.plugins.configuration.di

import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.nssdk.interfaces.RunningConfiguration
import app.aaps.plugins.configuration.configBuilder.ConfigBuilderFragment
import app.aaps.plugins.configuration.configBuilder.ConfigBuilderPlugin
import app.aaps.plugins.configuration.configBuilder.RunningConfigurationImpl
import dagger.Binds
import dagger.Module
import dagger.android.ContributesAndroidInjector
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Suppress("unused")
@Module(
    includes = [
        ConfigurationModule.Bindings::class,
        SetupWizardModule::class
    ]
)
@InstallIn(SingletonComponent::class)
abstract class ConfigurationModule {

    @ContributesAndroidInjector abstract fun contributesConfigBuilderFragment(): ConfigBuilderFragment

    @Module
    @InstallIn(SingletonComponent::class)
    interface Bindings {

        @Binds fun bindRunningConfiguration(runningConfigurationImpl: RunningConfigurationImpl): RunningConfiguration
        @Binds fun bindConfigBuilderInterface(configBuilderPlugin: ConfigBuilderPlugin): ConfigBuilder
    }
}
