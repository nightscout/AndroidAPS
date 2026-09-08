package app.aaps.di.metro

import app.aaps.implementation.maintenance.ImportExportPrefsImpl
import app.aaps.implementation.receivers.KeepAliveWorker
import app.aaps.implementation.scenes.SceneExpiryWorker
import app.aaps.plugins.aps.loop.runningMode.RunningModeExpiryWorker
import app.aaps.plugins.sync.nsclientV3.workers.DataSyncWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadBgWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadDeviceStatusWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadFoodsWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadLastModificationWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadProfileStoreWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadSettingsWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadStatusWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadTreatmentsWorker
import app.aaps.plugins.sync.smsCommunicator.SmsCommunicatorPlugin
import app.aaps.plugins.sync.xdrip.workers.XdripDataSyncWorker
import app.aaps.workflow.PostCalculationWorker
import app.aaps.workflow.PrepareGraphDataWorker
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The app-wide workers Metro builds, keyed by class.
 * Each worker registers itself - `@ContributesIntoMap` plus `@WorkerKey` on its nested
 * `@AssistedFactory` - so nothing central lists them any more, and this is the only place that states
 * which ones exist. Note the contribution targets `AppScope` while this map lives on a graph extension:
 * that it arrives here at all is the thing worth asserting.
 * Same shape as `SourceGraphTest` does for the twelve source workers.
 */
class AppWorkersGraphTest {

    @Test
    fun `every app worker wired to Metro is registered under its own class`() {
        assertThat(testRoot().workersGraph.workerCreators.keys).containsExactly(
            RunningModeExpiryWorker::class,
            KeepAliveWorker::class,
            SceneExpiryWorker::class,
            ImportExportPrefsImpl.CsvExportWorker::class,
            ImportExportPrefsImpl.ApsResultExportWorker::class,
            // :workflow - recalculate and redraw the overview graph.
            PostCalculationWorker::class,
            PrepareGraphDataWorker::class,
            // :plugins:sync - the nine nsclientV3 loaders, plus xdrip and SMS.
            DataSyncWorker::class,
            LoadBgWorker::class,
            LoadDeviceStatusWorker::class,
            LoadFoodsWorker::class,
            LoadLastModificationWorker::class,
            LoadProfileStoreWorker::class,
            LoadSettingsWorker::class,
            LoadStatusWorker::class,
            LoadTreatmentsWorker::class,
            XdripDataSyncWorker::class,
            SmsCommunicatorPlugin.SmsCommunicatorWorker::class
        )
    }
}
