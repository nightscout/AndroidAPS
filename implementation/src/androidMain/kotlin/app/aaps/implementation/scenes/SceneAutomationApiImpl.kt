package app.aaps.implementation.scenes

import app.aaps.core.data.model.Scene
import app.aaps.core.data.model.SceneEndAction
import app.aaps.core.interfaces.bolus.WizardBolusExecutor
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.scenes.SceneAutomationApi
import app.aaps.core.interfaces.scenes.SceneAutomationResult
import app.aaps.core.ui.CoreUiStrings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

// Metro builds this; Dagger receives it via a @Provides delegate in `:app`. Metro's @SingleIn,
// not javax @Singleton, because the graph is generated in `:app`.
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class SceneAutomationApiImpl @Inject constructor(
    private val sceneRepository: SceneRepository,
    private val sceneExecutor: SceneExecutor,
    private val activeSceneManager: ActiveSceneManager,
    private val sceneChainTargetResolver: SceneChainTargetResolver,
    private val notificationManager: NotificationManager,
    private val rh: TextResolver
) : SceneAutomationApi {

    override val scenesFlow: StateFlow<String> get() = sceneRepository.scenesFlow

    // An expired scene keeps its slot until the user dismisses the banner, so "a scene exists" and
    // "a scene is in force" are different questions. isActive() answers the first one.
    override fun isAnySceneActive(): Boolean = activeSceneManager.isActive() && !activeSceneManager.isExpired()

    override fun hasSceneToStop(): Boolean = activeSceneManager.isActive()

    override val activeFlow: Flow<Boolean> =
        activeSceneManager.activeSceneState.map { it != null }.distinctUntilChanged()

    override suspend fun stopActiveScene(): SceneAutomationResult =
        when {
            activeSceneManager.isExpired() -> {
                sceneExecutor.dismiss()
                SceneAutomationResult.Success
            }

            activeSceneManager.isActive()  -> {
                val result = sceneExecutor.deactivate()
                if (result.success) SceneAutomationResult.Success
                else SceneAutomationResult.Failed(result.errorMessage)
            }

            else                           -> SceneAutomationResult.Success
        }

    override fun getScenes(): List<Scene> = sceneRepository.getScenes()

    override fun getScene(id: String): Scene? = sceneRepository.getScene(id)

    override suspend fun runScene(id: String, durationMinutes: Int?): SceneAutomationResult {
        val scene = sceneRepository.getScene(id) ?: return SceneAutomationResult.SceneNotFound
        if (!scene.isEnabled) return SceneAutomationResult.SceneDisabled
        val effective = durationMinutes ?: scene.defaultDurationMinutes
        val result = sceneExecutor.activate(scene, effective)
        return if (result.success) SceneAutomationResult.Success
        else SceneAutomationResult.Failed(result.errorMessage)
    }

    override suspend fun prepareScene(id: String, durationMinutes: Int?): WizardBolusExecutor.PrepareResult {
        val scene = sceneRepository.getScene(id) ?: return WizardBolusExecutor.PrepareResult.Error(rh.gs(CoreUiStrings.clientcontrol_fail_scene_not_found))
        if (!scene.isEnabled) return WizardBolusExecutor.PrepareResult.Error(rh.gs(CoreUiStrings.clientcontrol_fail_scene_disabled))
        return sceneExecutor.prepareScene(scene, durationMinutes)
    }

    override suspend fun commitScene(bolusId: Long, onError: (String) -> Unit): WizardBolusExecutor.ConfirmResult =
        sceneExecutor.commitScene(bolusId, onError)

    override suspend fun stopActiveSceneAndStartScene(targetSceneId: String): SceneAutomationResult {
        // TOCTOU re-check: caller (dialog or wire command) captured the target id at some earlier
        // moment; in the gap the target may have been disabled/deleted, the pump dropped, the loop
        // suspended, or the profile cleared. End the current scene cleanly either way; only
        // activate the target if everything is still healthy.
        val endedName = activeSceneManager.getActiveState()?.scene?.name
        val target = sceneRepository.getScene(targetSceneId)
        val canActivate = target != null && target.isEnabled && sceneChainTargetResolver.runtimeAllowsActivation()
        val deactivateResult = sceneExecutor.deactivate()
        if (!deactivateResult.success) return SceneAutomationResult.Failed(deactivateResult.errorMessage)
        if (!canActivate) return when {
            target == null    -> SceneAutomationResult.SceneNotFound
            !target.isEnabled -> SceneAutomationResult.SceneDisabled
            else              -> SceneAutomationResult.Failed(null) // preconditions failed (loop/pump/profile)
        }
        val activateResult = sceneExecutor.activate(target, target.defaultDurationMinutes)
        // ChainCompleted carries action-level detail so callers (notifications) can render the
        // "ended → target: X of Y failed" message without re-implementing the activation logic.
        return SceneAutomationResult.ChainCompleted(
            endedSceneName = endedName,
            targetSceneName = target.name,
            failedCount = activateResult.actionResults.count { !it.success },
            totalCount = activateResult.actionResults.size
        )
    }

    override suspend fun stopActiveSceneAndChain(): SceneAutomationResult {
        // Resolve the chain target from the active scene's own endAction (no caller-supplied id), then
        // delegate to the single canonical stop+start path (which TOCTOU-re-checks preconditions).
        val targetId = (activeSceneManager.getActiveState()?.scene?.endAction as? SceneEndAction.ChainScene)?.sceneId
        val result = if (targetId != null) stopActiveSceneAndStartScene(targetId) else stopActiveScene()
        // Chain-completion notification — posted here so it fires identically whether the chain was
        // triggered from the End-Scene dialog or a client's scene.stop(triggerChain) command.
        if (result is SceneAutomationResult.ChainCompleted) {
            // Read into a local: the format arguments are non-null now, and a public property of
            // another module cannot be smart cast.
            val endedSceneName = result.endedSceneName
            if (result.failedCount == 0 && endedSceneName != null)
                notificationManager.post(
                    id = NotificationId.SCENE_CHAINED,
                    text = rh.gs(CoreUiStrings.scene_chained_format, endedSceneName, result.targetSceneName)
                )
            else if (result.failedCount > 0)
                notificationManager.post(
                    id = NotificationId.SCENE_CHAIN_ERROR,
                    text = rh.gs(CoreUiStrings.scene_chain_error_summary, endedSceneName ?: "", result.targetSceneName, result.failedCount, result.totalCount)
                )
        }
        return result
    }

    override fun setEnabled(id: String, enabled: Boolean): SceneAutomationResult {
        val scene = sceneRepository.getScene(id) ?: return SceneAutomationResult.SceneNotFound
        if (scene.isEnabled == enabled) return SceneAutomationResult.Success
        sceneRepository.saveScene(scene.copy(isEnabled = enabled))
        return SceneAutomationResult.Success
    }
}
