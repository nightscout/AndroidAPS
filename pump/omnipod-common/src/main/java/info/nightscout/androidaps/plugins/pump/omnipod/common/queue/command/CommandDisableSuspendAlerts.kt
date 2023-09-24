package info.nightscout.androidaps.plugins.pump.omnipod.common.queue.command

import app.aaps.core.interfaces.queue.CustomCommand

class CommandDisableSuspendAlerts : CustomCommand {

    override val statusDescription: String
        get() = "DISABLE SUSPEND ALERTS"
}
