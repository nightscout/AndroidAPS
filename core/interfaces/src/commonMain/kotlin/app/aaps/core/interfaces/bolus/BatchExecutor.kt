package app.aaps.core.interfaces.bolus

import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.clientcontrol.ActionProgress

/**
 * Role-transparent multi-action apply: a dialog builds the [BatchAction] list and calls [apply]. On a paired
 * CLIENT the batch is routed to the master over the signed round-trip; on a MASTER it's applied locally — one
 * transaction either way (`_docs/CLIENT_BATCH_COMMAND.md`). The caller is role-agnostic. Offline on a client →
 * the whole batch is blocked (Rejected.NotReachable), not partially applied.
 */
interface BatchExecutor {

    /**
     * Two-step PREPARE: ask the master to cap + build the MERGED confirmation for [actions] and return
     * [ActionProgress.Prepared] (master's `bolusId` + lines) for the dialog to render, or a [ActionProgress.Rejected].
     * Client → signed round-trip; master → local prepare (no app-level modal — the dialog shows the lines). Offline on
     * a client → Rejected.NotReachable. [source] tags the master-local records; [label] shows in the round-trip modal.
     */
    suspend fun prepare(actions: List<BatchAction>, source: Sources, label: String): ActionProgress

    /**
     * Commit a prepared batch by [id] (the `bolusId` from a prior [prepare]'s [ActionProgress.Prepared]): the
     * master applies the parked bundle (bolus + TT per the ordering rule) EXACTLY once. Client → round-trip; master →
     * local. Returns [ActionProgress.Applied] or a failure ([ActionProgress.Rejected.NoPendingBolus] if already consumed).
     *
     * [pumpDirect] = this commit drives a slow pump command on the master (a TBR / extended-bolus SET or CANCEL) that
     * blocks until the pump enacts it — the client then waits the longer pump round-trip window for the real result
     * instead of giving up early. Leave false for a bolus/carbs commit (fast queue-ack + progress mirror).
     */
    suspend fun commit(id: Long, source: Sources, label: String, pumpDirect: Boolean = false): ActionProgress
}
