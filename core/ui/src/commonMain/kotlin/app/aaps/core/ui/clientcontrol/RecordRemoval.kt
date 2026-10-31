package app.aaps.core.ui.clientcontrol

import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ui.ConfirmationLine
import app.aaps.core.interfaces.bolus.BatchAction
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.clientcontrol.ActionProgress
import app.aaps.core.interfaces.clientcontrol.FailureReason
import app.aaps.core.interfaces.clientcontrol.isNotDeliveryError
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowDialog
import app.aaps.core.ui.CoreUiStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The two steps of removing records from a list screen, the same on a master and on a client.
 *
 * [request] asks the master to describe the removal (on a client over the round trip), the screen shows
 * [confirmation] (see `RecordRemovalDialog`), and [confirm] makes the master remove the records. A client never
 * removes a record the master uses on its own: the master would keep using it. See [BatchAction.Remove].
 *
 * A view model builds one with its own scope and [source] (the screen, for the user entry log).
 */
class RecordRemoval(
    private val batchExecutor: BatchExecutor,
    private val rh: TextResolver,
    private val rxBus: RxBus,
    private val config: Config,
    private val scope: CoroutineScope,
    private val source: Sources
) {

    /** The master's description of a prepared removal, waiting for the user's OK. */
    data class Confirmation(val id: Long, val lines: List<ConfirmationLine>)

    private val _confirmation = MutableStateFlow<Confirmation?>(null)
    val confirmation: StateFlow<Confirmation?> = _confirmation.asStateFlow()

    private val label get() = rh.gs(CoreUiStrings.removerecord)

    /** Ask the master to describe removing [actions]. Nothing is removed yet. */
    fun request(actions: List<BatchAction>) {
        if (actions.isEmpty()) return
        scope.launch {
            when (val prepared = batchExecutor.prepare(actions, source, label)) {
                is ActionProgress.Prepared -> _confirmation.value = Confirmation(prepared.id, prepared.lines)
                is ActionProgress.Rejected -> showFailure(prepared)
                else                       -> Unit // Unconfirmed: the round-trip modal tells the user
            }
        }
    }

    /** The user confirmed: the master removes the records. [onApplied] runs after it did, e.g. to leave the selection. */
    fun confirm(onApplied: () -> Unit) {
        val pending = _confirmation.value ?: return
        _confirmation.value = null
        scope.launch {
            when (val result = batchExecutor.commit(pending.id, source, label)) {
                ActionProgress.Applied     -> onApplied()
                is ActionProgress.Rejected -> showFailure(result)
                else                       -> Unit
            }
        }
    }

    /** The user cancelled. The master drops the prepared removal on its own after a while. */
    fun dismiss() {
        _confirmation.value = null
    }

    /**
     * On a client the round-trip modal already shows a failure that came back from the master; only the block
     * before sending (master offline, remote control off) has no modal. On a master every failure is shown here.
     * A commit that was already used (a double tap) stays silent: the removal ran once.
     */
    private fun showFailure(rejected: ActionProgress.Rejected) {
        if (rejected.reason == FailureReason.NoPendingBolus) return
        val message = when {
            rejected.reason.isNotDeliveryError() -> rh.gs(rejected.reason.failText())
            config.AAPSCLIENT                    -> return
            else                                 -> rejected.detail ?: rh.gs(rejected.reason.failText())
        }
        rxBus.send(EventShowDialog.Ok(title = label, message = message))
    }
}
