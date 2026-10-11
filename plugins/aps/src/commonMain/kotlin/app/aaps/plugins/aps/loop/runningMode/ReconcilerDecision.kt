package app.aaps.plugins.aps.loop.runningMode

import app.aaps.core.data.model.RM

/**
 * Pure transition logic for the running-mode reconciler.
 *
 * Given the previous and next active running modes, computes the *intended* pump-side action.
 * This is purely policy; the reconciler observer is responsible for applying idempotency
 * (checking current pump state and skipping commands that would be no-ops).
 *
 * Buckets:
 *  - Working        — OPEN_LOOP / CLOSED_LOOP / CLOSED_LOOP_LGS / RESUME: APS drives the pump;
 *                     reconciler does not interfere.
 *  - ZeroDelivery   — DISCONNECTED_PUMP / SUPER_BOLUS: pump must deliver zero (zero-TBR, no EB).
 *  - SuspendedNoTbr — SUSPENDED_BY_DST: pump must have no active TBR.
 *  - Stopped        — DISABLED_LOOP / SUSPENDED_BY_USER: APS is off, no APS-driven TBR should
 *                     remain. Pump is otherwise usable for manual delivery; treated identically
 *                     to SuspendedNoTbr at the gate (cancel any active TBR on entry).
 *                     SUSPENDED_BY_USER is the temporary counterpart of DISABLED_LOOP.
 *  - PumpReported   — SUSPENDED_BY_PUMP: LoopPlugin.runningModePreCheck owns this; reconciler
 *                     stays out of its way, except on the way back into a zero-delivery mode (see
 *                     [decide]).
 */
object ReconcilerDecision {

    sealed interface Action {
        data object NoOp : Action
        data object CancelTbr : Action
        data class IssueZeroTbr(val cancelExtendedBolus: Boolean) : Action
    }

    fun decide(prev: RM.Mode, next: RM.Mode): Action {
        val prevBucket = bucketOf(prev)
        val nextBucket = bucketOf(next)

        // Precheck owns SUSPENDED_BY_PUMP entry and exit.
        if (nextBucket == Bucket.PumpReported) return Action.NoOp
        // The pump runs again while the user's disconnect (or a super bolus) is still active. The zero
        // TBR set when that mode started may be gone: a reservoir change or a suspend on the pump can
        // clear it. Without this the pump gave profile basal while AAPS showed it disconnected (#4486).
        // Observer de-duplicates if the pump is still zero.
        if (prevBucket == Bucket.PumpReported) {
            return if (nextBucket == Bucket.ZeroDelivery) Action.IssueZeroTbr(cancelExtendedBolus = true) else Action.NoOp
        }

        // Entry to zero-delivery: ensure zero-TBR, cancel any extended bolus defensively.
        // Observer de-duplicates if pump already zero.
        if (nextBucket == Bucket.ZeroDelivery) {
            return Action.IssueZeroTbr(cancelExtendedBolus = true)
        }

        // The same stopped mode again (the user extends a suspend): the entry already cancelled the
        // APS TBR and the loop has not run since, so a TBR active now was set by the user by hand.
        // Leave it alone.
        if (prev == next && nextBucket == Bucket.Stopped) return Action.NoOp

        // Entry to suspended-no-tbr or stopped: cancel any active TBR.
        if (nextBucket == Bucket.SuspendedNoTbr || nextBucket == Bucket.Stopped) return Action.CancelTbr

        // Exit from zero-delivery to working: cancel the zero-TBR so APS can take over.
        if (prevBucket == Bucket.ZeroDelivery && nextBucket == Bucket.Working) return Action.CancelTbr

        // Everything else (working→working, suspended-no-tbr→working, stopped→working): no pump
        // action needed. SuspendedNoTbr / Stopped did not set a TBR; exiting them leaves nothing
        // to clean up.
        return Action.NoOp
    }

    internal enum class Bucket { Working, ZeroDelivery, SuspendedNoTbr, Stopped, PumpReported }

    internal fun bucketOf(mode: RM.Mode): Bucket = when (mode) {
        RM.Mode.OPEN_LOOP,
        RM.Mode.CLOSED_LOOP,
        RM.Mode.CLOSED_LOOP_LGS,
        RM.Mode.RESUME            -> Bucket.Working

        RM.Mode.DISABLED_LOOP,
        RM.Mode.SUSPENDED_BY_USER -> Bucket.Stopped

        RM.Mode.DISCONNECTED_PUMP,
        RM.Mode.SUPER_BOLUS       -> Bucket.ZeroDelivery

        RM.Mode.SUSPENDED_BY_DST  -> Bucket.SuspendedNoTbr

        RM.Mode.SUSPENDED_BY_PUMP -> Bucket.PumpReported
    }
}
