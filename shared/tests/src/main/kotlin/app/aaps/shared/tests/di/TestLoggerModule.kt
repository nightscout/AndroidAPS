package app.aaps.shared.tests.di

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.shared.tests.AAPSLoggerTest
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module(
    includes = [
    ]
)
open class TestLoggerModule {

    @Provides
    @Singleton
    fun provideAAPSLogger(): AAPSLogger = AAPSLoggerTest()
}