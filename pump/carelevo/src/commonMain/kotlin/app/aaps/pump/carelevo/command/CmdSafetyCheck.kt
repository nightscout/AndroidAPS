package app.aaps.pump.carelevo.command

import app.aaps.core.interfaces.queue.CustomCommand

/**
 * Activation safety-check routed through the AAPS CommandQueue so it gets the queue's managed
 * connect-before-execute / reconnect lifecycle (instead of the wizard doing a direct BLE call that
 * silently no-ops when the link has dropped).
 */
class CmdSafetyCheck : CustomCommand {

    override val statusDescription: String = "SAFETY CHECK"
}
