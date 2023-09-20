package info.nightscout.androidaps.danar.comm

import dagger.android.HasAndroidInjector
import info.nightscout.core.constraints.ConstraintObject
import info.nightscout.rx.logging.LTag

class MsgSetExtendedBolusStart(
    injector: HasAndroidInjector,
    private var amount: Double,
    private var halfHours: Byte

) : MessageBase(injector) {

    init {
        setCommand(0x0407)
        aapsLogger.debug(LTag.PUMPBTCOMM, "New message")
        // HARDCODED LIMITS
        if (halfHours < 1) halfHours = 1
        if (halfHours > 16) halfHours = 16
        amount = constraintChecker.applyBolusConstraints(ConstraintObject(amount, aapsLogger)).value()
        addParamInt((amount * 100).toInt())
        addParamByte(halfHours)
        aapsLogger.debug(LTag.PUMPBTCOMM, "Set extended bolus start: " + (amount * 100).toInt() / 100.0 + "U halfHours: " + halfHours.toInt())
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