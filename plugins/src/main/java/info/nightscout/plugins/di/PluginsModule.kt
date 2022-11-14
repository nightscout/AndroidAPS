package info.nightscout.plugins.di

import dagger.Module

@Module(
    includes = [
        InsulinModule::class,
        FoodModule::class,
        SMSCommunicatorModule::class,
        AutotuneModule::class,
        ProfileModule::class,
        SyncModule::class,
        SourceModule::class,
        VirtualPumpModule::class,
        ObjectivesModule::class,
        SkinsModule::class
    ]
)

@Suppress("unused")
abstract class PluginsModule