package app.aaps.shared.impl.di

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.L
import app.aaps.shared.impl.logging.AAPSLoggerProduction
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module(
    includes = [
    ]
)
open class LoggerModule {

    @Provides
    @Singleton
    fun provideAAPSLogger(l: L): AAPSLogger = AAPSLoggerProduction(l)
}