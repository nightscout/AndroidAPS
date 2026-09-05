package app.aaps.desktop.shell.platform

import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.objects.workflow.WorkOutcome
import app.aaps.plugins.sync.nsclientV3.ws.NsLoadChain
import app.aaps.plugins.sync.nsclientV3.workers.DataSyncRunner
import app.aaps.plugins.sync.nsclientV3.workers.LoadBgRunner
import app.aaps.plugins.sync.nsclientV3.workers.LoadDeviceStatusRunner
import app.aaps.plugins.sync.nsclientV3.workers.LoadFoodsRunner
import app.aaps.plugins.sync.nsclientV3.workers.LoadLastModificationRunner
import app.aaps.plugins.sync.nsclientV3.workers.LoadProfileStoreRunner
import app.aaps.plugins.sync.nsclientV3.workers.LoadSettingsRunner
import app.aaps.plugins.sync.nsclientV3.workers.LoadStatusRunner
import app.aaps.plugins.sync.nsclientV3.workers.LoadTreatmentsRunner
import app.aaps.plugins.sync.nsclientV3.ws.NsLoadExecutor
import app.aaps.plugins.sync.nsclientV3.ws.NsLoadStep
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The Nightscout load round on coroutines, where Android uses WorkManager.
 *
 * Real work rather than a placeholder, and cheaper than it looks: the WorkManager classes were only
 * ever 28-line shims. Each one says "the work itself is `XxxRunner`, which is shared", and those
 * runners are already in `commonMain`. This calls the same nine, in the same order, so a desktop
 * follower loads exactly what a phone loads.
 *
 * ## What is different from Android, and why it is acceptable here
 *
 * Android runs the round as workers so that a round started from the overview finishes even if the
 * screen goes away, and survives the process being killed. A desktop window has no such lifecycle -
 * closing it is closing the app - so an in-process coroutine covers the same ground. What is lost is
 * a round resuming after a crash; the next start simply loads again, which is what a follower does
 * anyway.
 *
 * The runners are held as [Provider]s. `NSClientV3Plugin` holds this executor, and the runners reach
 * back into the plugin's own dependencies, so resolving them while the graph is being built is a
 * cycle. Deferring means the first lookup happens when a round actually runs.
 *
 * One round at a time, keyed the same way the WorkManager unique name is: [runChain] and
 * [runReplacing] cancel whatever was running, [runDetached] does not.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DesktopNsLoadExecutor @Inject constructor(
    private val aapsLogger: AAPSLogger,
    @ApplicationScope private val scope: CoroutineScope,
    private val loadStatus: () -> LoadStatusRunner,
    private val loadLastModification: () -> LoadLastModificationRunner,
    private val loadBg: () -> LoadBgRunner,
    private val loadTreatments: () -> LoadTreatmentsRunner,
    private val loadFoods: () -> LoadFoodsRunner,
    private val loadProfileStore: () -> LoadProfileStoreRunner,
    private val loadSettings: () -> LoadSettingsRunner,
    private val loadDeviceStatus: () -> LoadDeviceStatusRunner,
    private val dataSync: () -> DataSyncRunner
) : NsLoadExecutor {

    private var round: Job? = null

    private val _idle = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val isRunning: Boolean get() = round?.isActive == true

    /** Emits when a round finishes, which is what the plugin waits on before starting another. */
    override val idle: Flow<Unit> = _idle.asSharedFlow()

    override fun runChain(steps: List<NsLoadStep>) {
        if (steps.isEmpty()) return
        start(steps)
    }

    override fun runReplacing(step: NsLoadStep) {
        start(listOf(step))
    }

    /**
     * Runs alongside whatever is already going, and does not report idle.
     *
     * Detached work is not part of the round, so letting it finish must not tell the plugin the
     * round is over - that would start the next one early.
     */
    override fun runDetached(step: NsLoadStep) {
        scope.launch(Dispatchers.IO) { runStep(step) }
    }

    override fun cancel() {
        round?.cancel()
        round = null
    }

    private fun start(steps: List<NsLoadStep>) {
        cancel()
        round = scope.launch(Dispatchers.IO) {
            for (step in steps) {
                // A cancelled round stops between steps rather than part way through one, so a step
                // either ran or did not - it is never half applied to the database.
                if (!isActive) break
                // Stops at the first step that fails, through the same rule the other two platforms
                // use. This used to run the whole chain regardless: the outcome was computed, logged
                // and dropped. A failed BG step still reached LoadDeviceStatusRunner, which sets
                // initialLoadFinished = true, so the round declared the catch-up done and
                // executeLoop returned early from then on - the missed window never fetched, and no
                // error left to show for it because a later step had cleared it.
                if (!NsLoadChain.shouldContinue(runStep(step))) break
            }
        }.also { started ->
            // Idle is reported from invokeOnCompletion, not as the last line of the block above, so
            // that a **cancelled** round reports it too. The plugin queues its next round on this
            // signal, so a round that ends without it leaves pendingLoop and pendingUpload set and
            // the queued uploads waiting for an unrelated trigger.
            //
            // It did work before, but only by accident: the emit was reached because the cancellation
            // was swallowed upstream, and `emit` happened to take a fast path rather than suspending
            // and throwing. Nothing pinned either of those. This is the arrangement iOS already uses -
            // see `CoroutineNsLoadExecutor.replaceRound` - and DROP_OLDEST above is the other half of
            // it, so tryEmit cannot silently fail on a full buffer.
            started.invokeOnCompletion { _idle.tryEmit(Unit) }
        }
    }

    /**
     * One step, with a thrown runner counted as a failure.
     *
     * The iOS executor lets a throw propagate and kill the round. Catching keeps this shell running
     * and reaches the same decision, which is the behaviour worth keeping of the two.
     */
    private suspend fun runStep(step: NsLoadStep): WorkOutcome {
        val outcome = runCatching { runnerFor(step)() }
            .getOrElse {
                aapsLogger.error(LTag.NSCLIENT, "Load step $step failed: ${it.message}")
                WorkOutcome.Failure(it.message ?: "threw")
            }
        aapsLogger.debug(LTag.NSCLIENT, "Load step $step: $outcome")
        return outcome
    }

    /** The same mapping the WorkManager executor makes, one runner per step. */
    private fun runnerFor(step: NsLoadStep): suspend () -> WorkOutcome = when (step) {
        NsLoadStep.STATUS            -> ({ loadStatus().run() })
        NsLoadStep.LAST_MODIFICATION -> ({ loadLastModification().run() })
        NsLoadStep.BG                -> ({ loadBg().run() })
        NsLoadStep.TREATMENTS        -> ({ loadTreatments().run() })
        NsLoadStep.FOODS             -> ({ loadFoods().run() })
        NsLoadStep.PROFILE_STORE     -> ({ loadProfileStore().run() })
        NsLoadStep.SETTINGS          -> ({ loadSettings().run() })
        NsLoadStep.DEVICE_STATUS     -> ({ loadDeviceStatus().run() })
        NsLoadStep.DATA_SYNC         -> ({ dataSync().run() })
    }
}
