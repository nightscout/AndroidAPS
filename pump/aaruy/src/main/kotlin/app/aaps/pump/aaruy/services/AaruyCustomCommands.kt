package app.aaps.pump.aaruy.services

import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.CustomCommand

/** The pump answers each active alarm. Through the command queue, which opens the link for it. */
class AaruyConfirmAlarmsCommand : CustomCommand {

    override val statusDescription: String = "CONFIRM ALARMS"
}

/** The pump is told to forget this phone, so it can be paired again. Through the command queue, which opens the link for it. */
class AaruyUnpairCommand : CustomCommand {

    override val statusDescription: String = "UNPAIR"
}

// The steps of a reservoir change that move the motor. See AaruyReplaceSteps.

class AaruyRewindCommand : CustomCommand {

    override val statusDescription: String = "REWIND"
}

class AaruyPrimeCommand : CustomCommand {

    override val statusDescription: String = "PRIME"
}

/** @param needleType 0 is a 6 mm cannula, 1 is 9 mm */
class AaruyFillCannulaCommand(val needleType: Int) : CustomCommand {

    override val statusDescription: String = "FILL CANNULA"
}

class AaruyStartBasalCommand : CustomCommand {

    override val statusDescription: String = "START BASAL"
}

/** UNKNOWN: it began and its end was not seen, or the link was lost after the command went out. */
enum class AaruyMotorResult { DONE, NOT_RUN, UNKNOWN }

/** Through the queue: a step that may have reached the pump is enacted, but not a success. */
fun PumpEnactResult.withMotorResult(result: AaruyMotorResult): PumpEnactResult = apply {
    success = result == AaruyMotorResult.DONE
    enacted = result != AaruyMotorResult.NOT_RUN
}

/** A command the queue never ran, as without a connection, is not enacted: NOT_RUN. */
fun PumpEnactResult.toMotorResult(): AaruyMotorResult = when {
    success -> AaruyMotorResult.DONE
    enacted -> AaruyMotorResult.UNKNOWN
    else    -> AaruyMotorResult.NOT_RUN
}
