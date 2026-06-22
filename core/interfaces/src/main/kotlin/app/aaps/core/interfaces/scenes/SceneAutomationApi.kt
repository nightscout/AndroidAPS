package app.aaps.core.interfaces.scenes

import app.aaps.core.data.model.Scene
import app.aaps.core.interfaces.bolus.WizardBolusExecutor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Surface exposed to the automation plugin and the wear-sync plugin so scene
 * actions can list, run, enable and disable scenes without depending on :ui.
 */
interface SceneAutomationApi {

    /** All scenes (enabled and disabled). */
    fun getScenes(): List<Scene>

    /** Scene by id, or null if not found. */
    fun getScene(id: String): Scene?

    /** Observable serialized scene list — emits on add / update / delete.
     *  Consumers (e.g. wear-sync) treat this as an opaque "scenes changed" signal. */
    val scenesFlow: StateFlow<String>

    /**
     * Activate a scene. Fails if missing or disabled.
     * @param durationMinutes override duration; null uses the scene's default.
     */
    suspend fun runScene(id: String, durationMinutes: Int? = null): SceneAutomationResult

    /**
     * Two-step PREPARE: resolve scene [id], gate it, and author the master's confirmation lines + park it consume-once.
     * Returns the preview ([WizardBolusExecutor.PrepareResult.Preview] = bolusId + lines) or an Error; nothing is
     * activated. Commit via [commitScene] with the returned bolusId. The master is the SSOT for the confirmation.
     */
    suspend fun prepareScene(id: String, durationMinutes: Int? = null): WizardBolusExecutor.PrepareResult

    /** Two-step COMMIT: activate the parked scene matching [bolusId] EXACTLY once; an activation failure rides [onError]. */
    suspend fun commitScene(bolusId: Long, onError: (String) -> Unit): WizardBolusExecutor.ConfirmResult

    /** Set the enabled flag on a scene by id. Fails only if the scene is missing. */
    fun setEnabled(id: String, enabled: Boolean): SceneAutomationResult

    /** Whether a scene is currently active (running, not yet expired). */
    fun isAnySceneActive(): Boolean

    /** Emits true when there's a stoppable scene state — running OR expired-with-banner.
     *  Used by wear-sync to drive the tile's stop button visibility. */
    val activeFlow: Flow<Boolean>

    /** End the active scene (deactivate) or dismiss the expired banner. No-op if nothing active. */
    suspend fun stopActiveScene(): SceneAutomationResult

    /**
     * Deactivate the active scene then activate [targetSceneId] as a chain follow-up.
     * Re-checks canChain preconditions (target enabled, loop running, pump initialized, profile set)
     * at execute time as a TOCTOU guard — drops back to plain deactivate if anything's off.
     * Single canonical place for the "Skip to <X>" choice exposed by the End Scene dialog and the
     * scene.stop client-control command with triggerChain=true.
     */
    suspend fun stopActiveSceneAndStartScene(targetSceneId: String): SceneAutomationResult

    /**
     * Deactivate the active scene, and if it has a configured chain target, fire it — resolving the
     * target from the active scene's own `endAction` internally (no caller-supplied id). Posts the
     * SCENE_CHAINED / SCENE_CHAIN_ERROR notification on completion. This is the single master-side entry
     * for "stop + chain", shared by the End-Scene dialog, the `scene.stop(triggerChain=true)` command,
     * so the chain (and its notification) behaves identically however it was triggered. Falls back to a
     * plain [stopActiveScene] when there's no chain target.
     */
    suspend fun stopActiveSceneAndChain(): SceneAutomationResult
}

sealed interface SceneAutomationResult {

    data object Success : SceneAutomationResult
    data object SceneNotFound : SceneAutomationResult
    data object SceneDisabled : SceneAutomationResult
    data class Failed(val message: String?) : SceneAutomationResult

    /**
     * Returned only by [SceneAutomationApi.stopActiveSceneAndStartScene] when the chain
     * follow-up actually reached the target (the active scene was deactivated AND the
     * target was activated). Carries action-level detail so callers can present a richer
     * notification than [Failed]'s opaque message would allow.
     *
     * - [failedCount] == 0 → every target action succeeded (treat as success).
     * - [failedCount] > 0  → target was activated but some actions failed (the
     *   "X of Y failed" case the End Scene dialog surfaces as SCENE_CHAIN_ERROR).
     *
     * [endedSceneName] is null only if no scene was actually active at deactivate time
     * (the dialog could have raced an expiry); in that case the chain still ran.
     */
    data class ChainCompleted(
        val endedSceneName: String?,
        val targetSceneName: String,
        val failedCount: Int,
        val totalCount: Int
    ) : SceneAutomationResult
}
