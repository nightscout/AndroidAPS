package app.aaps.pump.omnipod.common.queue.command

import app.aaps.core.interfaces.queue.CustomCommand

class CommandSilenceAlerts : CustomCommand {

    override val statusDescription = "ACKNOWLEDGE ALERTS"
}
