package app.aaps.implementation.scenes

import app.aaps.core.data.model.Scene
import app.aaps.core.data.model.SceneEndAction
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.scenes.SceneChainResolver
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for the "can we chain to a follow-up scene right now?" policy.
 *
 * The check has three customers that previously each held their own inline copy:
 * - [MainViewModel.requestSceneDeactivation] — builds the End Scene 3-option dialog state
 * - [MainViewModel.executeConfirmableAction] (DeactivateAndChainScene branch) — TOCTOU re-check
 * - [SceneListViewModel.requestDeactivation] — same dialog from the Scenes list screen
 *
 * Conditions: the active scene's `endAction` is a [SceneEndAction.ChainScene], the target
 * scene exists in the catalog and is enabled, and the master's runtime state allows scene
 * activation right now (loop running, pump initialized, profile set).
 *
 * Note: on AAPSClient the [Loop] / [ActivePlugin] / [ProfileFunction] reflect *local* device
 * state, not the master's. On a stripped-down AAPSClient device pump probably reports
 * not-initialized, which would suppress the "Skip to X" affordance even when the master
 * could in fact run the chain. The receiver re-checks runtime allowance at command receipt
 * via [SceneAutomationApiImpl.stopActiveSceneAndStartScene], so the dialog being too
 * conservative on AAPSClient is the safe direction — never offers a chain that would later
 * fail at execution.
 */
@Singleton
class SceneChainTargetResolver @Inject constructor(
    private val loop: Loop,
    private val activePlugin: ActivePlugin,
    private val profileFunction: ProfileFunction,
    private val sceneRepository: SceneRepository
) : SceneChainResolver {

    /** True iff the master's runtime state currently allows activating any scene. Suspend
     *  because the underlying loop / profile lookups suspend. */
    suspend fun runtimeAllowsActivation(): Boolean =
        !loop.runningMode().pausesLoopExecution() &&
            activePlugin.activePump.isInitialized() &&
            profileFunction.getProfile() != null

    /**
     * **Master-side** chain resolution — full check including runtime preconditions. Used by
     * the master to decide whether to offer "Skip to X" in its own End Scene dialog and as
     * the TOCTOU re-check at command receipt time.
     */
    override suspend fun resolveRunnableChainTarget(activeScene: Scene): Scene? {
        val chain = activeScene.endAction as? SceneEndAction.ChainScene ?: return null
        val target = sceneRepository.getScene(chain.sceneId) ?: return null
        if (!target.isEnabled) return null
        if (!runtimeAllowsActivation()) return null
        return target
    }

    /**
     * **AAPSClient-side** chain resolution — catalog-only check, ignores runtime preconditions.
     *
     * AAPSClient cannot evaluate the master's loop/pump/profile state from synced data alone
     * — its own [Loop] / [ActivePlugin] / [ProfileFunction] reflect a stripped-down local
     * device, where the pump is typically not initialized at all. Using [resolveRunnableChainTarget]
     * on AAPSClient would suppress the "Skip to X" affordance even when the master could
     * happily run the chain.
     *
     * Master re-validates runtime allowance at command receipt via
     * [SceneAutomationApiImpl.stopActiveSceneAndStartScene], so showing the option on
     * AAPSClient when master would in fact reject is harmless — the user gets a `no-chain-target`
     * outcome in the NS log and a plain stop, identical to what they'd see if they triggered it
     * locally on master while the loop was suspended.
     */
    override fun resolveCatalogChainTarget(activeScene: Scene): Scene? {
        val chain = activeScene.endAction as? SceneEndAction.ChainScene ?: return null
        val target = sceneRepository.getScene(chain.sceneId) ?: return null
        if (!target.isEnabled) return null
        return target
    }
}
