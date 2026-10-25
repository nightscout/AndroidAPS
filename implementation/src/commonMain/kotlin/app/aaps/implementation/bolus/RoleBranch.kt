package app.aaps.implementation.bolus

import app.aaps.core.interfaces.bolus.MasterActionExecutor
import app.aaps.core.interfaces.clientcontrol.ActionProgress
import app.aaps.core.interfaces.clientcontrol.ClientControlActionDispatcher
import app.aaps.core.interfaces.clientcontrol.FailureReason
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.sync.NsClient
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * The shared role branch for the two-step relayed execution path — extracted from [BatchExecutorImpl] and
 * [WizardExecutorImpl], which were structurally identical twins. On a paired CLIENT the request is routed to the
 * master over the signed round-trip (gated on `masterReachable` per the offline-block decision); on a MASTER it
 * runs against the local executor. The only per-path difference is the client [ClientControlActionDispatcher.Command]
 * to dispatch and the master executor call to make — both supplied by the caller — so the gate, the round-trip,
 * and the `PrepareResult`/`ConfirmResult` → [ActionProgress] mapping live exactly once here.
 */
@SingleIn(AppScope::class)
@Inject
class RoleBranch(
    private val dispatcher: ClientControlActionDispatcher,
    private val nsClient: NsClient,
    private val config: Config
) {

    /**
     * Run a PREPARE: client → [clientCommand] over the round-trip (offline → [FailureReason.NotReachable]);
     * master → [masterPrepare] locally, mapping its [MasterActionExecutor.PrepareResult] to [ActionProgress].
     * [label] shows in the round-trip modal.
     */
    suspend fun prepare(
        label: String,
        clientCommand: ClientControlActionDispatcher.Command,
        masterPrepare: suspend () -> MasterActionExecutor.PrepareResult
    ): ActionProgress {
        if (config.AAPSCLIENT) {
            if (!nsClient.masterReachable.value) return ActionProgress.Rejected(clientBlockReason())
            return dispatcher.run(clientCommand, label)
        }
        // Master: prepare locally — NO app-level modal (the caller renders the returned lines as the confirmation).
        return when (val r = masterPrepare()) {
            is MasterActionExecutor.PrepareResult.Preview -> ActionProgress.Prepared(r.bolusId, r.lines, r.advisorApplies, r.advisorLines, r.wizardDetail)
            is MasterActionExecutor.PrepareResult.Error   -> ActionProgress.Rejected(FailureReason.ExecutionFailed, r.message)
            MasterActionExecutor.PrepareResult.NoAction   -> ActionProgress.Rejected(FailureReason.NoAction)
        }
    }

    /**
     * Run a COMMIT: client → [clientCommand] over the round-trip (offline → [FailureReason.NotReachable]);
     * master → [masterConfirm] locally. The async delivery failure is alarmed centrally by the executor, so the
     * [MasterActionExecutor.Failure] captured here only carries a synchronous rejection (it doesn't double-report).
     *
     * A command dropped from the queue on purpose fills that same comment, so
     * [MasterActionExecutor.ConfirmResult.Cancelled] is checked FIRST - it is not a delivery failure and must not
     * reach the caller as one. The return value is checked rather than `failure.cancelled` because a drop does not
     * always produce a failure at all: a drain that reports `success = true` (the "pump not configured" path in
     * `CommandExecutor`) never calls `onError`, and reading only the callback would report [ActionProgress.Applied]
     * for a command that never left the queue.
     */
    suspend fun commit(
        label: String,
        clientCommand: ClientControlActionDispatcher.Command,
        masterConfirm: suspend ((MasterActionExecutor.Failure) -> Unit) -> MasterActionExecutor.ConfirmResult
    ): ActionProgress {
        if (config.AAPSCLIENT) {
            if (!nsClient.masterReachable.value) return ActionProgress.Rejected(clientBlockReason())
            return dispatcher.run(clientCommand, label)
        }
        // Master: deliver the parked dose/bundle locally.
        var failure: MasterActionExecutor.Failure? = null
        val result = masterConfirm { failure = it }
        val err = failure?.comment
        return when {
            result is MasterActionExecutor.ConfirmResult.NoPending -> ActionProgress.Rejected(FailureReason.NoPendingBolus)
            result is MasterActionExecutor.ConfirmResult.Cancelled -> ActionProgress.Rejected(FailureReason.Cancelled, err)
            err != null                                           -> ActionProgress.Rejected(FailureReason.ExecutionFailed, err)
            else                                                  -> ActionProgress.Applied
        }
    }

    /**
     * Drop a prepared entry the user cancelled: [masterDiscard] on a master. A client sends nothing - the master's
     * entry and its SMB pause run out by themselves.
     */
    fun discard(masterDiscard: () -> Unit) {
        if (!config.AAPSCLIENT) masterDiscard()
    }

    /**
     * Why a client request is blocked, when [NsClient.masterReachable] is false. [NsClient.masterControlAllowed]
     * folds into masterReachable, so here it is the discriminator: the master is reachable but has remote control
     * turned OFF ([FailureReason.ControlDisabled]) vs. simply unreachable/unpaired ([FailureReason.NotReachable]).
     * The caller maps the reason to the correct user-facing message (offline vs. control disabled).
     */
    private fun clientBlockReason(): FailureReason =
        if (!nsClient.masterControlAllowed.value) FailureReason.ControlDisabled else FailureReason.NotReachable
}
