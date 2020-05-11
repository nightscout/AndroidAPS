package info.nightscout.androidaps.danar.comm

import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker

class MsgSetExtendedBolusStart(
    private val aapsLogger: AAPSLogger,
    constraintChecker: ConstraintChecker,
    private var amount: Double,
    private var halfhours: Byte

) : MessageBase() {

    init {
        SetCommand(0x0407)
        aapsLogger.debug(LTag.PUMPBTCOMM, "New message")
        // HARDCODED LIMITS
        if (halfhours < 1) halfhours = 1
        if (halfhours > 16) halfhours = 16
        amount = constraintChecker.applyBolusConstraints(Constraint(amount)).value()
        AddParamInt((amount * 100).toInt())
        AddParamByte(halfhours)
        aapsLogger.debug(LTag.PUMPBTCOMM, "Set extended bolus start: " + (amount * 100).toInt() / 100.0 + "U halfhours: " + halfhours.toInt())
    }

    override fun handleMessage(bytes: ByteArray) {
        val result = intFromBuff(bytes, 0, 1)
        if (result != 1) {
            failed = true
            aapsLogger.debug(LTag.PUMPBTCOMM, "Set extended bolus start result: $result FAILED!!!")
        } else {
            aapsLogger.debug(LTag.PUMPBTCOMM, "Set extended bolus start result: $result")
        }
    }
}