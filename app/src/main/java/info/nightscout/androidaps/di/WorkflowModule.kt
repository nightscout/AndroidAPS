package info.nightscout.androidaps.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.workflow.InvokeLoopWorker
import info.nightscout.androidaps.workflow.LoadBgDataWorker
import info.nightscout.androidaps.workflow.PrepareBasalDataWorker
import info.nightscout.androidaps.workflow.PrepareBgDataWorker
import info.nightscout.androidaps.workflow.PrepareBucketedDataWorker
import info.nightscout.androidaps.workflow.PrepareIobAutosensGraphDataWorker
import info.nightscout.androidaps.workflow.PreparePredictionsWorker
import info.nightscout.androidaps.workflow.PrepareTemporaryTargetDataWorker
import info.nightscout.androidaps.workflow.PrepareTreatmentsDataWorker
import info.nightscout.androidaps.workflow.UpdateGraphWorker
import info.nightscout.androidaps.workflow.UpdateIobCobSensWorker
import info.nightscout.plugins.iob.iobCobCalculator.IobCobOref1Worker
import info.nightscout.plugins.iob.iobCobCalculator.IobCobOrefWorker

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
}