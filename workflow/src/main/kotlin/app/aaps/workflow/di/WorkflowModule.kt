package app.aaps.workflow.di

import app.aaps.core.interfaces.workflow.CalculationSignals
import app.aaps.core.interfaces.workflow.CalculationSignalsEmitter
import app.aaps.core.interfaces.workflow.CalculationWorkflow
import app.aaps.core.objects.workflow.CalculationSignalsImpl
import app.aaps.workflow.CalculationWorkflowImpl
import app.aaps.workflow.DummyWorker
import app.aaps.workflow.InvokeLoopWorker
import app.aaps.workflow.LoadBgDataWorker
import app.aaps.workflow.PrepareBgDataWorker
import app.aaps.workflow.PrepareBucketedDataWorker
import app.aaps.workflow.PrepareIobAutosensGraphDataWorker
import app.aaps.workflow.PreparePredictionsWorker
import app.aaps.workflow.UpdateGraphWorker
import app.aaps.workflow.UpdateIobCobSensWorker
import app.aaps.workflow.UpdateWidgetWorker
import app.aaps.workflow.iob.IobCobOref1Worker
import app.aaps.workflow.iob.IobCobOrefWorker
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

    @ContributesAndroidInjector abstract fun iobCobWorkerInjector(): IobCobOrefWorker
    @ContributesAndroidInjector abstract fun iobCobOref1WorkerInjector(): IobCobOref1Worker
    @ContributesAndroidInjector abstract fun prepareIobAutosensDataWorkerInjector(): PrepareIobAutosensGraphDataWorker
    @ContributesAndroidInjector abstract fun loadIobCobResultsWorkerInjector(): UpdateIobCobSensWorker
    @ContributesAndroidInjector abstract fun preparePredictionsWorkerInjector(): PreparePredictionsWorker
    @ContributesAndroidInjector abstract fun updateGraphAndIobWorkerInjector(): UpdateGraphWorker
    @ContributesAndroidInjector abstract fun prepareBgDataWorkerInjector(): PrepareBgDataWorker
    @ContributesAndroidInjector abstract fun prepareBucketedDataWorkerInjector(): PrepareBucketedDataWorker
    @ContributesAndroidInjector abstract fun loadBgDataWorkerInjector(): LoadBgDataWorker
    @ContributesAndroidInjector abstract fun invokeLoopWorkerInjector(): InvokeLoopWorker
    @ContributesAndroidInjector abstract fun updateWidgetWorkerInjector(): UpdateWidgetWorker
    @ContributesAndroidInjector abstract fun dummyWorkerInjector(): DummyWorker
}