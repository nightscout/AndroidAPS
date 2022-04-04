package info.nightscout.androidaps.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.*
import info.nightscout.androidaps.workflow.*

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