package app.aaps.plugins.main.di

import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.overview.Overview
import app.aaps.plugins.main.general.overview.OverviewPlugin
import app.aaps.plugins.main.general.persistentNotification.DummyService
import app.aaps.plugins.main.iob.iobCobCalculator.IobCobCalculatorPlugin
import dagger.Binds
import dagger.Module
import dagger.android.ContributesAndroidInjector
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module(
    includes = [
        PluginsModule.Bindings::class,
        FoodModule::class,
        ProfileModule::class,
        ProfileModule.Bindings::class,
        SkinsModule::class,
        ActionsModule::class,
        OverviewModule::class,
    ]
)
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class PluginsModule {

    @ContributesAndroidInjector abstract fun contributesDummyService(): DummyService

    @Module
    @InstallIn(SingletonComponent::class)
    interface Bindings {

        @Binds fun bindOverview(overviewPlugin: OverviewPlugin): Overview
        @Binds fun bindIobCobCalculator(iobCobCalculatorPlugin: IobCobCalculatorPlugin): IobCobCalculator
    }
}