package app.aaps.workflow.di

import app.aaps.core.interfaces.workflow.CalculationSignals
import app.aaps.core.interfaces.workflow.CalculationSignalsEmitter
import app.aaps.core.interfaces.workflow.CalculationWorkflow
import app.aaps.core.objects.workflow.CalculationSignalsImpl
import app.aaps.workflow.CalculationWorkflowImpl
import app.aaps.workflow.PostCalculationWorker
import app.aaps.workflow.PrepareGraphDataWorker
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Suppress("unused")
@Module(
    includes = [
        WorkflowModule.WorkflowBindings::class
    ]
)
@InstallIn(SingletonComponent::class)
abstract class WorkflowModule {

    @Module
    @InstallIn(SingletonComponent::class)
    interface WorkflowBindings {

        @Binds fun bindCalculationWorkflow(calculationWorkflow: CalculationWorkflowImpl): CalculationWorkflow
    }

    companion object {

        @Provides @Singleton fun provideMainSignalsImpl(): CalculationSignalsImpl = CalculationSignalsImpl()
        @Provides @Singleton fun provideMainSignals(impl: CalculationSignalsImpl): CalculationSignals = impl
        @Provides @Singleton fun provideMainSignalsEmitter(impl: CalculationSignalsImpl): CalculationSignalsEmitter = impl
    }

    @ContributesAndroidInjector abstract fun prepareGraphDataWorkerInjector(): PrepareGraphDataWorker
    @ContributesAndroidInjector abstract fun postCalculationWorkerInjector(): PostCalculationWorker
}
