package info.nightscout.androidaps.di

import android.content.Context
import androidx.preference.PreferenceManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Aaps
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.AAPSLoggerProduction
import info.nightscout.shared.logging.L
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.sharedPreferences.SPImplementation
import javax.inject.Singleton

@Suppress("unused")
@Module(includes = [
    WearModule.AppBindings::class
])
open class WearModule {

    @Provides
    @Singleton
    fun provideSharedPreferences(context: Context): SP = SPImplementation(PreferenceManager.getDefaultSharedPreferences(context), context)

    @Provides
    @Singleton
    fun provideAAPSLogger(l: L): AAPSLogger = AAPSLoggerProduction(l)

    @Module
    interface AppBindings {

        @Binds fun bindContext(aaps: Aaps): Context
        @Binds fun bindInjector(aaps: Aaps): HasAndroidInjector
    }
}

