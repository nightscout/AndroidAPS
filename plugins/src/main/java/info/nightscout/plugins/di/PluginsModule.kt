package info.nightscout.plugins.di

import dagger.Module

@Module(
    includes = [
        InsulinModule::class,
        FoodModule::class,
        SMSCommunicatorModule::class
    ]
)

@Suppress("unused")
abstract class PluginsModule