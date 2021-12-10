package info.nightscout.shared.di

import android.content.Context
import androidx.preference.PreferenceManager
import dagger.Module
import dagger.Provides
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.AAPSLoggerProduction
import info.nightscout.shared.logging.L
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.sharedPreferences.SPImplementation
import javax.inject.Singleton

@Module(includes = [
])
open class SharedModule {

    @Provides
    @Singleton
    fun provideSharedPreferences(context: Context): SP = SPImplementation(PreferenceManager.getDefaultSharedPreferences(context), context)

    @Provides
    @Singleton
    fun provideAAPSLogger(l: L): AAPSLogger = AAPSLoggerProduction(l)
}