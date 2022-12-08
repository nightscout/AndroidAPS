package info.nightscout.plugins.di

import dagger.Binds
import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.interfaces.iob.IobCobCalculator
import info.nightscout.interfaces.smsCommunicator.SmsCommunicator
import info.nightscout.plugins.general.persistentNotification.DummyService
import info.nightscout.plugins.general.smsCommunicator.SmsCommunicatorPlugin
import info.nightscout.plugins.general.wear.WearFragment
import info.nightscout.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin

@Module(
    includes = [
        PluginsModule.Bindings::class,
        InsulinModule::class,
        FoodModule::class,
        SMSCommunicatorModule::class,
        ProfileModule::class,
        SourceModule::class,
        VirtualPumpModule::class,
        SkinsModule::class,
        SkinsUiModule::class,
        ActionsModule::class,
        WearModule::class,
        OverviewModule::class
    ]
)

@Suppress("unused")
abstract class PluginsModule {

    @ContributesAndroidInjector abstract fun contributesWearFragment(): WearFragment
    @ContributesAndroidInjector abstract fun contributesDummyService(): DummyService

    @Module
    interface Bindings {

        @Binds fun bindSmsCommunicator(smsCommunicatorPlugin: SmsCommunicatorPlugin): SmsCommunicator
        @Binds fun bindIobCobCalculator(iobCobCalculatorPlugin: IobCobCalculatorPlugin): IobCobCalculator
    }
}