package app.aaps.pump.aaruy.services

import app.aaps.core.interfaces.queue.CustomCommand

/** The pump answers each active alarm. Through the command queue, which opens the link for it. */
class AaruyConfirmAlarmsCommand : CustomCommand {

    override val statusDescription: String = "CONFIRM ALARMS"
}

/** The pump is told to forget this phone, so it can be paired again. Through the command queue, which opens the link for it. */
class AaruyUnpairCommand : CustomCommand {

    override val statusDescription: String = "UNPAIR"
}
