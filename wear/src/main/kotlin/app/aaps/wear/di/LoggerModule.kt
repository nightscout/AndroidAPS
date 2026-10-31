package app.aaps.wear.di

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.L
import app.aaps.shared.impl.logging.AAPSLoggerProduction
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * The logger wiring for **:wear only**. The phone builds `AAPSLogger` in its own `SharedImplBindings`;
 * the implementation class is shared, the wiring is not.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object LoggerModule {

    @Provides
    @SingleIn(AppScope::class)
    fun provideAAPSLogger(l: L): AAPSLogger = AAPSLoggerProduction(l)
}
