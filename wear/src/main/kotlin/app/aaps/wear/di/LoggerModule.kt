package app.aaps.wear.di

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.L
import app.aaps.shared.impl.logging.AAPSLoggerProduction
import dagger.Module
import dagger.Provides
import dagger.hilt.migration.DisableInstallInCheck
import javax.inject.Singleton

/**
 * The logger wiring for **:wear only**.
 *
 * There is deliberately no `@InstallIn(SingletonComponent::class)` here any more. `WearModule` names
 * this module in its `includes`, so wear is unaffected, while the phone - which only ever got it
 * through that auto-install - now builds `AAPSLogger` in Metro instead (`SharedImplBindings`). Adding
 * `@InstallIn` back would give the phone two of everything below.
 *
 * The implementation classes themselves stay shared; only the wiring is split.
 */
@Module
@DisableInstallInCheck
open class LoggerModule {

    @Provides
    @Singleton
    fun provideAAPSLogger(l: L): AAPSLogger = AAPSLoggerProduction(l)
}