package app.aaps.pump.carelevo.command

import app.aaps.core.interfaces.queue.CustomCommand
import app.aaps.pump.carelevo.domain.type.AlarmCause
import app.aaps.pump.carelevo.domain.type.AlarmType

/**
 * Activation operations routed through the AAPS CommandQueue (managed connect-before-execute /
 * reconnect). See [CarelevoActivationExecutor]. Each is a marker command; the executor runs the
 * corresponding use case blocking on the queue thread and returns the result.
 */

class CmdNeedleCheck : CustomCommand {

    override val statusDescription: String = "NEEDLE CHECK"
}

class CmdSetBasal : CustomCommand {

    override val statusDescription: String = "SET BASAL"
}

class CmdAdditionalPriming : CustomCommand {

    override val statusDescription: String = "ADDITIONAL PRIMING"
}

class CmdDiscard : CustomCommand {

    override val statusDescription: String = "DISCARD PATCH"
}

class CmdPumpStop(val durationMin: Int) : CustomCommand {

    override val statusDescription: String = "PUMP STOP"
}

class CmdPumpResume : CustomCommand {

    override val statusDescription: String = "PUMP RESUME"
}

class CmdTimeZoneUpdate(val insulinAmount: Int) : CustomCommand {

    override val statusDescription: String = "TIMEZONE UPDATE"
}

class CmdUpdateMaxBolus(val maxBolusDose: Double) : CustomCommand {

    override val statusDescription: String = "UPDATE MAX BOLUS"
}

class CmdUpdateLowInsulinNotice(val hours: Int) : CustomCommand {

    override val statusDescription: String = "UPDATE LOW INSULIN NOTICE"
}

class CmdUpdateExpiredThreshold(val hours: Int) : CustomCommand {

    override val statusDescription: String = "UPDATE EXPIRY THRESHOLD"
}

class CmdUpdateBuzzer(val on: Boolean) : CustomCommand {

    override val statusDescription: String = "UPDATE BUZZER"
}

class CmdAlarmClear(val alarmId: String, val alarmType: AlarmType, val alarmCause: AlarmCause) : CustomCommand {

    override val statusDescription: String = "ALARM CLEAR"
}

class CmdAlarmClearPatchDiscard(val alarmId: String, val alarmType: AlarmType, val alarmCause: AlarmCause) : CustomCommand {

    override val statusDescription: String = "ALARM CLEAR PATCH DISCARD"
}
