package app.aaps.di.metro

import androidx.work.ListenableWorker
import app.aaps.core.objects.workflow.MetroWorkerCreator
import app.aaps.plugins.sync.smsCommunicator.SmsCommunicatorPlugin
import app.aaps.plugins.sync.xdrip.workers.XdripDataSyncWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadTreatmentsWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadStatusWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadSettingsWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadProfileStoreWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadLastModificationWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadFoodsWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadDeviceStatusWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadBgWorker
import app.aaps.plugins.sync.nsclientV3.workers.DataSyncWorker
import app.aaps.workflow.PrepareGraphDataWorker
import app.aaps.workflow.PostCalculationWorker
import app.aaps.core.objects.workflow.WorkerKey
import app.aaps.implementation.maintenance.ImportExportPrefsImpl
import app.aaps.implementation.receivers.KeepAliveWorker
import app.aaps.implementation.scenes.SceneExpiryWorker
import app.aaps.plugins.aps.loop.runningMode.RunningModeExpiryScheduler
import app.aaps.plugins.aps.loop.runningMode.RunningModeExpiryWorker
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
import kotlin.reflect.KClass

/**
 * Metro wiring for workers, replacing what `@HiltWorker` does today.
 *
 * This graph exists to answer one question: can Metro build an Android entry point that the framework
 * constructs for us? A worker is the hardest of the four, because WorkManager holds the constructor
 * call and only hands over a class name at runtime. The answer is the map below - a real compile-time
 * `@IntoMap` multibinding keyed by worker class, which [MetroWorkerFactory] looks up.
 *
 * Two workers are wired here on purpose. [RunningModeExpiryWorker] is small and now lives in the
 * multiplatform module that owns its job, which Dagger could not have done.
 * [KeepAliveWorker] takes nineteen dependencies, lives in another module, and runs every fifteen
 * minutes - so it both stresses the wiring and reports on itself from the device log.
 *
 * Workers are Android-only and always will be, so this graph stays in `:app` rather than moving to
 * commonMain. It is here to prove Hilt can be removed, not to reach iOS.
 *
 * This is a graph extension of [AppRootGraph], so it declares only what it adds. It used to be a root
 * of its own, with a factory restating twenty-nine app-wide dependencies and twenty-nine functions
 * unwrapping them - all of that now lives once in the root and is inherited.
 */
@GraphExtension
interface AppWorkersGraph {

    /**
     * Schedules [RunningModeExpiryWorker]. It lives in the same multiplatform module as the worker
     * and the job now, so Metro builds it and Dagger consumers are handed this instance.
     */
    val runningModeExpiryScheduler: RunningModeExpiryScheduler

    /** Every Metro-wired worker, keyed by its class. [MetroWorkerFactory] matches on the class name. */
    val workerCreators: Map<KClass<out ListenableWorker>, MetroWorkerCreator>

    /**
     * The whole `@HiltWorker` replacement, per worker: one line binding the generated assisted factory
     * into the map. Metro generates the `Factory` from `@AssistedFactory`, and it already extends
     * [MetroWorkerCreator], so no adapter is needed.
     */
    @Provides
    @IntoMap
    @WorkerKey(RunningModeExpiryWorker::class)
    fun bindRunningModeExpiryWorker(factory: RunningModeExpiryWorker.Factory): MetroWorkerCreator = factory

    @Provides
    @IntoMap
    @WorkerKey(KeepAliveWorker::class)
    fun bindKeepAliveWorker(factory: KeepAliveWorker.Factory): MetroWorkerCreator = factory

    @Provides
    @IntoMap
    @WorkerKey(SceneExpiryWorker::class)
    fun bindSceneExpiryWorker(factory: SceneExpiryWorker.Factory): MetroWorkerCreator = factory

    @Provides
    @IntoMap
    @WorkerKey(ImportExportPrefsImpl.CsvExportWorker::class)
    fun bindCsvExportWorker(f: ImportExportPrefsImpl.CsvExportWorker.Factory): MetroWorkerCreator = f

    @Provides
    @IntoMap
    @WorkerKey(ImportExportPrefsImpl.ApsResultExportWorker::class)
    fun bindApsResultExportWorker(
        f: ImportExportPrefsImpl.ApsResultExportWorker.Factory
    ): MetroWorkerCreator = f

    @Provides
    @IntoMap
    @WorkerKey(DataSyncWorker::class)
    fun bindDataSyncWorker(f: DataSyncWorker.Factory): MetroWorkerCreator = f

    @Provides
    @IntoMap
    @WorkerKey(LoadBgWorker::class)
    fun bindLoadBgWorker(f: LoadBgWorker.Factory): MetroWorkerCreator = f

    @Provides
    @IntoMap
    @WorkerKey(LoadDeviceStatusWorker::class)
    fun bindLoadDeviceStatusWorker(f: LoadDeviceStatusWorker.Factory): MetroWorkerCreator = f

    @Provides
    @IntoMap
    @WorkerKey(LoadFoodsWorker::class)
    fun bindLoadFoodsWorker(f: LoadFoodsWorker.Factory): MetroWorkerCreator = f

    @Provides
    @IntoMap
    @WorkerKey(LoadLastModificationWorker::class)
    fun bindLoadLastModificationWorker(f: LoadLastModificationWorker.Factory): MetroWorkerCreator = f

    @Provides
    @IntoMap
    @WorkerKey(LoadProfileStoreWorker::class)
    fun bindLoadProfileStoreWorker(f: LoadProfileStoreWorker.Factory): MetroWorkerCreator = f

    @Provides
    @IntoMap
    @WorkerKey(LoadSettingsWorker::class)
    fun bindLoadSettingsWorker(f: LoadSettingsWorker.Factory): MetroWorkerCreator = f

    @Provides
    @IntoMap
    @WorkerKey(LoadStatusWorker::class)
    fun bindLoadStatusWorker(f: LoadStatusWorker.Factory): MetroWorkerCreator = f

    @Provides
    @IntoMap
    @WorkerKey(LoadTreatmentsWorker::class)
    fun bindLoadTreatmentsWorker(f: LoadTreatmentsWorker.Factory): MetroWorkerCreator = f

    @Provides
    @IntoMap
    @WorkerKey(XdripDataSyncWorker::class)
    fun bindXdripDataSyncWorker(f: XdripDataSyncWorker.Factory): MetroWorkerCreator = f

    @Provides
    @IntoMap
    @WorkerKey(PostCalculationWorker::class)
    fun bindPostCalculationWorker(f: PostCalculationWorker.Factory): MetroWorkerCreator = f

    @Provides
    @IntoMap
    @WorkerKey(PrepareGraphDataWorker::class)
    fun bindPrepareGraphDataWorker(f: PrepareGraphDataWorker.Factory): MetroWorkerCreator = f

    @Provides
    @IntoMap
    @WorkerKey(SmsCommunicatorPlugin.SmsCommunicatorWorker::class)
    fun bindSmsCommunicatorWorker(f: SmsCommunicatorPlugin.SmsCommunicatorWorker.Factory): MetroWorkerCreator = f
}
