package app.aaps.pump.omnipod.common.queue.command

import app.aaps.core.interfaces.queue.CustomCommand

class CommandHandleTimeChange(val requestedByUser: Boolean) : CustomCommand {

    override val statusDescription = "HANDLE TIME CHANGE"
}
