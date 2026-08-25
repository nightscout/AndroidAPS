package app.aaps.di.metro

import androidx.work.ListenableWorker
import app.aaps.core.objects.workflow.MetroWorkerCreator
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
}
