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
import app.aaps.core.objects.workflow.WorkOutcome
import app.aaps.core.ui.CoreUiStrings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * What happens when a timed scene reaches its end: revert what does not expire on its own, mark the
 * scene expired, and then either start the chained follow-up scene or tell the user it ended.
 *
 * All of that is a rule, not a platform call - the only Android part was being woken up at the right
 * moment, which is [SceneExpiryScheduler]'s job. `SceneExpiryWorker` is now a shell that holds this
 * and nothing else.
 */
@SingleIn(AppScope::class)
class SceneExpiryRunner @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val activeSceneManager: ActiveSceneManager,
    private val sceneExecutor: SceneExecutor,
    private val sceneRepository: SceneRepository,
    private val loop: Loop,
    private val activePlugin: ActivePlugin,
    private val profileFunction: ProfileFunction,
    private val rh: TextResolver,
    private val notificationManager: NotificationManager,
    private val config: Config
) {

    suspend fun run(sceneName: String): WorkOutcome {
        if (!config.awaitInitialized()) {
            aapsLogger.info(LTag.UI, "SceneExpiryRunner: app not yet initialized, retrying")
            return WorkOutcome.Retry("app not initialized")
        }
        val activeState = activeSceneManager.getActiveState()
            ?: return WorkOutcome.Skipped("no active scene")
        // If a previous run already expired this scene (e.g. it asked to be retried from the init gate
        // above), skip — re-running onExpiry could double-activate a chained scene.
        if (activeSceneManager.isExpired()) return WorkOutcome.Skipped("scene already expired")

        val endAction = activeState.scene.endAction
        sceneExecutor.onExpiry()

        if (endAction is SceneEndAction.ChainScene) {
            runChain(sceneName, endAction.sceneId)
        } else {
            postEndedNotification(sceneName)
        }

        return WorkOutcome.Success
    }

    private suspend fun runChain(endedName: String, targetId: String) {
        val target = sceneRepository.getScene(targetId)
        if (target == null) {
            postEndedWithSkip(endedName, rh.gs(CoreUiStrings.scene_chain_skipped_deleted))
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
            postEndedWithSkip(endedName, skipReason)
            return
        }

        val result = sceneExecutor.activate(target)

        if (result.success) {
            postChainSuccess(endedName, target.name)
        } else {
            val failed = result.actionResults.filter { !it.success }
            val details = failed.joinToString("; ") {
                "${it.action::class.simpleName}${it.errorMessage?.let { e -> ": $e" } ?: ""}"
            }
            aapsLogger.error(LTag.UI, "Scene chain '$endedName' → '${target.name}' partial failure — ${failed.size}/${result.actionResults.size} actions failed: $details")
            postChainError(endedName, target.name, failed.size, result.actionResults.size, details)
        }
    }

    private fun postEndedNotification(sceneName: String) {
        notificationManager.post(
            id = NotificationId.SCENE_ENDED,
            text = rh.gs(CoreUiStrings.scene_ended_format, sceneName)
        )
    }

    private fun postChainSuccess(endedName: String, nextName: String) {
        notificationManager.post(
            id = NotificationId.SCENE_CHAINED,
            text = rh.gs(CoreUiStrings.scene_chained_format, endedName, nextName)
        )
    }

    private fun postEndedWithSkip(endedName: String, reason: String) {
        notificationManager.post(
            id = NotificationId.SCENE_CHAIN_SKIPPED,
            text = rh.gs(CoreUiStrings.scene_chain_ended_with_skip, endedName, reason)
        )
    }

    private fun postChainError(endedName: String, nextName: String, failedCount: Int, totalCount: Int, details: String) {
        val summary = rh.gs(CoreUiStrings.scene_chain_error_summary, endedName, nextName, failedCount, totalCount)
        notificationManager.post(
            id = NotificationId.SCENE_CHAIN_ERROR,
            text = "$summary\n$details"
        )
    }
}
