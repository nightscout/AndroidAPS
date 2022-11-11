package info.nightcout.shared.impl.di

import android.content.Context
import androidx.preference.PreferenceManager
import dagger.Module
import dagger.Provides
import info.nightcout.shared.impl.logging.LImpl
import info.nightscout.rx.interfaces.L
import info.nightscout.shared.sharedPreferences.SP
import javax.inject.Singleton

@Module(
    includes = [
    ]
)
open class SharedImplModule {

    @Provides
    @Singleton
    fun provideSharedPreferences(context: Context): SP = info.nightcout.shared.impl.sharedPreferences.SPImplementation(PreferenceManager.getDefaultSharedPreferences(context), context)

    @Provides
    @Singleton
    fun provideL(sp: SP): L = LImpl(sp)
}