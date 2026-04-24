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
 *  - Working        — OPEN_LOOP / CLOSED_LOOP / CLOSED_LOOP_LGS / DISABLED_LOOP / RESUME: APS
 *                     drives the pump; reconciler does not interfere.
 *  - ZeroDelivery   — DISCONNECTED_PUMP / SUPER_BOLUS: pump must deliver zero (zero-TBR, no EB).
 *  - SuspendedNoTbr — SUSPENDED_BY_USER / SUSPENDED_BY_DST: pump must have no active TBR.
 *  - PumpReported   — SUSPENDED_BY_PUMP: LoopPlugin.runningModePreCheck owns this; reconciler
 *                     stays out of its way.
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
        if (prevBucket == Bucket.PumpReported) return Action.NoOp

        // Entry to zero-delivery: ensure zero-TBR, cancel any extended bolus defensively.
        // Observer de-duplicates if pump already zero.
        if (nextBucket == Bucket.ZeroDelivery) {
            return Action.IssueZeroTbr(cancelExtendedBolus = true)
        }

        // Entry to suspended-no-tbr: cancel any active TBR.
        if (nextBucket == Bucket.SuspendedNoTbr) return Action.CancelTbr

        // Exit from zero-delivery to working: cancel the zero-TBR so APS can take over.
        if (prevBucket == Bucket.ZeroDelivery && nextBucket == Bucket.Working) return Action.CancelTbr

        // Everything else (working→working, suspended-no-tbr→working): no pump action needed.
        // SuspendedNoTbr did not set a TBR; exiting it leaves nothing to clean up.
        return Action.NoOp
    }

    internal enum class Bucket { Working, ZeroDelivery, SuspendedNoTbr, PumpReported }

    internal fun bucketOf(mode: RM.Mode): Bucket = when (mode) {
        RM.Mode.OPEN_LOOP,
        RM.Mode.CLOSED_LOOP,
        RM.Mode.CLOSED_LOOP_LGS,
        RM.Mode.DISABLED_LOOP,
        RM.Mode.RESUME            -> Bucket.Working

        RM.Mode.DISCONNECTED_PUMP,
        RM.Mode.SUPER_BOLUS       -> Bucket.ZeroDelivery

        RM.Mode.SUSPENDED_BY_USER,
        RM.Mode.SUSPENDED_BY_DST  -> Bucket.SuspendedNoTbr

        RM.Mode.SUSPENDED_BY_PUMP -> Bucket.PumpReported
    }
}
