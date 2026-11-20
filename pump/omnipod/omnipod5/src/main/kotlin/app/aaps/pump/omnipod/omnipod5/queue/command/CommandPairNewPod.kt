package app.aaps.pump.omnipod.omnipod5.queue.command

import app.aaps.core.interfaces.queue.CustomCommand

/**
 * Triggers [app.aaps.pump.omnipod.omnipod5.bledriver.comm.O5BleManager.pairNewPod] via
 * [app.aaps.pump.omnipod.omnipod5.O5PumpPlugin.executeCustomCommand] - the only pairing
 * entry point for O5 right now, since no pairing/setup wizard UI exists yet.
 */
class CommandPairNewPod : CustomCommand {

    override val statusDescription = "PAIR NEW POD"
}
