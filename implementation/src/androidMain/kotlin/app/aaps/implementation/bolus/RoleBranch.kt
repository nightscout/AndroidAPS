package app.aaps.implementation.bolus

import app.aaps.core.interfaces.bolus.WizardBolusExecutor
import app.aaps.core.interfaces.clientcontrol.ActionProgress
import app.aaps.core.interfaces.clientcontrol.ClientControlActionDispatcher
import app.aaps.core.interfaces.clientcontrol.FailureReason
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.sync.NsClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The shared role branch for the two-step relayed execution path — extracted from [BatchExecutorImpl] and
 * [WizardExecutorImpl], which were structurally identical twins. On a paired CLIENT the request is routed to the
 * master over the signed round-trip (gated on `masterReachable` per the offline-block decision); on a MASTER it
 * runs against the local executor. The only per-path difference is the client [ClientControlActionDispatcher.Command]
 * to dispatch and the master executor call to make — both supplied by the caller — so the gate, the round-trip,
 * and the `PrepareResult`/`ConfirmResult` → [ActionProgress] mapping live exactly once here.
 */
@Singleton
class RoleBranch @Inject constructor(
    private val dispatcher: ClientControlActionDispatcher,
    private val nsClient: NsClient,
    private val config: Config
) {

    /**
     * Run a PREPARE: client → [clientCommand] over the round-trip (offline → [FailureReason.NotReachable]);
     * master → [masterPrepare] locally, mapping its [WizardBolusExecutor.PrepareResult] to [ActionProgress].
     * [label] shows in the round-trip modal.
     */
    suspend fun prepare(
        label: String,
        clientCommand: ClientControlActionDispatcher.Command,
        masterPrepare: suspend () -> WizardBolusExecutor.PrepareResult
    ): ActionProgress {
        if (config.AAPSCLIENT) {
            if (!nsClient.masterReachable.value) return ActionProgress.Rejected(clientBlockReason())
            return dispatcher.run(clientCommand, label)
        }
        // Master: prepare locally — NO app-level modal (the caller renders the returned lines as the confirmation).
        return when (val r = masterPrepare()) {
            is WizardBolusExecutor.PrepareResult.Preview -> ActionProgress.Prepared(r.bolusId, r.lines, r.advisorApplies, r.advisorLines, r.wizardDetail)
            is WizardBolusExecutor.PrepareResult.Error   -> ActionProgress.Rejected(FailureReason.ExecutionFailed, r.message)
            WizardBolusExecutor.PrepareResult.NoAction   -> ActionProgress.Rejected(FailureReason.NoAction)
        }
    }

    /**
     * Run a COMMIT: client → [clientCommand] over the round-trip (offline → [FailureReason.NotReachable]);
     * master → [masterConfirm] locally. The async delivery failure is alarmed centrally by the executor, so the
     * `onError` captured here only carries a synchronous rejection comment (it doesn't double-report).
     */
    suspend fun commit(
        label: String,
        clientCommand: ClientControlActionDispatcher.Command,
        masterConfirm: suspend ((String) -> Unit) -> WizardBolusExecutor.ConfirmResult
    ): ActionProgress {
        if (config.AAPSCLIENT) {
            if (!nsClient.masterReachable.value) return ActionProgress.Rejected(clientBlockReason())
            return dispatcher.run(clientCommand, label)
        }
        // Master: deliver the parked dose/bundle locally.
        var error: String? = null
        val result = masterConfirm { error = it }
        val err = error
        return when {
            result is WizardBolusExecutor.ConfirmResult.NoPending -> ActionProgress.Rejected(FailureReason.NoPendingBolus)
            err != null                                           -> ActionProgress.Rejected(FailureReason.ExecutionFailed, err)
            else                                                  -> ActionProgress.Applied
        }
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
