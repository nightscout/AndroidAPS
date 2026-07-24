package app.aaps.pump.carelevo.coordinator

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.carelevo.ble.CarelevoBleSession
import app.aaps.pump.carelevo.common.CarelevoPatch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.jvm.optionals.getOrNull

/**
 * The CommandQueue's connection border. Once a patch is activated the queue owns a real held link on
 * [CarelevoBleSession]: [connect] opens it (one attempt; the queue re-polls [isConnected]), ops reuse it,
 * and [disconnect] closes it after the queue drains — the Dash/Medtrum shape. Pre-activation [isConnected]
 * is forced true so the queue never dials a missing/unactivated device. [isInitialized] stays
 * activation-based. See `_docs/CARELEVO_QUEUE_OWNED_LINK.md`.
 */
@Singleton
class CarelevoConnectionCoordinator @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val carelevoPatch: CarelevoPatch,
    private val bleSession: CarelevoBleSession
) {

    fun isInitialized(): Boolean {
        // Activation-based, NOT connection-based (matches Omnipod Dash / Medtrum): true once the
        // patch is paired and its operational status has been read at least once. Must stay true with
        // no link up, otherwise the loop's applyTBRRequest / applySMBRequest gate aborts with "pump
        // not initialized" during the normal resting state.
        val patchInfo = carelevoPatch.patchInfo.value?.getOrNull() ?: return false
        return patchInfo.mode != null ||
            patchInfo.runningMinutes != null ||
            patchInfo.pumpState != null
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
