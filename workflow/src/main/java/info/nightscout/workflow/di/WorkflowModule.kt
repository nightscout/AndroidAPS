package info.nightscout.workflow.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.workflow.DummyWorker
import info.nightscout.workflow.InvokeLoopWorker
import info.nightscout.workflow.LoadBgDataWorker
import info.nightscout.workflow.PrepareBasalDataWorker
import info.nightscout.workflow.PrepareBgDataWorker
import info.nightscout.workflow.PrepareBucketedDataWorker
import info.nightscout.workflow.PrepareIobAutosensGraphDataWorker
import info.nightscout.workflow.PreparePredictionsWorker
import info.nightscout.workflow.PrepareTemporaryTargetDataWorker
import info.nightscout.workflow.PrepareTreatmentsDataWorker
import info.nightscout.workflow.UpdateGraphWorker
import info.nightscout.workflow.UpdateIobCobSensWorker
import info.nightscout.workflow.UpdateWidgetWorker
import info.nightscout.workflow.iob.IobCobOref1Worker
import info.nightscout.workflow.iob.IobCobOrefWorker

@Module
@Suppress("unused")
abstract class WorkflowModule {

    @ContributesAndroidInjector abstract fun iobCobWorkerInjector(): IobCobOrefWorker
    @ContributesAndroidInjector abstract fun iobCobOref1WorkerInjector(): IobCobOref1Worker
    @ContributesAndroidInjector abstract fun prepareIobAutosensDataWorkerInjector(): PrepareIobAutosensGraphDataWorker
    @ContributesAndroidInjector abstract fun prepareBasalDataWorkerInjector(): PrepareBasalDataWorker
    @ContributesAndroidInjector abstract fun prepareTemporaryTargetDataWorkerInjector(): PrepareTemporaryTargetDataWorker
    @ContributesAndroidInjector abstract fun prepareTreatmentsDataWorkerInjector(): PrepareTreatmentsDataWorker
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