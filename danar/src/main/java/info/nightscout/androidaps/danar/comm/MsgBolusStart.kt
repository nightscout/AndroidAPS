package info.nightscout.androidaps.danar.comm

import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.dana.DanaPump

class MsgBolusStart(
    private val aapsLogger: AAPSLogger,
    constraintChecker: ConstraintChecker,
    private val danaPump: DanaPump,
    private var amount: Double
) : MessageBase() {

    init {
        SetCommand(0x0102)
        // HARDCODED LIMIT
        amount = constraintChecker.applyBolusConstraints(Constraint(amount)).value()
        AddParamInt((amount * 100).toInt())
        aapsLogger.debug(LTag.PUMPBTCOMM, "Bolus start : $amount")
    }

    override fun handleMessage(bytes: ByteArray) {
        val errorCode = intFromBuff(bytes, 0, 1)
        if (errorCode != 2) {
            failed = true
            aapsLogger.debug(LTag.PUMPBTCOMM, "Messsage response: $errorCode FAILED!!")
        } else {
            failed = false
            aapsLogger.debug(LTag.PUMPBTCOMM, "Messsage response: $errorCode OK")
        }
        danaPump.bolusStartErrorCode = errorCode
    }
}