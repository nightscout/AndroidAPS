package app.aaps.pump.omnipod.common.queue.command

import app.aaps.core.interfaces.queue.CustomCommand

/** The first part of a pod activation: pair the pod and prime it. */
class CommandInitializePod : CustomCommand {

    override val statusDescription: String
        get() = "INITIALIZE POD"
}
