package info.nightscout.androidaps.plugins.pump.omnipod.common.queue.command

import app.aaps.core.interfaces.queue.CustomCommand
import app.aaps.core.interfaces.resources.ResourceHelper
import info.nightscout.androidaps.plugins.pump.omnipod.common.R

class CommandDisableSuspendAlerts(private val rh: ResourceHelper) : CustomCommand {

    override val statusDescription: String
        get() = rh.gs(R.string.omnipod_common_disable_suspend_alerts)
}
