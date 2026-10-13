package app.aaps.pump.omnipod.common.queue.command

import app.aaps.core.interfaces.queue.CustomCommand

class CommandSuspendDelivery : CustomCommand {

    override val statusDescription = "SUSPEND DELIVERY"
}
