package app.aaps.receivers

import android.content.Context
import androidx.work.WorkerParameters
import app.aaps.core.data.model.SceneEndAction
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.awaitInitialized
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.core.ui.R
import app.aaps.ui.compose.scenes.ActiveSceneManager
import app.aaps.ui.compose.scenes.SceneExecutor
import app.aaps.ui.compose.scenes.SceneRepository
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

/**
 * WorkManager worker that fires when a scene's duration expires.
 * Reverts non-duration actions (SMB), marks scene as expired, and either
 * auto-activates a chained follow-up scene or posts an "ended" notification.
 */
class SceneExpiryWorker(
    context: Context,
    params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.Default) {

    @Inject lateinit var activeSceneManager: ActiveSceneManager
    @Inject lateinit var sceneExecutor: SceneExecutor
    @Inject lateinit var sceneRepository: SceneRepository
    @Inject lateinit var loop: Loop
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var notificationManager: NotificationManager
    @Inject lateinit var config: Config

    override suspend fun doWorkAndLog(): Result {
        if (!config.awaitInitialized()) {
            aapsLogger.info(LTag.UI, "SceneExpiryWorker: app not yet initialized, retrying")
            return Result.retry()
        }
        val sceneName = inputData.getString(KEY_SCENE_NAME) ?: "Scene"
        aapsLogger.info(LTag.UI, "XXXX SceneExpiryWorker fired for '$sceneName'")
        val activeState = activeSceneManager.getActiveState()
        if (activeState == null) {
            aapsLogger.info(LTag.UI, "XXXX no active state — worker exiting")
            return Result.success()
        }
        // If a previous run already expired this scene (e.g. WorkManager retried
        // after we returned Result.retry from the init gate), skip — re-running
        // onExpiry could double-activate a chained scene.
        if (activeSceneManager.isExpired()) {
            aapsLogger.info(LTag.UI, "XXXX scene already expired — worker exiting")
            return Result.success()
        }
        aapsLogger.info(LTag.UI, "XXXX active=${activeState.scene.name} id=${activeState.scene.id} endAction=${activeState.scene.endAction}")

        val endAction = activeState.scene.endAction
        aapsLogger.info(LTag.UI, "XXXX calling onExpiry() — reverting non-duration actions")
        sceneExecutor.onExpiry()
        aapsLogger.info(LTag.UI, "XXXX onExpiry() returned; activeState now=${activeSceneManager.getActiveState()?.scene?.name} expired=${activeSceneManager.isActive()}")

        if (endAction is SceneEndAction.ChainScene) {
            aapsLogger.info(LTag.UI, "XXXX endAction is ChainScene → targetId=${endAction.sceneId}")
            runChain(sceneName, endAction.sceneId)
        } else {
            aapsLogger.info(LTag.UI, "XXXX no chain configured (endAction=$endAction) → posting ended notification")
            postEndedNotification(sceneName)
        }

        aapsLogger.info(LTag.UI, "XXXX SceneExpiryWorker finishing; final activeState=${activeSceneManager.getActiveState()?.scene?.name}")
        return Result.success()
    }

    private suspend fun runChain(endedName: String, targetId: String) {
        aapsLogger.info(LTag.UI, "XXXX runChain from '$endedName' to targetId=$targetId")
        val target = sceneRepository.getScene(targetId)
        if (target == null) {
            aapsLogger.info(LTag.UI, "XXXX target $targetId NOT FOUND in repository — skipping")
            postEndedWithSkip(endedName, rh.gs(R.string.scene_chain_skipped_deleted))
            return
        }
        aapsLogger.info(LTag.UI, "XXXX target resolved: name='${target.name}' enabled=${target.isEnabled} actions=${target.actions.size} duration=${target.defaultDurationMinutes}min")

        val loopSuspended = loop.runningMode.isSuspended()
        val pumpInit = activePlugin.activePump.isInitialized()
        val profile = profileFunction.getProfile()
        aapsLogger.info(LTag.UI, "XXXX preconditions: loopSuspended=$loopSuspended pumpInit=$pumpInit profile=${profile != null}")

        val skipReason: String? = when {
            !target.isEnabled            -> rh.gs(R.string.scene_chain_skipped_disabled, target.name)
            loopSuspended                -> rh.gs(R.string.pump_disconnected)
            !pumpInit || profile == null -> rh.gs(R.string.pump_not_initialized_profile_not_set)
            else                         -> null
        }

        if (skipReason != null) {
            aapsLogger.info(LTag.UI, "XXXX chain SKIPPED: $skipReason")
            postEndedWithSkip(endedName, skipReason)
            return
        }

        aapsLogger.info(LTag.UI, "XXXX calling sceneExecutor.activate('${target.name}')")
        val result = try {
            sceneExecutor.activate(target)
        } catch (e: Throwable) {
            aapsLogger.error(LTag.UI, "XXXX sceneExecutor.activate('${target.name}') THREW", e)
            throw e
        }
        aapsLogger.info(LTag.UI, "XXXX activate() returned: success=${result.success} error=${result.errorMessage}")
        aapsLogger.info(LTag.UI, "XXXX actionResults: ${result.actionResults.joinToString { "${it.action::class.simpleName}=${it.success}${it.errorMessage?.let { e -> "($e)" } ?: ""}" }}")
        aapsLogger.info(LTag.UI, "XXXX post-activate activeState=${activeSceneManager.getActiveState()?.scene?.name}")

        if (result.success) {
            postChainSuccess(endedName, target.name)
        } else {
            val failed = result.actionResults.filter { !it.success }
            val details = failed.joinToString("; ") {
                "${it.action::class.simpleName}${it.errorMessage?.let { e -> ": $e" } ?: ""}"
            }
            aapsLogger.error(LTag.UI, "XXXX chain '$endedName' → '${target.name}' partial failure — ${failed.size}/${result.actionResults.size} actions failed: $details")
            postChainError(endedName, target.name, failed.size, result.actionResults.size, details)
        }
    }

    private fun postEndedNotification(sceneName: String) {
        notificationManager.post(
            id = NotificationId.SCENE_ENDED,
            text = rh.gs(R.string.scene_ended_format, sceneName)
        )
    }

    private fun postChainSuccess(endedName: String, nextName: String) {
        notificationManager.post(
            id = NotificationId.SCENE_CHAINED,
            text = rh.gs(R.string.scene_chained_format, endedName, nextName)
        )
    }

    private fun postEndedWithSkip(endedName: String, reason: String) {
        notificationManager.post(
            id = NotificationId.SCENE_CHAIN_SKIPPED,
            text = rh.gs(R.string.scene_chain_ended_with_skip, endedName, reason)
        )
    }

    private fun postChainError(endedName: String, nextName: String, failedCount: Int, totalCount: Int, details: String) {
        val summary = rh.gs(R.string.scene_chain_error_summary, endedName, nextName, failedCount, totalCount)
        notificationManager.post(
            id = NotificationId.SCENE_CHAIN_ERROR,
            text = "$summary\n$details"
        )
    }

    companion object {

        const val KEY_SCENE_NAME = "scene_name"
    }
}
