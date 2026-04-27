package app.aaps.core.objects.runningMode

import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowSnackbar
import app.aaps.core.ui.R
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pre-check helper for UI / sync / automation entry points that call CommandQueue.
 *
 * The queue-level [TbrGate] check is a last-resort safety net: by the time a command reaches
 * the queue and is rejected, the caller's failure callback fires — which in many call sites
 * plays the "treatment delivery error" alarm. That alarm is appropriate for real pump failures,
 * not for commands that were intentionally blocked by the running mode.
 *
 * Entry points use this guard to decline the action quietly (snackbar, SMS reply, watch
 * response) before ever touching CommandQueue.
 */
@Singleton
class RunningModeGuard @Inject constructor(
    private val loop: Loop,
    private val rh: ResourceHelper,
    private val rxBus: RxBus
) {

    /**
     * Returns the localized rejection message if the current running mode forbids [kind],
     * or null if the command is allowed.
     *
     * Use this in callers that render their own error channel (SMS reply text, Wear response,
     * Garmin callback, etc.).
     */
    fun rejectionMessage(kind: TbrGate.CommandKind): String? {
        // TODO: Loop.runningMode() is now suspend; this guard is invoked from many synchronous
        // entry points (Compose ViewModels' non-suspend handlers, BolusWizard.confirmAndExecute,
        // SmsCommunicator, Wear DataHandlerMobile). The underlying call is a fast persistence read.
        // Localizing runBlocking here avoids cascading suspend through dozens of call sites.
        val mode = runBlocking { loop.runningMode() }
        val decision = TbrGate.check(mode, kind)
        return (decision as? TbrGate.Decision.Reject)?.let { rh.gs(it.reason.toStringRes()) }
    }

    /**
     * UI convenience: if the gate rejects [kind], sends a Warning snackbar and returns true.
     * Callers should early-return on true:
     * ```
     * if (runningModeGuard.checkWithSnackbar(TbrGate.CommandKind.BOLUS)) return
     * commandQueue.bolus(...)
     * ```
     */
    fun checkWithSnackbar(kind: TbrGate.CommandKind): Boolean {
        val msg = rejectionMessage(kind) ?: return false
        rxBus.send(EventShowSnackbar(msg, EventShowSnackbar.Type.Warning))
        return true
    }

    private fun TbrGate.Reason.toStringRes(): Int = when (this) {
        TbrGate.Reason.PUMP_DISCONNECTED       -> R.string.pump_disconnected
        TbrGate.Reason.LOOP_SUSPENDED_USER,
        TbrGate.Reason.LOOP_SUSPENDED_DST,
        TbrGate.Reason.SUPER_BOLUS_ACTIVE      -> R.string.loopsuspended

        TbrGate.Reason.PUMP_REPORTED_SUSPENDED -> R.string.pumpsuspended
    }
}
