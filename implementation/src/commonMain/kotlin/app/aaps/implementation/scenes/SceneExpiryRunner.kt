package app.aaps.implementation.scenes

import app.aaps.core.data.model.SceneEndAction
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.awaitInitialized
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.scenes.SceneExpiryCheck
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.objects.workflow.WorkOutcome
import app.aaps.core.ui.CoreUiStrings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * What happens when a timed scene reaches its end: revert what does not expire on its own, mark the
 * scene expired, and then either start the chained follow-up scene or tell the user it ended.
 *
 * All of that is a rule, not a platform call - the only Android part was being woken up at the right
 * moment, which is [SceneExpiryScheduler]'s job. `SceneExpiryWorker` is now a shell that holds this
 * and nothing else.
 *
 * Two callers can end a scene: the scheduled end ([run]) and the loop's check for an overdue scene
 * ([endIfOverdue]). They take turns on one lock, and each one checks under it whether the scene has
 * ended already, so a scene is ended once and a chained scene is started once.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class SceneExpiryRunner(
    private val aapsLogger: AAPSLogger,
    private val activeSceneManager: ActiveSceneManager,
    private val sceneExecutor: SceneExecutor,
    private val sceneRepository: SceneRepository,
    private val loop: Loop,
    private val activePlugin: ActivePlugin,
    private val profileFunction: ProfileFunction,
    private val rh: TextResolver,
    private val notificationManager: NotificationManager,
    private val config: Config,
    private val dateUtil: DateUtil
) : SceneExpiryCheck {

    private val mutex = Mutex()

    suspend fun run(sceneName: String): WorkOutcome {
        if (!config.awaitInitialized()) {
            aapsLogger.info(LTag.UI, "SceneExpiryRunner: app not yet initialized, retrying")
            return WorkOutcome.Retry("app not initialized")
        }
        return mutex.withLock { end(sceneName) }
    }

    override suspend fun endIfOverdue() {
        if (!config.appInitialized) return
        mutex.withLock {
            val state = activeSceneManager.getActiveState() ?: return@withLock
            if (state.durationMs <= 0 || activeSceneManager.isExpired()) return@withLock
            if (dateUtil.now() < state.activatedAt + state.durationMs) return@withLock
            aapsLogger.warn(LTag.UI, "Scene '${state.scene.name}' is past its end and its scheduled end has not run, ending it now")
            end(state.scene.name)
        }
    }

    /** Ends the active scene. Call with [mutex] held. */
    private suspend fun end(sceneName: String): WorkOutcome {
        val activeState = activeSceneManager.getActiveState()
            ?: return WorkOutcome.Skipped("no active scene")
        // If a previous run already expired this scene (e.g. it asked to be retried from the init gate
        // above), skip — re-running onExpiry could double-activate a chained scene.
        if (activeSceneManager.isExpired()) return WorkOutcome.Skipped("scene already expired")

        val endAction = activeState.scene.endAction
        sceneExecutor.onExpiry()

        // A plain end says everything it has to say on the banner, which stays until the user closes
        // it, so it gets no notification of its own.
        if (endAction is SceneEndAction.ChainScene) runChain(sceneName, endAction.sceneId)

        return WorkOutcome.Success
    }

    private suspend fun runChain(endedName: String, targetId: String) {
        val target = sceneRepository.getScene(targetId)
        if (target == null) {
            keepSkipReason(rh.gs(CoreUiStrings.scene_chain_skipped_deleted))
            return
        }

        val loopSuspended = loop.runningMode().pausesLoopExecution()
        val pumpInit = activePlugin.activePump.isInitialized()
        val profile = profileFunction.getProfile()

        // Deliberately not SceneChainTargetResolver, which answers the same question as a boolean:
        // here each way of being blocked has to name itself, because the user is told why their
        // follow-up scene did not start.
        val skipReason: String? = when {
            !target.isEnabled            -> rh.gs(CoreUiStrings.scene_chain_skipped_disabled, target.name)
            loopSuspended                -> rh.gs(InterfacesStrings.pump_disconnected)
            !pumpInit || profile == null -> rh.gs(CoreUiStrings.pump_not_initialized_profile_not_set)
            else                         -> null
        }

        if (skipReason != null) {
            keepSkipReason(skipReason)
            return
        }

        val result = sceneExecutor.activate(target)

        // A follow-up that started is its own banner now, so nothing has to be said about it.
        if (!result.success) {
            val failed = result.actionResults.filter { !it.success }
            val details = failed.joinToString("; ") {
                "${it.action::class.simpleName}${it.errorMessage?.let { e -> ": $e" } ?: ""}"
            }
            aapsLogger.error(LTag.UI, "Scene chain '$endedName' → '${target.name}' partial failure — ${failed.size}/${result.actionResults.size} actions failed: $details")
            postChainError(endedName, target.name, failed.size, result.actionResults.size, details)
        }
    }

    /**
     * The follow-up did not start. The reason goes on the expired banner, which the user has to close
     * anyway - a notification next to it would be the same news twice, and two things to confirm.
     */
    private fun keepSkipReason(reason: String) {
        activeSceneManager.setChainSkipReason(reason)
    }

    private fun postChainError(endedName: String, nextName: String, failedCount: Int, totalCount: Int, details: String) {
        val summary = rh.gs(CoreUiStrings.scene_chain_error_summary, endedName, nextName, failedCount, totalCount)
        notificationManager.post(
            id = NotificationId.SCENE_CHAIN_ERROR,
            text = "$summary\n$details"
        )
    }
}
