package app.aaps.pump.carelevo.coordinator

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.carelevo.ble.CarelevoBleSession
import app.aaps.pump.carelevo.common.CarelevoPatch
import app.aaps.pump.carelevo.common.latest
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * The CommandQueue's connection border. Once a patch is activated the queue owns a real held link on
 * [CarelevoBleSession]: [connect] opens it (one attempt; the queue re-polls [isConnected]), ops reuse it,
 * and [disconnect] closes it after the queue drains — the Dash/Medtrum shape. Pre-activation [isConnected]
 * is forced true so the queue never dials a missing/unactivated device. [isInitialized] stays
 * activation-based. See `_docs/CARELEVO_QUEUE_OWNED_LINK.md`.
 */
@SingleIn(AppScope::class)
class CarelevoConnectionCoordinator @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val carelevoPatch: CarelevoPatch,
    private val bleSession: CarelevoBleSession
) {

    /**
     * True once the activation is complete and the patch status has been read at least once (as Omnipod Dash and
     * Medtrum). Must stay true with no link up, otherwise the loop's applyTBRRequest / applySMBRequest gate aborts
     * with "pump not initialized" during the normal resting state.
     *
     * Not before the activation is complete: everything that doses checks this, and a status read alone came early
     * enough that on a real patch (2026-11-18) the loop set temp basals during the safety check, before the needle
     * was in, and gave an SMB before the last step. A patch stored by an older build has no flag; it counts once its
     * needle check passed.
     */
    fun isInitialized(): Boolean {
        val patchInfo = carelevoPatch.patchInfo.latest ?: return false
        val activated = patchInfo.activationComplete ?: (patchInfo.checkNeedle == true)
        return activated && (
            patchInfo.mode != null ||
                patchInfo.runningMinutes != null ||
                patchInfo.pumpState != null
            )
    }

    /**
     * Pre-activation → always true so the queue never dials a missing/unactivated device (the Medtrum
     * fallback). Post-activation → the real held-link state, so the QueueWorker's connect-before-execute
     * loop drives [connect]/[disconnect] against a link it actually owns.
     */
    fun isConnected(): Boolean = if (!isInitialized()) true else bleSession.connected.value

    fun isConnecting(): Boolean = bleSession.isConnecting.value

    /** Fire one connect attempt against the stored patch MAC; the queue re-polls [isConnected]. */
    fun connect(reason: String) {
        val address = carelevoPatch.getPatchInfoAddress() ?: run {
            aapsLogger.debug(LTag.PUMPCOMM, "connect: no patch address, reason=$reason")
            return
        }
        bleSession.requestConnect(address, reason)
    }

    fun disconnect(reason: String) = bleSession.requestDisconnect(reason)

    fun stopConnecting() = bleSession.requestDisconnect("stopConnecting")
}
