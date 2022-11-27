package info.nightscout.plugins.di

import dagger.Binds
import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.interfaces.aps.Loop
import info.nightscout.interfaces.iob.IobCobCalculator
import info.nightscout.interfaces.nsclient.NSSettingsStatus
import info.nightscout.interfaces.nsclient.ProcessedDeviceStatusData
import info.nightscout.interfaces.smsCommunicator.SmsCommunicator
import info.nightscout.interfaces.sync.DataSyncSelector
import info.nightscout.plugins.aps.loop.LoopPlugin
import info.nightscout.plugins.general.smsCommunicator.SmsCommunicatorPlugin
import info.nightscout.plugins.general.wear.WearFragment
import info.nightscout.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.plugins.sync.nsclient.DataSyncSelectorImplementation
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
        WearModule::class,
        OverviewModule::class
    ]
)

@Suppress("unused")
abstract class PluginsModule {

    @ContributesAndroidInjector abstract fun contributesWearFragment(): WearFragment

    @Module
    interface Bindings {

        @Binds fun bindProcessedDeviceStatusData(processedDeviceStatusDataImpl: ProcessedDeviceStatusDataImpl): ProcessedDeviceStatusData
        @Binds fun bindNSSettingsStatus(nsSettingsStatusImpl: NSSettingsStatusImpl): NSSettingsStatus
        @Binds fun bindSmsCommunicator(smsCommunicatorPlugin: SmsCommunicatorPlugin): SmsCommunicator
        @Binds fun bindIobCobCalculator(iobCobCalculatorPlugin: IobCobCalculatorPlugin): IobCobCalculator
        @Binds fun bindLoop(loopPlugin: LoopPlugin): Loop
        @Binds fun bindDataSyncSelectorInterface(dataSyncSelectorImplementation: DataSyncSelectorImplementation): DataSyncSelector
    }
}