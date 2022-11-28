package info.nightscout.shared.impl.di

import android.content.Context
import androidx.preference.PreferenceManager
import dagger.Module
import dagger.Provides
import info.nightscout.rx.interfaces.L
import info.nightscout.shared.impl.logging.LImpl
import info.nightscout.shared.impl.sharedPreferences.SPImplementation
import info.nightscout.shared.sharedPreferences.SP
import javax.inject.Singleton

@Module(
    includes = [
    ]
)
open class SharedImplModule {

    @Provides
    @Singleton
    fun provideSharedPreferences(context: Context): SP = SPImplementation(PreferenceManager.getDefaultSharedPreferences(context), context)

    @Provides
    @Singleton
    fun provideL(sp: SP): L = LImpl(sp)
}