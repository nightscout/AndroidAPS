package app.aaps.implementation.bolus

import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.bolus.BatchAction
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.bolus.WizardBolusExecutor
import app.aaps.core.interfaces.clientcontrol.ActionProgress
import app.aaps.core.interfaces.clientcontrol.ClientControlActionDispatcher
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [BatchExecutor] implementation — the FIXED/capped multi-action path. It only supplies the client command to
 * dispatch (`BatchPrepare`/`BolusCommit`) and the master executor call (`prepareBatch`/`confirm`); the role
 * branch (client round-trip vs master-local + the result mapping) lives once in [RoleBranch], shared with
 * [WizardExecutorImpl]. The prepare/deliver logic lives once in the executor, so a client's batch lands in that
 * same executor on the master and both roles render the master's identical confirmation.
 */
@Singleton
class BatchExecutorImpl @Inject constructor(
    private val roleBranch: RoleBranch,
    private val wizardBolusExecutor: WizardBolusExecutor
) : BatchExecutor {

    override suspend fun prepare(actions: List<BatchAction>, source: Sources, label: String): ActionProgress =
        roleBranch.prepare(label, ClientControlActionDispatcher.Command.BatchPrepare(actions)) {
            wizardBolusExecutor.prepareBatch(actions)
        }

    override suspend fun commit(id: Long, source: Sources, label: String, pumpDirect: Boolean): ActionProgress =
        roleBranch.commit(label, ClientControlActionDispatcher.Command.BolusCommit(id, pumpDirect = pumpDirect)) { onError ->
            // Decision-B bundle order (bolus + TT) lives in the executor's confirm().
            wizardBolusExecutor.confirm(id, source, onError)
        }
}
