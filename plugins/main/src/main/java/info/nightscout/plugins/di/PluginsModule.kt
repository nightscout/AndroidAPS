package info.nightscout.plugins.di

import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import dagger.Binds
import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.plugins.general.persistentNotification.DummyService
import info.nightscout.plugins.general.smsCommunicator.SmsCommunicatorPlugin
import info.nightscout.plugins.general.wear.WearFragment
import info.nightscout.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.plugins.iob.iobCobCalculator.data.AutosensDataObject

@Module(
    includes = [
        PluginsModule.Bindings::class,
        FoodModule::class,
        SMSCommunicatorModule::class,
        ProfileModule::class,
        ProfileModule.Bindings::class,
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
    @ContributesAndroidInjector abstract fun autosensDataObjectInjector(): AutosensDataObject

    @Module
    interface Bindings {

        @Binds fun bindSmsCommunicator(smsCommunicatorPlugin: SmsCommunicatorPlugin): SmsCommunicator
        @Binds fun bindIobCobCalculator(iobCobCalculatorPlugin: IobCobCalculatorPlugin): IobCobCalculator
    }
}