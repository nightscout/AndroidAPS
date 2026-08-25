package app.aaps.implementation.scenes

import app.aaps.core.data.model.Scene
import app.aaps.core.interfaces.clientcontrol.ActionProgress
import app.aaps.core.interfaces.clientcontrol.ClientControlActionDispatcher
import app.aaps.core.interfaces.clientcontrol.FailureReason
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.scenes.SceneActions
import app.aaps.core.interfaces.scenes.SceneAutomationApi
import app.aaps.core.interfaces.scenes.SceneAutomationResult
import app.aaps.core.ui.R
import app.aaps.implementation.bolus.RoleBranch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SceneActions] implementation. Scene START is the two-step master-controlled flow (the master authors the
 * confirmation) routed through the shared [RoleBranch] (client = round-trip, master = local) — reusing the same
 * prepare/commit plumbing as bolus/batch. Scene STOP stays single-step via [ClientControlActionDispatcher.execute]
 * (already master-authoritative — no preview to review). [validateActivation] is a pure query, untouched.
 */
@Singleton
class SceneActionsImpl @Inject constructor(
    private val dispatcher: ClientControlActionDispatcher,
    private val roleBranch: RoleBranch,
    private val sceneApi: SceneAutomationApi,
    private val sceneExecutor: SceneExecutor,
    private val rh: ResourceHelper
) : SceneActions {

    override suspend fun validateActivation(scene: Scene): String? = sceneExecutor.validateActivation(scene)

    override suspend fun prepareStart(sceneId: String, durationMinutes: Int?): ActionProgress =
        roleBranch.prepare(
            rh.gs(R.string.clientcontrol_action_activate_scene, sceneApi.getScene(sceneId)?.name ?: ""),
            ClientControlActionDispatcher.Command.ScenePrepare(sceneId, durationMinutes)
        ) { sceneApi.prepareScene(sceneId, durationMinutes) }

    override suspend fun commitStart(id: Long): ActionProgress =
        roleBranch.commit(
            rh.gs(R.string.scene),
            ClientControlActionDispatcher.Command.SceneCommit(id)
        ) { onError -> sceneApi.commitScene(id, onError) }

    override suspend fun stop(triggerChain: Boolean): ActionProgress =
        dispatcher.execute(
            ClientControlActionDispatcher.Command.SceneStop(triggerChain),
            rh.gs(R.string.clientcontrol_action_deactivate_scene)
        ) { (if (triggerChain) sceneApi.stopActiveSceneAndChain() else sceneApi.stopActiveScene()).toProgress() }

    // Master-side mapping only (the client path's terminal comes from the master's ACK).
    private fun SceneAutomationResult.toProgress(): ActionProgress = when (this) {
        SceneAutomationResult.Success,
        is SceneAutomationResult.ChainCompleted -> ActionProgress.Applied

        SceneAutomationResult.SceneNotFound     -> ActionProgress.Rejected(FailureReason.SceneNotFound)
        SceneAutomationResult.SceneDisabled     -> ActionProgress.Rejected(FailureReason.SceneDisabled)
        is SceneAutomationResult.Failed         -> ActionProgress.Rejected(FailureReason.ExecutionFailed, message)
    }
}
