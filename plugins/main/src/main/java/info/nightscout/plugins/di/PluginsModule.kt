package info.nightscout.plugins.di

import dagger.Module

@Module(
    includes = [
        InsulinModule::class,
        FoodModule::class,
        SMSCommunicatorModule::class,
        ProfileModule::class,
        SyncModule::class,
        SourceModule::class,
        VirtualPumpModule::class,
        ObjectivesModule::class,
        SkinsModule::class,
        SkinsUiModule::class,
        LoopModule::class,
        ActionsModule::class,
        WearModule::class
    ]
)

@Suppress("unused")
abstract class PluginsModule