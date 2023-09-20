package info.nightscout.androidaps.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.WearApp
import info.nightscout.shared.impl.di.SharedImplModule
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

