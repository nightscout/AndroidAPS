package app.aaps.core.interfaces.clientcontrol

import app.aaps.core.data.ui.ConfirmationLine
import app.aaps.core.interfaces.rx.weardata.EventData

/**
 * Progress/outcome of an action dispatched through [ClientControlActionDispatcher]. A `dispatch`
 * emits a sequence ending in exactly one terminal value.
 *
 * The split between [Rejected] and [Unconfirmed] is the whole point of the round-trip: [Rejected]
 * means "definitely not applied" (safe to tell the user nothing happened), while [Unconfirmed] means
 * "unknown" — the command was sent but no result came back in time, so the real state must be read
 * from normal sync-back, not assumed. A therapy action must never be reported as done on uncertainty.
 *
 * Failures carry a [FailureReason] **code** (not free text) so the message can be localized on the
 * showing device — crucial for a master→client ack, where free text would arrive in the master's
 * locale. [detail] is optional extra context (counts, an exception message) for display/logs.
 */
sealed interface ActionProgress {

    /** Intermediate: the command is being uploaded. */
    data object Sending : ActionProgress

    /** Intermediate (remote only): the master acknowledged receipt and is applying the command. */
    data object MasterExecuting : ActionProgress

    /** Terminal: applied successfully (locally, or confirmed by the master's Done/Ok ack). */
    data object Applied : ActionProgress

    /**
     * Terminal (remote only): a `BolusPrepare` succeeded — the master computed + constraint-capped the dose
     * and returned its confirmation [lines] (and [advisorLines] when [advisorApplies]) for the user to confirm
     * before the matching `BolusCommit`. The client must NOT treat this as "delivered".
     */
    data class Prepared(
        val id: Long,
        val lines: List<ConfirmationLine> = emptyList(),
        val advisorApplies: Boolean = false,
        val advisorLines: List<ConfirmationLine> = emptyList(),
        val wizardDetail: EventData.WizardDetail? = null,
    ) : ActionProgress

    /** Terminal: definitely not applied — master refused / failed, or it never reached NS. */
    data class Rejected(val reason: FailureReason, val detail: String? = null) : ActionProgress

    /** Terminal (remote only): sent, but no confirmation before the deadline / connection lost. Unknown. */
    data class Unconfirmed(val reason: FailureReason, val detail: String? = null) : ActionProgress
}

/**
 * Localizable failure code for [ActionProgress.Rejected] / [ActionProgress.Unconfirmed]. The wire/ack
 * carries the enum **name**; the showing device maps it to a localized string (unknown names → [Unknown]
 * for forward compatibility).
 */
enum class FailureReason {

    // --- transport / round-trip (client side) ---
    NotPaired,       // this device isn't paired with a master
    NotReachable,    // master offline — couldn't send, or connection lost mid-wait (Unconfirmed)
    NoReply,         // sent, but no confirmation before the deadline (Unconfirmed)
    Expired,         // master dropped it as too old to apply safely
    Busy,            // another client-control action is already in progress
    SendFailed,      // upload to Nightscout failed (detail = transport error)

    // --- master-side execution ---
    NoActiveProfile, // insulin: master has no active profile to re-apply onto
    SceneNotFound,   // scene no longer exists
    SceneDisabled,   // scene is disabled
    PartialFailure,  // scene chained but some actions failed (detail = "x/y")
    ExecutionFailed, // master-side execution failed (detail = message)
    ControlDisabled, // master has client control turned OFF — command refused by policy (not an error, not offline)
    NoAction,        // prepare resolved to a no-op (nothing to do, e.g. negative carbs with no COB to remove) — NOT an error
    NoPendingBolus,  // bolus commit: the prepared dose was already consumed / superseded → re-prepare
    BolusComputeFailed, // bolus prepare: master couldn't compute the dose (no BG / profile / pump not ready)

    // --- catch-alls ---
    Internal,        // a bug / unexpected state (shouldn't normally surface)
    Unknown          // unrecognised code (e.g. newer master) or unmapped failure
}
