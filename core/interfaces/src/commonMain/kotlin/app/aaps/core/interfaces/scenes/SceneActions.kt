package app.aaps.core.interfaces.scenes

import app.aaps.core.data.model.Scene
import app.aaps.core.interfaces.clientcontrol.ActionProgress

/**
 * Per-domain execution facade for scene actions, hiding the master-vs-client split from callers (VMs).
 * The same call works on both roles:
 *  - on a **master** it executes locally (via [SceneAutomationApi]) and returns the terminal outcome
 *    immediately — no modal;
 *  - on an **AAPSCLIENT** it goes through the confirmed client→master round-trip, which drives the
 *    single app-level pending modal and returns the master's terminal [ActionProgress].
 *
 * Callers just `start`/`stop` and react to the returned terminal (e.g. nothing on a plain success);
 * the modal is owned centrally, not by the screen.
 */
interface SceneActions {

    /**
     * Two-step PREPARE activating scene [sceneId] (null [durationMinutes] = the scene's stored default): the MASTER
     * authors the confirmation and returns [ActionProgress.Prepared] (its `id` + `lines`) for the UI to render, or a
     * [ActionProgress.Rejected]. Nothing is activated — the user confirms the master's lines and [commitStart] applies it.
     * Client → signed round-trip; master → local.
     */
    suspend fun prepareStart(sceneId: String, durationMinutes: Int? = null): ActionProgress

    /** Confirm a prepared scene by [id] (the `id` from a prior [prepareStart]'s [ActionProgress.Prepared]): activate it exactly once. */
    suspend fun commitStart(id: Long): ActionProgress

    /** End the active scene; [triggerChain] = also fire its configured chain target (master derives it). */
    suspend fun stop(triggerChain: Boolean = false): ActionProgress

    /** Validation gate for activating [scene] (pump/loop/profile/actions); null = OK, else a reason. */
    suspend fun validateActivation(scene: Scene): String?
}
