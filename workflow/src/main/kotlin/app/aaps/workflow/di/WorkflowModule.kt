package app.aaps.workflow.di

import app.aaps.core.interfaces.workflow.CalculationSignals
import app.aaps.core.interfaces.workflow.CalculationSignalsEmitter
import app.aaps.core.objects.workflow.CalculationSignalsImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * What is left of this module after the move to Metro.
 *
 * `CalculationWorkflowImpl` and `WorkflowChainData` are now Metro bindings and reach Dagger through the
 * delegates in `CoreObjectsModule`. The signals below stay here on purpose: `CalculationSignalsImpl` is
 * a **commonMain** class, and the history window deliberately builds a second, separate emitter of its
 * own in `HistoryWindowGraph`. Contributing this one to `AppScope` would collide with that.
 */
@Suppress("unused")
@Module
@InstallIn(SingletonComponent::class)
abstract class WorkflowModule {

    companion object {

        @Provides @Singleton fun provideMainSignalsImpl(): CalculationSignalsImpl = CalculationSignalsImpl()
        @Provides @Singleton fun provideMainSignals(impl: CalculationSignalsImpl): CalculationSignals = impl
        @Provides @Singleton fun provideMainSignalsEmitter(impl: CalculationSignalsImpl): CalculationSignalsEmitter = impl
    }

    // PostCalculationWorker and PrepareGraphDataWorker migrated to @HiltWorker (constructed by HiltWorkerFactory).
}
