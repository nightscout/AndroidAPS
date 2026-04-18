package app.aaps.plugins.main.di

import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.plugins.main.general.overview.OverviewDataImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module(
    includes = [
        OverviewModule.Bindings::class
    ]
)
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class OverviewModule {

    @Module
    @InstallIn(SingletonComponent::class)
    interface Bindings {

        @Binds fun bindOverviewData(overviewData: OverviewDataImpl): OverviewData
    }
}