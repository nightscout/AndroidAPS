package app.aaps.plugins.main.di

import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.plugins.main.general.persistentNotification.DummyService
import app.aaps.plugins.main.general.smsCommunicator.SmsCommunicatorPlugin
import app.aaps.plugins.main.general.wear.WearFragment
import app.aaps.plugins.main.iob.iobCobCalculator.IobCobCalculatorPlugin
import app.aaps.plugins.main.iob.iobCobCalculator.data.AutosensDataObject
import dagger.Binds
import dagger.Module
import dagger.android.ContributesAndroidInjector

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