package app.aaps.plugins.sync.nsclientV3.ws

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.objects.workflow.WorkOutcome
import app.aaps.plugins.sync.nsclientV3.workers.DataSyncRunner
import app.aaps.plugins.sync.nsclientV3.workers.LoadBgRunner
import app.aaps.plugins.sync.nsclientV3.workers.LoadDeviceStatusRunner
import app.aaps.plugins.sync.nsclientV3.workers.LoadFoodsRunner
import app.aaps.plugins.sync.nsclientV3.workers.LoadLastModificationRunner
import app.aaps.plugins.sync.nsclientV3.workers.LoadProfileStoreRunner
import app.aaps.plugins.sync.nsclientV3.workers.LoadSettingsRunner
import app.aaps.plugins.sync.nsclientV3.workers.LoadStatusRunner
import app.aaps.plugins.sync.nsclientV3.workers.LoadTreatmentsRunner
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * [NsLoadExecutor] on plain coroutines.
 *
 * Android runs the round through WorkManager so it outlives the screen that asked for it. iOS has
 * nothing of the sort - a round that is interrupted is simply run again, because every step reads
 * its own high-water mark from the database and picks up where it left off.
 *
 * The whole round shares one [Job], which is what makes [isRunning] meaningful and what lets a new
 * round replace one in flight rather than run beside it. Two rounds writing the same high-water
 * marks would interleave.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class CoroutineNsLoadExecutor @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val scope: CoroutineScope,
    private val loadStatus: LoadStatusRunner,
    private val loadLastModification: LoadLastModificationRunner,
    private val loadBg: LoadBgRunner,
    private val loadTreatments: LoadTreatmentsRunner,
    private val loadFoods: LoadFoodsRunner,
    private val loadProfileStore: LoadProfileStoreRunner,
    private val loadSettings: LoadSettingsRunner,
    private val loadDeviceStatus: LoadDeviceStatusRunner,
    private val dataSync: DataSyncRunner
) : NsLoadExecutor {

    /** The one round. Replaced by each new one, which is what "under a shared name" means here. */
    private var round: Job? = null

    private val _idle = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val isRunning: Boolean get() = round?.isActive == true

    override val idle: Flow<Unit> = _idle.asSharedFlow()

    override fun runChain(steps: List<NsLoadStep>) {
        if (steps.isEmpty()) return
        replaceRound {
            for (step in steps) {
                if (!isActive) return@replaceRound
                // Stops at the first step that fails, as the WorkManager chain does: a later step
                // usually depends on what an earlier one wrote.
                if (runStep(step) !is WorkOutcome.Success) return@replaceRound
            }
        }
    }

    override fun runReplacing(step: NsLoadStep) {
        replaceRound { runStep(step) }
    }

    override fun runDetached(step: NsLoadStep) {
        // Outside the round on purpose: it must not replace one, and a round must not stop it.
        scope.launch { runStep(step) }
    }

    override fun cancel() {
        round?.cancel()
    }

    /**
     * Starts [body] as the round, cancelling whatever was running.
     *
     * The idle signal is emitted from `invokeOnCompletion` rather than at the end of [body], so a
     * cancelled round reports idle too. The plugin queues a follow-up round on that signal, and a
     * cancelled round that never reported would leave the queue stuck.
     */
    private fun replaceRound(body: suspend CoroutineScope.() -> Unit) {
        round?.cancel()
        round = scope.launch(block = body).also { started ->
            started.invokeOnCompletion { _idle.tryEmit(Unit) }
        }
    }

    private suspend fun runStep(step: NsLoadStep): WorkOutcome {
        val outcome = when (step) {
            NsLoadStep.STATUS            -> loadStatus.run()
            NsLoadStep.LAST_MODIFICATION -> loadLastModification.run()
            NsLoadStep.BG                -> loadBg.run()
            NsLoadStep.TREATMENTS        -> loadTreatments.run()
            NsLoadStep.FOODS             -> loadFoods.run()
            NsLoadStep.PROFILE_STORE     -> loadProfileStore.run()
            NsLoadStep.SETTINGS          -> loadSettings.run()
            NsLoadStep.DEVICE_STATUS     -> loadDeviceStatus.run()
            NsLoadStep.DATA_SYNC         -> dataSync.run()
        }
        if (outcome !is WorkOutcome.Success) {
            aapsLogger.debug(LTag.NSCLIENT, "Load step $step did not succeed: $outcome")
        }
        return outcome
    }
}
