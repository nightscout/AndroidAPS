package app.aaps.wear.di

import android.content.Context
import app.aaps.shared.impl.di.SharedImplModule
import app.aaps.wear.WearApp
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.android.HasAndroidInjector
import kotlinx.datetime.Clock

@Suppress("unused")
@Module(
    includes = [
        WearModule.AppBindings::class,
        WearActivitiesModule::class,
        SharedImplModule::class
    ]
)
open class WearModule {

    @Module
    interface AppBindings {

        @Binds fun bindContext(aaps: WearApp): Context
        @Binds fun bindInjector(aaps: WearApp): HasAndroidInjector
    }

    @Provides
    fun providesClock(): Clock = Clock.System
}

