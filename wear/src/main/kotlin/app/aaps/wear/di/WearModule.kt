package app.aaps.wear.di

import android.content.Context
import app.aaps.core.keys.Preferences
import app.aaps.shared.impl.di.LoggerModule
import app.aaps.shared.impl.di.SharedImplModule
import app.aaps.wear.WearApp
import app.aaps.wear.sharedPreferences.PreferencesImpl
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
        SharedImplModule::class,
        LoggerModule::class
    ]
)
open class WearModule {

    @Module
    interface AppBindings {

        @Binds fun bindPreferences(preferencesImpl: PreferencesImpl): Preferences
        @Binds fun bindContext(aaps: WearApp): Context
        @Binds fun bindInjector(aaps: WearApp): HasAndroidInjector
    }

    @Provides
    fun providesClock(): Clock = Clock.System
}

