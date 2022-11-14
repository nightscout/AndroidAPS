package info.nightscout.androidaps.plugins.pump.omnipod.common.queue.command

import info.nightscout.interfaces.queue.CustomCommand

class CommandDisableSuspendAlerts: CustomCommand {

    override val statusDescription: String
        get() = "DISABLE SUSPEND ALERTS"
}
