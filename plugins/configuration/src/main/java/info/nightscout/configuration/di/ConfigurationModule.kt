package info.nightscout.configuration.di

import dagger.Module

@Module(
    includes = [
        SetupWizardModule::class
    ]
)
open class ConfigurationModule