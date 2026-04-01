package app.aaps.plugins.main.di

import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.OverviewMenus
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.main.general.overview.OverviewDataImpl
import app.aaps.plugins.main.general.overview.OverviewFragment
import app.aaps.plugins.main.general.overview.OverviewMenusImpl
import app.aaps.plugins.main.general.overview.graphData.GraphData
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module(
    includes = [
        OverviewModule.Bindings::class,
        OverviewModule.Provide::class
    ]
)
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class OverviewModule {

    @ContributesAndroidInjector abstract fun contributesOverviewFragment(): OverviewFragment
    @ContributesAndroidInjector abstract fun graphDataInjector(): GraphData

    @Module
    @InstallIn(SingletonComponent::class)
    class Provide {

        @Provides
        fun providesGraphData(
            profileFunction: ProfileFunction,
            preferences: Preferences,
            rh: ResourceHelper
        ): GraphData = GraphData(profileFunction, preferences, rh)
    }

    @Module
    @InstallIn(SingletonComponent::class)
    interface Bindings {

        @Binds fun bindOverviewMenus(overviewMenusImpl: OverviewMenusImpl): OverviewMenus
        @Binds fun bindOverviewData(overviewData: OverviewDataImpl): OverviewData
    }
}