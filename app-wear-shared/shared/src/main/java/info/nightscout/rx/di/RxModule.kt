package info.nightscout.rx.di

import dagger.Module
import dagger.Provides
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.DefaultAapsSchedulers
import info.nightscout.rx.interfaces.L
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.AAPSLoggerProduction
import javax.inject.Singleton

@Module(
    includes = [
    ]
)
open class RxModule {

    @Provides
    @Singleton
    internal fun provideSchedulers(): AapsSchedulers = DefaultAapsSchedulers()

    @Provides
    @Singleton
    fun provideAAPSLogger(l: L): AAPSLogger = AAPSLoggerProduction(l)
}