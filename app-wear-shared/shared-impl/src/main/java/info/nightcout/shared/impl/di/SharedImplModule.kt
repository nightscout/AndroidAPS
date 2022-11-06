package info.nightscout.rx.di

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
    fun provideL(sp: SP): L = LImpl(sp)
}