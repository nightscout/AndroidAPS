package app.aaps.core.interfaces.bolus

import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.clientcontrol.ActionProgress

/**
 * Role-transparent **recompute** bolus path (QuickWizard WIZARD-mode + manual Bolus Wizard) — the sibling of
 * [BatchExecutor] for the cases where the master must RECOMPUTE the dose (vs [BatchExecutor]'s fixed/capped
 * amounts). A caller builds a [WizardSource] and calls [prepare] → [commit]; on a paired CLIENT the request is
 * routed to the master over the signed round-trip, on a MASTER it runs locally — one audited path either way,
 * and the master is the sole author of the confirmation lines both roles render (`_docs/GENERAL_EXECUTION_PATH_PLAN.md`).
 *
 * Two-step like [BatchExecutor]: [prepare] caps + parks + returns the master's [ActionProgress.Prepared] (bolusId
 * + color-coded lines + the high-BG advisor fork), [commit] delivers the parked dose exactly once. Record-only
 * (a master that can't deliver) is NOT modelled here — it's a master-local log with no role dimension; the dialog
 * keeps that on its local path.
 */
interface WizardExecutor {

    /**
     * Ask the master to PREPARE the wizard bolus for [source] (compute on the master's own profile / temp target /
     * COB / IOB, constraint-cap, park) and return [ActionProgress.Prepared] for the caller to render, or an
     * [ActionProgress.Rejected]. Client → signed round-trip; master → local. Offline on a client → Rejected.NotReachable.
     * [label] shows in the round-trip modal.
     */
    suspend fun prepare(source: WizardSource, label: String): ActionProgress

    /**
     * Commit a prepared wizard bolus by [bolusId] (from a prior [prepare]'s [ActionProgress.Prepared]): the master
     * delivers the parked dose EXACTLY once. [asAdvisor] = the user took the high-BG "correct now, eat later" branch.
     * Client → round-trip; master → local. Returns [ActionProgress.Applied] or a failure
     * ([ActionProgress.Rejected.NoPendingBolus] if already consumed). [source] tags the master-local records.
     */
    suspend fun commit(bolusId: Long, asAdvisor: Boolean, source: Sources, label: String, correctionU: Double = 0.0): ActionProgress

    /** What to recompute: a synced QuickWizard entry by guid, or the manual wizard's raw inputs. */
    sealed interface WizardSource {

        /** A QuickWizard WIZARD-mode entry; the master computes from the synced entry [guid]. */
        data class QuickWizard(val guid: String) : WizardSource

        /** The manual Bolus Wizard; the master recomputes from the user's raw [inputs] (incl. the profile selection). */
        data class Manual(val inputs: WizardBolusExecutor.WizardInputs) : WizardSource
    }
}
