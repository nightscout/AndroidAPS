package app.aaps.plugins.main.di

import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.overview.Overview
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.plugins.main.general.overview.OverviewPlugin
import app.aaps.plugins.main.general.persistentNotification.DummyService
import app.aaps.plugins.main.general.smsCommunicator.SmsCommunicatorPlugin
import app.aaps.plugins.main.iob.iobCobCalculator.IobCobCalculatorPlugin
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
        ActionsModule::class,
        OverviewModule::class,
    ]
)

@Suppress("unused")
abstract class PluginsModule {

    @ContributesAndroidInjector abstract fun contributesDummyService(): DummyService

    @Module
    interface Bindings {

        @Binds fun bindOverview(overviewPlugin: OverviewPlugin): Overview
        @Binds fun bindSmsCommunicator(smsCommunicatorPlugin: SmsCommunicatorPlugin): SmsCommunicator
        @Binds fun bindIobCobCalculator(iobCobCalculatorPlugin: IobCobCalculatorPlugin): IobCobCalculator
    }
}