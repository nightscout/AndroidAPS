package app.aaps.workflow.di

import app.aaps.core.interfaces.workflow.CalculationSignals
import app.aaps.core.interfaces.workflow.CalculationSignalsEmitter
import app.aaps.core.objects.workflow.CalculationSignalsImpl
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * The app wide calculation signals.
 *
 * `CalculationSignalsImpl` is a commonMain class with no constructor injection, so it is provided
 * here rather than annotated. The history window deliberately builds a second, separate emitter in
 * `HistoryWindowGraph`; that graph declares its own binding, which shadows this one.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object WorkflowBindings {

    @Provides
    @SingleIn(AppScope::class)
    fun mainSignalsImpl(): CalculationSignalsImpl = CalculationSignalsImpl()

    @Provides fun mainSignals(impl: CalculationSignalsImpl): CalculationSignals = impl
    @Provides fun mainSignalsEmitter(impl: CalculationSignalsImpl): CalculationSignalsEmitter = impl
}
