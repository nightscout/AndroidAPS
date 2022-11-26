package info.nightscout.plugins.di

import dagger.Binds
import dagger.Module
import info.nightscout.interfaces.nsclient.ProcessedDeviceStatusData
import info.nightscout.interfaces.nsclient.NSSettingsStatus
import info.nightscout.plugins.sync.nsclient.data.NSSettingsStatusImpl
import info.nightscout.plugins.sync.nsclient.data.ProcessedDeviceStatusDataImpl

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
abstract class PluginsModule {

    @Module
    interface Bindings {

        @Binds fun bindProcessedDeviceStatusData(processedDeviceStatusDataImpl: ProcessedDeviceStatusDataImpl): ProcessedDeviceStatusData
        @Binds fun bindNSSettingsStatus(nsSettingsStatusImpl: NSSettingsStatusImpl): NSSettingsStatus
    }
}