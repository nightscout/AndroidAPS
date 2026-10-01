package app.aaps.core.objects.runningMode

import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowSnackbar
import app.aaps.core.keys.interfaces.TextRef

/**
 * Pre-check helper for UI / sync / automation entry points that call CommandQueue.
 *
 * The queue-level [PumpCommandGate] check is a last-resort safety net: by the time a command reaches
 * the queue and is rejected, the caller's failure callback fires — which in many call sites
 * plays the "treatment delivery error" alarm. That alarm is appropriate for real pump failures,
 * not for commands that were intentionally blocked by the running mode.
 *
 * Entry points use this guard to decline the action quietly (snackbar, SMS reply, watch
 * response) before ever touching CommandQueue.
 */
class RunningModeGuard(
    private val loop: Loop,
    private val rh: TextResolver,
    private val rxBus: RxBus
) {

    /**
     * Returns the localized rejection message if the current running mode forbids [kind],
     * or null if the command is allowed.
     *
     * Use this in callers that render their own error channel (SMS reply text, Wear response,
     * Garmin callback, etc.).
     */
    suspend fun rejectionMessage(kind: PumpCommandGate.CommandKind): String? {
        val mode = loop.runningMode()
        val decision = PumpCommandGate.check(mode, kind)
        return (decision as? PumpCommandGate.Decision.Reject)?.let { rh.gs(it.reason.toTextRef()) }
    }

    /**
     * UI convenience: if the gate rejects [kind], sends a Warning snackbar and returns true.
     * Callers should early-return on true:
     * ```
     * if (runningModeGuard.checkWithSnackbar(PumpCommandGate.CommandKind.BOLUS)) return
     * commandQueue.bolus(...)
     * ```
     */
    suspend fun checkWithSnackbar(kind: PumpCommandGate.CommandKind): Boolean {
        val msg = rejectionMessage(kind) ?: return false
        rxBus.send(EventShowSnackbar(msg, EventShowSnackbar.Type.Warning))
        return true
    }

    private fun PumpCommandGate.Reason.toTextRef(): TextRef = when (this) {
        PumpCommandGate.Reason.PUMP_DISCONNECTED       -> InterfacesStrings.pump_disconnected
        PumpCommandGate.Reason.LOOP_SUSPENDED_DST,
        PumpCommandGate.Reason.SUPER_BOLUS_ACTIVE      -> InterfacesStrings.loopsuspended

        PumpCommandGate.Reason.PUMP_REPORTED_SUSPENDED -> InterfacesStrings.pumpsuspended
    }
}
