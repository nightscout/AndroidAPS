package app.aaps.pump.omnipod.common.queue.command

import app.aaps.core.interfaces.queue.CustomCommand

class CommandDeliverBasalCorrection : CustomCommand {

    override val statusDescription = "BASAL COMPENSATION BOLUS"
}
