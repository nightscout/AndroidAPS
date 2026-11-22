package app.aaps.pump.omnipod.common.queue.command

import app.aaps.core.interfaces.queue.CustomCommand

/** The second part of a pod activation: set the basal program and insert the cannula. */
class CommandInsertCannula : CustomCommand {

    override val statusDescription: String
        get() = "INSERT CANNULA"
}
